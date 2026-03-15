package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.function.Function

/**
 * 사용자의 질문에 LLM이 직접 답변을 수행하는 클래스
 *
 * 이 클래스는 LLM 모델을 사용하여 사용자의 질문에 대한 답변을 생성합니다.
 * LlmGateway를 통해 호출되며, 모든 AI 호출이 자동으로 추적됩니다.
 *
 * @param llmGateway LLM 게이트웨이 (추적 자동화)
 * @param assistant AI 어시스턴트
 * @param eventSender 이벤트 전송 컴포넌트
 * @param chatTopic 이벤트 토픽
 * @param accountId 계정 ID
 * @param aiThreadId AI 스레드 ID
 * @param runId 실행 ID (선택)
 * @param onStatusChanged 상태 변경 콜백 (선택)
 */
class QuestionAnswerer(
  private val llmGateway: LlmGateway,
  private val assistant: RunnableAiAssistant,
  private val eventSender: EventSender,
  private val chatTopic: String,
  private val accountId: Long,
  private val aiThreadId: Long,
  private val runId: Long? = null,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : Function<List<IAiChatMessage>, String> {
  private val kLogger = KotlinLogging.logger {}

  override fun apply(messages: List<IAiChatMessage>): String {
    onStatusChanged?.invoke(StatusType.RUNNING, messages.last().content(), null, assistant)

    // ChatRequest 빌드
    val chatRequest = buildChatRequest(messages)

    // LlmGateway를 통한 스트리밍 생성 (자동 추적)
    val output = llmGateway.generateStreamingWithChatRequest(
      assistant = assistant,
      chatRequest = chatRequest,
      callerComponent = "QuestionAnswerer",
      contextId = aiThreadId,
      requestDescription = "사용자 질문 답변"
    ) { partialResponse ->
      // 부분 응답 이벤트 전송
      val payload = runId
        ?.let { mapOf("key" to runId, "delta" to partialResponse, "aiThreadId" to aiThreadId) }
        ?: mapOf("delta" to partialResponse, "aiThreadId" to aiThreadId)
      eventSender.sendEventToAccount(
        chatTopic,
        MessageType.AI_CHAT_DELTA,
        payload,
        accountId,
        useFcm = false
      )
    }.get()  // CompletableFuture 대기

    onStatusChanged?.invoke(StatusType.ENDED, null, output, assistant)
    return output
  }

  /**
   * 메시지 리스트로부터 ChatRequest 빌드
   */
  private fun buildChatRequest(messages: List<IAiChatMessage>): ChatRequest {
    val instructions = assistant.getInstructionsWithCurrentTime()
    val systemMessage = SystemMessage.from(instructions)

    val convertedMessage = messages.map {
      when (it.type()) {
        ChatMessageType.USER -> UserMessage.from(it.content())
        ChatMessageType.SYSTEM -> SystemMessage.from(it.content())
        ChatMessageType.AI -> AiMessage.from(it.content())
        else -> throw IllegalArgumentException("Invalid ChatMessageType: ${it.type()}")
      }
    }

    return ChatRequest.builder()
      .messages(listOf(systemMessage) + convertedMessage)
      .build()
  }
}
