package com.jongmin.ai.core

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageType
import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.mistralai.MistralAiChatModel
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.http.HttpClient
import java.time.Duration
import java.time.format.DateTimeFormatter

// ========== 동적 옵션 적용을 위한 내부 헬퍼 DTO ==========

/**
 * 실제 LLM 호출에 사용되는 최종 옵션 값
 * RunnableAiModel 프리셋과 LlmDynamicOptions를 병합한 결과
 */
data class ResolvedLlmOptions(
  val provider: String,
  val baseUrl: String?,
  val model: String,
  val apiKey: String,
  val temperature: Double?,
  val topP: Double?,
  val topK: Int?,
  val frequencyPenalty: Double?,
  val presencePenalty: Double?,
  val responseFormat: String?,
  val maxTokens: Int?,
  val supportsReasoning: Boolean,
  val reasoningEffort: ReasoningEffort?,
  val noThinkTrigger: String?,
)


data class AiCoreProperties(
  val provider: String,
  val baseUrl: String? = null,
  val model: String,
  val supportsReasoning: Boolean,
  val reasoningEffort: ReasoningEffort?,
  val noThinkTrigger: String?,
  var apiKey: String,
)

data class AiModelItem(
  val id: Long,
  val name: String,
  val supportsReasoning: Boolean,
  val reasoningEffort: ReasoningEffort?,
)

interface RunnableAiModel {
  companion object {
    private val kLogger = KotlinLogging.logger {}
  }

  val id: Long
  val name: String
  val description: String?
  val provider: String
  val baseUrl: String?
  val model: String
  val supportsReasoning: Boolean
  val reasoningEffort: ReasoningEffort?
  val noThinkTrigger: String?
  var apiKey: String
  val temperature: Double?
  val topP: Double?
  val topK: Int?
  val frequencyPenalty: Double?
  val presencePenalty: Double?
  val responseFormat: String?
  val maxTokens: Int?
  val status: StatusType

  // ========== 기존 메서드 (프리셋 사용) ==========

  fun chat(chatRequest: ChatRequest): ChatResponse {
    try {
      return chatModel().chat(chatRequest)
    } catch (e: Exception) {
      throw IllegalStateException("Failed to chat with ai model: $model", e)
    }
  }

  /**
   * Rate Limit 적용 채팅 (분산 환경 안전)
   *
   * Redis 기반 분산 Rate Limiter를 사용하여 Provider별 동시 실행 수를 제한합니다.
   * 슬롯이 없으면 Exponential Backoff로 대기 후 재시도합니다.
   *
   * 사용 예시:
   * ```kotlin
   * @Autowired lateinit var rateLimiter: LlmRateLimiter
   *
   * val response = runnableModel.chatWithRateLimit(chatRequest, rateLimiter)
   * ```
   *
   * @param chatRequest 채팅 요청
   * @param rateLimiter LlmRateLimiter 인스턴스
   * @return 채팅 응답
   * @throws LlmRateLimitExceededException 슬롯 확보 실패 시
   */
  fun chatWithRateLimit(
    chatRequest: ChatRequest,
    rateLimiter: com.jongmin.ai.core.platform.component.LlmRateLimiter
  ): ChatResponse {
    // 슬롯 확보 (Exponential Backoff 적용)
    val acquireResult = rateLimiter.acquireByProviderName(provider)
    try {
      return chatModel().chat(chatRequest)
    } finally {
      // 슬롯 반환 (항상 실행)
      rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
    }
  }

  /**
   * Rate Limit 적용 채팅 + 동적 옵션 (분산 환경 안전)
   *
   * @param chatRequest 채팅 요청
   * @param rateLimiter LlmRateLimiter 인스턴스
   * @param dynamicOptions 런타임에 오버라이드할 LLM 옵션
   * @return 채팅 응답
   */
  fun chatWithRateLimit(
    chatRequest: ChatRequest,
    rateLimiter: com.jongmin.ai.core.platform.component.LlmRateLimiter,
    dynamicOptions: LlmDynamicOptions?
  ): ChatResponse {
    val acquireResult = rateLimiter.acquireByProviderName(provider)
    try {
      return chatModel(dynamicOptions).chat(chatRequest)
    } finally {
      rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
    }
  }

  fun chatWithStreaming(chatRequest: ChatRequest, param: StreamingChatResponseHandler) {
    kLogger.info { "스트리밍 채팅 시작 - 모델: $model, provider: $provider" }
    streamingChatModel().chat(chatRequest, param)
  }

  // ========== 동적 옵션 오버로드 메서드 ==========

  /**
   * 동적 옵션을 적용하여 채팅 실행
   *
   * 엔티티 ID 기반 변경(aiProviderId, aiModelId, aiApiKeyId)은 상위 레벨에서 처리해야 함.
   * 이 메서드는 생성 파라미터(temperature, topP 등)만 동적 오버라이드 가능.
   *
   * @param chatRequest 채팅 요청
   * @param dynamicOptions 런타임에 오버라이드할 LLM 옵션 (null이면 프리셋 사용)
   * @return 채팅 응답
   */
  fun chat(chatRequest: ChatRequest, dynamicOptions: LlmDynamicOptions?): ChatResponse {
    if (dynamicOptions == null || !dynamicOptions.hasAnyOption()) {
      return chat(chatRequest)
    }
    try {
      return chatModel(dynamicOptions).chat(chatRequest)
    } catch (e: Exception) {
      // 엔티티 ID 기반 모델 변경은 상위 레벨에서 처리되므로, 여기서는 프리셋 모델명 사용
      throw IllegalStateException("Failed to chat with ai model: $model", e)
    }
  }

  /**
   * 동적 옵션을 적용하여 스트리밍 채팅 실행
   *
   * @param chatRequest 채팅 요청
   * @param param 스트리밍 응답 핸들러
   * @param dynamicOptions 런타임에 오버라이드할 LLM 옵션 (null이면 프리셋 사용)
   */
  fun chatWithStreaming(
    chatRequest: ChatRequest,
    param: StreamingChatResponseHandler,
    dynamicOptions: LlmDynamicOptions?
  ) {
    if (dynamicOptions == null || !dynamicOptions.hasAnyOption()) {
      chatWithStreaming(chatRequest, param)
      return
    }
    val resolved = resolveOptions(dynamicOptions)
    kLogger.info { "스트리밍 채팅 시작 (동적 옵션) - 모델: ${resolved.model}, provider: ${resolved.provider}" }
    streamingChatModel(dynamicOptions).chat(chatRequest, param)
  }

  /**
   * 동적 옵션을 적용한 ChatModel 생성
   */
  fun chatModel(dynamicOptions: LlmDynamicOptions?): ChatModel {
    if (dynamicOptions == null || !dynamicOptions.hasAnyOption()) {
      return chatModel()
    }
    val opts = resolveOptions(dynamicOptions)
    return buildChatModel(opts)
  }

  /**
   * 동적 옵션을 적용한 StreamingChatModel 생성
   */
  fun streamingChatModel(dynamicOptions: LlmDynamicOptions?): StreamingChatModel {
    if (dynamicOptions == null || !dynamicOptions.hasAnyOption()) {
      return streamingChatModel()
    }
    val opts = resolveOptions(dynamicOptions)
    return buildStreamingChatModel(opts)
  }

  /**
   * 동적 옵션과 프리셋 병합
   *
   * 엔티티 ID 기반 필드(aiProviderId, aiModelId, aiApiKeyId)는 이 레벨에서 처리하지 않음.
   * 해당 필드들은 상위 레벨(노드/서비스)에서 엔티티 조회 후 별도로 처리해야 함.
   * 여기서는 생성 파라미터(temperature, topP 등)만 동적 오버라이드.
   *
   * @param dynamicOptions 동적 LLM 옵션 (null인 필드는 프리셋 값 사용)
   * @return 프리셋과 동적 옵션이 병합된 최종 옵션
   */
  fun resolveOptions(dynamicOptions: LlmDynamicOptions): ResolvedLlmOptions {
    // 엔티티 ID 기반 필드(provider, model, apiKey)는 프리셋 값 사용
    // 엔티티 ID로 다른 provider/model/apiKey를 사용하려면 상위 레벨에서 처리 필요
    return ResolvedLlmOptions(
      provider = provider,
      baseUrl = baseUrl,
      model = model,
      apiKey = apiKey,
      // 생성 파라미터는 동적 오버라이드 가능
      temperature = dynamicOptions.temperature ?: temperature,
      topP = dynamicOptions.topP ?: topP,
      topK = dynamicOptions.topK ?: topK,
      frequencyPenalty = dynamicOptions.frequencyPenalty ?: frequencyPenalty,
      presencePenalty = dynamicOptions.presencePenalty ?: presencePenalty,
      responseFormat = dynamicOptions.responseFormat ?: responseFormat,
      maxTokens = dynamicOptions.maxTokens ?: maxTokens,
      // 리즈닝 설정도 동적 오버라이드 가능
      supportsReasoning = dynamicOptions.supportsReasoning ?: supportsReasoning,
      reasoningEffort = dynamicOptions.reasoningEffort ?: reasoningEffort,
      noThinkTrigger = dynamicOptions.noThinkTrigger ?: noThinkTrigger,
    )
  }

  /**
   * ResolvedLlmOptions로부터 AiCoreProperties 생성 (ReasoningProcessingUtil 호환)
   */
  fun toAiCoreProperties(dynamicOptions: LlmDynamicOptions? = null): AiCoreProperties {
    val opts = if (dynamicOptions != null) resolveOptions(dynamicOptions) else null
    return AiCoreProperties(
      provider = opts?.provider ?: provider,
      baseUrl = opts?.baseUrl ?: baseUrl,
      model = opts?.model ?: model,
      supportsReasoning = opts?.supportsReasoning ?: supportsReasoning,
      reasoningEffort = opts?.reasoningEffort ?: reasoningEffort,
      noThinkTrigger = opts?.noThinkTrigger ?: noThinkTrigger,
      apiKey = opts?.apiKey ?: apiKey,
    )
  }

  // ========== 기존 메서드 (프리셋 기반) ==========

  fun chatModel(): ChatModel {
    return when (provider.lowercase()) {
      "openai" -> OpenAiChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(apiKey)
        .modelName(model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .frequencyPenalty(frequencyPenalty.let { if (it == 0.toDouble()) null else it })
        .presencePenalty(presencePenalty.let { if (it == 0.toDouble()) null else it })
        .responseFormat(responseFormat)
        .strictJsonSchema(responseFormat == "json_schema")
//        .maxTokens(maxTokens)
        .build()

      "anthropic" -> AnthropicChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(apiKey)
        .modelName(model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .topK(topK.let { if (it == 0) null else it })
        .maxTokens(maxTokens)
        .build()

      "zai", "xai", "deepseek", "cerebras", "openrouter", "kluster", "vllm", "lm studio" -> {

        OpenAiChatModel.builder()
          .baseUrl(baseUrl)
          .timeout(Duration.ofMinutes(30))
          .apiKey(apiKey)
          .modelName(model)
          .maxRetries(0)  // Rate Limiter가 재시도 담당
          .temperature(temperature)
          .httpClientBuilder(
            if (provider.lowercase() == "lm studio") JdkHttpClient.builder()
              .httpClientBuilder(
                HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
              ) else null
          )
          .topP(topP.let { if (it == 0.toDouble()) null else it })
          .frequencyPenalty(frequencyPenalty.let { if (it == 0.toDouble()) null else it })
          .presencePenalty(presencePenalty.let { if (it == 0.toDouble()) null else it })
          .responseFormat(responseFormat)
          .strictJsonSchema(responseFormat == "json_object")
          .build()
      }


      "ollama" -> OllamaChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .timeout(Duration.ofMinutes(30))
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .topK(topK ?: 64) // 파라미터 미설정 시 64 (젬마 추천 값)
        .numCtx(1024 * 16)
        .build()

      "mistral ai" -> MistralAiChatModel.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .modelName(model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .timeout(Duration.ofMinutes(30))
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        // .responseFormat(responseFormat) // text 이면 오류남
        // .maxTokens(maxTokens)
        .build()

      else -> throw IllegalArgumentException("Unsupported ai provider: $provider")
    }
  }

  fun streamingChatModel(): StreamingChatModel {
    return when (provider.lowercase()) {
      "openai" -> OpenAiStreamingChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(apiKey)
        .modelName(model)
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .frequencyPenalty(frequencyPenalty.let { if (it == 0.toDouble()) null else it })
        .presencePenalty(presencePenalty.let { if (it == 0.toDouble()) null else it })
        .responseFormat(responseFormat)
        .strictJsonSchema(responseFormat == "json_schema")
//        .maxTokens(maxTokens)
        .build()

      "anthropic" -> AnthropicStreamingChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(apiKey)
        .modelName(model)
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .topK(topK.let { if (it == 0) null else it })
//        .maxTokens(maxTokens)
        .build()

      "zai", "xai", "deepseek", "cerebras", "openrouter", "kluster", "vllm", "lm studio" -> {
        return OpenAiStreamingChatModel.builder()
          .timeout(Duration.ofMinutes(30))
          .baseUrl(baseUrl)
          .modelName(model)
          .apiKey(apiKey)
          .temperature(temperature)
          .httpClientBuilder(
            if (provider.lowercase() == "lm studio") JdkHttpClient.builder()
              .httpClientBuilder(
                HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
              ) else null
          ).topP(topP.let { if (it == 0.toDouble()) null else it })
          .frequencyPenalty(frequencyPenalty.let { if (it == 0.toDouble()) null else it })
          .presencePenalty(presencePenalty.let { if (it == 0.toDouble()) null else it })
          .responseFormat(responseFormat)
          .strictJsonSchema(responseFormat == "json_schema")
//        .maxTokens(maxTokens)
          .build()
      }

      "ollama" -> OllamaStreamingChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(model)
        .timeout(Duration.ofMinutes(30))
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        .topK(topK ?: 64) // 파라미터 미설정 시 64 (젬마 추천 값)
        .numCtx(1024 * 16)
        .build()

      "mistral ai" -> MistralAiStreamingChatModel.builder()
        .baseUrl(baseUrl)
        .timeout(Duration.ofMinutes(30))
        .apiKey(apiKey)
        .modelName(model)
        .temperature(temperature)
        .topP(topP.let { if (it == 0.toDouble()) null else it })
        // .responseFormat(responseFormat) // text 이면 오류남
        // .maxTokens(maxTokens)
        .build()

      else -> throw IllegalArgumentException("Unsupported ai provider: $provider")
    }
  }

  // ========== 동적 옵션 기반 모델 빌더 ==========

  /**
   * ResolvedLlmOptions를 사용하여 ChatModel 생성
   * 동적 옵션과 프리셋이 병합된 최종 값으로 모델 인스턴스 생성
   */
  fun buildChatModel(opts: ResolvedLlmOptions): ChatModel {
    return when (opts.provider.lowercase()) {
      "openai" -> OpenAiChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .frequencyPenalty(opts.frequencyPenalty.let { if (it == 0.toDouble()) null else it })
        .presencePenalty(opts.presencePenalty.let { if (it == 0.toDouble()) null else it })
        .responseFormat(opts.responseFormat)
        .strictJsonSchema(opts.responseFormat == "json_schema")
        .build()

      "anthropic" -> AnthropicChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .topK(opts.topK.let { if (it == 0) null else it })
        .maxTokens(opts.maxTokens)
        .build()

      "zai", "xai", "deepseek", "cerebras", "openrouter", "kluster", "vllm", "lm studio" -> {
        OpenAiChatModel.builder()
          .baseUrl(opts.baseUrl)
          .timeout(Duration.ofMinutes(30))
          .apiKey(opts.apiKey)
          .modelName(opts.model)
          .maxRetries(0)  // Rate Limiter가 재시도 담당
          .temperature(opts.temperature)
          .httpClientBuilder(
            if (opts.provider.lowercase() == "lm studio") JdkHttpClient.builder()
              .httpClientBuilder(
                HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
              ) else null
          )
          .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
          .frequencyPenalty(opts.frequencyPenalty.let { if (it == 0.toDouble()) null else it })
          .presencePenalty(opts.presencePenalty.let { if (it == 0.toDouble()) null else it })
          .responseFormat(opts.responseFormat)
          .strictJsonSchema(opts.responseFormat == "json_object")
          .build()
      }

      "ollama" -> OllamaChatModel.builder()
        .baseUrl(opts.baseUrl)
        .modelName(opts.model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .timeout(Duration.ofMinutes(30))
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .topK(opts.topK ?: 64)
        .numCtx(1024 * 16)
        .build()

      "mistral ai" -> MistralAiChatModel.builder()
        .baseUrl(opts.baseUrl)
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .maxRetries(0)  // Rate Limiter가 재시도 담당
        .timeout(Duration.ofMinutes(30))
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .build()

      else -> throw IllegalArgumentException("Unsupported ai provider: ${opts.provider}")
    }
  }

  /**
   * ResolvedLlmOptions를 사용하여 StreamingChatModel 생성
   * 동적 옵션과 프리셋이 병합된 최종 값으로 스트리밍 모델 인스턴스 생성
   */
  fun buildStreamingChatModel(opts: ResolvedLlmOptions): StreamingChatModel {
    return when (opts.provider.lowercase()) {
      "openai" -> OpenAiStreamingChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .frequencyPenalty(opts.frequencyPenalty.let { if (it == 0.toDouble()) null else it })
        .presencePenalty(opts.presencePenalty.let { if (it == 0.toDouble()) null else it })
        .responseFormat(opts.responseFormat)
        .strictJsonSchema(opts.responseFormat == "json_schema")
        .build()

      "anthropic" -> AnthropicStreamingChatModel.builder()
        .timeout(Duration.ofMinutes(30))
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .topK(opts.topK.let { if (it == 0) null else it })
        .build()

      "zai", "xai", "deepseek", "cerebras", "openrouter", "kluster", "vllm", "lm studio" -> {
        OpenAiStreamingChatModel.builder()
          .timeout(Duration.ofMinutes(30))
          .baseUrl(opts.baseUrl)
          .modelName(opts.model)
          .apiKey(opts.apiKey)
          .temperature(opts.temperature)
          .httpClientBuilder(
            if (opts.provider.lowercase() == "lm studio") JdkHttpClient.builder()
              .httpClientBuilder(
                HttpClient.newBuilder()
                  .version(HttpClient.Version.HTTP_1_1)
              ) else null
          )
          .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
          .frequencyPenalty(opts.frequencyPenalty.let { if (it == 0.toDouble()) null else it })
          .presencePenalty(opts.presencePenalty.let { if (it == 0.toDouble()) null else it })
          .responseFormat(opts.responseFormat)
          .strictJsonSchema(opts.responseFormat == "json_schema")
          .build()
      }

      "ollama" -> OllamaStreamingChatModel.builder()
        .baseUrl(opts.baseUrl)
        .modelName(opts.model)
        .timeout(Duration.ofMinutes(30))
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .topK(opts.topK ?: 64)
        .numCtx(1024 * 16)
        .build()

      "mistral ai" -> MistralAiStreamingChatModel.builder()
        .baseUrl(opts.baseUrl)
        .timeout(Duration.ofMinutes(30))
        .apiKey(opts.apiKey)
        .modelName(opts.model)
        .temperature(opts.temperature)
        .topP(opts.topP.let { if (it == 0.toDouble()) null else it })
        .build()

      else -> throw IllegalArgumentException("Unsupported ai provider: ${opts.provider}")
    }
  }
}

/**
 * AI 어시스턴트의 실행 가능한 인스턴스를 나타내는 데이터 클래스
 *
 * @property id 어시스턴트의 고유 식별자
 * @property name 어시스턴트의 이름
 * @property description 어시스턴트에 대한 설명 (선택사항)
 * @property provider AI 서비스 제공자 (예: OpenAI, Anthropic 등)
 * @property baseUrl 커스텀 AI 서비스의 기본 URL (선택사항)
 * @property model 사용할 AI 모델의 이름
 * @property apiKey AI 서비스 인증을 위한 API 키
 * @property instructions 어시스턴트에게 주어질 기본 지시사항 (선택사항)
 * @property temperature 모델의 출력 다양성을 조절하는 파라미터 (0-1 사이)
 * @property topP 모델의 토큰 샘플링을 제어하는 파라미터
 * @property responseFormat 응답 형식 지정 (예: text, json 등)
 * @property maxTokens 생성할 최대 토큰 수
 * @property status 어시스턴트의 현재 상태
 * @property type 어시스턴트의 유형 (예: QUESTION_ROUTER, CONTENT_WRITER 등)
 */
data class RunnableAiAssistant(
  override val id: Long,
  override val name: String,
  override val description: String? = null,
  override val provider: String,
  override val baseUrl: String? = null,
  override val model: String,
  override val supportsReasoning: Boolean,
  override val reasoningEffort: ReasoningEffort? = null,
  override val noThinkTrigger: String? = null,
  override var apiKey: String,
  val instructions: String? = null,
  override val temperature: Double? = null,
  override val topP: Double? = null,
  override val topK: Int? = null,
  override val frequencyPenalty: Double? = null,
  override val presencePenalty: Double? = null,
  override val responseFormat: String? = null,
  override val maxTokens: Int? = null,
  override val status: StatusType,
  val type: AiAssistantType,
) : RunnableAiModel {
  // val metadata: Map<String, String> = emptyMap()

  fun getInstructionsWithCurrentTime(): String {
    return instructions?.replace("{{currentTime}}", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(JTimeUtils.now())) ?: ""
  }

  fun getAiRunStatus(): AiRunStatus = when (type) {
    AiAssistantType.QUESTION_ROUTER, AiAssistantType.CONTENT_CREATIVE_ROUTER -> AiRunStatus.ROUTING
    AiAssistantType.QUESTION_ANSWERER, AiAssistantType.CONTENT_WRITER_FOR_QA, AiAssistantType.ADAPTIVE_ANSWERER -> AiRunStatus.GENERATING
    AiAssistantType.WEB_SEARCH_EXPERT, AiAssistantType.WEB_SEARCH_TOOL -> AiRunStatus.WEB_SEARCHING
    AiAssistantType.QUESTION_REWRITER -> AiRunStatus.QUESTION_REWRITING
    AiAssistantType.HALLUCINATION_GRADER -> AiRunStatus.INFERENCE_EVALUATION
    AiAssistantType.ANSWER_GRADER, AiAssistantType.RETRIEVAL_GRADER -> AiRunStatus.ANSWER_GRADING
    AiAssistantType.MARKDOWN_CONTENT_EXTRACTOR, AiAssistantType.THREAD_TITLE_GENERATOR, AiAssistantType.SINGLE_EMOJI_GENERATOR, AiAssistantType.FORTUNE_TELLER -> AiRunStatus.LLM_INFERENCE
    else -> AiRunStatus.UNKNOWN
  }
}
