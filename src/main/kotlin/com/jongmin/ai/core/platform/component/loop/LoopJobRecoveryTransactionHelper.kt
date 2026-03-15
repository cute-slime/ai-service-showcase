package com.jongmin.ai.core.platform.component.loop

import com.jongmin.jspring.web.dto.TokenType
import com.jongmin.jspring.web.entity.JPermissions
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.entity.LoopJobState
import com.jongmin.ai.core.platform.repository.LoopJobCheckpointRepository
import com.jongmin.ai.core.platform.repository.LoopJobRedisRepository
import com.jongmin.ai.core.platform.repository.LoopJobRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Loop Job 복구 트랜잭션 헬퍼
 *
 * LoopJobRecoveryService에서 self-invocation 문제를 해결하기 위한 헬퍼 클래스.
 * Spring AOP 프록시 특성상 같은 클래스 내 @Transactional 메서드 호출은 트랜잭션이 적용되지 않으므로,
 * 별도 클래스로 분리하여 프록시를 통한 호출이 가능하도록 한다.
 *
 * @author Claude Code
 * @since 2026.01.04
 */
@Component
class LoopJobRecoveryTransactionHelper(
  private val loopJobRepository: LoopJobRepository,
  private val loopJobCheckpointRepository: LoopJobCheckpointRepository,
  private val loopJobRedisRepository: LoopJobRedisRepository,
  private val loopJobService: LoopJobService,
  @param:Value($$"${app.instance-id:default-instance}")
  private val instanceId: String,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 체크포인트 기반 Job 복구 (상태 변경만 수행)
   *
   * Job의 autoRestart 설정에 따라:
   * - autoRestart=true: RECOVERING 상태로 전환 (호출자가 별도로 resume 호출 필요)
   * - autoRestart=false: PAUSED 상태로 전환 (수동 재개 필요)
   *
   * ⚠️ 중요: @Transactional 메서드에서 Thread 시작하면 트랜잭션 커밋 전에
   * 스레드가 실행되어 문제가 발생할 수 있으므로, 상태 변경만 수행하고
   * 실제 재시작은 호출자가 트랜잭션 외부에서 처리해야 한다.
   *
   * @param jobId Job ID
   * @param lastIteration 마지막 반복 횟수
   * @return 자동 재시작이 필요하면 accountId 반환, 아니면 null
   */
  @Transactional
  fun recoverFromCheckpoint(jobId: String, lastIteration: Int): Long? {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: throw IllegalStateException("Job을 찾을 수 없습니다: $jobId")

    // Redis 정리 (복구 전 깨끗하게)
    loopJobRedisRepository.removeFromRunningSet(jobId)
    loopJobRedisRepository.forceReleaseJobLock(jobId)

    return if (job.autoRestart) {
      // 자동 재시작: RECOVERING 상태로 전환
      kLogger.info { "🔄 Job 자동 재시작 준비: $jobId, 마지막 반복: $lastIteration" }

      job.state = LoopJobState.RECOVERING
      job.errorMessage = "좀비 복구됨 - 반복 $lastIteration 에서 자동 재시작 대기 중"
      job.instanceId = instanceId
      job.updatedAt = now()
      loopJobRepository.save(job)

      // accountId 반환 (호출자가 세션 생성 및 resume 호출)
      job.accountId
    } else {
      // 수동 재개 필요: PAUSED 상태로 전환
      job.state = LoopJobState.PAUSED
      job.errorMessage = "좀비 복구됨 - 반복 $lastIteration 에서 중단. resume 호출로 재개하세요."
      job.instanceId = null
      job.updatedAt = now()
      loopJobRepository.save(job)

      kLogger.info { "✅ Job 복구 완료 (PAUSED): $jobId, 마지막 반복: $lastIteration" }
      null
    }
  }

  /**
   * 시스템 세션 생성 (복구용)
   *
   * 외부에서 사용할 수 있도록 public으로 변경
   */
  fun createSystemSession(accountId: Long): JSession {
    return JSession(
      accountId = accountId,
      accessToken = "system-recovery-token",
      tokenType = TokenType.AUTH,
      username = "system-recovery",
      deviceUid = null,
      permissions = JPermissions(),
    )
  }

  /**
   * Job을 복구 실패 상태로 변경
   */
  @Transactional
  fun markJobAsRecoveryFailed(jobId: String, reason: String) {
    val job = loopJobRepository.findById(jobId).orElse(null) ?: return

    job.state = LoopJobState.ERROR
    job.errorMessage = reason
    job.updatedAt = now()
    loopJobRepository.save(job)

    // Redis 정리
    loopJobRedisRepository.removeFromRunningSet(jobId)
    loopJobRedisRepository.forceReleaseJobLock(jobId)

    kLogger.warn { "❌ Job 복구 실패: $jobId, 사유: $reason" }
  }

  /**
   * 오래된 완료 Job 정리
   *
   * @param cutoffDays 기준 일수
   * @return 삭제된 Job 수, 삭제된 체크포인트 수
   */
  @Transactional
  fun cleanupOldJobs(cutoffDays: Long): Pair<Int, Int> {
    val cutoffTime = now().minusDays(cutoffDays)

    // 체크포인트 먼저 삭제
    val deletedCheckpoints = loopJobCheckpointRepository.deleteOldCheckpoints(cutoffTime)

    // Job 삭제
    val deletedJobs = loopJobRepository.deleteOldCompletedJobs(cutoffTime)

    return Pair(deletedJobs, deletedCheckpoints)
  }

  /**
   * Job 수동 복구
   *
   * @param jobId 복구할 Job ID
   * @return 복구 성공 여부
   */
  @Transactional
  fun manualRecover(jobId: String): Boolean {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return false

    if (job.state != LoopJobState.ERROR) {
      kLogger.warn { "ERROR 상태의 Job만 복구할 수 있습니다. 현재: ${job.state}" }
      return false
    }

    val checkpoint = loopJobCheckpointRepository.findFirstByJobIdOrderByTimestampDesc(jobId)
    val lastIteration = checkpoint?.iterationCount ?: 0

    // PAUSED 상태로 변경 (사용자가 resume으로 재개)
    job.state = LoopJobState.PAUSED
    job.errorMessage = "수동 복구됨 - 반복 $lastIteration 에서 재개 가능"
    job.instanceId = null
    job.updatedAt = now()
    loopJobRepository.save(job)

    kLogger.info { "수동 복구 완료: $jobId, 상태: PAUSED" }
    return true
  }
}
