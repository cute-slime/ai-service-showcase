package com.jongmin.ai.core.platform.component

import com.jongmin.ai.core.platform.component.gateway.EmbeddingGateway
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingSearchResult
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Chroma 검색을 위한 클래스
 *
 * EmbeddingGateway를 통해 임베딩 생성 및 검색을 수행합니다 (자동 추적 적용).
 *
 * @property embeddingGateway 임베딩 게이트웨이 (추적 자동 적용)
 * @property host ChromaDB 호스트 URL
 */
@Component
class ChromaStore(
  private val embeddingGateway: EmbeddingGateway,
  @param:Value($$"${app.chromadb.base-url}")
  private val host: String
) {

  private val kLogger = KotlinLogging.logger {}

  @PostConstruct
  private fun init() {
    kLogger.info { "ChromaStore initialized - host: $host" }
  }

  private val chroma = ChromaEmbeddingStore.builder()
    .baseUrl(host)
    .timeout(Duration.ofMinutes(1))
    .collectionName("rag-chroma")
    .logRequests(true)
    .logResponses(true)
    .build()

  /**
   * 쿼리를 임베딩하고 Chroma에서 유사한 문서를 검색합니다.
   *
   * EmbeddingGateway를 통해 임베딩을 생성하므로 AI 사용량이 자동으로 추적됩니다.
   *
   * @param query 검색 쿼리
   * @return 검색 결과
   */
  fun search(query: String?): EmbeddingSearchResult<TextSegment> {
    if (query.isNullOrBlank()) {
      kLogger.warn { "빈 쿼리로 Chroma 검색 시도됨" }
      return chroma.search(
        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
          .maxResults(0)
          .build()
      )
    }

    // EmbeddingGateway 경유 임베딩 생성 (자동 추적)
    val embedResult = embeddingGateway.embed(
      text = query,
      callerComponent = "ChromaStore.search"
    )

    if (!embedResult.success || embedResult.embeddings.isEmpty()) {
      kLogger.warn { "임베딩 생성 실패: ${embedResult.errorMessage}" }
      return chroma.search(
        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
          .maxResults(0)
          .build()
      )
    }

    // 생성된 임베딩으로 Chroma 검색 수행
    val queryEmbedding = dev.langchain4j.data.embedding.Embedding.from(embedResult.embeddings.first())
    val searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
      .queryEmbedding(queryEmbedding)
      .maxResults(1)
      .minScore(0.0)
      .build()

    return chroma.search(searchRequest)
  }
}
