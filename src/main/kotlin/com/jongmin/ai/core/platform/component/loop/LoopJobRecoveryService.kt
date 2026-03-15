package com.jongmin.ai.core.platform.component.loop

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.entity.LoopJobState
import com.jongmin.ai.core.platform.repository.LoopJobCheckpointRepository
import com.jongmin.ai.core.platform.repository.LoopJobRedisRepository
import com.jongmin.ai.core.platform.repository.LoopJobRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Loop Job 복구 서비스
 *
 * 좀비 Job 감지 및 자동 복구를 담당한다.
 * - 애플리케이션 시작 시 이전 인스턴스의 좀비 Job 복구
 * - 주기적인 좀비 Job 감지 및 복구
 * - 체크포인트 기반 워크플로우 재개
 *
 * ## 복구 시나리오
 * 1. 인스턴스 재시작: ApplicationReadyEvent에서 복구 시도
 * 2. Heartbeat 타임아웃: @Scheduled(30초)에서 좀비 감지
 * 3. Redis 락 만료: tryAcquireRecoveryLock으로 중복 복구 방지
 *
 * ## 트랜잭션 처리
 * 내부에서 @Transactional 메서드를 호출하면 self-invocation 문제가 발생하므로,
 * 트랜잭션이 필요한 로직은 LoopJobRecoveryTransactionHelper로 분리했다.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Service
class LoopJobRecoveryService(
  private val loopJobRepository: LoopJobRepository,
  private val loopJobCheckpointRepository: LoopJobCheckpointRepository,
  private val loopJobRedisRepository: LoopJobRedisRepository,
  private val loopJobService: LoopJobService,
  private val transactionHelper: LoopJobRecoveryTransactionHelper,
  @param:Value($$"${app.instance-id:default-instance}")
  private val instanceId: String,
  @param:Value($$"${app.loop-job.recovery.enabled:true}")
  private val recoveryEnabled: Boolean,
  @param:Value($$"${app.loop-job.zombie-detection.enabled:true}")
  private val zombieDetectionEnabled: Boolean,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** Heartbeat 타임아웃 (초) */
    const val HEARTBEAT_TIMEOUT_SECONDS = 60L

    /** 오래된 완료 Job 정리 기준 (일) */
    const val OLD_JOB_CLEANUP_DAYS = 30L
  }

  // ========== 애플리케이션 시작 시 복구 ==========

  /**
   * 애플리케이션 시작 시 실행 중이던 Job 복구
   *
   * 서버 재시작 시 RUNNING 상태인 모든 Job을 복구한다.
   * Heartbeat 타임아웃과 관계없이 복구를 시도한다.
   * (서버가 재시작되면 실제로 실행 중인 스레드가 없으므로)
   *
   * - RUNNING 상태인 Job: 무조건 복구 시도
   * - PAUSED 상태인 Job: 그대로 유지 (사용자가 resume 호출 필요)
   */
  @EventListener(ApplicationReadyEvent::class)
  fun onApplicationReady() {
    if (!recoveryEnabled) {
      kLogger.info { "Loop Job 복구가 비활성화되어 있습니다." }
      return
    }

    kLogger.info { "🔧 Loop Job 복구 서비스 시작 - instanceId: $instanceId" }

    try {
      // 복구 락 획득 (중복 복구 방지)
      if (!loopJobRedisRepository.tryAcquireRecoveryLock(instanceId)) {
        kLogger.info { "다른 인스턴스에서 복구 진행 중 - 스킵" }
        return
      }

      try {
        // 서버 시작 시에는 RUNNING 상태인 모든 Job을 복구 (Heartbeat 체크 없이)
        recoverRunningJobsOnStartup()
        cleanupRedisRunningSet()
      } finally {
        loopJobRedisRepository.releaseRecoveryLock(instanceId)
      }
    } catch (e: Exception) {
      kLogger.error(e) { "Loop Job 복구 중 오류 발생" }
    }
  }

  /**
   * 서버 시작 시 실행 중이던 모든 Job 복구
   *
   * 서버가 재시작되면 실제로 실행 중인 스레드가 없으므로,
   * Heartbeat 타임아웃과 관계없이 복구 대상 Job들을 찾아서 복구한다.
   *
   * 복구 대상 상태:
   * - RUNNING: 실행 중이던 Job
   * - RECOVERING: 이전 복구 시도 중 서버가 죽은 경우
   *
   * ⚠️ 중요: 트랜잭션이 완료된 후에 VirtualThread를 시작해야 한다.
   * @Transactional 메서드 안에서 Thread를 시작하면 커밋 전에 실행되어 문제 발생.
   */
  private fun recoverRunningJobsOnStartup() {
    // RUNNING과 RECOVERING 상태 모두 복구 대상
    val runningJobs = loopJobRepository.findByState(LoopJobState.RUNNING)
    val recoveringJobs = loopJobRepository.findByState(LoopJobState.RECOVERING)
    val jobsToRecover = runningJobs + recoveringJobs

    if (jobsToRecover.isEmpty()) {
      kLogger.info { "복구할 Job이 없습니다. (RUNNING: 0, RECOVERING: 0)" }
      return
    }

    kLogger.warn { "🔄 서버 시작 시 복구 대상 Job: ${jobsToRecover.size}개 (RUNNING: ${runningJobs.size}, RECOVERING: ${recoveringJobs.size})" }

    // 자동 재시작이 필요한 Job들 (트랜잭션 완료 후 처리)
    val jobsToAutoRestart = mutableListOf<Pair<String, Long>>() // jobId, accountId

    jobsToRecover.forEach { job ->
      kLogger.info { "  └─ Job 복구 시도: ${job.id}, state: ${job.state}, workflowId: ${job.workflowId}, 마지막 heartbeat: ${job.lastHeartbeat}" }

      try {
        // 체크포인트 확인
        val checkpoint = loopJobCheckpointRepository.findFirstByJobIdOrderByTimestampDesc(job.id)
        val lastIteration = checkpoint?.iterationCount ?: job.currentCount

        // Helper를 통한 트랜잭션 메서드 호출 (self-invocation 방지)
        // accountId가 반환되면 자동 재시작 필요
        val accountIdForRestart = transactionHelper.recoverFromCheckpoint(job.id, lastIteration)

        if (accountIdForRestart != null) {
          jobsToAutoRestart.add(job.id to accountIdForRestart)
        }

      } catch (e: Exception) {
        kLogger.error(e) { "Job 복구 실패: ${job.id}" }
        transactionHelper.markJobAsRecoveryFailed(job.id, "서버 시작 시 복구 실패: ${e.message}")
      }
    }

    // 트랜잭션 완료 후 자동 재시작 (VirtualThread)
    autoRestartJobsAsync(jobsToAutoRestart, "자동 재시작")
  }

  // ========== 주기적인 좀비 Job 감지 ==========

  // 향후 k8s batch job 으로 호출해야한다.
  /**
   * 주기적인 좀비 Job 감지 및 복구
   *
   * 30초마다 실행되어 Heartbeat 타임아웃된 좀비 Job을 감지하고 복구한다.
   * Redis 락으로 여러 인스턴스 간 중복 실행을 방지한다.
   */
  fun scheduledZombieDetection() {
    if (!zombieDetectionEnabled) {
      return
    }

    try {
      // 복구 락 획득 시도
      if (!loopJobRedisRepository.tryAcquireRecoveryLock(instanceId)) {
        kLogger.debug { "다른 인스턴스에서 좀비 감지 진행 중 - 스킵" }
        return
      }

      try {
        val result = loopJobService.detectAndRecoverZombieJobs()
        if (result.detected > 0) {
          kLogger.info { "🧟 좀비 Job 감지 완료 - 감지: ${result.detected}, 복구: ${result.recovered}" }
        }
      } finally {
        loopJobRedisRepository.releaseRecoveryLock(instanceId)
      }
    } catch (e: Exception) {
      kLogger.error(e) { "좀비 Job 감지 중 오류 발생" }
    }
  }

  // ========== 오래된 Job 정리 ==========

  /**
   * 오래된 완료 Job 정리
   *
   * 매일 자정에 실행되어 30일 이상된 완료/취소/에러 Job을 삭제한다.
   */
  @Scheduled(cron = "0 0 0 * * ?") // 매일 자정
  fun cleanupOldJobs() {
    try {
      val (deletedJobs, deletedCheckpoints) = transactionHelper.cleanupOldJobs(OLD_JOB_CLEANUP_DAYS)

      if (deletedJobs > 0 || deletedCheckpoints > 0) {
        kLogger.info { "🧹 오래된 Job 정리 완료 - jobs: $deletedJobs, checkpoints: $deletedCheckpoints" }
      }
    } catch (e: Exception) {
      kLogger.error(e) { "오래된 Job 정리 중 오류 발생" }
    }
  }

  // ========== 복구 로직 ==========

  /**
   * 좀비 Job 복구
   *
   * RUNNING 상태인데 Heartbeat가 오래된 Job들을 복구한다.
   * 체크포인트가 있으면 해당 위치부터 재개, 없으면 에러 상태로 전환.
   *
   * ⚠️ 중요: 트랜잭션이 완료된 후에 VirtualThread를 시작해야 한다.
   */
  fun recoverZombieJobs() {
    val cutoffTime = now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS)
    val zombieJobs = loopJobRepository.findByStateAndLastHeartbeatBefore(
      LoopJobState.RUNNING, cutoffTime
    )

    if (zombieJobs.isEmpty()) {
      kLogger.info { "복구할 좀비 Job이 없습니다." }
      return
    }

    kLogger.warn { "🧟 복구 대상 좀비 Job: ${zombieJobs.size}개" }

    // 자동 재시작이 필요한 Job들 (트랜잭션 완료 후 처리)
    val jobsToAutoRestart = mutableListOf<Pair<String, Long>>() // jobId, accountId

    zombieJobs.forEach { job ->
      kLogger.info { "  └─ Job 복구 시도: ${job.id}, 마지막 heartbeat: ${job.lastHeartbeat}" }

      try {
        // 체크포인트 확인
        val checkpoint = loopJobCheckpointRepository.findFirstByJobIdOrderByTimestampDesc(job.id)

        if (checkpoint != null) {
          // Helper를 통한 트랜잭션 메서드 호출 (self-invocation 방지)
          // accountId가 반환되면 자동 재시작 필요
          val accountIdForRestart = transactionHelper.recoverFromCheckpoint(job.id, checkpoint.iterationCount)
          if (accountIdForRestart != null) {
            jobsToAutoRestart.add(job.id to accountIdForRestart)
          }
        } else {
          // 체크포인트 없음 - 복구 불가
          transactionHelper.markJobAsRecoveryFailed(job.id, "체크포인트 없음 - 복구 불가")
        }

      } catch (e: Exception) {
        kLogger.error(e) { "Job 복구 실패: ${job.id}" }
        transactionHelper.markJobAsRecoveryFailed(job.id, "복구 중 오류: ${e.message}")
      }
    }

    // 트랜잭션 완료 후 자동 재시작 (VirtualThread)
    autoRestartJobsAsync(jobsToAutoRestart, "좀비 Job 자동 재시작")
  }

  // ========== Redis 동기화 ==========

  /**
   * Redis 실행 중 Set 정리
   *
   * DB와 Redis 간 상태 불일치를 해결한다.
   * Redis에는 있지만 DB에서 RUNNING 상태가 아닌 Job들을 Set에서 제거.
   */
  @Transactional(readOnly = true)
  fun cleanupRedisRunningSet() {
    val redisRunningIds = loopJobRedisRepository.getRunningJobIds()
    if (redisRunningIds.isEmpty()) {
      return
    }

    kLogger.info { "Redis 실행 중 Set 정리 시작 - ${redisRunningIds.size}개 확인" }

    var cleanedCount = 0
    redisRunningIds.forEach { jobId ->
      val job = loopJobRepository.findById(jobId).orElse(null)

      // DB에 없거나 RUNNING 상태가 아니면 Redis에서 제거
      if (job == null || job.state != LoopJobState.RUNNING) {
        loopJobRedisRepository.removeFromRunningSet(jobId)
        loopJobRedisRepository.forceReleaseJobLock(jobId)
        cleanedCount++
        kLogger.debug { "Redis Set에서 제거: $jobId (DB 상태: ${job?.state ?: "없음"})" }
      }
    }

    if (cleanedCount > 0) {
      kLogger.info { "Redis 실행 중 Set 정리 완료 - $cleanedCount 개 제거" }
    }
  }

  // ========== 수동 복구 API ==========

  /**
   * Job 수동 복구 시도
   *
   * 관리자가 특정 Job을 수동으로 복구할 때 사용.
   *
   * @param jobId 복구할 Job ID
   * @return 복구 성공 여부
   */
  fun manualRecover(jobId: String): Boolean {
    return transactionHelper.manualRecover(jobId)
  }

  /**
   * 전체 시스템 상태 점검
   *
   * Redis와 DB 간 상태 일관성을 확인하고 리포트한다.
   */
  @Transactional(readOnly = true)
  fun healthCheck(): Map<String, Any> {
    val redisRunningCount = loopJobRedisRepository.getRunningCount()
    val dbRunningCount = loopJobRepository.findByState(
      LoopJobState.RUNNING,
      org.springframework.data.domain.Pageable.unpaged()
    ).totalElements

    val cutoffTime = now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS)
    val zombieCount = loopJobRepository.findByStateAndLastHeartbeatBefore(
      LoopJobState.RUNNING, cutoffTime
    ).size

    return mapOf(
      "instanceId" to instanceId,
      "recoveryEnabled" to recoveryEnabled,
      "zombieDetectionEnabled" to zombieDetectionEnabled,
      "redisRunningCount" to redisRunningCount,
      "dbRunningCount" to dbRunningCount,
      "zombieCount" to zombieCount,
      "isHealthy" to (redisRunningCount == dbRunningCount && zombieCount == 0),
      "timestamp" to now(),
    )
  }

  // ========== 내부 헬퍼 ==========

  /**
   * Job 목록을 VirtualThread로 비동기 재시작
   *
   * 트랜잭션이 완료된 후 호출해야 한다.
   */
  private fun autoRestartJobsAsync(jobs: List<Pair<String, Long>>, logPrefix: String) {
    if (jobs.isEmpty()) return
    kLogger.info { "🚀 $logPrefix 대상: ${jobs.size}개" }

    jobs.forEach { (jobId, accountId) ->
      Thread.startVirtualThread {
        try {
          val systemSession = transactionHelper.createSystemSession(accountId)
          loopJobService.resume(jobId, systemSession)
          kLogger.info { "✅ $logPrefix 성공: $jobId" }
        } catch (e: Exception) {
          kLogger.error(e) { "❌ $logPrefix 실패: $jobId" }
          transactionHelper.markJobAsRecoveryFailed(jobId, "$logPrefix 실패: ${e.message}")
        }
      }
    }
  }
}
