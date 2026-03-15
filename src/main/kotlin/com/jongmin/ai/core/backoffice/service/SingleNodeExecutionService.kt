package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.ai.core.backoffice.dto.request.ExecuteMode
import com.jongmin.ai.core.backoffice.dto.request.ExecuteNodeRequest
import com.jongmin.ai.core.backoffice.dto.request.LlmOverrides
import com.jongmin.ai.core.backoffice.dto.response.ExecuteNodeErrorCode
import com.jongmin.ai.core.backoffice.dto.response.ExecuteNodeResponse
import com.jongmin.ai.core.platform.component.agent.executor.model.*
import com.jongmin.ai.core.platform.service.AiAssistantService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 노드 실행 서비스
 *
 * 워크플로우 에디터에서 노드를 테스트하기 위한 서비스.
 * - single 모드: 특정 노드만 독립적으로 실행
 * - flow 모드: 특정 노드부터 마지막 노드까지 순차 실행
 *
 * 주요 기능:
 * - 노드 타입별 Executor 인스턴스화
 * - 입력 데이터 주입
 * - LLM 오버라이드 적용
 * - 동기/스트리밍 실행 지원
 * - Flow 모드: 위상 정렬 순서대로 노드 체인 실행
 *
 * @author Claude Code
 * @since 2025.12.26
 */
@Service
class SingleNodeExecutionService(
  private val objectMapper: ObjectMapper,
  @param:Value("\${app.stream.event.topic.event-app}") private val topic: String,
  private val eventSender: EventSender,
  private val factory: NodeExecutorFactory,
  private val flowExecutionCoordinator: SingleNodeFlowExecutionCoordinator,
  private val aiAssistantService: AiAssistantService,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * LLM 호출이 필요한 노드 타입 목록
   * 이 목록에 포함된 노드는 aiAssistantId가 필수
   */
  private val llmNodeTypes = setOf(
    // 시나리오 생성 노드 (10단계 전체)
    "scenario-concept", "scenario-truth", "scenario-characters", "scenario-timeline",
    "scenario-clues", "scenario-roleplay", "scenario-world-building",
    "scenario-synopsis", "scenario-prologue", "scenario-epilogue",
    // 레거시 호환
    "concept-generator", "truth-generator", "characters-generator", "timeline-generator",
    "clues-generator", "roleplay-generator", "world-building-generator",
    "synopsis-generator", "prologue-generator", "epilogue-generator",
    // 텍스트 생성 노드
    "generate-text"
  )

  /**
   * LLM 없이 실행 가능한 Tool 노드 타입 목록
   * 이 목록에 포함된 노드는 aiAssistantId가 불필요
   */
  private val toolNodeTypes = setOf(
    "rest-api-call-tool",
    "web-search-tool",
    "youtube-summary-tool",
    "article-analyzer-tool"
  )

  /**
   * 선택적 LLM 노드 타입 목록
   * 내부에서 LLM을 사용하지만 aiAssistantId가 선택적인 노드들.
   * (노드 내부 서비스에서 기본 어시스턴트를 사용할 수 있음)
   */
  private val optionalLlmNodeTypes = setOf(
    "concept-suggestion"  // 컨셉 추천 노드 - aiAssistantId 선택적
  )

  /**
   * 제어/유틸리티 노드 타입 목록
   * 라우터, 머지, 딜레이 등 흐름 제어 노드
   */
  private val utilityNodeTypes = setOf(
    "router", "finish", "merge", "join", "delay"
  )

  /** 지원하는 전체 노드 타입 목록 */
  private val supportedNodeTypes = llmNodeTypes + toolNodeTypes + utilityNodeTypes + optionalLlmNodeTypes

  /** 노드 타입별 필수 입력 핸들 정의 */
  private val requiredInputsByNodeType = mapOf(
    // 시나리오 생성 노드 (10단계)
    "scenario-concept" to listOf("input"),
    "scenario-truth" to listOf("concept"),
    "scenario-characters" to listOf("concept", "truth"),
    "scenario-timeline" to listOf("concept", "truth", "characters"),
    "scenario-clues" to listOf("truth", "characters", "timeline"),
    "scenario-roleplay" to listOf("characters", "clues", "truth"),
    "scenario-world-building" to listOf("concept", "clues", "characters"),
    "scenario-synopsis" to listOf("concept", "truth", "characters", "worldBuilding"),
    "scenario-prologue" to listOf("synopsis", "timeline", "characters", "worldBuilding"),
    "scenario-epilogue" to listOf("truth", "characters", "clues", "timeline", "prologue"),
    // 레거시 호환
    "concept-generator" to listOf("input"),
    "truth-generator" to listOf("concept"),
    "characters-generator" to listOf("concept", "truth"),
    "timeline-generator" to listOf("concept", "truth", "characters"),
    "clues-generator" to listOf("truth", "characters", "timeline"),
    "roleplay-generator" to listOf("characters", "clues", "truth"),
    "world-building-generator" to listOf("concept", "clues", "characters"),
    "synopsis-generator" to listOf("concept", "truth", "characters", "worldBuilding"),
    "prologue-generator" to listOf("synopsis", "timeline", "characters", "worldBuilding"),
    "epilogue-generator" to listOf("truth", "characters", "clues", "timeline", "prologue"),
    // 일반 노드
    "generate-text" to listOf("prompt"),
    "router" to emptyList(), // 동적 입력
    "finish" to emptyList(), // 종료 노드 - 입력 검사 불필요
    // Tool 노드
    "rest-api-call-tool" to listOf("payload"),
    "web-search-tool" to listOf("query"),
    "youtube-summary-tool" to listOf("url"),
    "article-analyzer-tool" to listOf("url"),
    // 유틸리티 노드
    "merge" to emptyList(),
    "join" to emptyList(),
    "delay" to emptyList(),
    // 선택적 LLM 노드
    "concept-suggestion" to emptyList()  // 모든 입력 선택적 (fixedFields, excludeThemes, preferredStyle)
  )

  /** 실행 타임아웃 (초) */
  private val executionTimeoutSeconds = 300L

  private data class PreparedSingleNodeExecution(
    val workflow: Workflow,
    val node: Node,
    val context: SingleNodeExecutionContext
  )

  /**
   * 동기 노드 실행
   *
   * 노드를 실행하고 완료될 때까지 대기한 후 결과를 반환한다.
   *
   * @param session 현재 세션
   * @param request 실행 요청
   * @return 실행 결과 (성공/실패)
   */
  fun executeNode(session: JSession, request: ExecuteNodeRequest): ExecuteNodeResponse {
    kLogger.info { "[SingleNodeExecution] 동기 구동 시작 - nodeType: ${request.nodeType}, nodeId: ${request.nodeId}" }

    // 1. 유효성 검사
    val validationError = validateRequest(request)
    if (validationError != null) return validationError

    return try {
      // 2. AI 어시스턴트 확인 (LLM 노드인 경우에만)
      validateAssistantIfRequired(request)?.let { return it }

      // 3. 실행 준비
      val prepared = prepareSingleNodeExecution(request, streaming = false)

      // 4. 노드 실행
      val executor = createExecutor(
        session = session,
        nodeType = request.nodeType,
        emitter = null,
        canvasId = prepared.workflow.canvasId
      )

      // 타임아웃 적용 실행
      val future = CompletableFuture.supplyAsync {
        executeNodeInternal(executor, prepared.node, prepared.context)
      }

      val results = future.get(executionTimeoutSeconds, TimeUnit.SECONDS)

      kLogger.info { "[SingleNodeExecution] 동기 실행 완료 - outputs: ${results.keys}" }
      ExecuteNodeResponse.success(results)

    } catch (e: TimeoutException) {
      kLogger.error(e) { "[SingleNodeExecution] 실행 타임아웃 - nodeId: ${request.nodeId}" }
      ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.TIMEOUT,
        "노드 실행 시간이 초과되었습니다 (${executionTimeoutSeconds}초)"
      )
    } catch (e: Exception) {
      kLogger.error(e) { "[SingleNodeExecution] 실행 실패 - nodeId: ${request.nodeId}" }
      ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.LLM_GENERATION_FAILED,
        "노드 실행 중 오류가 발생했습니다: ${e.message}",
        mapOf("exception" to e.javaClass.simpleName)
      )
    }
  }

  /**
   * 스트리밍 노드 실행 (진입점)
   *
   * executeMode에 따라 single 또는 flow 모드로 실행.
   *
   * @param session 현재 세션
   * @param request 실행 요청
   * @param sink SSE 스트리밍 sink
   */
  fun executeNodeStreaming(
    session: JSession,
    request: ExecuteNodeRequest,
    sink: FluxSink<String>
  ) {
    when (request.executeMode) {
      ExecuteMode.SINGLE -> executeSingleNodeStreaming(session, request, sink)
      ExecuteMode.FLOW -> executeFlowStreaming(session, request, sink)
    }
  }

  /**
   * 싱글 노드 스트리밍 실행
   *
   * 노드를 실행하면서 중간 결과를 FluxSink로 스트리밍한다.
   * 완료 후 최종 결과도 스트리밍으로 전송.
   */
  private fun executeSingleNodeStreaming(
    session: JSession,
    request: ExecuteNodeRequest,
    sink: FluxSink<String>
  ) {
    kLogger.info { "[SingleNodeExecution] 싱글 모드 구동 시작 - nodeType: ${request.nodeType}, nodeId: ${request.nodeId}" }

    // 1. 유효성 검사
    val validationError = validateRequest(request)
    if (validationError != null) {
      emitErrorAndComplete(sink, validationError)
      return
    }

    try {
      // 2. AI 어시스턴트 확인 (LLM 노드인 경우에만)
      val assistantValidationError = validateAssistantIfRequired(request)
      if (assistantValidationError != null) {
        emitErrorAndComplete(sink, assistantValidationError)
        return
      }

      // 3. 실행 준비
      val prepared = prepareSingleNodeExecution(request, streaming = request.streaming)

      // 4. 노드 실행 (스트리밍 모드)
      val executor = createExecutor(
        session = session,
        nodeType = request.nodeType,
        emitter = sink,
        canvasId = prepared.workflow.canvasId
      )

      val results = executeNodeInternal(executor, prepared.node, prepared.context)

      // 6. 최종 결과 전송
      emitNodeExecutionComplete(sink, request.nodeId, results)
      emitResponseAndComplete(sink, ExecuteNodeResponse.success(results))

      kLogger.info { "[SingleNodeExecution] 싱글 모드 실행 완료 - outputs: ${results.keys}" }

    } catch (e: Exception) {
      kLogger.error(e) { "[SingleNodeExecution] 싱글 모드 실행 실패 - nodeId: ${request.nodeId}" }
      emitErrorAndComplete(
        sink, ExecuteNodeResponse.failure(
          ExecuteNodeErrorCode.LLM_GENERATION_FAILED,
          "노드 실행 중 오류가 발생했습니다: ${e.message}",
          mapOf("exception" to e.javaClass.simpleName)
        )
      )
    }
  }

  /**
   * Flow 모드 스트리밍 실행
   *
   * 요청한 노드부터 마지막 노드까지 순차적으로 실행.
   * AI Agent의 저장된 워크플로우를 사용한다.
   */
  private fun executeFlowStreaming(
    session: JSession,
    request: ExecuteNodeRequest,
    sink: FluxSink<String>
  ) {
    flowExecutionCoordinator.execute(
      session = session,
      request = request,
      sink = sink,
      supportedNodeTypes = supportedNodeTypes,
      requiredInputsByNodeType = requiredInputsByNodeType,
    )
  }

  private fun validateAssistantIfRequired(request: ExecuteNodeRequest): ExecuteNodeResponse? {
    if (request.nodeType !in llmNodeTypes) {
      kLogger.debug { "  └─ Tool/Utility 노드 - AI 어시스턴트 불필요" }
      return null
    }

    val assistant = try {
      aiAssistantService.findById(request.aiAssistantId!!)
    } catch (e: ObjectNotFoundException) {
      return ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.AI_ASSISTANT_NOT_FOUND,
        "AI 어시스턴트를 찾을 수 없습니다: ${request.aiAssistantId}"
      )
    }
    kLogger.debug { "  └─ AI 어시스턴트 확인: ${assistant.name} (${assistant.id})" }

    return null
  }

  private fun prepareSingleNodeExecution(
    request: ExecuteNodeRequest,
    streaming: Boolean
  ): PreparedSingleNodeExecution {
    val overridesMap = request.overrides?.toConfigMap()
    val workflow = SingleNodeExecutionContext.createMinimalWorkflow(
      nodeId = request.nodeId,
      nodeType = request.nodeType,
      aiAssistantId = request.aiAssistantId,
      streaming = streaming,
      nodeConfig = request.nodeConfig,
      overrides = overridesMap
    )
    kLogger.debug { "  └─ 워크플로우 생성 완료 - config: ${workflow.nodes.first().data.config}" }

    val context = SingleNodeExecutionContext(
      factory = factory,
      workflow = workflow,
      injectedInputs = request.inputs
    )
    kLogger.debug { "  └─ 주입된 입력: ${request.inputs.keys}" }

    return PreparedSingleNodeExecution(
      workflow = workflow,
      node = workflow.nodes.first(),
      context = context
    )
  }

  private fun createExecutor(
    session: JSession,
    nodeType: String,
    emitter: FluxSink<String>?,
    canvasId: String?
  ): NodeExecutor<ExecutionContext> {
    return factory.getExecutor<ExecutionContext>(
      objectMapper = objectMapper,
      session = session.deepCopy(),
      type = nodeType,
      topic = topic,
      eventSender = eventSender,
      emitter = emitter,
      canvasId = canvasId
    )
  }

  /**
   * 요청 유효성 검사
   */
  private fun validateRequest(request: ExecuteNodeRequest): ExecuteNodeResponse? {
    // 노드 타입 검사
    if (request.nodeType !in supportedNodeTypes) {
      return ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.INVALID_NODE_TYPE,
        "지원하지 않는 노드 타입입니다: ${request.nodeType}",
        mapOf("supportedTypes" to supportedNodeTypes)
      )
    }

    // LLM 노드인 경우 aiAssistantId 필수 검사
    if (request.nodeType in llmNodeTypes && request.aiAssistantId == null) {
      return ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.MISSING_INPUT,
        "LLM 노드(${request.nodeType})는 aiAssistantId가 필수입니다",
        mapOf(
          "nodeType" to request.nodeType,
          "isLlmNode" to true
        )
      )
    }

    // 필수 입력 검사
    val requiredInputs = requiredInputsByNodeType[request.nodeType] ?: emptyList()
    val missingInputs = requiredInputs.filter { it !in request.inputs }
    if (missingInputs.isNotEmpty()) {
      return ExecuteNodeResponse.failure(
        ExecuteNodeErrorCode.MISSING_INPUT,
        "필수 입력이 누락되었습니다: ${missingInputs.joinToString(", ")}",
        mapOf(
          "missingInputs" to missingInputs,
          "requiredInputs" to requiredInputs,
          "providedInputs" to request.inputs.keys.toList()
        )
      )
    }

    return null
  }

  /**
   * 노드 실행 내부 로직 (SingleNodeExecutionContext용)
   *
   * ⚠️ 중요: asynchronous = false로 동기 실행해야 결과를 기다릴 수 있다.
   * 기본값 true면 virtual thread에서 비동기 실행되어 즉시 반환됨.
   */
  private fun executeNodeInternal(
    executor: NodeExecutor<ExecutionContext>,
    node: Node,
    context: SingleNodeExecutionContext
  ): Map<String, String> {
    executeNode(executor, node, context)
    return context.getOutputResults()
  }

  /**
   * 노드 실행 공통 로직
   */
  private fun executeNode(
    executor: NodeExecutor<ExecutionContext>,
    node: Node,
    context: ExecutionContext
  ) {
    // PreparedNodeExecutor인 경우 prepare 먼저 호출
    if (executor is PreparedNodeExecutor<*>) {
      @Suppress("UNCHECKED_CAST")
      (executor as PreparedNodeExecutor<ExecutionContext>).prepare(node, context)
    }

    // 노드 실행 (동기 모드 - 완료까지 대기)
    executor.execute(node, context, asynchronous = false)
  }

  /**
   * 에러 응답 스트리밍 후 완료
   *
   * Spring WebFlux가 "data:" 접두사를 자동 추가하지만 공백 없이 붙이므로 앞에 공백 추가.
   * 결과: "data: {json}" 형태로 정상 SSE 포맷
   */
  private fun emitErrorAndComplete(sink: FluxSink<String>, response: ExecuteNodeResponse) {
    val json = objectMapper.writeValueAsString(response)
    sink.next(" $json")
    sink.complete()
  }

  /**
   * 성공 응답 스트리밍 후 완료
   *
   * Spring WebFlux가 "data:" 접두사를 자동 추가하지만 공백 없이 붙이므로 앞에 공백 추가.
   * 결과: "data: {json}" 형태로 정상 SSE 포맷
   */
  private fun emitResponseAndComplete(sink: FluxSink<String>, response: ExecuteNodeResponse) {
    val json = objectMapper.writeValueAsString(response)
    sink.next(" $json")
    sink.complete()
  }

  /**
   * 노드 실행 완료 이벤트 전송
   *
   * 각 노드 실행이 완료될 때마다 FE에 알림.
   * FE는 이 이벤트를 받아 해당 노드의 상태를 SUCCESS로 업데이트.
   *
   * Spring WebFlux가 "data:" 접두사를 자동 추가하지만 공백 없이 붙이므로 앞에 공백 추가.
   * 결과: "data: {json}" 형태로 정상 SSE 포맷
   *
   * @param sink SSE sink
   * @param nodeId 완료된 노드 ID
   * @param outputs 노드 출력 결과
   */
  private fun emitNodeExecutionComplete(
    sink: FluxSink<String>,
    nodeId: String,
    outputs: Map<String, String>
  ) {
    val payload = mapOf(
      "type" to "NODE_EXECUTION_COMPLETE",
      "nodeId" to nodeId,
      "outputs" to outputs,
      "timestamp" to System.currentTimeMillis()
    )
    val json = objectMapper.writeValueAsString(payload)
    sink.next(" $json")
  }

  /**
   * LlmOverrides를 노드 config Map으로 변환
   */
  private fun LlmOverrides.toConfigMap(): Map<String, Any>? {
    val map = mutableMapOf<String, Any>()

    aiProviderId?.let { map["aiProviderId"] = it }
    aiModelId?.let { map["aiModelId"] = it }
    aiApiKeyId?.let { map["aiApiKeyId"] = it }
    temperature?.let { map["temperature"] = it }
    topP?.let { map["topP"] = it }
    topK?.let { map["topK"] = it }
    maxTokens?.let { map["maxTokens"] = it }
    frequencyPenalty?.let { map["frequencyPenalty"] = it }
    presencePenalty?.let { map["presencePenalty"] = it }
    responseFormat?.let { map["responseFormat"] = it }
    reasoningEffort?.let { map["reasoningEffort"] = it }
    noThinkTrigger?.let { map["noThinkTrigger"] = it }

    return if (map.isEmpty()) null else map
  }
}
