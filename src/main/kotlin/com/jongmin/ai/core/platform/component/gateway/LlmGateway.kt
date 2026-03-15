package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.ai.core.AiExecutionType
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.RunnableAiModel
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * LLM/VLM 게이트웨이
 *
 * 모든 LLM(텍스트→텍스트) 및 VLM(이미지+텍스트→텍스트) 호출의 단일 진입점.
 * UnifiedAiExecutionTracker를 통해 자동으로 AiRun/AiRunStep을 생성하고 메트릭을 추적.
 *
 * 주요 기능:
 * - 단순 텍스트 생성 (generate)
 * - 스트리밍 텍스트 생성 (generateStreaming)
 * - 이미지 분석 - VLM (analyzeImage)
 * - Rate Limiter 통합
 * - 자동 추적 (토큰, 비용, 요청/응답 저장)
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class LlmGateway(
  private val tracker: UnifiedAiExecutionTracker,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 텍스트 생성 (동기)
   *
   * @param assistant AI 어시스턴트 (프로바이더, 모델, 설정 포함)
   * @param prompt 사용자 프롬프트
   * @param systemPrompt 시스템 프롬프트 (선택)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID (aiMessageId, gameSessionId 등)
   * @return 생성된 텍스트
   */
  fun generate(
    assistant: RunnableAiAssistant,
    prompt: String,
    systemPrompt: String? = null,
    callerComponent: String,
    contextId: Long? = null
  ): String {
    val requestPayload = buildRequestPayload(prompt, systemPrompt)

    // 추적 시작
    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    return try {
      // Rate Limiter 적용 채팅
      val chatRequest = buildChatRequest(prompt, systemPrompt)
      val response = assistant.chatWithRateLimit(chatRequest, rateLimiter)

      val generatedText = response.aiMessage()?.text()
        ?: throw IllegalStateException("[LlmGateway] LLM 응답이 null입니다")

      // 추적 완료 (rawUsage 기반)
      val rawUsage = extractRawUsage(response)
      tracker.completeLlmExecution(
        context = context,
        rawUsage = rawUsage,
        responsePayload = buildResponsePayload(response)
      )

      kLogger.debug {
        "[LlmGateway] 생성 완료 - caller: $callerComponent, model: ${assistant.model}, " +
            "length: ${generatedText.length}"
      }

      generatedText
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 텍스트 생성 (동기) - RunnableAiModel 인터페이스 사용
   */
  fun generate(
    model: RunnableAiModel,
    prompt: String,
    systemPrompt: String? = null,
    callerComponent: String,
    contextId: Long? = null,
    assistantId: Long? = null
  ): String {
    val requestPayload = buildRequestPayload(prompt, systemPrompt)

    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = model.provider,
      modelName = model.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistantId,
      requestPayload = requestPayload
    )

    return try {
      val chatRequest = buildChatRequest(prompt, systemPrompt)
      val response = model.chatWithRateLimit(chatRequest, rateLimiter)

      val generatedText = response.aiMessage()?.text()
        ?: throw IllegalStateException("[LlmGateway] LLM 응답이 null입니다")

      val rawUsage = extractRawUsage(response)
      tracker.completeLlmExecution(
        context = context,
        rawUsage = rawUsage,
        responsePayload = buildResponsePayload(response)
      )

      generatedText
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 스트리밍 텍스트 생성 (콜백 기반)
   *
   * 기존 스트리밍 패턴과 호환되는 메서드.
   *
   * @param assistant AI 어시스턴트
   * @param prompt 사용자 프롬프트
   * @param systemPrompt 시스템 프롬프트 (선택)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param onPartial 부분 응답 콜백
   * @return 완료 시 전체 텍스트를 반환하는 CompletableFuture
   */
  fun generateStreamingWithCallback(
    assistant: RunnableAiAssistant,
    prompt: String,
    systemPrompt: String? = null,
    callerComponent: String,
    contextId: Long? = null,
    onPartial: (String) -> Unit
  ): CompletableFuture<String> {
    val requestPayload = buildRequestPayload(prompt, systemPrompt)
    val future = CompletableFuture<String>()

    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    // Rate Limiter 슬롯 확보
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)

    val chatRequest = buildChatRequest(prompt, systemPrompt)

    assistant.streamingChatModel().chat(chatRequest, object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        onPartial(partialResponse)
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)

        val text = completeResponse.aiMessage()?.text() ?: ""

        val rawUsage = extractRawUsage(completeResponse)
        tracker.completeLlmExecution(
          context = context,
          rawUsage = rawUsage,
          responsePayload = buildResponsePayload(completeResponse)
        )

        future.complete(text)
      }

      override fun onError(error: Throwable) {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        tracker.failExecution(context, error)
        future.completeExceptionally(error)
      }
    })

    return future
  }

  /**
   * 이미지 분석 - VLM (Vision Language Model)
   *
   * @param assistant AI 어시스턴트 (VLM 지원 모델 필요)
   * @param imageUrl 분석할 이미지 URL
   * @param prompt 분석 프롬프트
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 분석 결과 텍스트
   */
  fun analyzeImage(
    assistant: RunnableAiAssistant,
    imageUrl: String,
    prompt: String,
    callerComponent: String,
    contextId: Long? = null
  ): String {
    val requestPayload = mapOf(
      "imageUrl" to imageUrl,
      "prompt" to prompt
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.VLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    return try {
      // VLM 요청 빌드 (이미지 + 텍스트)
      val userMessage = UserMessage.from(
        ImageContent.from(imageUrl),
        TextContent.from(prompt)
      )
      val chatRequest = ChatRequest.builder()
        .messages(listOf(userMessage))
        .build()

      // Rate Limiter 적용 채팅
      val response = assistant.chatWithRateLimit(chatRequest, rateLimiter)

      val analysisResult = response.aiMessage()?.text()
        ?: throw IllegalStateException("[LlmGateway] VLM 응답이 null입니다")

      val rawUsage = extractRawUsage(response)
      tracker.completeLlmExecution(
        context = context,
        rawUsage = rawUsage,
        responsePayload = buildResponsePayload(response)
      )

      kLogger.debug {
        "[LlmGateway] 이미지 분석 완료 - caller: $callerComponent, model: ${assistant.model}"
      }

      analysisResult
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 다중 이미지 분석 - VLM
   *
   * @param assistant AI 어시스턴트
   * @param imageUrls 분석할 이미지 URL 목록
   * @param prompt 분석 프롬프트
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 분석 결과 텍스트
   */
  fun analyzeImages(
    assistant: RunnableAiAssistant,
    imageUrls: List<String>,
    prompt: String,
    callerComponent: String,
    contextId: Long? = null
  ): String {
    val requestPayload = mapOf(
      "imageUrls" to imageUrls,
      "prompt" to prompt
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.VLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    return try {
      // 다중 이미지 + 텍스트 메시지 빌드
      val contents = imageUrls.map { ImageContent.from(it) } + TextContent.from(prompt)
      val userMessage = UserMessage.from(contents)

      val chatRequest = ChatRequest.builder()
        .messages(listOf(userMessage))
        .build()

      val response = assistant.chatWithRateLimit(chatRequest, rateLimiter)

      val analysisResult = response.aiMessage()?.text()
        ?: throw IllegalStateException("[LlmGateway] VLM 응답이 null입니다")

      val rawUsage = extractRawUsage(response)
      tracker.completeLlmExecution(
        context = context,
        rawUsage = rawUsage,
        responsePayload = buildResponsePayload(response)
      )

      analysisResult
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  // ==================== ChatRequest 기반 메서드 (유연한 메시지 구성) ====================

  /**
   * ChatRequest 기반 텍스트 생성 (동기)
   *
   * 복잡한 메시지 구성이 필요한 경우 사용.
   * 예: 대화 히스토리, 멀티턴 대화, 시스템/사용자/AI 메시지 조합
   *
   * @param assistant AI 어시스턴트
   * @param chatRequest 완성된 ChatRequest
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param requestDescription 요청 설명 (로깅용)
   * @return 생성된 텍스트
   */
  fun generateWithChatRequest(
    assistant: RunnableAiAssistant,
    chatRequest: ChatRequest,
    callerComponent: String,
    contextId: Long? = null,
    requestDescription: String? = null
  ): String {
    val requestPayload = buildChatRequestPayload(chatRequest, requestDescription)

    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    return try {
      val response = assistant.chatWithRateLimit(chatRequest, rateLimiter)

      val generatedText = response.aiMessage()?.text()
        ?: throw IllegalStateException("[LlmGateway] LLM 응답이 null입니다")

      val rawUsage = extractRawUsage(response)
      tracker.completeLlmExecution(
        context = context,
        rawUsage = rawUsage,
        responsePayload = buildResponsePayload(response)
      )

      kLogger.debug {
        "[LlmGateway] ChatRequest 생성 완료 - caller: $callerComponent, model: ${assistant.model}"
      }

      generatedText
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * ChatRequest 기반 스트리밍 (콜백 방식)
   *
   * 대화 히스토리를 포함한 스트리밍 생성이 필요한 경우 사용.
   * QuestionAnswerer, AdaptiveAnswerer 등에서 활용.
   *
   * @param assistant AI 어시스턴트
   * @param chatRequest 완성된 ChatRequest
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param requestDescription 요청 설명 (로깅용)
   * @param onPartial 부분 응답 콜백
   * @return 완료 시 전체 텍스트를 반환하는 CompletableFuture
   */
  fun generateStreamingWithChatRequest(
    assistant: RunnableAiAssistant,
    chatRequest: ChatRequest,
    callerComponent: String,
    contextId: Long? = null,
    requestDescription: String? = null,
    onPartial: (String) -> Unit
  ): CompletableFuture<String> {
    val requestPayload = buildChatRequestPayload(chatRequest, requestDescription)
    val future = CompletableFuture<String>()

    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    // Rate Limiter 슬롯 확보
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
    kLogger.debug { "[LlmGateway] Rate Limit 슬롯 확보 - provider: ${acquireResult.providerName}" }

    assistant.streamingChatModel().chat(chatRequest, object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        onPartial(partialResponse)
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)

        val text = completeResponse.aiMessage()?.text() ?: ""

        val rawUsage = extractRawUsage(completeResponse)
        tracker.completeLlmExecution(
          context = context,
          rawUsage = rawUsage,
          responsePayload = buildResponsePayload(completeResponse)
        )

        kLogger.debug {
          "[LlmGateway] ChatRequest 스트리밍 완료 - caller: $callerComponent, length: ${text.length}"
        }

        future.complete(text)
      }

      override fun onError(error: Throwable) {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        tracker.failExecution(context, error)
        kLogger.error(error) { "[LlmGateway] ChatRequest 스트리밍 에러 - caller: $callerComponent" }
        future.completeExceptionally(error)
      }
    })

    return future
  }

  /**
   * ChatRequest 기반 스트리밍 (커스텀 핸들러 방식)
   *
   * 리즈닝 처리, 취소 관리 등 복잡한 스트리밍 로직이 필요한 경우 사용.
   * 핸들러는 외부 팩토리에서 생성하며, Rate Limiter와 추적은 게이트웨이가 관리.
   *
   * handlerFactory는 두 개의 콜백을 받아 StreamingChatResponseHandler를 생성:
   * - onStreamingComplete: 스트리밍 완료 시 호출 (결과 텍스트, ChatResponse)
   * - onStreamingError: 에러 발생 시 호출
   *
   * @param assistant AI 어시스턴트
   * @param chatRequest 완성된 ChatRequest
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param requestDescription 요청 설명 (로깅용)
   * @param handlerFactory 핸들러 팩토리 - 완료/에러 콜백을 받아 핸들러 생성
   * @return 완료 시 결과를 반환하는 CompletableFuture
   */
  fun executeStreamingWithHandler(
    assistant: RunnableAiAssistant,
    chatRequest: ChatRequest,
    callerComponent: String,
    contextId: Long? = null,
    requestDescription: String? = null,
    handlerFactory: (
      onStreamingComplete: (text: String, response: ChatResponse) -> Unit,
      onStreamingError: (Throwable) -> Unit
    ) -> StreamingChatResponseHandler
  ): CompletableFuture<String> {
    val requestPayload = buildChatRequestPayload(chatRequest, requestDescription)
    val future = CompletableFuture<String>()

    // 추적 시작
    val context = tracker.startExecution(
      executionType = AiExecutionType.LLM,
      provider = assistant.provider,
      modelName = assistant.model,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistant.id,
      requestPayload = requestPayload
    )

    // Rate Limiter 슬롯 확보
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
    kLogger.debug { "[LlmGateway] Rate Limit 슬롯 확보 (커스텀 핸들러) - provider: ${acquireResult.providerName}" }

    // 핸들러 팩토리를 통해 핸들러 생성
    val handler = handlerFactory(
      // onStreamingComplete 콜백
      { text: String, response: ChatResponse ->
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)

        val rawUsage = extractRawUsage(response)
        tracker.completeLlmExecution(
          context = context,
          rawUsage = rawUsage,
          responsePayload = buildResponsePayload(response)
        )

        kLogger.debug {
          "[LlmGateway] 커스텀 핸들러 스트리밍 완료 - caller: $callerComponent, length: ${text.length}"
        }

        future.complete(text)
      },
      // onStreamingError 콜백
      { error: Throwable ->
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        tracker.failExecution(context, error)
        kLogger.error(error) { "[LlmGateway] 커스텀 핸들러 스트리밍 에러 - caller: $callerComponent" }
        future.completeExceptionally(error)
      }
    )

    // 스트리밍 채팅 실행
    assistant.streamingChatModel().chat(chatRequest, handler)

    return future
  }

  // ==================== Private Helper Methods ====================

  /**
   * ChatRequest 빌드
   */
  private fun buildChatRequest(prompt: String, systemPrompt: String?): ChatRequest {
    val messages = buildList {
      systemPrompt?.let { add(SystemMessage.from(it)) }
      add(UserMessage.from(prompt))
    }
    return ChatRequest.builder()
      .messages(messages)
      .build()
  }

  /**
   * 요청 페이로드 빌드
   */
  private fun buildRequestPayload(prompt: String, systemPrompt: String?): Map<String, Any> {
    return buildMap {
      put("prompt", prompt)
      systemPrompt?.let { put("systemPrompt", it) }
    }
  }

  /**
   * ChatRequest 기반 요청 페이로드 빌드
   */
  private fun buildChatRequestPayload(chatRequest: ChatRequest, description: String?): Map<String, Any> {
    return buildMap {
      // 메시지 요약 정보
      val messages = chatRequest.messages()
      put("messageCount", messages.size)
      put("messageTypes", messages.map { it.type().name })

      // 마지막 사용자 메시지 (프롬프트 요약)
      messages.lastOrNull { it.type() == dev.langchain4j.data.message.ChatMessageType.USER }?.let { lastUserMsg ->
        val text = when (lastUserMsg) {
          is UserMessage -> lastUserMsg.contents()
            .filterIsInstance<TextContent>()
            .joinToString(" ") { it.text() }

          else -> lastUserMsg.toString()
        }
        // 너무 길면 자르기
        put("lastUserMessage", if (text.length > 500) text.take(500) + "..." else text)
      }

      description?.let { put("description", it) }
    }
  }

  /**
   * ChatResponse에서 raw usage 추출
   */
  private fun extractRawUsage(response: ChatResponse): Map<String, Any> {
    val usage = response.metadata()?.tokenUsage() ?: return emptyMap()
    return buildMap {
      put("inputTokenCount", usage.inputTokenCount() ?: 0)
      put("outputTokenCount", usage.outputTokenCount() ?: 0)
      usage.totalTokenCount()?.let { put("totalTokenCount", it) }
    }
  }

  /**
   * 응답 페이로드 빌드
   */
  private fun buildResponsePayload(response: ChatResponse): Map<String, Any> {
    return buildMap {
      response.aiMessage()?.text()?.let { put("text", it) }
      response.metadata()?.let { metadata ->
        metadata.modelName()?.let { put("modelName", it) }
        metadata.finishReason()?.let { put("finishReason", it.name) }
        metadata.tokenUsage()?.let { usage ->
          put(
            "usage", mapOf(
              "inputTokenCount" to (usage.inputTokenCount() ?: 0),
              "outputTokenCount" to (usage.outputTokenCount() ?: 0),
              "totalTokenCount" to (usage.totalTokenCount() ?: 0)
            )
          )
        }
      }
    }
  }
}
