package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.ai.core.AiExecutionType
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 임베딩/벡터 생성 게이트웨이
 *
 * 모든 임베딩 관련 AI 호출의 단일 진입점.
 * UnifiedAiExecutionTracker를 통해 자동으로 AiRun/AiRunStep을 생성하고 메트릭을 추적.
 *
 * 지원 기능:
 * - 텍스트 임베딩 생성
 * - 다중 텍스트 일괄 임베딩
 * - 벡터 스토어 검색
 * - 벡터 스토어 저장
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class EmbeddingGateway(
  private val tracker: UnifiedAiExecutionTracker,
  @param:Value($$"${app.ai.openai.api-key}")
  private val openAiApiKey: String
) {
  private val kLogger = KotlinLogging.logger {}

  // ==================== 프로바이더/결과 타입 정의 ====================

  /**
   * 임베딩 프로바이더
   */
  enum class EmbeddingProvider(
    val code: String,
    val displayName: String,
    val dimensions: Int,
    val costPerMillion: Double
  ) {
    OPENAI_SMALL("openai/text-embedding-3-small", "OpenAI Embedding Small", 1536, 0.02),
    OPENAI_LARGE("openai/text-embedding-3-large", "OpenAI Embedding Large", 3072, 0.13),
    OPENAI_ADA("openai/text-embedding-ada-002", "OpenAI Ada", 1536, 0.10);

    companion object {
      fun fromCode(code: String): EmbeddingProvider {
        return entries.find { it.code.equals(code, ignoreCase = true) } ?: OPENAI_SMALL
      }

      fun getDefault(): EmbeddingProvider = OPENAI_SMALL
    }
  }

  /**
   * 임베딩 결과
   */
  data class EmbeddingResult(
    val success: Boolean,
    val embeddings: List<FloatArray> = emptyList(),
    val dimensions: Int = 0,
    val inputTokenCount: Long = 0,
    val processingTimeMs: Long = 0,
    val errorMessage: String? = null
  )

  /**
   * 검색 결과
   */
  data class SearchResult(
    val success: Boolean,
    val matches: List<SearchMatch> = emptyList(),
    val queryTokenCount: Long = 0,
    val processingTimeMs: Long = 0,
    val errorMessage: String? = null
  )

  data class SearchMatch(
    val text: String,
    val score: Double,
    val metadata: Map<String, String> = emptyMap()
  )

  // ==================== 임베딩 모델 캐시 ====================

  private val embeddingModels = mutableMapOf<EmbeddingProvider, EmbeddingModel>()

  private fun getEmbeddingModel(provider: EmbeddingProvider): EmbeddingModel {
    return embeddingModels.getOrPut(provider) {
      when (provider) {
        EmbeddingProvider.OPENAI_SMALL -> OpenAiEmbeddingModel.builder()
          .apiKey(openAiApiKey)
          .modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_SMALL)
          .build()

        EmbeddingProvider.OPENAI_LARGE -> OpenAiEmbeddingModel.builder()
          .apiKey(openAiApiKey)
          .modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_3_LARGE)
          .build()

        EmbeddingProvider.OPENAI_ADA -> OpenAiEmbeddingModel.builder()
          .apiKey(openAiApiKey)
          .modelName("text-embedding-ada-002")
          .build()
      }
    }
  }

  // ==================== 임베딩 생성 ====================

  /**
   * 단일 텍스트 임베딩 생성
   *
   * @param provider 임베딩 프로바이더
   * @param text 임베딩할 텍스트
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 임베딩 결과
   */
  fun embed(
    provider: EmbeddingProvider = EmbeddingProvider.getDefault(),
    text: String,
    callerComponent: String,
    contextId: Long? = null
  ): EmbeddingResult {
    val requestPayload = mapOf(
      "text" to text,
      "textLength" to text.length
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.EMBEDDING,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    val startTime = System.currentTimeMillis()

    return try {
      val model = getEmbeddingModel(provider)
      val response = model.embed(text)
      val embedding = response.content()

      val processingTime = System.currentTimeMillis() - startTime
      val tokenCount = response.tokenUsage()?.inputTokenCount()?.toLong() ?: estimateTokenCount(text)

      val result = EmbeddingResult(
        success = true,
        embeddings = listOf(embedding.vector()),
        dimensions = provider.dimensions,
        inputTokenCount = tokenCount,
        processingTimeMs = processingTime
      )

      val metrics = AiExecutionMetrics.Embedding(
        inputTokens = tokenCount,
        dimensions = provider.dimensions,
        chunkCount = 1,
        totalCost = calculateCost(provider, tokenCount)
      )

      tracker.completeExecution(
        context = context,
        metrics = metrics,
        responsePayload = mapOf(
          "success" to true,
          "dimensions" to provider.dimensions,
          "inputTokenCount" to tokenCount,
          "processingTimeMs" to processingTime
        )
      )

      kLogger.debug {
        "[EmbeddingGateway] 임베딩 생성 완료 - caller: $callerComponent, " +
            "tokens: $tokenCount, dims: ${provider.dimensions}"
      }

      result
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      EmbeddingResult(
        success = false,
        processingTimeMs = System.currentTimeMillis() - startTime,
        errorMessage = e.message
      )
    }
  }

  /**
   * 다중 텍스트 일괄 임베딩 생성
   *
   * @param provider 임베딩 프로바이더
   * @param texts 임베딩할 텍스트 목록
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 임베딩 결과
   */
  fun embedBatch(
    provider: EmbeddingProvider = EmbeddingProvider.getDefault(),
    texts: List<String>,
    callerComponent: String,
    contextId: Long? = null
  ): EmbeddingResult {
    if (texts.isEmpty()) {
      return EmbeddingResult(success = true, embeddings = emptyList())
    }

    val requestPayload = mapOf(
      "textCount" to texts.size,
      "totalCharCount" to texts.sumOf { it.length }
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.EMBEDDING,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    val startTime = System.currentTimeMillis()

    return try {
      val model = getEmbeddingModel(provider)
      val segments = texts.map { TextSegment.from(it) }
      val response = model.embedAll(segments)
      val embeddings = response.content().map { it.vector() }

      val processingTime = System.currentTimeMillis() - startTime
      val tokenCount = response.tokenUsage()?.inputTokenCount()?.toLong()
        ?: texts.sumOf { estimateTokenCount(it) }

      val result = EmbeddingResult(
        success = true,
        embeddings = embeddings,
        dimensions = provider.dimensions,
        inputTokenCount = tokenCount,
        processingTimeMs = processingTime
      )

      val metrics = AiExecutionMetrics.Embedding(
        inputTokens = tokenCount,
        dimensions = provider.dimensions,
        chunkCount = texts.size,
        totalCost = calculateCost(provider, tokenCount)
      )

      tracker.completeExecution(
        context = context,
        metrics = metrics,
        responsePayload = mapOf(
          "success" to true,
          "embeddingCount" to embeddings.size,
          "dimensions" to provider.dimensions,
          "inputTokenCount" to tokenCount,
          "processingTimeMs" to processingTime
        )
      )

      kLogger.info {
        "[EmbeddingGateway] 배치 임베딩 완료 - caller: $callerComponent, " +
            "count: ${texts.size}, tokens: $tokenCount"
      }

      result
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      EmbeddingResult(
        success = false,
        processingTimeMs = System.currentTimeMillis() - startTime,
        errorMessage = e.message
      )
    }
  }

  // ==================== 벡터 검색 ====================

  /**
   * 벡터 스토어 검색
   *
   * @param embeddingStore 임베딩 스토어
   * @param provider 임베딩 프로바이더
   * @param query 검색 쿼리
   * @param maxResults 최대 결과 수
   * @param minScore 최소 유사도 점수
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 검색 결과
   */
  fun search(
    embeddingStore: EmbeddingStore<TextSegment>,
    provider: EmbeddingProvider = EmbeddingProvider.getDefault(),
    query: String,
    maxResults: Int = 5,
    minScore: Double = 0.0,
    callerComponent: String,
    contextId: Long? = null
  ): SearchResult {
    val requestPayload = mapOf(
      "query" to query,
      "maxResults" to maxResults,
      "minScore" to minScore
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.EMBEDDING,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    val startTime = System.currentTimeMillis()

    return try {
      val model = getEmbeddingModel(provider)

      // 쿼리 임베딩 생성
      val queryEmbedding = model.embed(query).content()
      val tokenCount = estimateTokenCount(query)

      // 검색 실행
      val searchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(maxResults)
        .minScore(minScore)
        .build()

      val searchResult = embeddingStore.search(searchRequest)

      val processingTime = System.currentTimeMillis() - startTime

      val matches = searchResult.matches().map { match ->
        SearchMatch(
          text = match.embedded().text(),
          score = match.score(),
          metadata = match.embedded().metadata().toMap().mapValues { it.value.toString() }
        )
      }

      val result = SearchResult(
        success = true,
        matches = matches,
        queryTokenCount = tokenCount,
        processingTimeMs = processingTime
      )

      val metrics = AiExecutionMetrics.Embedding(
        inputTokens = tokenCount,
        dimensions = provider.dimensions,
        chunkCount = 1,
        totalCost = calculateCost(provider, tokenCount)
      )

      tracker.completeExecution(
        context = context,
        metrics = metrics,
        responsePayload = mapOf(
          "success" to true,
          "matchCount" to matches.size,
          "queryTokenCount" to tokenCount,
          "processingTimeMs" to processingTime
        )
      )

      kLogger.debug {
        "[EmbeddingGateway] 검색 완료 - caller: $callerComponent, " +
            "matches: ${matches.size}, topScore: ${matches.firstOrNull()?.score ?: 0.0}"
      }

      result
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      SearchResult(
        success = false,
        processingTimeMs = System.currentTimeMillis() - startTime,
        errorMessage = e.message
      )
    }
  }

  // ==================== 헬퍼 메서드 ====================

  /**
   * 토큰 수 추정 (실제 토큰 수가 없을 때 사용)
   * 영어 기준 약 4자 = 1토큰, 한국어 기준 약 2자 = 1토큰
   */
  private fun estimateTokenCount(text: String): Long {
    // 간단한 추정: 평균 3자 = 1토큰
    return (text.length / 3).toLong().coerceAtLeast(1)
  }

  /**
   * 비용 계산
   */
  private fun calculateCost(provider: EmbeddingProvider, tokenCount: Long): Double {
    return provider.costPerMillion * (tokenCount / 1_000_000.0)
  }
}
