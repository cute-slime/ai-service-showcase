package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.truncateInput
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import org.apache.commons.lang3.ObjectUtils
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture

/**
 * 액션 생성 노드
 *
 * 롤플레잉 시나리오에서 특정 배우(Actor)의 행동/대사를 AI로 생성하는 노드.
 * 배우의 컨텍스트와 의도(Intent)를 기반으로 LLM을 호출하여 행동을 추론한다.
 *
 * 입력:
 * - actor (Long, 필수): 행동을 수행할 배우 ID
 * - additionalPrompt (선택): 추가 시스템 프롬프트
 *
 * 출력:
 * - AI가 생성한 행동/대사 텍스트 (String)
 *
 * 설정:
 * - aiAssistantId (Long, 필수): 사용할 AI 어시스턴트 ID
 * - streaming (Boolean, 기본값 false): 스트리밍 응답 사용 여부
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["action"])
class ActionNode<T : RolePlayingExecutionContext>(
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
    val prompt = context.findAndGetInputForNode(node.id, "actor")
    val notReady = ObjectUtils.isEmpty(prompt)
    return notReady
  }

  override fun executeInternal(node: Node, context: T) {
    val systemMessage = context.findAndGetInputForNode(node.id, "additionalPrompt") as? String
    systemMessage?.let { context.addAdditionalPrompt(it) }
    val actorId =
      context.findAndGetInputForNode(node.id, "actor") as? Long ?: throw BadRequestException("독백은 아직 지원되지 않아요. 캐릭터와의 대화면 지원됩니다.")
    val aiAssistantId = node.data.config?.get("aiAssistantId") as Number? ?: throw BadRequestException("aiAssistantId is required")
    val streaming = node.data.config?.get("streaming") as Boolean? ?: false
    val result = inference(node, context, aiAssistantId.toLong(), streaming, actorId)
    logging(node, context, "  → 생성됨: ${truncateInput(result.replace("\n", " "), 100)}")
    context.storeOutput(node.id, result)
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  private fun inference(
    node: Node,
    context: T,
    aiAssistantId: Long,
    streaming: Boolean = false,
    actorId: Long
  ): String {
    val aiAssistantService = factory.applicationContext.getBean(AiAssistantService::class.java)
    val runnableAiAssistant = aiAssistantService.findById(aiAssistantId)

    // 동적 LLM 옵션 추출 및 로깅
    val dynamicOptions = getLlmDynamicOptions(node)
    logResolvedOptions(node, dynamicOptions, runnableAiAssistant)

    val systemPrompt = SystemMessage.from(context.act(actorId).ifBlank { runnableAiAssistant.getInstructionsWithCurrentTime() })
    val intent = context.getIntent()
    // val convertedMessage = inputs.map {
    //   when (it.type()) {
    //     ChatMessageType.USER -> UserMessage.from(it.content())
    //     ChatMessageType.SYSTEM -> SystemMessage.from(it.content())
    //     ChatMessageType.AI -> AiMessage.from(it.content())
    //     else -> throw IllegalArgumentException("Invalid ChatMessageType: ${it.type()}")
    //   }
    // }
    // 리즈닝 처리가 적용된 ChatRequest 생성 (동적 옵션 포함)
    val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
      runnableAiAssistant,
      listOf(systemPrompt, UserMessage.from(intent.content)),
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
          onReasoning = null, // ActionNode는 reasoning을 별도로 처리하지 않음
          onComplete = { completeResponse ->
            val text = completeResponse.aiMessage()?.text()
              ?: throw IllegalStateException("[ActionNode] 스트리밍 LLM 응답이 null입니다")
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
          ?: throw IllegalStateException("[ActionNode] LLM 응답이 null입니다. response=$response")
      }
    }
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * ActionNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("action")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return ActionNode 인스턴스
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
      return ActionNode<RolePlayingExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
