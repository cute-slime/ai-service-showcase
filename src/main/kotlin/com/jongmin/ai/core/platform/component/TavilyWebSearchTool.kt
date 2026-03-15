package com.jongmin.ai.core.platform.component

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever
import dev.langchain4j.rag.query.Query
import dev.langchain4j.web.search.WebSearchEngine
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine
import java.time.Duration
import java.util.function.Function

/**
 * 웹 검색을 통해 관련 콘텐츠를 검색하는 도구 클래스
 * Tavily 웹 검색 엔진을 사용하여 주어진 쿼리에 대한 검색 결과를 반환
 * @property tavilyApiKey Tavily API 키
 */
class TavilyWebSearchTool(private val tavilyApiKey: String) : Function<String, List<Content>> {

  /**
   * 주어진 쿼리에 대해 웹 검색을 수행하고 관련 콘텐츠를 반환
   * @param query 검색할 쿼리 문자열
   * @return 검색된 콘텐츠 리스트 (최대 3개)
   */
  override fun apply(query: String): List<Content> {
    val webSearchEngine: WebSearchEngine = TavilyWebSearchEngine.builder()
      .apiKey(tavilyApiKey)
      .timeout(Duration.ofMinutes(1))
      .build()

    // 웹 검색 콘텐츠 검색기 설정 (최대 3개 결과 반환)
    val webSearchContentRetriever: ContentRetriever = WebSearchContentRetriever.builder()
      .webSearchEngine(webSearchEngine)
      .maxResults(3)
      .build()

    // 쿼리를 사용하여 콘텐츠 검색 및 반환
    return webSearchContentRetriever.retrieve(Query(query))
  }
}
