package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.dto.request.ChatCompletionRequest
import com.jongmin.ai.core.platform.dto.response.ChatCompletionChunkChoiceDto
import com.jongmin.ai.core.platform.dto.response.ChatCompletionChunkDeltaDto
import com.jongmin.ai.core.platform.dto.response.ChatCompletionChunkResponseDto
import com.jongmin.ai.core.platform.dto.response.ReasoningDetail
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.internal.chat.ResponseFormatType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.*


/**
 * OpenAI 호환 LLM 백엔드 서비스
 *
 * AI 채팅 완료 요청을 처리하고 스트리밍 응답을 생성합니다.
 */
@Service
class OpenLlmBackendService(
  private val objectMapper: ObjectMapper,
  private val cancellationManager: AIInferenceCancellationManager,
  private val aiModelRepository: AiModelRepository,
  private val rateLimiter: LlmRateLimiter,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // 인증 관련 상수
    private const val BEARER_PREFIX = "Bearer sk-co-v1-"
    private const val AUTHORIZED_TOKEN = "Bearer sk-co-v1-6d52f76e005bb0ab5ffb0990765e689192d1c567cfc23df041fbf957252d7df7"

    // 기본 설정 상수
    private const val DEFAULT_MODEL = "glm-4.6-mlx-gs32"
    private const val ASSISTANT_ID = -1L
    private const val ASSISTANT_NAME = "catch-on-llm-backend"
    private const val PROVIDER = "lm studio"
    private const val BASE_URL = "http://192.168.0.105:1234/v1/"

    // 응답 ID 생성 상수
    private const val GENERATION_ID_PREFIX = "gen-"

    // 스트리밍 관련 상수
    const val CANCELLATION_CHECK_INTERVAL = 100  // 10개 토큰마다 취소 확인
    const val DONE_MESSAGE = " [DONE]"

    // 응답 형식 관련 상수
    private const val RESPONSE_PROVIDER = "CatchOn"
    private const val RESPONSE_OBJECT_TYPE = "chat.completion.chunk"
    private const val ASSISTANT_ROLE = "assistant"
    private const val REASONING_TYPE = "reasoning.text"
    private const val REASONING_FORMAT_UNKNOWN = "unknown"
    private const val MILLIS_TO_SECONDS_DIVISOR = 1000L

    fun sendContentChunk(
      emitter: FluxSink<String>,
      objectMapper: ObjectMapper,
      id: String,
      assistant: RunnableAiAssistant,
      created: Long,
      content: String
    ) {
      if (content.isEmpty()) return

      val responseData = ChatCompletionChunkResponseDto(
        id = id,
        provider = RESPONSE_PROVIDER,
        model = assistant.model,
        `object` = RESPONSE_OBJECT_TYPE,
        created = created / MILLIS_TO_SECONDS_DIVISOR,
        choices = listOf(
          ChatCompletionChunkChoiceDto(
            index = 0,
            delta = ChatCompletionChunkDeltaDto(
              role = ASSISTANT_ROLE,
              content = content,
              reasoning = null,
              reasoningDetails = emptyList()
            ),
            finishReason = null
          )
        )
      )
      emitter.next(" ${objectMapper.writeValueAsString(responseData)}")
    }

    fun sendReasoningChunk(
      emitter: FluxSink<String>?,
      objectMapper: ObjectMapper,
      id: String,
      assistant: RunnableAiAssistant,
      created: Long,
      reasoningText: String,
      index: Int
    ) {
      if (reasoningText.isEmpty() || emitter == null) return

      val responseData = ChatCompletionChunkResponseDto(
        id = id,
        provider = RESPONSE_PROVIDER,
        model = assistant.model,
        `object` = RESPONSE_OBJECT_TYPE,
        created = created / MILLIS_TO_SECONDS_DIVISOR,
        choices = listOf(
          ChatCompletionChunkChoiceDto(
            index = 0,
            delta = ChatCompletionChunkDeltaDto(
              role = ASSISTANT_ROLE,
              content = "",
              reasoning = reasoningText,
              reasoningDetails = listOf(
                ReasoningDetail(
                  type = REASONING_TYPE,
                  text = reasoningText,
                  format = REASONING_FORMAT_UNKNOWN,
                  index = index
                )
              )
            ),
            finishReason = null
          )
        )
      )
      emitter.next(" ${objectMapper.writeValueAsString(responseData)}")
    }
  }

  /**
   * 채팅 완료 요청을 처리합니다.
   *
   * @param authorizationHeader 인증 토큰
   * @param dto 채팅 완료 요청 DTO
   * @return SSE 스트리밍 응답
   */
  fun processChatCompletions(
    authorizationHeader: String?,
    dto: ChatCompletionRequest
  ): Flux<String> {
    // 요청 메시지 통계 수집
    val messageCount = dto.messages?.size ?: 0
    val totalInputChars = dto.messages?.sumOf { it.content.length } ?: 0

    kLogger.info {
      "채팅 완료 요청 처리 시작 - 모델: ${dto.model}, 스트리밍: ${dto.stream}, " +
          "메시지: ${messageCount}개, 입력 문자: ${totalInputChars}자"
    }

    // 인증 검증
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX))
      throw BadRequestException("유효한 인증 토큰이 필요합니다.")

    if (authorizationHeader == AUTHORIZED_TOKEN) {
      // 요청 DTO에서 파라미터 추출
      val model = dto.model ?: DEFAULT_MODEL
      val temperature = dto.temperature
      val topP = dto.topP
      val maxTokens = dto.maxTokens
      val responseFormat = dto.responseFormat ?: ResponseFormatType.TEXT
      var supportsReasoning = false
      var reasoningEffort: ReasoningEffort? = dto.reasoningEffort?.let { ReasoningEffort.valueOf(it.uppercase()) }
      var noThinkTrigger: String? = null

      kLogger.debug { "요청 파라미터 - 모델: $model, 온도: $temperature, topP: $topP, 최대토큰: $maxTokens" }

      // AI 모델 조회
      val foundModel = aiModelRepository
        .findAll(aiModel.name.equalsIgnoreCase(model))
        .firstOrNull()

      if (foundModel != null) {
        kLogger.debug { "AI 모델 조회 성공 - ID: ${foundModel.id}, 추론 지원: ${foundModel.supportsReasoning}" }
        supportsReasoning = foundModel.supportsReasoning
        reasoningEffort = reasoningEffort ?: foundModel.reasoningEffort
        noThinkTrigger = foundModel.noThinkTrigger
      } else {
        kLogger.debug { "AI 모델을 DB에서 찾지 못함, 기본값 사용 - 모델명: $model" }
      }

      val assistant = RunnableAiAssistant(
        id = ASSISTANT_ID,
        name = ASSISTANT_NAME,
        provider = PROVIDER,
        baseUrl = BASE_URL,
        model = model, // 요청에서 검출
        supportsReasoning = supportsReasoning,
        reasoningEffort = reasoningEffort,
        noThinkTrigger = noThinkTrigger,
        apiKey = authorizationHeader, // 항상 이 값
        temperature = temperature, // 요청에서 검출 또는 null
        topP = topP, // 요청에서 검출 또는 null
        topK = null,
        frequencyPenalty = null,
        presencePenalty = null,
        responseFormat = responseFormat.toString(), // 요청에서 검출 또는 "text"
        maxTokens = maxTokens, // LM 스튜디오 허용범위로 설정
        status = StatusType.ACTIVE,
        type = AiAssistantType.CUSTOM,
      )

      val chatRequest = from(assistant, dto)

      return Flux.create { emitter ->
        Thread.startVirtualThread {
          try {
            if (dto.stream == true)
              chatRequestWithStreaming(assistant, chatRequest, emitter)
            else
              chatRequest(assistant, chatRequest, emitter)
          } catch (e: Exception) {
            emitter.error(e)
          }
        }
      }
    } else {
      throw BadRequestException("사용할 수 없는 인증 토큰입니다.")
    }
  }

  /**
   * 논스트리밍 채팅 요청을 처리합니다.
   */
  private fun chatRequest(assistant: RunnableAiAssistant, chatRequest: ChatRequest, emitter: FluxSink<String>) {
    val id = "$GENERATION_ID_PREFIX${UUID.randomUUID()}"
    kLogger.info { "논스트리밍 채팅 요청 시작 - ID: $id, 모델: ${assistant.model}" }

    // 추론 취소 관리를 위한 등록
    cancellationManager.registerInference(id)

    try {
      // 취소되지 않았으면 추론 진행
      if (!cancellationManager.isCancelled(id)) {
        // Rate Limiter 적용 - 논스트리밍 채팅
        val chatResponse = assistant.chatWithRateLimit(chatRequest, rateLimiter)
        val response = chatResponse.aiMessage()?.text()
          ?: throw IllegalStateException("[OpenLlmBackendService] LLM 응답이 null입니다. response=$chatResponse")
        kLogger.info { "논스트리밍 응답 생성 완료 - ID: $id, 생성된 응답: ${response.length}자" }
        emitter.next(response)
      } else {
        kLogger.warn { "추론 취소됨 - ID: $id" }
      }
      emitter.complete()
      kLogger.info { "논스트리밍 채팅 요청 완료 - ID: $id" }
    } catch (e: Exception) {
      kLogger.error(e) { "논스트리밍 채팅 요청 실패 - ID: $id" }
      throw e
    } finally {
      // 추론 완료 후 정리
      cancellationManager.unregisterInference(id)
    }
  }

  /**
   * 스트리밍 채팅 요청을 처리합니다.
   */
  private fun chatRequestWithStreaming(assistant: RunnableAiAssistant, chatRequest: ChatRequest, emitter: FluxSink<String>) {
    val id = "$GENERATION_ID_PREFIX${UUID.randomUUID()}"
    val created = System.currentTimeMillis()
    kLogger.info { "스트리밍 채팅 요청 시작 - ID: $id, 모델: ${assistant.model}, 추론지원: ${assistant.supportsReasoning}" }

    // 추론 취소 관리를 위한 등록
    cancellationManager.registerInference(id)

    // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
    val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
    kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

    // 취소 확인을 위한 변수들
    var tokenCount = 0 // 토큰 카운터
    var isCancelled = false // 취소 플래그

    // 응답 통계 수집을 위한 변수들
    var totalContentChars = 0 // 총 content 문자 수
    var totalReasoningChars = 0 // 총 reasoning 문자 수
    var reasoningBlockCount = 0 // reasoning 블록 개수

    // 리즈닝 처리를 위한 내부 핸들러
    val internalHandler = ReasoningProcessingUtil.createReasoningAwareStreamingHandler(
      assistant = assistant,
      onContent = { content ->
        // 취소 확인
        if (!isCancelled) {
          totalContentChars += content.length
          sendContentChunk(emitter, objectMapper, id, assistant, created, content)
        }
      },
      onReasoning = { reasoningText, index ->
        // 취소 확인
        if (!isCancelled) {
          totalReasoningChars += reasoningText.length
          sendReasoningChunk(emitter, objectMapper, id, assistant, created, reasoningText, index)
          reasoningBlockCount = index + 1
        }
      },
      onComplete = { completeResponse ->
        // Rate Limiter 슬롯 반환
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

        // 취소된 경우 조기 종료
        if (isCancelled) {
          kLogger.info {
            "스트리밍 채팅 요청 취소됨 - ID: $id, 취소 시점까지 생성: 토큰 ${tokenCount}개, content ${totalContentChars}자, reasoning ${totalReasoningChars}자"
          }
          emitter.next(DONE_MESSAGE)
          emitter.complete()
          cancellationManager.unregisterInference(id)
        } else {
          kLogger.info {
            "스트리밍 응답 생성 완료 - ID: $id, 총 토큰: $tokenCount, 생성된 content: ${totalContentChars}자, reasoning: ${totalReasoningChars}자, 추론 블록: ${reasoningBlockCount}개"
          }
          emitter.next(DONE_MESSAGE)
          emitter.complete()
          kLogger.info { "스트리밍 채팅 요청 완료 - ID: $id" }
          cancellationManager.unregisterInference(id)
        }
      },
      onError = { error ->
        // Rate Limiter 슬롯 반환 (에러 시에도 반드시 반환)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (에러) - provider: ${acquireResult.providerName}" }

        kLogger.error(error) { "스트리밍 채팅 요청 실패 - ID: $id" }
        cancellationManager.unregisterInference(id)
        emitter.error(error)
      }
    )

    // 취소 확인을 위한 래퍼 핸들러
    val streamingHandler = object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        // 취소 확인 (10개 토큰마다 Redis 체크하여 성능 최적화)
        tokenCount++
        if (tokenCount % CANCELLATION_CHECK_INTERVAL == 0) {
          if (cancellationManager.isCancelled(id)) {
            kLogger.warn { "스트리밍 중 추론 취소 감지 - ID: $id, 토큰 수: $tokenCount" }
            isCancelled = true
            return // 추가 처리 중단
          }
        }

        // 이미 취소되었으면 처리하지 않음
        if (!isCancelled) {
          internalHandler.onPartialResponse(partialResponse)
        }
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        internalHandler.onCompleteResponse(completeResponse)
      }

      override fun onError(error: Throwable) {
        internalHandler.onError(error)
      }
    }

    // 스트리밍 시작
    assistant.chatWithStreaming(chatRequest, streamingHandler)
  }

  /**
   * 실행 중인 추론 요청을 취소합니다.
   *
   * @param id 취소할 추론 요청의 ID
   * @return 취소 성공 여부를 포함한 응답
   */
  fun cancelChatCompletion(id: String): Map<String, Any> {
    kLogger.info { "추론 취소 요청 - ID: $id" }

    // 취소 요청 등록
    val success = cancellationManager.requestCancellation(id)

    if (success) {
      kLogger.info { "추론 취소 요청 등록 성공 - ID: $id" }
    } else {
      kLogger.warn { "추론 취소 요청 등록 실패 - ID: $id" }
    }

    return mapOf(
      "id" to id,
      "cancelled" to success,
      "message" to if (success) "추론 취소 요청이 등록되었습니다." else "추론 취소 요청 등록에 실패했습니다."
    )
  }

  /**
   * ChatCompletionRequest를 LangChain4j의 ChatRequest로 변환합니다.
   * 리즈닝 처리는 공통 유틸리티를 사용합니다.
   */
  fun from(assistant: RunnableAiAssistant, request: ChatCompletionRequest): ChatRequest {
    return ReasoningProcessingUtil.createChatRequestWithReasoning(assistant, request)
  }
}
