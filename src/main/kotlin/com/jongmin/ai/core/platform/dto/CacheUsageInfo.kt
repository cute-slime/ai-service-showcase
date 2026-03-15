package com.jongmin.ai.core.platform.dto

/**
 * 프로바이더 독립적 캐시 사용량 정보
 *
 * 모든 AI 프로바이더의 응답에서 추출된 토큰 사용량 정보를 통합된 형태로 표현합니다.
 * OpenAI 계열과 Anthropic의 서로 다른 응답 형식을 이 DTO로 통합합니다.
 *
 * @property inputTokens 총 입력 토큰 수 (OpenAI: prompt_tokens, Anthropic: input_tokens)
 * @property outputTokens 총 출력 토큰 수 (OpenAI: completion_tokens, Anthropic: output_tokens)
 * @property cachedTokens 캐시에서 재사용된 토큰 수 (캐시 히트)
 * @property cacheCreationTokens 캐시에 새로 저장된 토큰 수 (Anthropic 전용)
 * @property totalTokens 전체 토큰 수
 * @property provider 프로바이더명
 * @property model 모델명
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class CacheUsageInfo(
  val inputTokens: Long,
  val outputTokens: Long,
  val cachedTokens: Long,
  val cacheCreationTokens: Long,
  val totalTokens: Long,
  val provider: String,
  val model: String
) {
  companion object {
    /**
     * 빈 캐시 정보 생성 (캐싱 미지원 또는 파싱 실패 시)
     */
    fun empty(provider: String, model: String) = CacheUsageInfo(
      inputTokens = 0,
      outputTokens = 0,
      cachedTokens = 0,
      cacheCreationTokens = 0,
      totalTokens = 0,
      provider = provider,
      model = model
    )
  }

  /**
   * 캐시 히트가 발생했는지 확인
   */
  fun hasCacheHit(): Boolean = cachedTokens > 0

  /**
   * 캐시 생성이 발생했는지 확인 (Anthropic 전용)
   */
  fun hasCacheCreation(): Boolean = cacheCreationTokens > 0

  /**
   * 캐시 관련 활동이 있는지 확인
   */
  fun hasCacheActivity(): Boolean = hasCacheHit() || hasCacheCreation()

  /**
   * 캐시 히트율 계산 (0.0 ~ 1.0)
   * 입력 토큰이 없으면 0.0 반환
   */
  fun cacheHitRate(): Double {
    if (inputTokens == 0L) return 0.0
    return cachedTokens.toDouble() / inputTokens.toDouble()
  }

  /**
   * 캐시 히트율 퍼센트 문자열 (예: "72.5%")
   */
  fun cacheHitRatePercent(): String {
    return String.format("%.1f%%", cacheHitRate() * 100)
  }
}
