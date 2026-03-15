package com.jongmin.ai.core.platform.parser

import com.jongmin.ai.core.platform.dto.CacheUsageInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Anthropic API 캐시 사용량 파서
 *
 * Anthropic의 고유한 응답 형식을 파싱합니다.
 * Anthropic은 명시적 캐싱(cache_control 블록)을 지원하며,
 * 캐시 읽기와 캐시 생성 토큰을 별도로 제공합니다.
 *
 * 응답 형식:
 * ```json
 * {
 *   "usage": {
 *     "input_tokens": 2006,
 *     "output_tokens": 300,
 *     "cache_creation_input_tokens": 0,
 *     "cache_read_input_tokens": 1920
 *   }
 * }
 * ```
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Component
class AnthropicCacheParser : CacheUsageParser {

  private val kLogger = KotlinLogging.logger {}

  override fun canParse(provider: String): Boolean {
    return provider.lowercase().trim() == "anthropic"
  }

  override fun parse(rawUsage: Map<String, Any>, provider: String, model: String): CacheUsageInfo {
    return try {
      // Anthropic 형식의 토큰 정보 추출
      val inputTokens = extractLong(rawUsage, "input_tokens")
      val outputTokens = extractLong(rawUsage, "output_tokens")

      // 캐시 관련 토큰 추출
      val cacheReadTokens = extractLong(rawUsage, "cache_read_input_tokens")
      val cacheCreationTokens = extractLong(rawUsage, "cache_creation_input_tokens")

      CacheUsageInfo(
        inputTokens = inputTokens ?: 0,
        outputTokens = outputTokens ?: 0,
        cachedTokens = cacheReadTokens ?: 0,
        cacheCreationTokens = cacheCreationTokens ?: 0,
        totalTokens = (inputTokens ?: 0) + (outputTokens ?: 0),
        provider = provider,
        model = model
      ).also {
        if (it.hasCacheActivity()) {
          kLogger.debug {
            "[Anthropic 파서] 캐시 활동 감지 - provider: $provider, model: $model, " +
                "cacheRead: ${it.cachedTokens}, cacheCreation: ${it.cacheCreationTokens}, " +
                "hitRate: ${it.cacheHitRatePercent()}"
          }
        }
      }
    } catch (e: Exception) {
      kLogger.warn(e) { "[Anthropic 파서] 파싱 실패 - provider: $provider, model: $model" }
      CacheUsageInfo.empty(provider, model)
    }
  }

  override fun priority(): Int = 1  // Anthropic 전용이므로 높은 우선순위

  /**
   * Map에서 Long 값 추출 (Number 타입 지원)
   */
  private fun extractLong(map: Map<String, Any>, key: String): Long? {
    return (map[key] as? Number)?.toLong()
  }
}
