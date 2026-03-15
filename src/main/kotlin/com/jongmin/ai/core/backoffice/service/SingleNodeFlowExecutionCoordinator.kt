package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.backoffice.dto.request.ExecuteNodeRequest
import com.jongmin.ai.core.backoffice.dto.request.LlmOverrides
import com.jongmin.ai.core.backoffice.dto.response.ExecuteNodeErrorCode
import com.jongmin.ai.core.backoffice.dto.response.ExecuteNodeResponse
import com.jongmin.ai.core.platform.component.agent.executor.model.*
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * Single Node Flow 모드 실행 전담 코디네이터
 *
 * Flow 실행 절차(워크플로우 로드/순서 계산/노드 순차 실행/이벤트 발행)를
 * SingleNodeExecutionService에서 분리한다.
 */
@Component
class SingleNodeFlowExecutionCoordinator(
  private val objectMapper: ObjectMapper,
  @param:Value("\${app.stream.event.topic.event-app}") private val topic: String,
  private val eventSender: EventSender,
  private val factory: NodeExecutorFactory,
  private val flowExecutionPlanner: SingleNodeFlowExecutionPlanner,
  private val boAiAgentService: BoAiAgentService,
) {
  private val kLogger = KotlinLogging.logger {}

  fun execute(
    session: JSession,
    request: ExecuteNodeRequest,
    sink: FluxSink<String>,
    supportedNodeTypes: Set<String>,
    requiredInputsByNodeType: Map<String, List<String>>,
  ) {
    kLogger.info { "[FlowExecution] Flow 모드 구동 시작 - startNodeId: ${request.nodeId}, aiAgentId: ${request.aiAgentId}" }

    if (request.aiAgentId == null) {
      emitErrorAndComplete(
        sink, ExecuteNodeResponse.failure(
          ExecuteNodeErrorCode.MISSING_INPUT,
          "Flow 모드에서는 aiAgentId가 필수입니다"
        )
      )
      return
    }

    try {
      val aiAgent = try {
        boAiAgentService.findById(request.aiAgentId)
      } catch (_: ObjectNotFoundException) {
        emitErrorAndComplete(
          sink, ExecuteNodeResponse.failure(
            ExecuteNodeErrorCode.INVALID_NODE_TYPE,
            "AI Agent를 찾을 수 없습니다: ${request.aiAgentId}"
          )
        )
        return
      }

      val parsedWorkflow = flowExecutionPlanner.parseWorkflow(aiAgent.workflow)
      if (parsedWorkflow == null) {
        emitErrorAndComplete(
          sink, ExecuteNodeResponse.failure(
            ExecuteNodeErrorCode.INVALID_INPUT_FORMAT,
            "워크플로우를 파싱할 수 없습니다"
          )
        )
        return
      }

      // 저장된 executionState(SUCCESS/ERROR 등)와 무관하게 매 실행은 fresh 상태로 시작한다.
      val workflow = parsedWorkflow.copy(
        nodes = parsedWorkflow.nodes.map { node ->
          node.copy(
            data = node.data.copy(
              executionState = ExecutionState(NodeExecutionState.IDLE)
            )
          )
        }
      )
      kLogger.debug { "  └─ 워크플로우 로드: ${workflow.nodes.size}개 노드, ${workflow.edges.size}개 엣지" }

      val startNode = workflow.nodes.find { it.id == request.nodeId }
      if (startNode == null) {
        emitErrorAndComplete(
          sink, ExecuteNodeResponse.failure(
            ExecuteNodeErrorCode.INVALID_NODE_TYPE,
            "시작 노드를 찾을 수 없습니다: ${request.nodeId}"
          )
        )
        return
      }

      val startNodeRequiredInputs = requiredInputsByNodeType[startNode.type] ?: emptyList()
      val missingInputs = startNodeRequiredInputs.filter { it !in request.inputs }
      if (missingInputs.isNotEmpty()) {
        kLogger.warn { "[FlowExecution] 시작 노드 필수 입력 누락 - nodeType: ${startNode.type}, missing: $missingInputs" }
        emitErrorAndComplete(
          sink, ExecuteNodeResponse.failure(
            ExecuteNodeErrorCode.MISSING_INPUT,
            "시작 노드(${startNode.type})의 필수 입력이 누락되었습니다: ${missingInputs.joinToString(", ")}",
            mapOf(
              "nodeType" to startNode.type,
              "missingInputs" to missingInputs,
              "requiredInputs" to startNodeRequiredInputs,
              "providedInputs" to request.inputs.keys.toList()
            )
          )
        )
        return
      }
      kLogger.debug { "  └─ 시작 노드 필수 입력 검사 통과: ${startNodeRequiredInputs.joinToString(", ")}" }

      val executionOrder = flowExecutionPlanner.getExecutionOrder(workflow, request.nodeId)
      kLogger.info { "  └─ 실행 순서: ${executionOrder.map { it.id }}" }

      if (executionOrder.isEmpty()) {
        emitErrorAndComplete(
          sink, ExecuteNodeResponse.failure(
            ExecuteNodeErrorCode.INVALID_NODE_TYPE,
            "실행할 노드가 없습니다"
          )
        )
        return
      }

      val flowContext = FlowExecutionContext(
        factory = factory,
        workflow = workflow.copy(canvasId = "flow-execution-${System.currentTimeMillis()}"),
        initialInputs = request.inputs
      )

      val executedNodes = mutableListOf<String>()
      val overridesMap = request.overrides?.toConfigMap()

      for (node in executionOrder) {
        if (!node.isExecutable()) {
          kLogger.debug { "  └─ 비실행 노드 스킵: ${node.id} (${node.type})" }
          continue
        }

        if (node.type !in supportedNodeTypes) {
          kLogger.debug { "  └─ 미지원 노드 스킵: ${node.id} (${node.type})" }
          continue
        }

        kLogger.info { "  └─ 노드 구동 시작: ${node.id} (${node.type})" }

        val nodeWithConfig = flowExecutionPlanner.applyConfigToNode(
          node = node,
          aiAssistantId = request.aiAssistantId,
          streaming = request.streaming,
          overrides = overridesMap
        )

        val executor = createExecutor(
          session = session,
          nodeType = node.type,
          emitter = sink,
          canvasId = flowContext.workflow.canvasId
        )

        try {
          val results = executeNodeInternal(executor, nodeWithConfig, flowContext)
          flowContext.storeNodeOutput(node.id, results)
          emitNodeExecutionComplete(sink, node.id, results)

          executedNodes.add(node.id)
          kLogger.info { "  └─ 노드 실행 완료: ${node.id}, outputs: ${results.keys}" }
        } catch (e: Exception) {
          kLogger.error(e) { "[FlowExecution] 노드 실행 실패 - nodeId: ${node.id}" }
          emitErrorAndComplete(
            sink, ExecuteNodeResponse.failure(
              ExecuteNodeErrorCode.LLM_GENERATION_FAILED,
              "노드 ${node.id} 실행 중 오류가 발생했습니다: ${e.message}",
              mapOf("failedNodeId" to node.id, "exception" to e.javaClass.simpleName)
            )
          )
          return
        }
      }

      emitFlowComplete(sink, executedNodes)
      sink.complete()
      kLogger.info { "[FlowExecution] Flow 모드 실행 완료 - 실행된 노드: $executedNodes" }
    } catch (e: Exception) {
      kLogger.error(e) { "[FlowExecution] Flow 모드 실행 실패" }
      emitErrorAndComplete(
        sink, ExecuteNodeResponse.failure(
          ExecuteNodeErrorCode.INTERNAL_ERROR,
          "Flow 실행 중 오류가 발생했습니다: ${e.message}",
          mapOf("exception" to e.javaClass.simpleName)
        )
      )
    }
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

  private fun executeNodeInternal(
    executor: NodeExecutor<ExecutionContext>,
    node: Node,
    context: FlowExecutionContext
  ): Map<String, String> {
    executeNode(executor, node, context)
    return context.getAllNodeOutputs()[node.id] ?: emptyMap()
  }

  private fun executeNode(
    executor: NodeExecutor<ExecutionContext>,
    node: Node,
    context: ExecutionContext
  ) {
    if (executor is PreparedNodeExecutor<*>) {
      @Suppress("UNCHECKED_CAST")
      (executor as PreparedNodeExecutor<ExecutionContext>).prepare(node, context)
    }
    executor.execute(node, context, asynchronous = false)
  }

  private fun emitErrorAndComplete(sink: FluxSink<String>, response: ExecuteNodeResponse) {
    val json = objectMapper.writeValueAsString(response)
    sink.next(" $json")
    sink.complete()
  }

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

  private fun emitFlowComplete(
    sink: FluxSink<String>,
    executedNodes: List<String>
  ) {
    val payload = mapOf(
      "type" to "FLOW_COMPLETE",
      "executedNodes" to executedNodes,
      "nodeCount" to executedNodes.size,
      "timestamp" to System.currentTimeMillis()
    )
    val json = objectMapper.writeValueAsString(payload)
    sink.next(" $json")
  }

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
