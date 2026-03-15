package com.jongmin.ai.core

/**
 * LLM 호출 시 동적으로 오버라이드할 수 있는 옵션 DTO
 *
 * 워크플로우 UI에서 사용자가 설정한 값을 런타임에 적용할 때 사용한다.
 * null인 필드는 RunnableAiAssistant의 기본값(프리셋)을 사용한다.
 *
 * 모든 엔티티 참조는 DB에 저장된 ID를 사용한다:
 * - aiProviderId: AiProvider 엔티티 ID
 * - aiModelId: AiModel 엔티티 ID
 * - aiApiKeyId: AiApiKey 엔티티 ID
 *
 * @property aiProviderId AI 제공자 엔티티 ID (AiProvider.id)
 * @property aiModelId AI 모델 엔티티 ID (AiModel.id)
 * @property aiApiKeyId API 키 엔티티 ID (AiApiKey.id)
 * @property temperature 출력 다양성 조절 (0.0 ~ 2.0)
 * @property topP 토큰 샘플링 확률 (0.0 ~ 1.0)
 * @property topK 샘플링할 상위 토큰 수
 * @property frequencyPenalty 빈도 페널티 (-2.0 ~ 2.0)
 * @property presencePenalty 존재 페널티 (-2.0 ~ 2.0)
 * @property responseFormat 응답 형식 (text, json, json_schema)
 * @property maxTokens 최대 생성 토큰 수
 * @property supportsReasoning 리즈닝 지원 여부
 * @property reasoningEffort 리즈닝 강도
 * @property noThinkTrigger 리즈닝 비활성화 트리거
 *
 * @author Claude Code
 * @since 2025.12.26
 */
data class LlmDynamicOptions(
  // 엔티티 ID 참조 (DB 기반)
  val aiProviderId: Long? = null,
  val aiModelId: Long? = null,
  val aiApiKeyId: Long? = null,

  // 생성 파라미터
  val temperature: Double? = null,
  val topP: Double? = null,
  val topK: Int? = null,
  val frequencyPenalty: Double? = null,
  val presencePenalty: Double? = null,
  val responseFormat: String? = null,
  val maxTokens: Int? = null,

  // 리즈닝 설정
  val supportsReasoning: Boolean? = null,
  val reasoningEffort: ReasoningEffort? = null,
  val noThinkTrigger: String? = null,
) {
  companion object {
    /**
     * 노드 설정(config)에서 동적 옵션 파싱
     *
     * 워크플로우 노드의 config Map에서 LLM 옵션을 추출한다.
     * 키 네이밍은 프론트엔드 UI와 일관성을 유지한다.
     */
    fun fromNodeConfig(config: Map<String, Any?>?): LlmDynamicOptions? {
      if (config == null) return null

      // llmOptions 하위 객체가 있으면 그것을 사용
      @Suppress("UNCHECKED_CAST")
      val llmConfig = (config["llmOptions"] as? Map<String, Any?>) ?: config

      // 모든 값이 null이면 null 반환 (기본값 사용)
      val hasAnyValue = listOf(
        "aiProviderId", "aiModelId", "aiApiKeyId",
        "temperature", "topP", "topK",
        "frequencyPenalty", "presencePenalty",
        "responseFormat", "maxTokens",
        "supportsReasoning", "reasoningEffort", "noThinkTrigger"
      ).any { llmConfig[it] != null }

      if (!hasAnyValue) return null

      return LlmDynamicOptions(
        aiProviderId = (llmConfig["aiProviderId"] as? Number)?.toLong(),
        aiModelId = (llmConfig["aiModelId"] as? Number)?.toLong(),
        aiApiKeyId = (llmConfig["aiApiKeyId"] as? Number)?.toLong(),
        temperature = (llmConfig["temperature"] as? Number)?.toDouble(),
        topP = (llmConfig["topP"] as? Number)?.toDouble(),
        topK = (llmConfig["topK"] as? Number)?.toInt(),
        frequencyPenalty = (llmConfig["frequencyPenalty"] as? Number)?.toDouble(),
        presencePenalty = (llmConfig["presencePenalty"] as? Number)?.toDouble(),
        responseFormat = llmConfig["responseFormat"] as? String,
        maxTokens = (llmConfig["maxTokens"] as? Number)?.toInt(),
        supportsReasoning = llmConfig["supportsReasoning"] as? Boolean,
        reasoningEffort = (llmConfig["reasoningEffort"] as? String)?.let {
          runCatching { ReasoningEffort.valueOf(it.uppercase()) }.getOrNull()
        },
        noThinkTrigger = llmConfig["noThinkTrigger"] as? String,
      )
    }

    /**
     * 빈 옵션 (모든 값 null - 프리셋 사용)
     */
    val EMPTY = LlmDynamicOptions()
  }

  /**
   * 다른 옵션과 병합 (this가 우선)
   * null인 필드는 other의 값으로 대체
   */
  fun mergeWith(other: LlmDynamicOptions?): LlmDynamicOptions {
    if (other == null) return this
    return LlmDynamicOptions(
      aiProviderId = this.aiProviderId ?: other.aiProviderId,
      aiModelId = this.aiModelId ?: other.aiModelId,
      aiApiKeyId = this.aiApiKeyId ?: other.aiApiKeyId,
      temperature = this.temperature ?: other.temperature,
      topP = this.topP ?: other.topP,
      topK = this.topK ?: other.topK,
      frequencyPenalty = this.frequencyPenalty ?: other.frequencyPenalty,
      presencePenalty = this.presencePenalty ?: other.presencePenalty,
      responseFormat = this.responseFormat ?: other.responseFormat,
      maxTokens = this.maxTokens ?: other.maxTokens,
      supportsReasoning = this.supportsReasoning ?: other.supportsReasoning,
      reasoningEffort = this.reasoningEffort ?: other.reasoningEffort,
      noThinkTrigger = this.noThinkTrigger ?: other.noThinkTrigger,
    )
  }

  /**
   * 유효한 옵션이 하나라도 있는지 확인
   */
  fun hasAnyOption(): Boolean {
    return aiProviderId != null || aiModelId != null || aiApiKeyId != null ||
        temperature != null || topP != null || topK != null ||
        frequencyPenalty != null || presencePenalty != null ||
        responseFormat != null || maxTokens != null ||
        supportsReasoning != null || reasoningEffort != null || noThinkTrigger != null
  }

  /**
   * 엔티티 ID 기반 옵션이 있는지 확인
   * (프로바이더, 모델, API 키 변경 여부)
   */
  fun hasEntityOverride(): Boolean {
    return aiProviderId != null || aiModelId != null || aiApiKeyId != null
  }
}
