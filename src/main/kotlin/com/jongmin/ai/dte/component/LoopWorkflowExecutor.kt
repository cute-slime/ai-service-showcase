package com.jongmin.ai.dte.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.dto.TokenType
import com.jongmin.jspring.web.entity.JPayload
import com.jongmin.jspring.web.entity.JPermissions
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.web.entity.Workspaces
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.platform.component.agent.executor.model.*
import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import com.jongmin.jspring.dte.component.WorkflowSseManager
import com.jongmin.ai.core.platform.component.loop.CheckpointCallback
import com.jongmin.ai.core.platform.component.loop.LoopJobEventBridge
import com.jongmin.ai.core.platform.entity.*
import com.jongmin.ai.core.platform.repository.LoopJobCheckpointRepository
import com.jongmin.ai.core.platform.repository.LoopJobIterationRepository
import com.jongmin.ai.core.platform.repository.LoopJobRedisRepository
import com.jongmin.ai.core.platform.repository.LoopJobRepository
import com.jongmin.ai.core.platform.service.AiAgentService
import com.jongmin.ai.dte.dto.LoopConfig
import com.jongmin.ai.dte.dto.WorkflowJobPayload
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loop 워크플로우 실행기
 *
 * DTE Job으로 등록된 워크플로우를 N회 반복 실행하는 컴포넌트입니다.
 * 기존 LoopJobService.executeLoop() 로직을 재사용하여 체크포인트/복구 기능을 제공합니다.
 *
 * 주요 기능:
 * - N회 반복 실행 (maxCount 기반)
 * - 반복 간 딜레이 설정 (delayMs)
 * - 에러 처리 정책 (STOP, CONTINUE, RETRY)
 * - 노드별 체크포인트 저장 (장애 복구용)
 * - 취소/일시정지 플래그 감지 (Redis)
 * - 각 반복별 Iteration 기록 (DB)
 *
 * 사용 예시:
 * ```kotlin
 * // WorkflowTaskHandler에서 호출
 * loopWorkflowExecutor.execute(job, payload)
 * ```
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property eventBridge SSE 이벤트 브릿지 (Redis Pub/Sub)
 * @property workflowSseManager 워크플로우 SSE 이벤트 매니저 (워크플로우/노드/Loop 이벤트 발행)
 * @property aiAgentService AI 에이전트 서비스 (workflowId 조회용)
 * @property factory 노드 실행기 팩토리
 * @property eventSender Kafka 이벤트 전송기
 * @property loopJobCheckpointRepository 체크포인트 저장소
 * @property loopJobIterationRepository 반복 기록 저장소
 * @property loopJobRedisRepository Redis 락/플래그 관리
 * @property instanceId 현재 서버 인스턴스 ID
 * @property topic Kafka 토픽명
 *
 * @author Claude Code
 * @since 2026.01.09
 */
@Component
class LoopWorkflowExecutor(
  private val objectMapper: ObjectMapper,
  private val eventBridge: DistributedJobEventBridge,
  private val workflowSseManager: WorkflowSseManager,
  private val loopJobEventBridge: LoopJobEventBridge,
  private val aiAgentService: AiAgentService,
  private val factory: NodeExecutorFactory,
  private val eventSender: EventSender,
  private val loopJobRepository: LoopJobRepository,
  private val loopJobCheckpointRepository: LoopJobCheckpointRepository,
  private val loopJobIterationRepository: LoopJobIterationRepository,
  private val loopJobRedisRepository: LoopJobRedisRepository,
  private val loopJobStateService: LoopJobStateService,
  @param:Value($$"${app.instance-id:default-instance}") private val instanceId: String,
  @param:Value($$"${app.stream.event.topic.event-app}") private val topic: String,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 태스크 타입 상수 */
    const val TASK_TYPE = "WORKFLOW"

    /** 워크플로우 실행 타임아웃 (분) */
    private const val WORKFLOW_EXECUTION_TIMEOUT_MINUTES = 120L
  }

  private enum class IterationOutcome {
    NEXT,
    RETRY
  }

  private data class LoopExecutionCounters(
    var currentCount: Int = 0,
    var retryCount: Int = 0,
    var successCount: Int = 0,
    var failureCount: Int = 0
  )

  /**
   * Loop 워크플로우를 N회 반복 실행합니다.
   *
   * 실행 흐름:
   * 1. 워크플로우 데이터 확보 (workflowData 우선, 없으면 workflowId로 조회)
   * 2. LoopConfig 추출 및 검증
   * 3. JSession 생성/복원
   * 4. Redis 락 획득
   * 5. N회 반복 실행 (에러 처리 정책 적용)
   * 6. 완료 처리 및 정리
   *
   * @param job DTE Job 엔티티
   * @param payload 워크플로우 Job 페이로드 (isLoop=true 필수)
   * @throws BadRequestException 워크플로우 데이터를 찾을 수 없는 경우
   * @throws IllegalStateException loopConfig가 없는 경우
   */
  fun execute(job: DistributedJob, payload: WorkflowJobPayload) {
    val loopConfig = payload.loopConfig
      ?: throw IllegalStateException("Loop 워크플로우 실행 시 loopConfig 필수")

    val workflowExecutionId = job.id
    kLogger.info { "🔄 Loop 워크플로우 실행 시작 - jobId: $workflowExecutionId, maxCount: ${loopConfig.maxCount}" }

    // 1. 워크플로우 데이터 확보 (JSON 원본 보관)
    val workflowData = resolveWorkflowData(payload)

    // 2. JSession 생성/복원
    val session = restoreSession(payload)

    // 3. WORKFLOW_STARTED 이벤트 발행
    workflowSseManager.emitWorkflowStarted(workflowExecutionId)

    // 4. Loop 실행
    try {
      executeLoop(
        jobId = workflowExecutionId,
        workflowData = workflowData,
        loopConfig = loopConfig,
        canvasId = payload.canvasId,
        session = session,
        correlationId = job.correlationId
      )

      // 5. WORKFLOW_COMPLETED 이벤트 발행
      workflowSseManager.emitWorkflowCompleted(workflowExecutionId)
      kLogger.info { "🏁 Loop 워크플로우 실행 완료 - jobId: $workflowExecutionId" }
    } catch (e: Exception) {
      // 6. WORKFLOW_FAILED 이벤트 발행
      workflowSseManager.emitWorkflowFailed(workflowExecutionId, e.message ?: "Unknown error")
      kLogger.error(e) { "Loop 워크플로우 실행 실패 - jobId: $workflowExecutionId" }
      throw e
    }
  }

  /**
   * Loop 실행 메인 로직
   *
   * 기존 LoopJobService.executeLoop() 로직을 재사용하여 구현.
   * DTE Job ID를 Loop Job ID로 사용하여 체크포인트/Iteration 저장.
   *
   * @param jobId DTE Job ID (= Loop Job ID로 사용)
   * @param workflowData 워크플로우 JSON 데이터
   * @param loopConfig 반복 설정
   * @param canvasId 캔버스 ID (SSE 토픽용)
   * @param session 워크플로우 실행에 사용할 세션
   * @param correlationId 연관 요청 ID
   */
  private fun executeLoop(
    jobId: String,
    workflowData: Map<String, Any>,
    loopConfig: LoopConfig,
    canvasId: String?,
    session: JSession,
    correlationId: String?
  ) {
    kLogger.info { "🔄 Loop 구동 시작 - jobId: $jobId, maxCount: ${loopConfig.maxCount}" }

    // 1. Redis 락 획득
    if (!loopJobRedisRepository.tryAcquireJobLock(jobId, instanceId)) {
      kLogger.warn { "Job 락 획득 실패 - 다른 인스턴스에서 실행 중: $jobId" }
      throw IllegalStateException("Job 락 획득 실패 - 다른 인스턴스에서 실행 중: $jobId")
    }

    try {
      // 2. 실행 중 Set에 추가 (Redis)
      loopJobRedisRepository.addToRunningSet(jobId)

      // 2-1. LoopJob 상태를 RUNNING으로 변경 (DB 동기화)
      loopJobRepository.updateState(jobId, LoopJobState.RUNNING)
      kLogger.debug { "LoopJob 상태 RUNNING으로 변경 - jobId: $jobId" }

      // 3. 반복 실행
      val counters = LoopExecutionCounters()

      while (shouldContinue(jobId, counters.currentCount, loopConfig.maxCount)) {
        val iteration = counters.currentCount + 1
        kLogger.info { "🔄 Loop 반복 시작: $jobId, iteration: $iteration/${loopConfig.maxCount}" }

        val outcome = executeSingleIteration(
          jobId = jobId,
          iteration = iteration,
          workflowData = workflowData,
          canvasId = canvasId,
          session = session,
          correlationId = correlationId,
          loopConfig = loopConfig,
          counters = counters
        )
        if (outcome == IterationOutcome.RETRY) {
          continue
        }

        // 3-4. 딜레이
        if (loopConfig.delayMs > 0) {
          Thread.sleep(loopConfig.delayMs)
        }

        // 3-5. 취소/일시중지 체크 (Redis 플래그)
        if (loopJobRedisRepository.isCancelled(jobId)) {
          kLogger.info { "🛑 Loop 취소됨 (Redis 플래그): $jobId" }
          break
        }
        if (loopJobRedisRepository.isPaused(jobId)) {
          kLogger.info { "⏸️ Loop 일시 중지됨 (Redis 플래그): $jobId" }
          // 일시 중지 상태로 종료 - 재개 시 DTE에서 새 Job으로 실행
          throw RuntimeException("Loop 일시 중지됨: $jobId")
        }
      }

      // 4. 완료 처리
      loopJobRedisRepository.removeFromRunningSet(jobId)

      // LoopJob 상태를 COMPLETED로 변경 (DB 동기화)
      loopJobStateService.updateJobCompleted(jobId)

      // SSE 완료 이벤트 발행
      emitLoopComplete(jobId, counters.currentCount, counters.successCount, counters.failureCount, correlationId)

      kLogger.info { "🏁 Loop Job 종료: $jobId, totalIterations: ${counters.currentCount}, success: ${counters.successCount}, failure: ${counters.failureCount}" }

    } catch (e: Exception) {
      kLogger.error(e) { "Loop Job 실행 중 예상치 못한 오류: $jobId" }
      loopJobRedisRepository.removeFromRunningSet(jobId)

      // LoopJob 상태를 ERROR로 변경 (DB 동기화)
      loopJobStateService.updateJobError(jobId, e.message)

      throw e
    } finally {
      // 5. 락 해제
      loopJobRedisRepository.releaseJobLock(jobId, instanceId)
    }
  }

  private fun executeSingleIteration(
    jobId: String,
    iteration: Int,
    workflowData: Map<String, Any>,
    canvasId: String?,
    session: JSession,
    correlationId: String?,
    loopConfig: LoopConfig,
    counters: LoopExecutionCounters
  ): IterationOutcome {
    // LOOP_ITERATION_STARTED 이벤트 발행 (iterationIndex는 0부터 시작)
    workflowSseManager.emitLoopIterationStarted(jobId, iteration - 1)

    var iterationRecord = getOrCreateIterationRecord(jobId, iteration)

    return try {
      // Heartbeat 갱신 (Redis 락 TTL 연장)
      loopJobRedisRepository.renewJobLock(jobId, instanceId)

      // 매 반복마다 JSON에서 새로 파싱하여 노드 상태를 초기화한다.
      val workflow = parseWorkflow(workflowData, canvasId)
      executeWorkflowIteration(
        jobId = jobId,
        workflow = workflow,
        iteration = iteration,
        session = session,
        correlationId = correlationId
      )

      completeIterationSuccess(jobId, iteration, iterationRecord, loopConfig.maxCount, correlationId, counters)
      IterationOutcome.NEXT
    } catch (e: Exception) {
      kLogger.error(e) { "Loop 반복 실패: $jobId, iteration: $iteration" }
      iterationRecord.incrementErrorCount()
      handleIterationError(jobId, iteration, iterationRecord, loopConfig, correlationId, counters, e)
    }
  }

  private fun getOrCreateIterationRecord(jobId: String, iteration: Int): LoopJobIteration {
    val record = loopJobIterationRepository.findByJobIdAndSeq(jobId, iteration)
      ?: LoopJobIteration(
        jobId = jobId,
        seq = iteration,
        status = LoopJobIterationStatus.RUNNING,
      )
    return loopJobIterationRepository.save(record)
  }

  private fun completeIterationSuccess(
    jobId: String,
    iteration: Int,
    iterationRecord: LoopJobIteration,
    maxCount: Int,
    correlationId: String?,
    counters: LoopExecutionCounters
  ) {
    iterationRecord.complete(LoopJobIterationStatus.SUCCESS)
    loopJobIterationRepository.save(iterationRecord)

    counters.successCount++
    counters.currentCount = iteration
    counters.retryCount = 0

    loopJobRepository.incrementSuccessCount(jobId)
    loopJobRepository.incrementCount(jobId)

    workflowSseManager.emitLoopIterationCompleted(jobId, iteration - 1)
    emitIterationComplete(jobId, iteration, maxCount, correlationId)

    kLogger.info { "✅ Loop 반복 완료: $jobId, iteration: $iteration, duration: ${iterationRecord.getDurationMs()}ms" }
  }

  private fun handleIterationError(
    jobId: String,
    iteration: Int,
    iterationRecord: LoopJobIteration,
    loopConfig: LoopConfig,
    correlationId: String?,
    counters: LoopExecutionCounters,
    cause: Exception
  ): IterationOutcome {
    return when (loopConfig.onError) {
      LoopJobErrorHandling.STOP -> {
        markIterationFailure(jobId, iterationRecord, cause.message)
        counters.failureCount++
        emitLoopError(jobId, iteration, cause.message ?: "Unknown error", correlationId)
        throw RuntimeException("Loop 반복 실패 (STOP 정책) - iteration: $iteration, error: ${cause.message}", cause)
      }

      LoopJobErrorHandling.CONTINUE -> {
        markIterationFailure(jobId, iterationRecord, cause.message)
        counters.failureCount++
        counters.currentCount = iteration
        counters.retryCount = 0
        emitIterationFailed(jobId, iteration, cause.message ?: "Unknown error", correlationId)
        IterationOutcome.NEXT
      }

      LoopJobErrorHandling.RETRY -> {
        counters.retryCount++
        if (counters.retryCount <= loopConfig.maxRetries) {
          loopJobIterationRepository.save(iterationRecord)
          kLogger.warn {
            "🔁 재시도 ${counters.retryCount}/${loopConfig.maxRetries} - job: $jobId, iteration: $iteration, errors: ${iterationRecord.internalErrorCount}"
          }
          val backoffMs = loopConfig.delayMs.coerceAtLeast(1000) * counters.retryCount
          Thread.sleep(backoffMs)
          IterationOutcome.RETRY
        } else {
          markIterationFailure(jobId, iterationRecord, "재시도 횟수 초과: ${cause.message}")
          counters.failureCount++
          kLogger.error { "❌ 재시도 횟수 초과 (${loopConfig.maxRetries}회) - job: $jobId, errors: ${iterationRecord.internalErrorCount}" }
          emitLoopError(jobId, iteration, "재시도 횟수 초과: ${cause.message}", correlationId)
          throw RuntimeException("Loop 반복 실패 (RETRY 횟수 초과) - iteration: $iteration, error: ${cause.message}", cause)
        }
      }
    }
  }

  private fun markIterationFailure(
    jobId: String,
    iterationRecord: LoopJobIteration,
    errorMessage: String?
  ) {
    iterationRecord.complete(LoopJobIterationStatus.FAILED, errorMessage)
    loopJobIterationRepository.save(iterationRecord)

    loopJobRepository.incrementFailureCount(jobId)
    loopJobRepository.incrementCount(jobId)
  }

  /**
   * 단일 워크플로우 반복 실행
   *
   * **핵심**: emitter = null로 전달하여 백그라운드 실행
   * 기존 노드들의 sink?.next(...) 호출은 null-safe로 무시됨
   *
   * **체크포인트**: 각 노드의 시작/완료/실패 시점에 체크포인트 저장
   * 복구 시 마지막 체크포인트 노드부터 재실행 가능
   *
   * @param jobId DTE Job ID (= Loop Job ID)
   * @param workflow 실행할 워크플로우
   * @param iteration 현재 반복 횟수
   * @param session 워크플로우 실행에 사용할 세션
   * @param correlationId 연관 요청 ID
   */
  private fun executeWorkflowIteration(
    jobId: String,
    workflow: Workflow,
    iteration: Int,
    session: JSession,
    correlationId: String?
  ) {
    val latch = CountDownLatch(1)

    // 노드별 체크포인트 콜백 생성
    val checkpointCallback = createCheckpointCallback(jobId, iteration)

    val engine = BasicWorkflowEngine(
      objectMapper = objectMapper,
      factory = factory,
      session = session.deepCopy(), // 복사본 사용 (스레드 안전성)
      workflow = workflow,
      topic = topic,
      eventSender = eventSender,
      emitter = null, // SSE 이벤트 발송 없음 (백그라운드)
      cancellationManager = null,
      cancellationKey = null,
      onFinish = { output ->
        kLogger.debug { "워크플로우 완료 - jobId: $jobId, iteration: $iteration, output: $output" }
        latch.countDown()
      },
      checkpointCallback = checkpointCallback
    )

    try {
      // 체크포인트 저장 (워크플로우 시작)
      saveCheckpoint(
        LoopJobCheckpoint(
          jobId = jobId,
          iterationCount = iteration,
          currentNodeId = "workflow-start",
          nodeState = LoopJobCheckpointState.STARTED,
          nodeType = "workflow",
        )
      )

      // 워크플로우 실행
      engine.executeWorkflow(emptyMap())

      // 완료 대기 (타임아웃 설정)
      val completed = latch.await(WORKFLOW_EXECUTION_TIMEOUT_MINUTES, TimeUnit.MINUTES)
      if (!completed) {
        throw RuntimeException("워크플로우 실행 타임아웃 (${WORKFLOW_EXECUTION_TIMEOUT_MINUTES}분)")
      }

      // 체크포인트 저장 (워크플로우 완료)
      saveCheckpoint(
        LoopJobCheckpoint(
          jobId = jobId,
          iterationCount = iteration,
          currentNodeId = "workflow-complete",
          nodeState = LoopJobCheckpointState.COMPLETED,
          nodeType = "workflow",
        )
      )

    } catch (e: Exception) {
      throw e
    }
  }

  /**
   * 체크포인트 콜백 생성
   *
   * 노드 실행 상태를 추적하여 장애 복구에 활용하며,
   * WorkflowSseManager를 통해 NODE_* 이벤트도 발행합니다.
   *
   * @param jobId Job ID (= 워크플로우 실행 ID)
   * @param iteration 현재 반복 횟수
   * @return CheckpointCallback 구현체
   */
  private fun createCheckpointCallback(jobId: String, iteration: Int): CheckpointCallback {
    return object : CheckpointCallback {
      override fun onNodeStarted(nodeId: String, nodeType: String) {
        // 1. 노드 구동 시작 시 체크포인트 저장 (복구 시 이 노드부터 재실행)
        saveCheckpoint(
          LoopJobCheckpoint(
            jobId = jobId,
            iterationCount = iteration,
            currentNodeId = nodeId,
            nodeState = LoopJobCheckpointState.STARTED,
            nodeType = nodeType,
          )
        )

        // 2. NODE_STARTED 이벤트 발행
        workflowSseManager.emitNodeStarted(
          workflowExecutionId = jobId,
          nodeId = nodeId,
          nodeStatus = NodeExecutionState.IN_PROGRESS.name
        )

        kLogger.debug { "📍 체크포인트(STARTED): job=$jobId, iter=$iteration, node=$nodeId ($nodeType)" }
      }

      override fun onNodeCompleted(nodeId: String, nodeType: String, output: Any?) {
        // 1. 노드 실행 완료 시 체크포인트 저장 (복구 시 다음 노드부터 실행)
        saveCheckpoint(
          LoopJobCheckpoint(
            jobId = jobId,
            iterationCount = iteration,
            currentNodeId = nodeId,
            nodeState = LoopJobCheckpointState.COMPLETED,
            nodeType = nodeType,
          )
        )

        // 2. NODE_COMPLETED 이벤트 발행
        workflowSseManager.emitNodeCompleted(
          workflowExecutionId = jobId,
          nodeId = nodeId,
          nodeStatus = NodeExecutionState.SUCCESS.name,
          output = output
        )

        kLogger.debug { "✅ 체크포인트(COMPLETED): job=$jobId, iter=$iteration, node=$nodeId ($nodeType)" }
      }

      override fun onNodeFailed(nodeId: String, nodeType: String, error: Throwable) {
        // 1. 노드 실행 실패 시 체크포인트 저장 (에러 정보 포함)
        saveCheckpoint(
          LoopJobCheckpoint(
            jobId = jobId,
            iterationCount = iteration,
            currentNodeId = nodeId,
            nodeState = LoopJobCheckpointState.FAILED,
            nodeType = nodeType,
            errorMessage = error.message,
          )
        )

        // 2. NODE_FAILED 이벤트 발행
        workflowSseManager.emitNodeFailed(
          workflowExecutionId = jobId,
          nodeId = nodeId,
          error = error.message ?: "Unknown error",
          nodeStatus = NodeExecutionState.ERROR.name
        )

        kLogger.warn { "❌ 체크포인트(FAILED): job=$jobId, iter=$iteration, node=$nodeId ($nodeType), error=${error.message}" }
      }
    }
  }

  /**
   * 체크포인트 저장 (Job당 1개만 유지)
   *
   * 체크포인트 저장 후 LoopJobEventBridge를 통해 상태 변경 이벤트를 발행하여
   * SSE 구독자에게 변경을 알린다.
   *
   * @param checkpoint 저장할 체크포인트
   */
  private fun saveCheckpoint(checkpoint: LoopJobCheckpoint) {
    loopJobCheckpointRepository.deleteByJobId(checkpoint.jobId)
    loopJobCheckpointRepository.save(checkpoint)
    kLogger.debug { "체크포인트 저장 - job: ${checkpoint.jobId}, node: ${checkpoint.currentNodeId}, state: ${checkpoint.nodeState}" }

    // SSE 구독자에게 상태 변경 이벤트 발행 (Loop Job 스트림용)
    loopJobEventBridge.emitStateChanged(checkpoint.jobId)
  }

  /**
   * 계속 실행 여부 확인
   *
   * 다음 조건을 모두 만족해야 계속 실행:
   * 1. 반복 횟수가 maxCount에 도달하지 않음
   * 2. Redis 취소/일시중지 플래그가 설정되지 않음
   *
   * @param jobId Job ID (Redis 플래그 확인용)
   * @param currentCount 현재 반복 횟수
   * @param maxCount 최대 반복 횟수
   * @return 계속 실행 여부
   */
  private fun shouldContinue(jobId: String, currentCount: Int, maxCount: Int): Boolean {
    // 1. 반복 횟수 체크
    if (currentCount >= maxCount) {
      return false
    }

    // 2. Redis 취소/일시중지 플래그 체크 (빠른 감지)
    if (loopJobRedisRepository.isCancelled(jobId) || loopJobRedisRepository.isPaused(jobId)) {
      return false
    }

    return true
  }

  /**
   * 워크플로우 데이터를 확보합니다.
   *
   * workflowData가 있으면 직접 사용하고,
   * 없으면 workflowId로 AiAgent를 조회하여 workflow 필드를 사용합니다.
   *
   * @param payload 워크플로우 Job 페이로드
   * @return 워크플로우 데이터 (Map)
   * @throws BadRequestException workflowId로 조회 실패 시
   */
  private fun resolveWorkflowData(payload: WorkflowJobPayload): Map<String, Any> {
    // workflowData가 있으면 우선 사용
    payload.workflowData?.let {
      kLogger.debug { "workflowData 직접 사용" }
      return it
    }

    // workflowId로 AiAgent 조회
    val workflowId = payload.workflowId
      ?: throw BadRequestException("workflowId 또는 workflowData 중 하나 필수")

    kLogger.debug { "workflowId로 AiAgent 조회 - workflowId: $workflowId" }
    val aiAgent = aiAgentService.findById(workflowId)
    return aiAgent.workflow
  }

  /**
   * AiAgent의 workflow JSON을 Workflow 객체로 변환
   *
   * @param workflowMap 워크플로우 JSON 데이터
   * @param canvasId 캔버스 ID
   * @return Workflow 객체
   */
  private fun parseWorkflow(workflowMap: Map<String, Any>, canvasId: String?): Workflow {
    val json = objectMapper.writeValueAsString(workflowMap)
    val workflow = objectMapper.readValue(json, Workflow::class.java)
    workflow.canvasId = canvasId

    // 노드 실행 상태 초기화
    workflow.nodes.forEach { node ->
      node.data.executionState = ExecutionState(NodeExecutionState.IDLE)
    }

    return workflow
  }

  /**
   * JSession을 복원하거나 새로 생성합니다.
   *
   * sessionData가 있으면 역직렬화하여 복원하고,
   * 없으면 accountId만으로 최소한의 세션을 생성합니다.
   *
   * @param payload 워크플로우 Job 페이로드
   * @return 복원된 또는 새로 생성된 JSession
   */
  private fun restoreSession(payload: WorkflowJobPayload): JSession {
    // sessionData가 있으면 복원 시도
    payload.sessionData?.let { sessionData ->
      try {
        kLogger.debug { "sessionData에서 JSession 복원" }
        return objectMapper.convertValue(sessionData, JSession::class.java)
      } catch (e: Exception) {
        kLogger.warn(e) { "JSession 복원 실패, 새 세션 생성" }
      }
    }

    // sessionData가 없거나 복원 실패 시 최소 세션 생성
    kLogger.debug { "최소 JSession 생성 - accountId: ${payload.accountId}" }
    return createMinimalSession(payload.accountId)
  }

  /**
   * 최소한의 JSession을 생성합니다.
   *
   * DTE Job 실행 시 세션 데이터가 없는 경우 사용합니다.
   * 백그라운드 실행이나 시스템 호출 시 주로 사용됩니다.
   *
   * @param accountId 계정 ID
   * @return 최소 정보만 담긴 JSession
   */
  private fun createMinimalSession(accountId: Long): JSession {
    return JSession(
      accountId = accountId,
      accessToken = "",
      tokenType = TokenType.SERVICE,
      username = "system",
      deviceUid = null,
      permissions = JPermissions(),
      payload = JPayload(),
      workspaces = Workspaces(),
      profileImageUrl = null,
      dead = false
    )
  }

  // ========== SSE 이벤트 발행 ==========

  /**
   * Loop 반복 완료 이벤트 발행
   */
  private fun emitIterationComplete(
    jobId: String,
    iteration: Int,
    maxCount: Int,
    correlationId: String?
  ) {
    val data = """{"iteration":$iteration,"maxCount":$maxCount,"event":"LOOP_ITERATION_COMPLETED"}"""
    eventBridge.emitData(jobId, TASK_TYPE, data, correlationId)
  }

  /**
   * Loop 반복 실패 이벤트 발행 (CONTINUE 모드)
   */
  private fun emitIterationFailed(
    jobId: String,
    iteration: Int,
    error: String,
    correlationId: String?
  ) {
    val data = """{"iteration":$iteration,"error":"$error","event":"LOOP_ITERATION_FAILED"}"""
    eventBridge.emitData(jobId, TASK_TYPE, data, correlationId)
  }

  /**
   * Loop 전체 완료 이벤트 발행
   */
  private fun emitLoopComplete(
    jobId: String,
    totalIterations: Int,
    successCount: Int,
    failureCount: Int,
    correlationId: String?
  ) {
    eventBridge.emitComplete(jobId, TASK_TYPE, correlationId)
  }

  /**
   * Loop 에러 이벤트 발행 (STOP/RETRY 실패)
   */
  private fun emitLoopError(
    jobId: String,
    iteration: Int,
    error: String,
    correlationId: String?
  ) {
    eventBridge.emitError(jobId, TASK_TYPE, "Loop 실패 (iteration: $iteration): $error", correlationId)
  }

}
