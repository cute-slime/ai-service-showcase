package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.RunnableAiAssistant
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

class AdaptiveAnswerer(
  private val assistant: RunnableAiAssistant,
  private val rateLimiter: LlmRateLimiter,
  private val eventSender: EventSender,
  private val chatTopic: String,
  private val accountId: Long,
  private val aiThreadId: Long,
  private val runId: Long? = null,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : BiFunction<String, List<String>, String> {
  private val kLogger = KotlinLogging.logger {}

  interface Service {
    @UserMessage("{{instructions}}")
    fun invoke(@V("instructions") instructions: String): String
  }

  override fun apply(input: String, context: List<String>): String {
    val instructions = assistant.getInstructionsWithCurrentTime()
      .replace("{{input}}", input)
      .replace("{{documents}}", context.joinToString("\n"))
    onStatusChanged?.invoke(StatusType.RUNNING, instructions, null, assistant)

    val chatRequest: ChatRequest = ChatRequest
      .builder()
      .messages(SystemMessage.from(instructions))
      .build()

    // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
    kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

    val future = CompletableFuture<String>()

    assistant.streamingChatModel()
      .chat(chatRequest, object : StreamingChatResponseHandler {
        override fun onPartialResponse(partialResponse: String) {
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
        }

        override fun onCompleteResponse(completeResponse: ChatResponse) {
          // Rate Limiter 슬롯 반환
          rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
          kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

          val text = completeResponse.aiMessage()?.text()
            ?: throw IllegalStateException("[AdaptiveAnswerer] 스트리밍 LLM 응답이 null입니다")
          future.complete(text)
        }

        override fun onError(error: Throwable) {
          // Rate Limiter 슬롯 반환 (에러 시)
          rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
          kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (에러) - provider: ${acquireResult.providerName}" }

          error.printStackTrace()
          future.completeExceptionally(error)
        }
      })
    val result = future.get()
    onStatusChanged?.invoke(StatusType.ENDED, null, result, assistant)
    return result
  }
}
//   private val defaultInstruction = """당신은 질문에 답변하는 작업을 돕는 어시스턴트입니다.
//
//다음의 컨텍스트를 사용하여 질문에 답변하세요.
//답변을 모른다면 모른다고 말하세요.
//최대 세 문장으로 간결하게 답변하세요.
//
//질문: {{input}}
//
//컨텍스트: {{documents}}
//
//답변:"""
