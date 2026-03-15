package com.jongmin.ai.core.system.service

import com.jongmin.ai.core.LlmDynamicOptions
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import com.jongmin.ai.core.system.dto.*
import com.jongmin.jspring.core.exception.BadRequestException
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 시스템 AI 채팅 서비스
 *
 * 다른 마이크로서비스에서 LLM 채팅을 요청할 때 사용하는 내부 서비스.
 * Rate Limiting, 토큰 추적 등 ai-service의 인프라를 활용한다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class SystemAiChatService(
  private val aiAssistantService: AiAssistantService,
  private val rateLimiter: LlmRateLimiter,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 동기 채팅 타임아웃 (초) */
    private const val SYNC_CHAT_TIMEOUT_SECONDS = 120L

    /** 스트리밍 채팅 타임아웃 (초) */
    private const val STREAMING_TIMEOUT_SECONDS = 180L
  }

  /**
   * 동기 채팅 실행
   *
   * Rate Limiter를 적용하여 LLM 호출을 수행한다.
   *
   * @param request 채팅 요청
   * @return 채팅 응답
   */
  fun chat(request: SystemChatRequest): SystemChatResponse {
    kLogger.info {
      "시스템 채팅 요청 - assistantId: ${request.assistantId}, " +
          "type: ${request.assistantType}, messages: ${request.messages.size}, " +
          "templateVars: ${request.templateVariables?.keys}"
    }

    // 1. 어시스턴트 로드
    val assistant = loadAssistant(request)

    // 2. 메시지 변환 (템플릿 변수 적용 포함)
    val messages = buildMessagesWithTemplateVariables(assistant, request)

    // 3. 동적 옵션 생성
    val dynamicOptions = buildDynamicOptions(request)

    // 4. ChatRequest 생성
    val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
      assistant = assistant,
      messages = messages
    )

    // 5. Rate Limiter 적용하여 LLM 호출
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
    kLogger.debug { "Rate Limit 슬롯 확보 - provider: ${acquireResult.providerName}" }

    try {
      val response: ChatResponse = if (dynamicOptions != null) {
        assistant.chat(chatRequest, dynamicOptions)
      } else {
        assistant.chat(chatRequest)
      }

      val content = response.aiMessage()?.text() ?: ""
      val usage = response.tokenUsage()

      kLogger.info {
        "시스템 채팅 완료 - assistant: ${assistant.name}, " +
            "tokens: ${usage?.totalTokenCount() ?: 0}"
      }

      return SystemChatResponse(
        content = content,
        assistantId = assistant.id,
        assistantName = assistant.name,
        model = assistant.model,
        usage = usage?.let {
          SystemChatUsage(
            inputTokens = it.inputTokenCount()?.toLong(),
            outputTokens = it.outputTokenCount()?.toLong(),
            totalTokens = it.totalTokenCount()?.toLong(),
          )
        }
      )
    } finally {
      rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
      kLogger.debug { "Rate Limit 슬롯 반환 - provider: ${acquireResult.providerName}" }
    }
  }

  /**
   * 스트리밍 채팅 실행
   *
   * SSE로 토큰을 스트리밍하며, Rate Limiter를 적용한다.
   *
   * @param request 채팅 요청
   * @return 스트리밍 청크 Flux
   */
  fun chatStream(request: SystemChatRequest): Flux<SystemChatStreamChunk> {
    kLogger.info {
      "시스템 스트리밍 채팅 요청 - assistantId: ${request.assistantId}, " +
          "type: ${request.assistantType}, messages: ${request.messages.size}, " +
          "templateVars: ${request.templateVariables?.keys}"
    }

    // 1. 어시스턴트 로드
    val assistant = loadAssistant(request)

    // 2. 메시지 변환 (템플릿 변수 적용 포함)
    val messages = buildMessagesWithTemplateVariables(assistant, request)

    // 3. 동적 옵션 생성
    val dynamicOptions = buildDynamicOptions(request)

    // 4. ChatRequest 생성
    val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
      assistant = assistant,
      messages = messages
    )

    // 5. Sink 생성
    val sink = Sinks.many().unicast().onBackpressureBuffer<SystemChatStreamChunk>()

    // 6. 비동기 스트리밍 실행
    Thread {
      val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
      kLogger.debug { "스트리밍 Rate Limit 슬롯 확보 - provider: ${acquireResult.providerName}" }

      val latch = CountDownLatch(1)

      try {
        val handler = object : StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            sink.tryEmitNext(
              SystemChatStreamChunk(
                content = partialResponse,
                done = false
              )
            )
          }

          override fun onCompleteResponse(response: ChatResponse) {
            val usage = response.tokenUsage()
            sink.tryEmitNext(
              SystemChatStreamChunk(
                content = "",
                done = true,
                usage = usage?.let {
                  SystemChatUsage(
                    inputTokens = it.inputTokenCount()?.toLong(),
                    outputTokens = it.outputTokenCount()?.toLong(),
                    totalTokens = it.totalTokenCount()?.toLong(),
                  )
                }
              )
            )
            sink.tryEmitComplete()
            latch.countDown()
          }

          override fun onError(e: Throwable) {
            kLogger.error(e) { "스트리밍 채팅 에러 - assistant: ${assistant.name}" }
            sink.tryEmitNext(
              SystemChatStreamChunk(
                content = "",
                done = true,
                error = e.message ?: "Unknown error"
              )
            )
            sink.tryEmitComplete()
            latch.countDown()
          }
        }

        if (dynamicOptions != null) {
          assistant.chatWithStreaming(chatRequest, handler, dynamicOptions)
        } else {
          assistant.chatWithStreaming(chatRequest, handler)
        }

        val completed = latch.await(STREAMING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
          kLogger.warn { "스트리밍 채팅 타임아웃 - assistant: ${assistant.name}" }
          sink.tryEmitNext(
            SystemChatStreamChunk(
              content = "",
              done = true,
              error = "Streaming timeout"
            )
          )
          sink.tryEmitComplete()
        }
      } catch (e: Exception) {
        kLogger.error(e) { "스트리밍 채팅 실패 - assistant: ${assistant.name}" }
        sink.tryEmitNext(
          SystemChatStreamChunk(
            content = "",
            done = true,
            error = e.message ?: "Unknown error"
          )
        )
        sink.tryEmitComplete()
      } finally {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.debug { "스트리밍 Rate Limit 슬롯 반환 - provider: ${acquireResult.providerName}" }
      }
    }.start()

    return sink.asFlux()
  }

  /**
   * 어시스턴트 로드
   *
   * assistantId가 있으면 ID로 조회, 없으면 type(+category)로 조회
   */
  private fun loadAssistant(request: SystemChatRequest): RunnableAiAssistant {
    return if (request.assistantId != null) {
      aiAssistantService.findById(request.assistantId)
    } else if (request.assistantType != null) {
      if (request.assistantCategory != null) {
        aiAssistantService.findFirst(request.assistantType, request.assistantCategory)
      } else {
        aiAssistantService.findFirst(request.assistantType)
      }
    } else {
      throw BadRequestException("assistantId 또는 assistantType이 필요합니다")
    }
  }

  /**
   * 메시지 변환
   */
  private fun convertMessages(messages: List<SystemChatMessage>): List<ChatMessage> {
    return messages.map { msg ->
      when (msg.role.lowercase()) {
        "system" -> SystemMessage.from(msg.content)
        "user" -> UserMessage.from(msg.content)
        "assistant" -> AiMessage.from(msg.content)
        else -> throw BadRequestException("알 수 없는 메시지 역할: ${msg.role}")
      }
    }
  }

  /**
   * 템플릿 변수를 적용한 메시지 빌드
   *
   * templateVariables가 있으면 어시스턴트의 시스템 프롬프트에서 `{{key}}` 형태의
   * 플레이스홀더를 해당 값으로 대체한 후, 메시지 목록 맨 앞에 추가한다.
   *
   * @param assistant 어시스턴트
   * @param request 요청
   * @return 템플릿 변수가 적용된 메시지 목록
   */
  private fun buildMessagesWithTemplateVariables(
    assistant: RunnableAiAssistant,
    request: SystemChatRequest
  ): List<ChatMessage> {
    val baseMessages = convertMessages(request.messages)

    // 템플릿 변수가 없으면 기본 메시지 반환
    if (request.templateVariables.isNullOrEmpty()) {
      return baseMessages
    }

    // 어시스턴트의 시스템 프롬프트를 가져와서 템플릿 변수 대체
    var systemPrompt = assistant.getInstructionsWithCurrentTime()
    request.templateVariables.forEach { (key, value) ->
      systemPrompt = systemPrompt.replace("{{$key}}", value)
    }

    kLogger.debug {
      "템플릿 변수 적용 - keys: ${request.templateVariables.keys}, " +
          "prompt length: ${systemPrompt.length}"
    }

    // 대체된 시스템 프롬프트를 맨 앞에 추가
    return listOf(SystemMessage.from(systemPrompt)) + baseMessages
  }

  /**
   * 동적 옵션 생성
   *
   * 요청에 오버라이드 옵션이 있으면 LlmDynamicOptions 생성
   */
  private fun buildDynamicOptions(request: SystemChatRequest): LlmDynamicOptions? {
    // 오버라이드 옵션이 하나도 없으면 null 반환
    if (request.temperature == null &&
      request.topP == null &&
      request.topK == null &&
      request.frequencyPenalty == null &&
      request.presencePenalty == null &&
      request.maxTokens == null
    ) {
      return null
    }

    return LlmDynamicOptions(
      temperature = request.temperature,
      topP = request.topP,
      topK = request.topK,
      frequencyPenalty = request.frequencyPenalty,
      presencePenalty = request.presencePenalty,
      maxTokens = request.maxTokens,
    )
  }
}
