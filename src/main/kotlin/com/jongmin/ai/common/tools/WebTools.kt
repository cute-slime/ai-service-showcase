package com.jongmin.ai.common.tools

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 웹 크롤링 요청 DTO
 */
data class Crawl4AiRequest(
  val urls: Set<String>,
)

/**
 * 웹 크롤링 응답 DTO
 */
data class Crawl4AiResponse(
  val results: List<CrawlResult>? = null,
)

data class CrawlResult(
  val url: String,
  val html: String? = null,
  val text: String? = null,
)

/**
 * 웹 검색 응답 DTO
 */
data class SearchResponse(
  val results: List<SearchResult>? = null,
)

data class SearchResult(
  val title: String,
  val url: String,
  val content: String,
) {
  fun snippet(): String = content
}

/**
 * 웹 크롤링 도구 인터페이스
 *
 * @author Jongmin
 * @since 2026-01-19
 */
interface Crawl4Ai {
  /**
   * 웹 페이지 크롤링
   *
   * @param request 크롤링 요청
   * @param callback 결과 콜백
   */
  fun crawlImmediately(request: Crawl4AiRequest, callback: (Crawl4AiResponse?) -> Unit)
}

/**
 * 웹 검색 도구 인터페이스
 *
 * @author Jongmin
 * @since 2026-01-19
 */
interface SearXng {
  /**
   * 웹 검색
   *
   * @param query 검색어
   * @param limit 결과 수 제한
   * @param callback 결과 콜백
   */
  fun search(query: String, limit: Int = 10, callback: (SearchResponse?) -> Unit)
}

/**
 * 유튜브 자막 추출 도구 인터페이스
 *
 * @author Jongmin
 * @since 2026-01-19
 */
interface YoutubeTranscriptTool {
  /**
   * 유튜브 영상 자막 추출 (타임스탬프 포함)
   *
   * @param url 유튜브 URL
   * @return 타임스탬프 포함 자막
   */
  fun getTranscriptToTimeBased(url: String): String
}

// ========== 임시 구현체들 (TODO: 실제 구현으로 교체) ==========

private val kLogger = KotlinLogging.logger {}

/**
 * Crawl4Ai 임시 구현체
 *
 * TODO: 실제 Crawl4Ai 서버 연동 또는 Playwright 기반 구현
 */
@Component
class Crawl4AiImpl : Crawl4Ai {
  override fun crawlImmediately(request: Crawl4AiRequest, callback: (Crawl4AiResponse?) -> Unit) {
    kLogger.warn { "Crawl4Ai 임시 구현체 - 크롤링 요청 무시됨: ${request.urls}" }
    callback(Crawl4AiResponse(results = emptyList()))
  }
}

/**
 * SearXng 임시 구현체
 *
 * TODO: 실제 SearXng 서버 연동 또는 외부 검색 API 사용
 */
@Component
class SearXngImpl : SearXng {
  override fun search(query: String, limit: Int, callback: (SearchResponse?) -> Unit) {
    kLogger.warn { "SearXng 임시 구현체 - 검색 요청 무시됨: $query" }
    callback(SearchResponse(results = emptyList()))
  }
}

/**
 * YoutubeTranscriptTool 임시 구현체
 *
 * TODO: 실제 YouTube API 또는 자막 추출 라이브러리 사용
 */
@Component
class YoutubeTranscriptToolImpl : YoutubeTranscriptTool {
  override fun getTranscriptToTimeBased(url: String): String {
    kLogger.warn { "YoutubeTranscriptTool 임시 구현체 - 자막 요청 무시됨: $url" }
    return "[자막 추출 기능이 아직 구현되지 않았습니다]"
  }
}
