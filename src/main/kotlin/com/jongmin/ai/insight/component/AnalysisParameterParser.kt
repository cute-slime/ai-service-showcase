package com.jongmin.ai.insight.component

import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.insight.platform.dto.request.ProductAnalysisOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 메타데이터 파싱 서비스
 *
 * PDP 카피라이팅 요청의 메타데이터(JSON String)를 파싱하고 검증하는 책임을 담당합니다.
 *
 * ### 주요 책임:
 * - JSON String 형태의 메타데이터를 Map<String, Any>로 파싱
 * - ProductAnalysisOptions 파싱 및 Jakarta Bean Validation 수행
 * - 파싱 실패 시 안전한 예외 처리 및 빈 Map 반환
 * - 파싱 실패 로그 기록
 *
 * ### 사용 예시:
 * ```kotlin
 * val metadata = metadataParser.parseToMap(dto.metadata)
 * val brand = metadata["brand"] as? String
 * ```
 *
 * @property objectMapper Jackson ObjectMapper (JSON 직렬화/역직렬화)
 * @property validator Jakarta Bean Validation Validator
 */
@Service
class AnalysisParameterParser(
  private val objectMapper: ObjectMapper,
  private val validator: Validator
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * JSON String 형태의 메타데이터를 Map으로 파싱
   *
   * ### 처리 흐름:
   * 1. null 또는 빈 문자열 체크 -> 빈 Map 반환
   * 2. Jackson ObjectMapper로 JSON 파싱
   * 3. 파싱 실패 시 경고 로그 출력 후 빈 Map 반환
   *
   * ### 안전성:
   * - 예외 발생 시에도 빈 Map을 반환하여 후속 로직이 중단되지 않음
   * - 원본 JSON 문자열을 로그에 기록하여 디버깅 가능
   *
   * @param metadataJson JSON String 형태의 메타데이터 (null 가능)
   * @return 파싱된 Map<String, Any>, 파싱 실패 시 빈 Map 반환
   */
  fun parseMetadata(metadataJson: String?): Map<String, Any> {
    // null 또는 빈 문자열 체크
    if (metadataJson.isNullOrBlank()) {
      return emptyMap()
    }

    return try {
      objectMapper.readValue(metadataJson, object : TypeReference<Map<String, Any>>() {})
    } catch (e: Exception) {
      kLogger.warn(e) { "메타데이터 JSON 파싱 실패 - 원본: $metadataJson" }
      emptyMap()
    }
  }

  /**
   * JSON String 형태의 분석 옵션을 ProductAnalysisOptions 객체로 파싱하고 검증
   *
   * ### 처리 흐름:
   * 1. null 또는 빈 문자열 체크 -> null 반환
   * 2. Jackson ObjectMapper로 JSON 파싱하여 ProductAnalysisOptions 객체 생성
   * 3. Jakarta Bean Validation 수행 (@NotBlank, @Length 등)
   * 4. 검증 실패 시 ConstraintViolationException 발생
   * 5. 파싱/검증 실패 시 경고 로그 출력 후 null 반환
   *
   * ### 검증 항목:
   * - ProductBasicInfo.productName: @NotBlank, @Length(max=80)
   * - ProductBasicInfo.category: @NotBlank, @Length(max=120)
   * - 기타 @Valid가 설정된 중첩 객체들
   *
   * ### 안전성:
   * - 예외 발생 시에도 null을 반환하여 후속 로직이 중단되지 않음
   * - 검증 실패 시 위반 항목을 상세히 로깅
   * - 원본 JSON 문자열을 로그에 기록하여 디버깅 가능
   *
   * ### 사용 예시:
   * ```kotlin
   * val analysisOptions = parameterParser.parseProductAnalysisOptions(dto.analysisOptions)
   * val productName = analysisOptions?.productBasicInfo?.productName
   * val targetAudience = analysisOptions?.productBasicInfo?.targetAudience
   * val businessGoals = analysisOptions?.analysisFocus?.businessGoals
   * ```
   *
   * @param analysisOptionsJson JSON String 형태의 분석 옵션 (null 가능)
   * @return 파싱 및 검증된 ProductAnalysisOptions 객체, 실패 시 null 반환
   * @throws ConstraintViolationException 검증 실패 시 (catch되어 null 반환됨)
   */
  fun parseProductAnalysisOptions(analysisOptionsJson: String?): ProductAnalysisOptions {
    // null 또는 빈 문자열 체크
    if (analysisOptionsJson.isNullOrBlank()) {
      throw BadRequestException("파라메터 오류")
    }

    return try {
      // 1. JSON 파싱
      val parsedOptions = objectMapper.readValue(analysisOptionsJson, ProductAnalysisOptions::class.java)

      // 2. Bean Validation 수행
      val violations = validator.validate(parsedOptions)

      // 3. 검증 실패 시 상세 로그 출력 및 예외 발생
      if (violations.isNotEmpty()) {
        val violationMessages = violations.joinToString(", ") { violation ->
          "${violation.propertyPath}: ${violation.message}"
        }
        kLogger.warn { "ProductAnalysisOptions 검증 실패 - 위반 항목: [$violationMessages]" }

        // ConstraintViolationException 발생 (또는 null 반환 선택 가능)
        throw ConstraintViolationException(violations)
      }

      parsedOptions
    } catch (e: ConstraintViolationException) {
      kLogger.warn { "ProductAnalysisOptions JSON 파싱 실패 - 원본: $analysisOptionsJson" }
      throw e
    }
  }
}
