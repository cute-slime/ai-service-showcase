package com.jongmin.ai.core.platform.parser

import com.jongmin.ai.core.platform.dto.CacheUsageInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 캐시 사용량 파서 팩토리
 *
 * 프로바이더명을 기반으로 적절한 CacheUsageParser를 선택합니다.
 * 모든 등록된 파서를 우선순위에 따라 정렬하여 첫 번째로 매칭되는 파서를 반환합니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Component
class CacheUsageParserFactory(
  parsers: List<CacheUsageParser>
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 우선순위 순으로 정렬된 파서 목록
   */
  private val sortedParsers: List<CacheUsageParser> = parsers.sortedBy { it.priority() }

  /**
   * 폴백 파서 (알 수 없는 프로바이더용)
   */
  private val fallbackParser = FallbackCacheParser()

  init {
    kLogger.info {
      "[CacheUsageParserFactory] 등록된 파서: ${sortedParsers.map { it::class.simpleName }}"
    }
  }

  /**
   * 프로바이더에 맞는 파서 반환
   *
   * @param provider 프로바이더명
   * @return 해당 프로바이더를 처리할 수 있는 파서 (없으면 폴백 파서)
   */
  fun getParser(provider: String): CacheUsageParser {
    return sortedParsers.firstOrNull { it.canParse(provider) }
      ?: fallbackParser.also {
        kLogger.debug { "[CacheUsageParserFactory] 알 수 없는 프로바이더, 폴백 사용: $provider" }
      }
  }

  /**
   * 프로바이더의 응답을 파싱하여 캐시 정보 추출
   *
   * @param rawUsage API 응답의 usage 객체
   * @param provider 프로바이더명
   * @param model 모델명
   * @return 캐시 사용량 정보
   */
  fun parse(rawUsage: Map<String, Any>, provider: String, model: String): CacheUsageInfo {
    val parser = getParser(provider)
    return parser.parse(rawUsage, provider, model)
  }

  /**
   * 알 수 없는 프로바이더용 폴백 파서
   *
   * 캐시 정보 없이 기본 토큰 정보만 추출 시도
   */
  private class FallbackCacheParser : CacheUsageParser {
    private val kLogger = KotlinLogging.logger {}

    override fun canParse(provider: String): Boolean = true  // 모든 프로바이더 허용

    override fun parse(rawUsage: Map<String, Any>, provider: String, model: String): CacheUsageInfo {
      return try {
        // OpenAI 형식 먼저 시도
        val promptTokens = (rawUsage["prompt_tokens"] as? Number)?.toLong()
        val completionTokens = (rawUsage["completion_tokens"] as? Number)?.toLong()

        // Anthropic 형식 시도
        val inputTokens = promptTokens ?: (rawUsage["input_tokens"] as? Number)?.toLong() ?: 0
        val outputTokens = completionTokens ?: (rawUsage["output_tokens"] as? Number)?.toLong() ?: 0

        // 캐시 정보 추출 시도 (있으면 사용)
        val promptTokensDetails = rawUsage["prompt_tokens_details"] as? Map<*, *>
        val cachedTokens = (promptTokensDetails?.get("cached_tokens") as? Number)?.toLong()
          ?: (rawUsage["cache_read_input_tokens"] as? Number)?.toLong()
          ?: 0
        val cacheCreationTokens = (rawUsage["cache_creation_input_tokens"] as? Number)?.toLong() ?: 0

        CacheUsageInfo(
          inputTokens = inputTokens,
          outputTokens = outputTokens,
          cachedTokens = cachedTokens,
          cacheCreationTokens = cacheCreationTokens,
          totalTokens = inputTokens + outputTokens,
          provider = provider,
          model = model
        ).also {
          if (it.hasCacheActivity()) {
            kLogger.info {
              "[폴백 파서] 예상치 못한 캐시 활동 감지! provider: $provider, model: $model, " +
                  "cached: ${it.cachedTokens}, cacheCreation: ${it.cacheCreationTokens}"
            }
          }
        }
      } catch (e: Exception) {
        kLogger.warn(e) { "[폴백 파서] 파싱 실패 - provider: $provider, model: $model" }
        CacheUsageInfo.empty(provider, model)
      }
    }

    override fun priority(): Int = Int.MAX_VALUE  // 가장 낮은 우선순위
  }
}
