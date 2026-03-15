package com.jongmin.ai.dte.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.dto.TokenType
import com.jongmin.jspring.web.entity.JPayload
import com.jongmin.jspring.web.entity.JPermissions
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.web.entity.Workspaces
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.agent.executor.model.*
import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import com.jongmin.jspring.dte.component.WorkflowSseManager
import com.jongmin.ai.core.platform.component.loop.CheckpointCallback
import com.jongmin.ai.core.platform.service.AiAgentService
import com.jongmin.ai.dte.dto.WorkflowJobPayload
import com.jongmin.jspring.dte.entity.DistributedJob
import dev.langchain4j.data.message.ChatMessageType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 단일 워크플로우 실행기
 *
 * DTE Job으로 등록된 워크플로우를 1회 실행하는 컴포넌트입니다.
 * BasicWorkflowEngine을 래핑하여 SSE 모드 또는 백그라운드 모드로 실행합니다.
 *
 * 주요 기능:
 * - SSE 모드: EventBridgeFluxSink를 통해 실시간 이벤트 스트리밍
 * - 백그라운드 모드: emitter=null로 비동기 실행 (결과만 저장)
 * - workflowId로 AiAgent 조회 또는 workflowData 직접 사용
 * - JSession 복원/생성 (DTE Job 간 세션 전달)
 *
 * 사용 예시:
 * ```kotlin
 * // WorkflowTaskHandler에서 호출
 * singleWorkflowExecutor.execute(job, payload)
 * ```
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property eventBridge SSE 이벤트 브릿지 (Redis Pub/Sub)
 * @property workflowSseManager 워크플로우 SSE 이벤트 매니저 (워크플로우/노드 이벤트 발행)
 * @property aiAgentService AI 에이전트 서비스 (workflowId 조회용)
 * @property factory 노드 실행기 팩토리
 * @property eventSender Kafka 이벤트 전송기
 * @property cancellationManager AI 추론 취소 관리자
 * @property topic Kafka 토픽명
 */
@Component
class SingleWorkflowExecutor(
  private val objectMapper: ObjectMapper,
  private val eventBridge: DistributedJobEventBridge,
  private val workflowSseManager: WorkflowSseManager,
  private val aiAgentService: AiAgentService,
  private val factory: NodeExecutorFactory,
  private val eventSender: EventSender,
  private val cancellationManager: AIInferenceCancellationManager,
  @param:Value($$"${app.stream.event.topic.event-app}") private val topic: String,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 워크플로우 실행 타임아웃 (분) */
    private const val TIMEOUT_MINUTES = 30L

    /** 태스크 타입 상수 */
    const val TASK_TYPE = "WORKFLOW"
  }

  /**
   * 단일 워크플로우를 1회 실행합니다.
   *
   * 실행 흐름:
   * 1. 워크플로우 데이터 확보 (workflowData 우선, 없으면 workflowId로 조회)
   * 2. JSession 생성/복원
   * 3. SSE 모드면 EventBridgeFluxSink 생성, 백그라운드면 null
   * 4. 취소 관리자에 등록
   * 5. WORKFLOW_STARTED 이벤트 발행
   * 6. BasicWorkflowEngine 생성 및 실행 (노드 콜백으로 NODE_* 이벤트 발행)
   * 7. SSE 모드일 경우 완료 대기
   * 8. 완료/실패 시 WORKFLOW_COMPLETED/WORKFLOW_FAILED 이벤트 발행
   *
   * @param job DTE Job 엔티티
   * @param payload 워크플로우 Job 페이로드
   * @return 워크플로우 실행 결과 (완료 시)
   * @throws BadRequestException 워크플로우 데이터를 찾을 수 없는 경우
   * @throws TimeoutException 실행이 타임아웃된 경우
   */
  fun execute(job: DistributedJob, payload: WorkflowJobPayload): Any? {
    val workflowExecutionId = job.id
    kLogger.info { "워크플로우 실행 시작 - jobId: $workflowExecutionId, streaming: ${payload.streaming}" }

    // 1. 워크플로우 데이터 확보
    val workflowData = resolveWorkflowData(payload)

    // 2. Workflow 객체로 변환
    val workflow = objectMapper.convertValue(workflowData, Workflow::class.java)
    workflow.canvasId = payload.canvasId

    // 노드 실행 상태 초기화
    workflow.nodes.forEach { node ->
      node.data.executionState = ExecutionState(NodeExecutionState.IDLE)
    }

    // 3. JSession 생성/복원
    val session = restoreSession(payload)

    // 4. 취소 키 등록
    val cancellationKey = workflowExecutionId
    cancellationManager.registerInference(cancellationKey)

    // 5. SSE 모드 여부에 따라 emitter 생성
    val emitter: FluxSink<String>? = if (payload.streaming) {
      createEmitter(job)
    } else {
      null
    }

    // 6. 노드 이벤트 발행을 위한 CheckpointCallback 생성
    val nodeEventCallback = createNodeEventCallback(workflowExecutionId)

    // 결과를 저장할 변수
    var workflowResult: Any? = null

    try {
      // 7. WORKFLOW_STARTED 이벤트 발행
      workflowSseManager.emitWorkflowStarted(workflowExecutionId)

      // 8. BasicWorkflowEngine 생성 및 실행
      workflowResult = executeWorkflow(
        session = session,
        workflow = workflow,
        payload = payload,
        emitter = emitter,
        cancellationKey = cancellationKey,
        jobId = workflowExecutionId,
        checkpointCallback = nodeEventCallback
      )

      // 9. SSE 모드일 경우 완료 대기
      if (emitter != null && emitter is EventBridgeFluxSink) {
        awaitCompletion(emitter, workflowExecutionId)
      }

      kLogger.info { "워크플로우 실행 완료 - jobId: $workflowExecutionId" }
      return workflowResult
    } catch (e: Exception) {
      // 에러 발생 시 WORKFLOW_FAILED 이벤트 발행 및 정리
      workflowSseManager.emitWorkflowFailed(workflowExecutionId, e.message ?: "Unknown error")
      cleanup(cancellationKey, emitter, e)
      throw e
    }
  }

  /**
   * 노드 이벤트 발행을 위한 CheckpointCallback을 생성합니다.
   *
   * 노드의 시작/완료/실패 시점에 WorkflowSseManager를 통해
   * NODE_STARTED, NODE_COMPLETED, NODE_FAILED 이벤트를 발행합니다.
   *
   * @param workflowExecutionId 워크플로우 실행 ID (=DTE Job ID)
   * @return CheckpointCallback 구현체
   */
  private fun createNodeEventCallback(workflowExecutionId: String): CheckpointCallback {
    return object : CheckpointCallback {
      override fun onNodeStarted(nodeId: String, nodeType: String) {
        kLogger.debug { "노드 시작 - workflowExecutionId: $workflowExecutionId, nodeId: $nodeId, nodeType: $nodeType" }
        workflowSseManager.emitNodeStarted(
          workflowExecutionId = workflowExecutionId,
          nodeId = nodeId,
          nodeStatus = NodeExecutionState.IN_PROGRESS.name
        )
      }

      override fun onNodeCompleted(nodeId: String, nodeType: String, output: Any?) {
        kLogger.debug { "노드 완료 - workflowExecutionId: $workflowExecutionId, nodeId: $nodeId, nodeType: $nodeType" }
        workflowSseManager.emitNodeCompleted(
          workflowExecutionId = workflowExecutionId,
          nodeId = nodeId,
          nodeStatus = NodeExecutionState.SUCCESS.name,
          output = output
        )
      }

      override fun onNodeFailed(nodeId: String, nodeType: String, error: Throwable) {
        kLogger.warn { "노드 실패 - workflowExecutionId: $workflowExecutionId, nodeId: $nodeId, nodeType: $nodeType, error: ${error.message}" }
        workflowSseManager.emitNodeFailed(
          workflowExecutionId = workflowExecutionId,
          nodeId = nodeId,
          error = error.message ?: "Unknown error",
          nodeStatus = NodeExecutionState.ERROR.name
        )
      }
    }
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

  /**
   * EventBridgeFluxSink를 생성합니다.
   *
   * SSE 모드 실행 시 워크플로우 이벤트를 Redis Pub/Sub로 전달합니다.
   *
   * @param job DTE Job
   * @return EventBridgeFluxSink 인스턴스
   */
  private fun createEmitter(job: DistributedJob): EventBridgeFluxSink {
    return EventBridgeFluxSink(
      jobId = job.id,
      jobType = TASK_TYPE,
      eventBridge = eventBridge,
      correlationId = job.correlationId
    )
  }

  /**
   * BasicWorkflowEngine을 생성하고 워크플로우를 실행합니다.
   *
   * messages 또는 input에 따라 적절한 executeWorkflow 메서드를 호출합니다.
   *
   * @param session 사용자 세션
   * @param workflow 워크플로우 객체
   * @param payload Job 페이로드
   * @param emitter SSE emitter (null이면 백그라운드 모드)
   * @param cancellationKey 취소 관리 키
   * @param jobId Job ID (워크플로우 실행 ID)
   * @param checkpointCallback 노드 이벤트 발행용 콜백
   * @return 워크플로우 실행 결과
   */
  private fun executeWorkflow(
    session: JSession,
    workflow: Workflow,
    payload: WorkflowJobPayload,
    emitter: FluxSink<String>?,
    cancellationKey: String,
    jobId: String,
    checkpointCallback: CheckpointCallback
  ): Any? {
    // 결과를 저장할 변수 (onFinish 콜백에서 설정)
    var result: Any? = null

    val engine = BasicWorkflowEngine(
      objectMapper = objectMapper,
      factory = factory,
      session = session.deepCopy(),
      workflow = workflow,
      topic = topic,
      eventSender = eventSender,
      emitter = emitter,
      cancellationManager = cancellationManager,
      cancellationKey = cancellationKey,
      onFinish = { output ->
        result = output
        handleWorkflowFinish(jobId, output, emitter)
      },
      checkpointCallback = checkpointCallback
    )

    // messages가 있으면 채팅 형식으로 실행
    val messages = payload.messages?.let { convertToMessages(it) }
    if (messages != null) {
      kLogger.debug { "메시지 기반 워크플로우 실행 - messageCount: ${messages.size}" }
      engine.executeWorkflow(messages)
      return result
    }

    // input이 있으면 Map 형식으로 실행
    payload.input?.let { input ->
      kLogger.debug { "입력 데이터 기반 워크플로우 실행 - inputKeys: ${input.keys}" }
      engine.executeWorkflow(input)
      return result
    }

    // 둘 다 없으면 빈 상태로 실행 (시작 노드의 기본 설정 사용)
    kLogger.debug { "기본 설정으로 워크플로우 실행" }
    engine.executeWorkflow(null as List<IAiChatMessage>?)
    return result
  }

  /**
   * 메시지 맵 리스트를 IAiChatMessage 리스트로 변환합니다.
   *
   * 각 메시지 맵에서 role과 content를 추출하여 IAiChatMessage를 생성합니다.
   * role 매핑:
   * - "system" -> ChatMessageType.SYSTEM
   * - "user" -> ChatMessageType.USER
   * - "assistant" -> ChatMessageType.AI
   * - 기타 -> ChatMessageType.CUSTOM
   *
   * @param messages 메시지 맵 리스트 (role, content 필드 포함)
   * @return IAiChatMessage 리스트
   */
  private fun convertToMessages(messages: List<Map<String, Any>>): List<IAiChatMessage> {
    return messages.mapNotNull { messageMap ->
      try {
        val role = messageMap["role"] as? String ?: "user"
        val content = messageMap["content"] as? String ?: ""

        // role을 ChatMessageType으로 변환
        val messageType = when (role.lowercase()) {
          "system" -> ChatMessageType.SYSTEM
          "user" -> ChatMessageType.USER
          "assistant" -> ChatMessageType.AI
          "tool" -> ChatMessageType.TOOL_EXECUTION_RESULT
          else -> ChatMessageType.CUSTOM
        }

        IAiChatMessage.from(messageType, content)
      } catch (e: Exception) {
        kLogger.warn(e) { "메시지 변환 실패 - message: $messageMap" }
        null
      }
    }
  }

  /**
   * 워크플로우 완료 시 처리합니다.
   *
   * onFinish 콜백에서 호출되며, WORKFLOW_COMPLETED 이벤트를 발행하고
   * 결과를 로깅한 뒤 emitter를 완료 처리합니다.
   *
   * @param jobId Job ID (워크플로우 실행 ID)
   * @param output 워크플로우 출력 결과
   * @param emitter SSE emitter (null이면 백그라운드 모드)
   */
  private fun handleWorkflowFinish(jobId: String, output: Any?, emitter: FluxSink<String>?) {
    kLogger.info { "워크플로우 onFinish 콜백 - jobId: $jobId" }

    // 결과 로깅
    when (output) {
      is Map<*, *> -> {
        kLogger.debug { "워크플로우 출력 - keys: ${output.keys}" }
      }

      else -> {
        kLogger.debug { "워크플로우 출력 - type: ${output?.javaClass?.simpleName}" }
      }
    }

    // WORKFLOW_COMPLETED 이벤트 발행
    workflowSseManager.emitWorkflowCompleted(jobId, output)

    // SSE 모드면 complete 호출
    emitter?.complete()
  }

  /**
   * SSE 스트리밍 완료를 대기합니다.
   *
   * EventBridgeFluxSink의 awaitCompletion을 사용하여
   * complete() 또는 error()가 호출될 때까지 블로킹합니다.
   *
   * @param emitter EventBridgeFluxSink
   * @param jobId Job ID
   * @throws TimeoutException 타임아웃 발생 시
   * @throws RuntimeException 스트림이 에러로 종료된 경우
   */
  private fun awaitCompletion(emitter: EventBridgeFluxSink, jobId: String) {
    kLogger.debug { "워크플로우 완료 대기 - jobId: $jobId, timeout: ${TIMEOUT_MINUTES}분" }

    val completed = emitter.awaitCompletion(TIMEOUT_MINUTES, TimeUnit.MINUTES)
    if (!completed) {
      throw TimeoutException("워크플로우 실행 타임아웃 - jobId: $jobId, timeout: ${TIMEOUT_MINUTES}분")
    }
  }

  /**
   * 에러 발생 시 정리 작업을 수행합니다.
   *
   * @param cancellationKey 취소 관리 키
   * @param emitter SSE emitter (null이면 백그라운드 모드)
   * @param error 발생한 에러
   */
  private fun cleanup(cancellationKey: String, emitter: FluxSink<String>?, error: Exception) {
    kLogger.error(error) { "워크플로우 실행 실패 - cancellationKey: $cancellationKey" }

    // 취소 상태로 설정
    cancellationManager.requestCancellation(cancellationKey)

    // emitter에 에러 전파
    emitter?.error(error)
  }
}
