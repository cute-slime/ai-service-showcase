package com.jongmin.ai.multiagent.skill.model

/**
 * LLM 트리거 설정
 * LLM_BASED, HYBRID 전략 사용 시 LLM 판단 관련 설정
 */
data class LlmTriggerConfig(
  /**
   * 사용할 모델 ID
   * null이면 시스템 기본 모델 사용
   */
  val modelId: String? = null,

  /**
   * 트리거 신뢰도 임계값 (0.0 ~ 1.0)
   * LLM이 반환한 confidence가 이 값 이상이면 트리거
   */
  val confidenceThreshold: Double = 0.7,

  /**
   * 판단용 최대 토큰 수
   * LLM 응답 생성에 사용할 최대 토큰
   */
  val maxTokens: Int = 500,

  /**
   * 커스텀 판단 프롬프트
   * null이면 기본 프롬프트 사용
   * 스킬별 특화된 판단 기준 설정 가능
   */
  val customPrompt: String? = null,

  /**
   * 타임아웃 (밀리초)
   * LLM 호출 최대 대기 시간
   */
  val timeoutMs: Long = 10000,

  /**
   * 폴백 활성화
   * LLM 호출 실패 시 규칙 기반으로 폴백할지 여부
   */
  val fallbackToRuleOnError: Boolean = true,
) {
  init {
    require(confidenceThreshold in 0.0..1.0) {
      "confidenceThreshold must be between 0.0 and 1.0"
    }
    require(maxTokens > 0) {
      "maxTokens must be positive"
    }
    require(timeoutMs > 0) {
      "timeoutMs must be positive"
    }
  }

  companion object {
    /**
     * 기본 설정
     */
    val DEFAULT = LlmTriggerConfig()

    /**
     * 빠른 판단용 설정 (토큰 절약)
     */
    val FAST = LlmTriggerConfig(
      maxTokens = 200,
      timeoutMs = 5000,
    )

    /**
     * 정밀 판단용 설정 (높은 신뢰도 요구)
     */
    val PRECISE = LlmTriggerConfig(
      confidenceThreshold = 0.85,
      maxTokens = 800,
      timeoutMs = 15000,
    )
  }
}
