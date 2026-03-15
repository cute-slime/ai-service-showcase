package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.JTimeUtils
import com.jongmin.jspring.core.util.truncateInput
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

/**
 * 텍스트 생성 노드
 *
 * LLM을 사용하여 프롬프트로부터 텍스트를 생성하는 노드.
 * 단일 턴 또는 멀티 턴 대화를 지원하며, 스트리밍 모드도 지원한다.
 *
 * 입력:
 * - system: (선택) 시스템 프롬프트
 * - prompt: 사용자 프롬프트 (단일 턴) 또는 대화 히스토리 (멀티 턴)
 *
 * 출력:
 * - LLM이 생성한 텍스트
 *
 * 설정:
 * - aiAssistantId: 사용할 AI 어시스턴트 ID
 * - streaming: 스트리밍 모드 활성화 여부 (기본: false)
 * - multiTurn: 멀티 턴 대화 모드 여부 (기본: false)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["generate-text"])
class GenerateTextNode(
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
    val prompt = (context.findAndGetInputForNode(node.id, "prompt") as? String) ?: ""
    val notReady = prompt.isBlank()
    return notReady
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val systemMessage = (context.findAndGetInputForNode(node.id, "system") as? String) ?: ""
    val prompt = (context.findAndGetInputForNode(node.id, "prompt") as? String) ?: ""
    val aiAssistantId = node.data.config?.get("aiAssistantId") as Number
    val streaming = node.data.config?.get("streaming") as Boolean? ?: false
    val inputs =
      if (node.data.config?.get("multiTurn") as Boolean? == true && context.getMessages() != null) context.getMessages()!!
      else listOf(IAiChatMessage.from(ChatMessageType.USER, prompt))
    val result = inference(node, systemMessage, inputs, aiAssistantId.toLong(), streaming)
    logging(node, context, "  → 생성됨: ${truncateInput(result.replace("\n", " "), 100)}")
    context.storeOutput(node.id, result)
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)

  private fun inference(
    node: Node,
    systemMessage: String,
    inputs: List<IAiChatMessage>,
    aiAssistantId: Long,
    streaming: Boolean = false
  ): String {
    val aiAssistantService = factory.applicationContext.getBean(AiAssistantService::class.java)
    val runnableAiAssistant = aiAssistantService.findById(aiAssistantId)

    // 동적 LLM 옵션 추출 및 로깅
    val dynamicOptions = getLlmDynamicOptions(node)
    logResolvedOptions(node, dynamicOptions, runnableAiAssistant)

    val systemPrompt = if (systemMessage.isNotBlank()) {
      SystemMessage.from(
        systemMessage.replace(
          "{{currentTime}}",
          // TODO 사용자의 국가에 맞는 타임존을 사용해야한다.
          DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(JTimeUtils.now())
        )
      )
    } else {
      SystemMessage.from(runnableAiAssistant.getInstructionsWithCurrentTime())
    }
    val convertedMessage = inputs.map {
      when (it.type()) {
        ChatMessageType.USER -> UserMessage.from(it.content())
        ChatMessageType.SYSTEM -> SystemMessage.from(it.content())
        ChatMessageType.AI -> AiMessage.from(it.content())
        else -> throw IllegalArgumentException("Invalid ChatMessageType: ${it.type()}")
      }
    }
    // 리즈닝 처리가 적용된 ChatRequest 생성 (동적 옵션 포함)
    val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
      runnableAiAssistant,
      listOf(systemPrompt) + convertedMessage,
      dynamicOptions
    )

    // Rate Limiter를 적용하여 LLM 호출 (스트리밍 완료까지 슬롯 유지)
    return if (streaming) {
      executeWithRateLimiting(runnableAiAssistant) {
        val future = CompletableFuture<String>()

        // 리즈닝 처리가 적용된 스트리밍 핸들러 사용
        val streamingHandler = ReasoningProcessingUtil.createReasoningAwareStreamingHandler(
          assistant = runnableAiAssistant,
          onContent = { partialResponse ->
            sendEvent(
              MessageType.AI_CHAT_DELTA,
              mutableMapOf(
                "nodeId" to node.id,
                "nodeType" to node.type,
                "delta" to partialResponse
              )
            )
          },
          onReasoning = null, // GenerateTextNode는 reasoning을 별도로 처리하지 않음
          onComplete = { completeResponse ->
            val text = completeResponse.aiMessage()?.text()
              ?: throw IllegalStateException("[GenerateTextNode] 스트리밍 LLM 응답이 null입니다")
            future.complete(text)
          },
          onError = { error ->
            error.printStackTrace()
            future.completeExceptionally(error)
          }
        )

        // 동적 옵션으로 StreamingChatModel 생성 후 호출
        val streamingModel = buildStreamingChatModelWithDynamicOptions(dynamicOptions, runnableAiAssistant)
        streamingModel.chat(chatRequest, streamingHandler)
        future.get()
      }
    } else {
      executeWithRateLimiting(runnableAiAssistant) {
        // 동적 옵션으로 ChatModel 생성 후 호출
        val chatModel = buildChatModelWithDynamicOptions(dynamicOptions, runnableAiAssistant)
        val response = chatModel.chat(chatRequest)
        response.aiMessage()?.text()
          ?: throw IllegalStateException("[GenerateTextNode] LLM 응답이 null입니다. response=$response")
      }
    }
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * GenerateTextNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("generate-text")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return GenerateTextNode 인스턴스
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
      return GenerateTextNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
