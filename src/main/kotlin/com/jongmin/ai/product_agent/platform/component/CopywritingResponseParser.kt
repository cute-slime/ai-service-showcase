package com.jongmin.ai.product_agent.platform.component

import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingData
import com.jongmin.ai.product_agent.platform.dto.response.AiCopyOnlyResponse
import com.jongmin.ai.product_agent.platform.dto.response.AiMarketingInsightsOnlyResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 카피라이팅 응답 파서
 *
 * 카피라이팅 요청 데이터 파싱 및 LLM 응답 파싱을 담당하는 컴포넌트입니다.
 *
 * ### 주요 책임:
 * - 카피라이팅 요청 JSON 데이터 파싱 및 검증
 * - 1차 LLM 응답 파싱 (카피라이팅)
 * - 2차 LLM 응답 파싱 (마케팅 인사이트)
 * - 파싱 실패 시 폴백 응답 생성
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property validator Jakarta Bean Validation
 */
@Component
class CopywritingResponseParser(
  private val objectMapper: ObjectMapper,
  private val validator: Validator
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 카피라이팅 요청 데이터 파싱
   *
   * JSON String 형태의 data 필드를 CopywritingData 객체로 파싱하고 검증합니다.
   *
   * ### 처리 흐름:
   * 1. null 또는 빈 문자열 체크
   * 2. JSON 파싱하여 CopywritingData 객체 생성
   * 3. Jakarta Bean Validation 수행
   * 4. 검증 실패 시 ConstraintViolationException 발생
   *
   * @param dataJson JSON String 형태의 카피라이팅 데이터
   * @return 파싱 및 검증된 CopywritingData 객체
   * @throws IllegalArgumentException 데이터가 null 또는 빈 문자열인 경우
   * @throws ConstraintViolationException 검증 실패 시
   */
  fun parseCopywritingData(dataJson: String?): CopywritingData {
    // null 또는 빈 문자열 체크
    if (dataJson.isNullOrBlank()) {
      throw IllegalArgumentException("카피라이팅 요청 데이터가 없습니다")
    }

    return try {
      // 1. JSON 파싱
      val parsedData = objectMapper.readValue(dataJson, CopywritingData::class.java)

      // 2. Bean Validation 수행
      val violations = validator.validate(parsedData)

      // 3. 검증 실패 시 상세 로그 출력 및 예외 발생
      if (violations.isNotEmpty()) {
        val violationMessages = violations.joinToString(", ") { violation ->
          "${violation.propertyPath}: ${violation.message}"
        }
        kLogger.warn { "CopywritingData 검증 실패 - 위반 항목: [$violationMessages]" }
        throw ConstraintViolationException(violations)
      }

      parsedData
    } catch (e: ConstraintViolationException) {
      kLogger.warn { "CopywritingData 검증 실패 - 원본: $dataJson" }
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "CopywritingData JSON 파싱 실패 - 원본: $dataJson" }
      throw IllegalArgumentException("카피라이팅 요청 데이터 파싱에 실패했습니다: ${e.message}")
    }
  }

  /**
   * 1차 LLM 응답 파싱 (카피라이팅 전용)
   *
   * AI가 생성한 JSON 응답을 AiCopyOnlyResponse로 파싱합니다.
   * 파싱 실패 시 원본 텍스트를 기반으로 폴백 응답을 생성합니다.
   *
   * @param rawContent AI가 생성한 원본 응답
   * @return 파싱된 카피라이팅 응답
   */
  fun parseCopywritingResponse(rawContent: String): AiCopyOnlyResponse {
    return try {
      val cleanedJson = rawContent.cleanJsonString()
      val response = objectMapper.readValue(cleanedJson, AiCopyOnlyResponse::class.java)
      kLogger.debug { "카피라이팅 응답 파싱 성공 - mainCopy 길이: ${response.mainCopy.length}" }
      response
    } catch (e: Exception) {
      kLogger.warn(e) { "카피라이팅 응답 JSON 파싱 실패, 폴백 생성 - 원본: $rawContent" }

      // 파싱 실패 시 원본 텍스트로 폴백 응답 생성
      AiCopyOnlyResponse(
        mainCopy = rawContent.take(500),
        subCopy = if (rawContent.length > 500) rawContent.drop(500).take(1000) else null
      )
    }
  }

  /**
   * 2차 LLM 응답 파싱 (마케팅 인사이트 전용)
   *
   * AI가 생성한 JSON 응답을 AiMarketingInsightsOnlyResponse로 파싱합니다.
   * 파싱 실패 시 기본 마케팅 인사이트를 생성합니다.
   *
   * @param rawContent AI가 생성한 원본 응답
   * @return 파싱된 마케팅 인사이트 응답
   */
  fun parseMarketingInsightsResponse(rawContent: String): AiMarketingInsightsOnlyResponse {
    return try {
      val cleanedJson = rawContent.cleanJsonString()
      val response = objectMapper.readValue(cleanedJson, AiMarketingInsightsOnlyResponse::class.java)
      kLogger.debug { "마케팅 인사이트 응답 파싱 성공 - 경쟁 우위 수: ${response.competitiveAdvantages.size}" }
      response
    } catch (e: Exception) {
      kLogger.warn(e) { "마케팅 인사이트 응답 JSON 파싱 실패, 폴백 생성 - 원본: $rawContent" }

      // 파싱 실패 시 기본 마케팅 인사이트 생성
      AiMarketingInsightsOnlyResponse(
        targetingStrategy = "타겟 고객층에 맞춘 맞춤형 마케팅 전략이 필요합니다. 고객 데이터를 분석하여 세그먼트별 접근 전략을 수립하세요.",
        promotionTiming = "시즌 및 이벤트에 맞춘 프로모션 타이밍을 고려하세요. 주요 쇼핑 시즌과 연계하면 효과적입니다.",
        competitiveAdvantages = listOf(
          "상품의 고유한 가치 제안을 명확히 전달",
          "고객 니즈에 맞춘 차별화된 서비스 제공",
          "품질과 가격의 균형 있는 포지셔닝"
        ),
        contentStrategy = "다양한 콘텐츠 형식을 활용하여 상품의 가치를 전달하세요. 이미지, 영상, 텍스트를 적절히 조합하세요.",
        pricingStrategy = "시장 상황과 경쟁 제품을 분석하여 최적의 가격 전략을 수립하세요."
      )
    }
  }
}
