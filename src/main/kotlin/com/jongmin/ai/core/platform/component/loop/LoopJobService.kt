package com.jongmin.ai.core.platform.component.loop

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.dto.*
import com.jongmin.ai.core.platform.entity.LoopJob
import com.jongmin.ai.core.platform.entity.LoopJobErrorHandling
import com.jongmin.ai.core.platform.entity.LoopJobState
import com.jongmin.ai.core.platform.entity.QLoopJob
import com.jongmin.ai.core.platform.repository.LoopJobCheckpointRepository
import com.jongmin.ai.core.platform.repository.LoopJobIterationRepository
import com.jongmin.ai.core.platform.repository.LoopJobRedisRepository
import com.jongmin.ai.core.platform.repository.LoopJobRepository
import com.jongmin.ai.core.platform.service.AiAgentService
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.ai.dte.component.handler.WorkflowTaskHandler
import com.jongmin.ai.dte.dto.LoopConfig
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.dte.entity.JobPriority
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * Loop Job 서비스
 *
 * 워크플로우를 N회 반복 실행하는 Job을 관리한다.
 * - Job 생성/조회/중단/재개/취소
 * - DTE(Distributed Task Executor)로 실행 위임
 * - Heartbeat 및 체크포인트 관리
 * - 좀비 Job 감지
 *
 * ### DTE 통합 (2026.01.09):
 * - 기존: Virtual Thread에서 직접 executeLoop() 실행
 * - 변경: DTE Job으로 등록하여 LoopWorkflowExecutor에서 실행
 * - pause/cancel: Redis 플래그 방식 유지 (LoopWorkflowExecutor가 체크)
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Service
class LoopJobService(
  private val loopJobRepository: LoopJobRepository,
  private val loopJobCheckpointRepository: LoopJobCheckpointRepository,
  private val loopJobIterationRepository: LoopJobIterationRepository,
  private val loopJobRedisRepository: LoopJobRedisRepository,
  private val aiAgentService: AiAgentService,
  private val objectMapper: ObjectMapper,
  private val loopJobEventBridge: LoopJobEventBridge,
  private val distributedTaskQueue: DistributedTaskQueue,
  @param:Value($$"${app.instance-id:default-instance}")
  private val instanceId: String,
) {
  private val kLogger = KotlinLogging.logger {}

  @PersistenceContext
  private lateinit var entityManager: EntityManager

  // ========== Job 생성 및 실행 ==========

  /**
   * 새 Loop Job 생성 및 DTE 큐에 등록
   *
   * ### 처리 흐름:
   * 1. 중복 실행 방지 (동일 워크플로우에 RUNNING Job 있으면 거부)
   * 2. DTE Job 먼저 생성 (UUID 확보)
   * 3. LoopJob 엔티티 생성 (DTE Job ID를 Loop Job ID로 사용)
   * 4. DTE 큐에 Job 등록 (LoopWorkflowExecutor에서 실행)
   *
   * ### DTE 통합 변경사항:
   * - 기존: Virtual Thread에서 직접 executeLoop() 실행
   * - 변경: DTE Job으로 등록하여 분산 실행, 동시성/큐잉/타임아웃 제어 지원
   *
   * @param request 생성 요청
   * @param session 현재 세션 (DTE Job payload에 직렬화되어 전달)
   * @return 생성된 Job 응답
   */
  @Transactional
  fun createAndExecute(request: CreateLoopJobRequest, session: JSession): LoopJobResponse {
    // 1. 중복 실행 방지
    val existingRunning = loopJobRepository.findByWorkflowIdAndState(
      request.workflowId, LoopJobState.RUNNING
    )
    if (existingRunning != null) {
      throw IllegalStateException("동일 워크플로우에 이미 실행 중인 Job이 있습니다: ${existingRunning.id}")
    }

    // 2. 워크플로우 존재 확인
    val aiAgent = aiAgentService.findById(request.workflowId)
    kLogger.info { "Loop Job 생성 - workflowId: ${request.workflowId}, name: ${aiAgent.name}" }

    // 3. JSession → Map 변환 (DTE Job payload에 전달)
    val sessionData: Map<String, Any> = objectMapper.convertValue(
      session.deepCopy(),
      object : TypeReference<Map<String, Any>>() {}
    )

    // 4. LoopConfig 생성 (DTE용)
    val loopConfig = LoopConfig(
      maxCount = request.maxCount,
      delayMs = request.delayMs ?: 0,
      onError = request.onError ?: LoopJobErrorHandling.STOP,
      maxRetries = request.maxRetries ?: 3
    )

    // 5. DTE Job payload 생성
    val payload = mutableMapOf<String, Any>(
      "workflowId" to request.workflowId,
      "accountId" to session.accountId,
      "sessionData" to sessionData,
      "streaming" to false, // Loop Job은 백그라운드 실행
      "isLoop" to true,
      "loopConfig" to objectMapper.convertValue(loopConfig, object : TypeReference<Map<String, Any>>() {})
    )

    // canvasId가 있으면 추가
    request.canvasId?.let { payload["canvasId"] = it }

    // 6. DTE Job 생성 (UUID 확보)
    val dteJob = DistributedJob.create(
      type = WorkflowTaskHandler.TASK_TYPE,
      payload = payload,
      priority = JobPriority.NORMAL,
      requesterId = session.accountId,
      correlationId = null // LoopJob.id가 DTE Job.id와 동일하므로 별도 설정 불필요
    )

    // 7. LoopJob 엔티티 생성 (DTE Job ID를 Loop Job ID로 사용)
    val loopJob = LoopJob(
      id = dteJob.id, // DTE Job ID를 Loop Job ID로 사용
      workflowId = request.workflowId,
      canvasId = request.canvasId,
      accountId = session.accountId,
      maxCount = request.maxCount,
      delayMs = request.delayMs ?: 0,
      onError = request.onError ?: LoopJobErrorHandling.STOP,
      maxRetries = request.maxRetries ?: 3,
      autoRestart = request.autoRestart ?: false,
      instanceId = instanceId,
      state = LoopJobState.PENDING
    )
    val savedLoopJob = loopJobRepository.save(loopJob)

    kLogger.debug { "LoopJob 엔티티 생성 - loopJobId: ${savedLoopJob.id}, dteJobId: ${dteJob.id}" }

    // 8. DTE 큐에 Job 등록 (LoopWorkflowExecutor에서 실행)
    val enqueuedJob = distributedTaskQueue.enqueue(WorkflowTaskHandler.TASK_TYPE, dteJob)

    kLogger.info {
      "Loop Job DTE 등록 완료 - loopJobId: ${savedLoopJob.id}, " +
          "dteJobId: ${enqueuedJob.id}, maxCount: ${savedLoopJob.maxCount}"
    }

    return LoopJobResponse.from(savedLoopJob)
  }

  // ========== 조회 ==========

  companion object {
    /** Heartbeat 타임아웃 (초) - 이 시간 동안 Heartbeat 없으면 좀비로 간주 */
    const val HEARTBEAT_TIMEOUT_SECONDS = 60L

    /** 단건 조회 시 iterations 최대 조회 건수 (성능 고려) */
    const val MAX_ITERATIONS_PER_QUERY = 100
  }

  /**
   * Job ID로 조회 (iterations 포함)
   *
   * 성능을 위해 최근 100개의 iteration만 조회한다.
   * 전체 iteration이 필요한 경우 별도 API 사용.
   *
   * @param jobId Job ID
   * @return Job 상세 정보 (iterations 포함)
   */
  @Transactional(readOnly = true)
  fun findById(jobId: String): LoopJobResponse? {
    val job = loopJobRepository.findById(jobId).orElse(null) ?: return null
    val checkpoint = loopJobCheckpointRepository.findFirstByJobIdOrderByTimestampDesc(jobId)

    // 성능을 위해 최근 N개의 iteration만 조회 (seq 내림차순 → 응답은 오름차순으로 정렬)
    val iterations = loopJobIterationRepository
      .findByJobIdOrderBySeqDesc(jobId, org.springframework.data.domain.PageRequest.of(0, MAX_ITERATIONS_PER_QUERY))
      .content
      .sortedBy { it.seq } // 응답은 seq 오름차순으로

    return LoopJobResponse.fromWithIterations(job, checkpoint, iterations)
  }

  /**
   * 상태별 Job 목록 조회 (페이징)
   */
  @Transactional(readOnly = true)
  fun findByState(state: LoopJobState, pageable: Pageable): Page<LoopJobResponse> {
    return loopJobRepository.findByState(state, pageable)
      .map { LoopJobResponse.from(it) }
  }

  /**
   * 여러 상태별 Job 목록 조회 (페이징)
   */
  @Transactional(readOnly = true)
  fun findByStates(states: List<LoopJobState>, pageable: Pageable): Page<LoopJobResponse> {
    return loopJobRepository.findByStateIn(states, pageable)
      .map { LoopJobResponse.from(it) }
  }

  /**
   * 동적 필터링 조회 (QueryDSL 사용)
   *
   * PRD Section 5.1.2에서 요구하는 다중 필터 조회 지원:
   * - state/states: 상태 필터
   * - accountId: 계정 필터
   * - workflowId: 워크플로우 필터
   *
   * @param state 단일 상태 필터 (선택)
   * @param states 복수 상태 필터 (선택, state보다 우선)
   * @param accountId 계정 ID 필터 (선택)
   * @param workflowId 워크플로우 ID 필터 (선택)
   * @param pageable 페이징 정보
   * @return 필터링된 Job 목록 (페이지)
   */
  @Transactional(readOnly = true)
  fun findByFilters(
    state: LoopJobState?,
    states: List<LoopJobState>?,
    accountId: Long?,
    workflowId: Long?,
    pageable: Pageable
  ): Page<LoopJobResponse> {
    val qJob = QLoopJob.loopJob

    // BooleanBuilder로 동적 조건 생성
    val builder = com.querydsl.core.BooleanBuilder()

    // 상태 필터 (states가 있으면 우선, 없으면 state 사용)
    when {
      !states.isNullOrEmpty() -> builder.and(qJob.state.`in`(states))
      state != null -> builder.and(qJob.state.eq(state))
    }

    // 계정 필터
    if (accountId != null) {
      builder.and(qJob.accountId.eq(accountId))
    }

    // 워크플로우 필터
    if (workflowId != null) {
      builder.and(qJob.workflowId.eq(workflowId))
    }

    // QueryDSL Predicate로 조회
    return loopJobRepository.findAll(builder, pageable)
      .map { LoopJobResponse.from(it) }
  }

  // ========== 제어 ==========

  /**
   * Job 일시 중지
   */
  @Transactional
  fun pause(jobId: String): LoopJobActionResponse {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job을 찾을 수 없습니다.")

    if (job.state != LoopJobState.RUNNING) {
      return LoopJobActionResponse(false, "RUNNING 상태의 Job만 일시 중지할 수 있습니다. 현재: ${job.state}")
    }

    // Redis 플래그 설정 (백그라운드 루프에서 빠르게 감지)
    loopJobRedisRepository.setPauseFlag(jobId)

    job.state = LoopJobState.PAUSED
    job.updatedAt = now()
    loopJobRepository.save(job)

    // SSE 상태 변경 이벤트 발행
    loopJobEventBridge.emitStateChanged(jobId)

    kLogger.info { "Job 일시 중지 - id: $jobId" }
    return LoopJobActionResponse(true, "Job이 일시 중지되었습니다. 현재 실행 중인 반복 완료 후 중지됩니다.")
  }

  /**
   * Job 재개 (DTE Job 등록으로 실행)
   *
   * ### DTE 통합 변경사항:
   * - 기존: Virtual Thread에서 직접 executeLoop() 실행
   * - 변경: DTE Job으로 등록하여 LoopWorkflowExecutor에서 실행
   *
   * @param jobId 재개할 Job ID
   * @param session 현재 세션 (DTE Job payload에 직렬화되어 전달)
   * @return 재개 결과
   */
  @Transactional
  fun resume(jobId: String, session: JSession): LoopJobActionResponse {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job을 찾을 수 없습니다.")

    // PAUSED 또는 RECOVERING 상태에서 재개 가능
    val resumableStates = listOf(LoopJobState.PAUSED, LoopJobState.RECOVERING)
    if (job.state !in resumableStates) {
      return LoopJobActionResponse(false, "PAUSED 또는 RECOVERING 상태의 Job만 재개할 수 있습니다. 현재: ${job.state}")
    }

    // Redis 플래그 해제
    loopJobRedisRepository.clearAllFlags(job.id)

    job.state = LoopJobState.PENDING // DTE 큐에 등록되므로 PENDING으로 설정
    job.instanceId = instanceId
    job.updatedAt = now()
    loopJobRepository.save(job)

    // SSE 상태 변경 이벤트 발행
    loopJobEventBridge.emitStateChanged(jobId)

    // DTE Job 등록으로 재개 실행
    enqueueDteJob(job, session)

    kLogger.info { "Job 재개 (DTE 등록) - id: $jobId" }
    return LoopJobActionResponse(true, "Job이 재개되었습니다. DTE 큐에 등록되어 실행됩니다.")
  }

  /**
   * Job 재시작 (초기화 후 처음부터 다시 실행)
   *
   * 종료된 Job(COMPLETED, CANCELLED, ERROR)을 초기화하고 처음부터 다시 실행한다.
   * 기존 iteration 기록은 물리적으로 삭제된다.
   *
   * ### DTE 통합 변경사항:
   * - 기존: Virtual Thread에서 직접 executeLoop() 실행
   * - 변경: DTE Job으로 등록하여 LoopWorkflowExecutor에서 실행
   *
   * @param jobId Job ID
   * @param request 재시작 옵션 (null인 필드는 기존 값 유지)
   * @param session 현재 세션 (DTE Job payload에 직렬화되어 전달)
   * @return 재시작 결과
   */
  @Transactional
  fun restart(jobId: String, request: RestartLoopJobRequest, session: JSession): LoopJobActionResponse {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job을 찾을 수 없습니다.")

    // 종료된 상태에서만 재시작 가능
    val restartableStates = listOf(LoopJobState.COMPLETED, LoopJobState.CANCELLED, LoopJobState.ERROR)
    if (job.state !in restartableStates) {
      return LoopJobActionResponse(
        false,
        "종료된 Job만 재시작할 수 있습니다. 현재: ${job.state}. (허용: COMPLETED, CANCELLED, ERROR)"
      )
    }

    kLogger.info { "🔄 Job 재시작 시작 - id: $jobId, 이전 상태: ${job.state}" }

    // 1. 설정 업데이트 (옵션이 제공된 경우)
    if (request.hasAnyOption()) {
      loopJobRepository.updateSettings(
        id = jobId,
        maxCount = request.maxCount,
        delayMs = request.delayMs,
        onError = request.onError?.name,
        maxRetries = request.maxRetries,
        autoRestart = request.autoRestart,
      )
      kLogger.debug { "Job 설정 업데이트 - maxCount: ${request.maxCount}, delayMs: ${request.delayMs}, onError: ${request.onError}" }
    }

    // 2. 상태 및 카운터 초기화
    loopJobRepository.resetForRestart(
      id = jobId,
      state = LoopJobState.PENDING,
      instanceId = instanceId,
    )

    // 3. 기존 iteration 기록 삭제 (물리적 삭제)
    val deletedIterations = loopJobIterationRepository.deleteByJobId(jobId)
    kLogger.debug { "Iteration 기록 삭제 - jobId: $jobId, 삭제된 건수: $deletedIterations" }

    // 4. 체크포인트 삭제
    loopJobCheckpointRepository.deleteByJobId(jobId)

    // 5. Redis 플래그 정리
    clearJobRedisState(jobId)

    // 6. JPA 1차 캐시 초기화 후 다시 조회 (업데이트된 설정 반영)
    // ⚠️ Native Query/JPQL은 1차 캐시를 갱신하지 않으므로 clear() 필수
    entityManager.clear()
    val updatedJob = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job 재조회 실패")

    // SSE 상태 변경 이벤트 발행
    loopJobEventBridge.emitStateChanged(jobId)

    // 7. DTE Job 등록으로 재시작 실행
    enqueueDteJob(updatedJob, session)

    kLogger.info { "✅ Job 재시작 완료 (DTE 등록) - id: $jobId, 새 maxCount: ${updatedJob.maxCount}" }
    return LoopJobActionResponse(true, "Job이 재시작되었습니다. 기존 기록이 초기화되고 DTE 큐에 등록되어 실행됩니다.")
  }

  /**
   * Job 취소
   */
  @Transactional
  fun cancel(jobId: String, reason: String?): LoopJobActionResponse {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job을 찾을 수 없습니다.")

    if (job.isTerminated()) {
      return LoopJobActionResponse(false, "이미 종료된 Job입니다. 현재: ${job.state}")
    }

    // Redis 취소 플래그 설정 (백그라운드 루프에서 빠르게 감지)
    loopJobRedisRepository.setCancelFlag(jobId)

    job.state = LoopJobState.CANCELLED
    job.cancelReason = reason
    job.completedAt = now()
    job.updatedAt = now()
    loopJobRepository.save(job)

    // 실행 중 Set에서 제거 및 플래그 정리
    loopJobRedisRepository.removeFromRunningSet(job.id)
    loopJobRedisRepository.forceReleaseJobLock(job.id)

    // SSE 완료 이벤트 발행
    loopJobEventBridge.emitComplete(jobId, "Job cancelled: ${reason ?: "No reason"}")

    kLogger.info { "Job 취소 - id: $jobId, reason: $reason" }
    return LoopJobActionResponse(true, "Job이 취소 요청되었습니다.")
  }

  /**
   * Job 삭제
   *
   * 실행 중이거나 복구 중인 Job은 삭제할 수 없다. 먼저 중지(pause) 또는 취소(cancel)해야 한다.
   * - RUNNING, RECOVERING 상태: 삭제 불가 (먼저 중지/취소 필요)
   * - PENDING, PAUSED, COMPLETED, CANCELLED, ERROR 상태: 삭제 가능
   *
   * @param jobId 삭제할 Job ID
   * @return 삭제 결과
   */
  @Transactional
  fun delete(jobId: String): LoopJobActionResponse {
    val job = loopJobRepository.findById(jobId).orElse(null)
      ?: return LoopJobActionResponse(false, "Job을 찾을 수 없습니다.")

    // 1. 실행 중이거나 복구 중인 Job은 삭제 불가
    val undeletableStates = listOf(LoopJobState.RUNNING, LoopJobState.RECOVERING)
    if (job.state in undeletableStates) {
      return LoopJobActionResponse(
        false,
        "실행 중이거나 복구 중인 Job은 삭제할 수 없습니다. 먼저 중지(pause) 또는 취소(cancel)하세요. 현재 상태: ${job.state}"
      )
    }

    // 2. Redis 관련 데이터 정리 (모든 플래그 해제)
    clearJobRedisState(jobId)

    // 3. 체크포인트 삭제
    loopJobCheckpointRepository.deleteByJobId(jobId)

    // 4. Iteration 기록 삭제
    val deletedIterations = loopJobIterationRepository.deleteByJobId(jobId)
    kLogger.debug { "Iteration 기록 삭제 - jobId: $jobId, 삭제된 건수: $deletedIterations" }

    // 5. SSE 에러 이벤트 발행 (구독자에게 삭제 알림)
    loopJobEventBridge.emitError(jobId, "Job deleted")

    // 6. Job 삭제
    loopJobRepository.delete(job)

    kLogger.info { "Job 삭제 완료 - id: $jobId, 삭제 전 상태: ${job.state}" }
    return LoopJobActionResponse(true, "Job이 삭제되었습니다.")
  }

  /**
   * Loop Job 일괄 삭제 (Hard Delete)
   *
   * Partial Success 방식:
   * - 존재하고 삭제 가능한 Job만 삭제
   * - 존재하지 않거나 RUNNING/RECOVERING 상태인 Job은 무시
   *
   * @param ids 삭제할 Loop Job ID 목록 (UUID)
   * @return 삭제 결과 (삭제 개수, 성공/실패 ID 목록)
   */
  @Transactional
  fun bulkDelete(ids: List<String>): com.jongmin.ai.core.backoffice.dto.response.BulkDeleteUuidResult {
    kLogger.info { "(BO) Loop Job 일괄 삭제 - ids: $ids" }

    if (ids.isEmpty()) {
      throw IllegalArgumentException("삭제할 ID가 제공되지 않았습니다.")
    }
    if (ids.size > 100) {
      throw IllegalArgumentException("한 번에 최대 100개까지만 삭제할 수 있습니다. 요청 개수: ${ids.size}")
    }

    val deletedIds = mutableListOf<String>()
    val failedIds = mutableListOf<String>()

    // 삭제 불가 상태 (실행 중, 복구 중)
    val undeletableStates = listOf(LoopJobState.RUNNING, LoopJobState.RECOVERING)

    ids.forEach { jobId ->
      val job = loopJobRepository.findById(jobId).orElse(null)

      if (job == null) {
        // 존재하지 않는 ID - 실패 처리
        failedIds.add(jobId)
        kLogger.warn { "(BO) Loop Job 일괄 삭제 - 존재하지 않는 ID 무시: $jobId" }
      } else if (job.state in undeletableStates) {
        // RUNNING/RECOVERING 상태 - 실패 처리
        failedIds.add(jobId)
        kLogger.warn { "(BO) Loop Job 일괄 삭제 - 삭제 불가 상태 무시: $jobId (${job.state})" }
      } else {
        try {
          // Redis 정리
          clearJobRedisState(jobId)

          // 체크포인트 삭제
          loopJobCheckpointRepository.deleteByJobId(jobId)

          // Iteration 삭제
          loopJobIterationRepository.deleteByJobId(jobId)

          // Job 삭제
          loopJobRepository.delete(job)

          deletedIds.add(jobId)
        } catch (e: Exception) {
          kLogger.error(e) { "(BO) Loop Job 일괄 삭제 - 삭제 중 오류: $jobId" }
          failedIds.add(jobId)
        }
      }
    }

    kLogger.info { "(BO) Loop Job 일괄 삭제 완료 - 삭제: ${deletedIds.size}개, 실패: ${failedIds.size}개" }
    return com.jongmin.ai.core.backoffice.dto.response.BulkDeleteUuidResult(
      deletedCount = deletedIds.size,
      deletedIds = deletedIds,
      failedIds = failedIds
    )
  }

  // ========== 좀비 Job 관리 ==========

  /**
   * 좀비 Job 목록 조회
   */
  @Transactional(readOnly = true)
  fun findZombieJobs(): ZombieJobListResponse {
    val cutoffTime = now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS)
    val zombieJobs = loopJobRepository.findByStateAndLastHeartbeatBefore(
      LoopJobState.RUNNING, cutoffTime
    )

    val zombieInfos = zombieJobs.map { ZombieJobInfo.from(it) }
    return ZombieJobListResponse(
      zombieJobs = zombieInfos,
      count = zombieInfos.size,
    )
  }

  /**
   * 좀비 Job 감지 및 자동 복구
   *
   * K8s CronJob에서 30초 주기로 호출
   */
  @Transactional
  fun detectAndRecoverZombieJobs(): ZombieDetectionResponse {
    val cutoffTime = now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS)
    val zombieJobs = loopJobRepository.findByStateAndLastHeartbeatBefore(
      LoopJobState.RUNNING, cutoffTime
    )

    if (zombieJobs.isEmpty()) {
      return ZombieDetectionResponse(detected = 0, recovered = 0)
    }

    kLogger.warn { "🧟 좀비 Job 감지: ${zombieJobs.size}개" }

    var recoveredCount = 0
    zombieJobs.forEach { job ->
      kLogger.warn { "  └─ Job: ${job.id}, 마지막 heartbeat: ${job.lastHeartbeat}" }

      try {
        // 상태를 ERROR로 변경하고 복구 대기
        // 실제 복구는 LoopJobRecoveryService에서 처리
        job.state = LoopJobState.ERROR
        job.errorMessage = "좀비 Job 감지 - Heartbeat 타임아웃"
        job.updatedAt = now()
        loopJobRepository.save(job)

        // Redis 정리
        loopJobRedisRepository.removeFromRunningSet(job.id)
        loopJobRedisRepository.forceReleaseJobLock(job.id)

        recoveredCount++
      } catch (e: Exception) {
        kLogger.error(e) { "좀비 Job 처리 실패: ${job.id}" }
      }
    }

    return ZombieDetectionResponse(
      detected = zombieJobs.size,
      recovered = recoveredCount,
    )
  }

  /**
   * Job의 Redis 상태 완전 초기화 (모든 플래그 + 실행 중 Set + 락 해제)
   */
  private fun clearJobRedisState(jobId: String) {
    loopJobRedisRepository.clearAllFlags(jobId)
    loopJobRedisRepository.removeFromRunningSet(jobId)
    loopJobRedisRepository.forceReleaseJobLock(jobId)
  }

  // ========== DTE 헬퍼 ==========

  /**
   * 기존 LoopJob을 DTE Job으로 등록
   *
   * resume(), restart() 등에서 사용됩니다.
   * 기존 LoopJob의 설정을 기반으로 DTE Job payload를 생성하고 큐에 등록합니다.
   *
   * ### 주의:
   * - LoopJob.id를 DTE Job.id로 사용하여 ID 일관성 유지
   * - 이미 DTE Job이 등록된 경우 중복 등록 방지 필요 (호출자 책임)
   *
   * @param loopJob 등록할 LoopJob 엔티티
   * @param session 세션 정보 (DTE Job payload에 포함)
   */
  private fun enqueueDteJob(loopJob: LoopJob, session: JSession) {
    // 1. JSession → Map 변환
    val sessionData: Map<String, Any> = objectMapper.convertValue(
      session.deepCopy(),
      object : TypeReference<Map<String, Any>>() {}
    )

    // 2. LoopConfig 생성 (LoopJob 설정 기반)
    val loopConfig = LoopConfig(
      maxCount = loopJob.maxCount,
      delayMs = loopJob.delayMs,
      onError = loopJob.onError,
      maxRetries = loopJob.maxRetries
    )

    // 3. DTE Job payload 생성
    val payload = mutableMapOf<String, Any>(
      "workflowId" to loopJob.workflowId,
      "accountId" to loopJob.accountId,
      "sessionData" to sessionData,
      "streaming" to false, // Loop Job은 백그라운드 실행
      "isLoop" to true,
      "loopConfig" to objectMapper.convertValue(loopConfig, object : TypeReference<Map<String, Any>>() {})
    )

    // canvasId가 있으면 추가
    loopJob.canvasId?.let { payload["canvasId"] = it }

    // 4. DTE Job 생성 (LoopJob.id를 DTE Job.id로 사용)
    val dteJob = DistributedJob(
      id = loopJob.id, // 기존 LoopJob ID 재사용
      type = WorkflowTaskHandler.TASK_TYPE,
      payload = payload,
      priority = JobPriority.NORMAL,
      requesterId = loopJob.accountId
    )

    // 5. DTE 큐에 등록
    val enqueuedJob = distributedTaskQueue.enqueue(WorkflowTaskHandler.TASK_TYPE, dteJob)

    kLogger.debug {
      "DTE Job 등록 완료 - loopJobId: ${loopJob.id}, dteJobId: ${enqueuedJob.id}"
    }
  }
}
