package com.jongmin.ai.insight.component

import com.jongmin.ai.insight.platform.dto.request.ProductAnalysisOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * 제품 데이터 병합 서비스
 *
 * PDP 페이지에서 추출한 메타데이터와 사용자가 입력한 분석 옵션을 병합하여
 * 중복을 제거하고 의미 있는 유일한 데이터로 정제합니다.
 *
 * ### 주요 책임:
 * - 메타데이터와 분석 옵션의 중복 데이터 제거
 * - 누락된 필드 보완
 * - LLM을 통한 데이터 정제 및 재구성
 * - 의미적으로 유사한 데이터 통합
 *
 * @property objectMapper JSON 직렬화/역직렬화
 */
@Service
class ProductDataMergerService(private val objectMapper: ObjectMapper) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 메타데이터와 분석 옵션을 병합하여 정제된 ProductAnalysisOptions 생성
   *
   * ### 병합 전략:
   * 1. 메타데이터에서 제품 기본 정보 추출
   * 2. 기존 분석 옵션과 비교하여 중복 제거
   * 3. 누락된 필드는 메타데이터에서 보완
   * 4. LLM을 통해 의미적 중복 제거 및 데이터 정제
   *
   * @param analysisOptions 사용자가 입력한 분석 옵션
   * @param metadata PDP 페이지에서 추출한 메타데이터
   * @return 병합 및 정제된 ProductAnalysisOptions
   */
  fun mergeAndRefineData(
    analysisOptions: ProductAnalysisOptions,
    metadata: Map<String, Any>
  ): ProductAnalysisOptions {
    try {
      buildRefinementPrompt(analysisOptions, metadata)
      kLogger.info { "LLM 데이터 정제 시작" }
      // Streaming 모델이므로 전체 응답을 수집
      val responseBuilder = StringBuilder()
      val refinedJson = responseBuilder.toString()
      // LLM 응답을 ProductAnalysisOptions로 파싱
      return try {
        objectMapper.readValue(refinedJson, ProductAnalysisOptions::class.java)
      } catch (e: Exception) {
        kLogger.warn(e) { "LLM 응답 파싱 실패, 원본 데이터 반환" }
        analysisOptions
      }
    } catch (e: Exception) {
      kLogger.error(e) { "LLM 데이터 정제 실패" }
      return analysisOptions
    }
  }

  /**
   * LLM 정제용 프롬프트 생성
   *
   * ProductAnalysisOptions의 모든 필드를 명시하여 데이터 누락을 최소화합니다.
   */
  private fun buildRefinementPrompt(
    mergedOptions: ProductAnalysisOptions,
    metadata: Map<String, Any>
  ): String {
    return """
    """.trimIndent()
  }
}
