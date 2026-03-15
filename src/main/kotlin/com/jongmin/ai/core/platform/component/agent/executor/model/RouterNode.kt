package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.platform.service.AiAssistantService
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 라우터 노드
 *
 * LLM을 사용하여 입력에 따라 적절한 경로를 선택하는 노드.
 * AI가 입력 내용을 분석하여 어떤 출력 핸들로 보낼지 결정한다.
 *
 * 입력:
 * - system: (선택) 라우팅 판단 기준을 지정하는 시스템 프롬프트
 * - input: 라우팅할 입력 텍스트
 *
 * 출력:
 * - 선택된 툴 핸들: 입력 텍스트
 * - reason: 라우팅 이유 (디버깅용)
 *
 * 설정:
 * - aiAssistant: 사용할 AI 어시스턴트 설정
 * - tools: 라우팅 가능한 경로 목록 (동적 핸들)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["router"])
class RouterNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val system = context.findAndGetInputForNode(node.id, "system") as? String
    val input = context.findAndGetInputForNode(node.id, "input") as String
    val aiAssistantData = node.data.config?.get("aiAssistant") as Map<*, *>
    val routed = determineRoute(node, system ?: "", input, aiAssistantData["id"].toString().toLong())
    val routingSources = node.data.dynamicHandles["tools"] ?: emptyList()
    val selectedTool = routingSources.find { it.name == routed.source }
    if (selectedTool == null) throw BadRequestException("Router ${node.id}: No routing source '$routed' found")
    val outputData = mapOf(
      selectedTool.id to input,
      "reason" to "${routed.source} - ${routed.reason}"
    )
    logging(node, context, "  → 탐색 경로 결정: ${selectedTool.name}")

    if (context.isPlaygroundRequest())
      sendEvent(MessageType.NODE_RESULT_CREATED, mutableMapOf("nodeId" to node.id, "data" to routed))

    context.storeOutput(node.id, outputData)
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) {
    val routes = context.getOutputForNode(node.id) as Map<*, *>
    routes.keys.forEach { sourceHandle ->
      val outgoingEdges = context.workflow.edges.filter { it.source == node.id && it.sourceHandle == sourceHandle }
      outgoingEdges.forEach { edge ->
        val targetNode = context.workflow.nodes.first { it.id == edge.target }
        factory.getExecutor<ExecutionContext>(objectMapper, session, targetNode.type, topic, eventSender, emitter, canvasId)
          .execute(targetNode, context)
      }
    }
  }

  private fun determineRoute(node: Node, system: String, input: String, aiAssistantId: Long): Routed {
    val aiAssistantService = factory.applicationContext.getBean(AiAssistantService::class.java)
    val runnableAiAssistant = aiAssistantService.findById(aiAssistantId)

    // 동적 LLM 옵션 추출 및 로깅
    val dynamicOptions = getLlmDynamicOptions(node)
    logResolvedOptions(node, dynamicOptions, runnableAiAssistant)

    // 함수 호출을 사용하면 return 을 자유롭게 할 수 없다.
    // val toolSpecification = ToolSpecification.builder()
    // .name("findRoutingSource")
    // .description("Find the routing source to determine the flow of input.")
    // .parameters(
    //   JsonObjectSchema.builder()
    //     .addStringProperty("city", "The city for which the weather forecast should be returned")
    //     .required("input")
    //     .build()
    // )
    // .build()

    // val responseFormat: ResponseFormat = ResponseFormat.builder()
    //   .type(ResponseFormatType.JSON)
    //   .jsonSchema(
    //     JsonSchema.builder()
    //       .name("Routed")
    //       .rootElement(
    //         JsonObjectSchema.builder()
    //           .addStringProperty("source")
    //           .addStringProperty("reason")
    //           .required("source", "reason")
    //           .build()
    //       )
    //       .build()
    //   )
    //   .build()
    val systemPrompt = SystemMessage.from(system.ifBlank { runnableAiAssistant.getInstructionsWithCurrentTime() })
    val chatRequest: ChatRequest =
      ChatRequest.builder()
        // .responseFormat(responseFormat) 미스트랄은 지원되지 않는다.
        .messages(listOf(systemPrompt, UserMessage.from(input))).build()

    // Rate Limiter를 적용하여 LLM 호출
    val response = executeWithRateLimiting(runnableAiAssistant) {
      // 동적 옵션으로 ChatModel 생성 후 호출
      val chatModel = buildChatModelWithDynamicOptions(dynamicOptions, runnableAiAssistant)
      // mistral 과 gpt4o-mini는 찐빠가 난다. 프롬프트로 잡을 수 있지만 상당한 토큰을 소진해야하기 때문에 로직으로 보정한다.
      val chatResponse = chatModel.chat(chatRequest)
      chatResponse.aiMessage()?.text()
        ?: throw IllegalStateException("[RouterNode] LLM 응답이 null입니다. response=$chatResponse")
    }
    val cleanedResponse = response.cleanJsonString()
    return factory.applicationContext.getBean(ObjectMapper::class.java).readValue(cleanedResponse, Routed::class.java)
  }

  data class Routed(val source: String, val reason: String? = "")

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * RouterNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("router")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return RouterNode 인스턴스
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
      return RouterNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
