package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.platform.service.AiAssistantService
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.chat.request.ResponseFormatType
import dev.langchain4j.model.chat.request.json.JsonEnumSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import reactor.core.publisher.FluxSink
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.*

/**
 * 상태 관리 노드
 *
 * LLM을 사용하여 입력으로부터 상태값을 추출/추론하는 노드.
 * JSON Schema를 사용하여 구조화된 출력을 강제하며, 여러 상태 필드를 한 번에 처리한다.
 *
 * 입력:
 * - input: 상태를 추론할 입력 텍스트
 *
 * 출력:
 * - input을 그대로 pass-through (상태는 context.set("states", ...)로 저장)
 *
 * 설정:
 * - aiAssistantId: 사용할 AI 어시스턴트 ID
 * - tools: 추론할 상태 필드 목록 (동적 핸들)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["state-manager"])
class StateManagerNode<T : ExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<T>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: T): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: T) {
    val input = context.findAndGetInputForNode(node.id, "input") as String
    val states = node.data.dynamicHandles["tools"] ?: emptyList()
    val aiAssistantId = node.data.config?.get("aiAssistantId") as Number
    val currentValue = manageState(node, input, states, aiAssistantId.toLong())
    context.set("states", currentValue)
    context.storeOutput(node.id, input)

    // TODO output으로 currentValue 를 전달
    // TODO changes로 변경점을 전달
    // val selectedTool = routingSources.find { it.name == routed.source }
    // if (selectedTool == null) throw BadRequestException("Router ${node.id}: No routing source '$routed' found")
    // val outputData = mapOf(
    //   selectedTool.id to input,
    //   "reason" to "${routed.source} - ${routed.reason}"
    // )
    logging(node, context, "  → 현재 상태: $currentValue")

    // if (context.isPlaygroundRequest())
    //   sendEvent(MessageType.AI_AGENT_WORKFLOW_NODE_RESULT_CREATED, mutableMapOf("nodeId" to node.id, "data" to routed))
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  /**
   * 매뉴얼은 다음 사이트를 참조한다. https://docs.langchain4j.dev/tutorials/structured-outputs
   */
  private fun manageState(node: Node, input: String, states: List<ToolHandle>, aiAssistantId: Long): MutableMap<String, Any> {
    val aiAssistantService = factory.applicationContext.getBean(AiAssistantService::class.java)
    val runnableAiAssistant = aiAssistantService.findById(aiAssistantId)

    // 동적 LLM 옵션 추출 및 로깅
    val dynamicOptions = getLlmDynamicOptions(node)
    logResolvedOptions(node, dynamicOptions, runnableAiAssistant)

    // Rate Limiter를 적용하여 LLM 호출 (여러 상태를 하나의 슬롯으로 처리)
    return executeWithRateLimiting(runnableAiAssistant) {
      // 동적 옵션으로 ChatModel 생성 (모든 상태에서 재사용)
      val chatModel = buildChatModelWithDynamicOptions(dynamicOptions, runnableAiAssistant)

      // TODO 스레드 ID 또는 이 상태를 저장할 타입과 키를 정의하여 영속화 해야한다.
      // 지금 개발하고 있는 타로의 경우 한 스레드에서 1번의 결과만 도출할 수 있으며, 이후 도출된 결과에 대해서 3번의 질문만 가능하다.

      // TODO 이 상태값을 플래이그라운드에서 접근하면 더욱 좋을 것 같다.

      // TODO 결과를 반환할 때 현재 데이터, 변경점 두개를 반환해야한다.

      val inferences = mutableMapOf<String, Any>()
      val systemPrompt = SystemMessage.from(runnableAiAssistant.getInstructionsWithCurrentTime())

      states.forEach { tool ->
        // 개별 필드 스키마 생성
        val fieldSchema = if (tool.optional?.isNotEmpty() == true) {
          JsonEnumSchema.builder()
            .description(tool.description)
            .enumValues(*tool.optional.split(",").map { it.trim() }.toTypedArray())
            .build()
        } else {
          JsonStringSchema.builder()
            .description(tool.description)
            .build()
        }

        // 개별 필드 요청 구성
        val rootElement = JsonObjectSchema.builder()
          .required(tool.name)
          .addProperties(mapOf(tool.name to fieldSchema))
          .build()

        val responseFormat = ResponseFormat.builder()
          .type(ResponseFormatType.JSON)
          .jsonSchema(JsonSchema.builder().name(tool.name).rootElement(rootElement).build())
          .build()

        val chatRequest = ChatRequest.builder()
          .responseFormat(responseFormat)
          .messages(listOf(systemPrompt, UserMessage.from(input)))
          .build()

        val chatResponse = chatModel.chat(chatRequest)
        val responseText = chatResponse.aiMessage()?.text()
          ?: throw IllegalStateException("[StateManagerNode] LLM 응답이 null입니다. state=${tool.name}, response=$chatResponse")
        inferences.putAll(
          objectMapper.readValue(
            responseText,
            object : TypeReference<Map<String, Any>>() {}
          ))
      }

      inferences
    }
  }

  fun uuidToLong(uuid: String): Long {
    val instance = UUID.fromString(uuid)
    return instance.mostSignificantBits xor instance.leastSignificantBits
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * StateManagerNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("state-manager")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return StateManagerNode 인스턴스
     */
    override fun createExecutor(
      objectMapper: ObjectMapper,
      factory: NodeExecutorFactory,
      session: JSession,
      topic: String,
      eventSender: EventSender,
      emitter: FluxSink<String>?,
      canvasId: String?
    ): NodeExecutor<*> {
      return StateManagerNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
