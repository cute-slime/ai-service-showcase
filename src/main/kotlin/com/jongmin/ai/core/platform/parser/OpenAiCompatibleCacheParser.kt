package com.jongmin.ai.core.platform.parser

import com.jongmin.ai.core.platform.dto.CacheUsageInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * OpenAI 호환 API 캐시 사용량 파서
 *
 * OpenAI 및 OpenAI 호환 API를 사용하는 프로바이더들의 응답을 파싱합니다.
 * 지원 프로바이더: OpenAI, Z.AI, DeepSeek, xAI, Mistral, Cerebras, OpenRouter,
 *                 Kluster, vLLM, LM Studio 등
 *
 * 응답 형식:
 * ```json
 * {
 *   "usage": {
 *     "prompt_tokens": 2006,
 *     "completion_tokens": 300,
 *     "total_tokens": 2306,
 *     "prompt_tokens_details": {
 *       "cached_tokens": 1920
 *     }
 *   }
 * }
 * ```
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Component
class OpenAiCompatibleCacheParser : CacheUsageParser {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    /**
     * OpenAI 호환 API를 사용하는 프로바이더 목록
     * 소문자로 정규화하여 비교
     */
    private val SUPPORTED_PROVIDERS = setOf(
      "openai",
      "zai",
      "z.ai",
      "deepseek",
      "xai",
      "x.ai",
      "mistral",
      "mistral ai",
      "cerebras",
      "openrouter",
      "kluster",
      "vllm",
      "lm studio",
      "lmstudio",
      "together",
      "together ai",
      "fireworks",
      "fireworks ai",
      "groq",
      "perplexity",
      "anyscale"
    )
  }

  override fun canParse(provider: String): Boolean {
    return provider.lowercase().trim() in SUPPORTED_PROVIDERS
  }

  override fun parse(rawUsage: Map<String, Any>, provider: String, model: String): CacheUsageInfo {
    return try {
      // 기본 토큰 정보 추출
      val promptTokens = extractLong(rawUsage, "prompt_tokens")
      val completionTokens = extractLong(rawUsage, "completion_tokens")
      val totalTokens = extractLong(rawUsage, "total_tokens")
        ?: (promptTokens ?: 0) + (completionTokens ?: 0)

      // 캐시 토큰 정보 추출 (prompt_tokens_details.cached_tokens)
      val promptTokensDetails = rawUsage["prompt_tokens_details"] as? Map<*, *>
      val cachedTokens = (promptTokensDetails?.get("cached_tokens") as? Number)?.toLong() ?: 0

      CacheUsageInfo(
        inputTokens = promptTokens ?: 0,
        outputTokens = completionTokens ?: 0,
        cachedTokens = cachedTokens,
        cacheCreationTokens = 0,  // OpenAI 호환 API에서는 별도 제공 안함
        totalTokens = totalTokens,
        provider = provider,
        model = model
      ).also {
        if (it.hasCacheHit()) {
          kLogger.debug {
            "[OpenAI 호환 파서] 캐시 히트 감지 - provider: $provider, model: $model, " +
                "cached: ${it.cachedTokens}/${it.inputTokens} (${it.cacheHitRatePercent()})"
          }
        }
      }
    } catch (e: Exception) {
      kLogger.warn(e) { "[OpenAI 호환 파서] 파싱 실패 - provider: $provider, model: $model" }
      CacheUsageInfo.empty(provider, model)
    }
  }

  override fun priority(): Int = 10  // OpenAI 호환이 가장 일반적이므로 낮은 우선순위

  /**
   * Map에서 Long 값 추출 (Number 타입 지원)
   */
  private fun extractLong(map: Map<String, Any>, key: String): Long? {
    return (map[key] as? Number)?.toLong()
  }
}
