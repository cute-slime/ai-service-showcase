package com.jongmin.ai.insight.component

import com.jongmin.ai.insight.platform.dto.request.ProductAnalysisOptions
import com.jongmin.ai.insight.platform.dto.request.ProductAnalyze
import com.jongmin.ai.insight.platform.dto.response.DataQualityReport
import com.jongmin.ai.insight.platform.dto.response.IncompleteFieldInfo
import com.jongmin.ai.insight.platform.dto.response.MissingFieldInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * 데이터 품질 평가 서비스
 *
 * PDP 카피라이팅 요청의 입력 데이터 품질을 평가하고, 품질 점수를 산출하며, 개선 가이드를 제공합니다.
 *
 * ### 주요 책임:
 * - 필수 필드 누락 검증 (상품명, 이미지 등)
 * - 선택 필드 불완전성 평가 (메타데이터, 리뷰 등)
 * - 데이터 품질 점수 계산 (0-100점)
 * - 데이터 개선 권장사항 생성
 * - 누락 데이터가 미치는 영향 분석
 *
 * ### 품질 등급:
 * - excellent: 모든 필드 완비
 * - good: 필수 필드 완비, 선택 필드 일부 누락
 * - fair: 필수 필드 일부 누락, 선택 필드 다수 누락
 * - poor: 필수 필드 다수 누락, 카피라이팅 품질 크게 저하
 *
 * @property analysisParameterParser 메타데이터 파싱 서비스
 */
@Service
class DataQualityEvaluator(
  private val analysisParameterParser: AnalysisParameterParser
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * PDP 요청 데이터의 품질을 평가
   *
   * ### 평가 항목:
   * 1. 필수 필드: shopName, images
   * 2. 선택 필드: metadata, brand, category, price, reviews
   *
   * ### 영향도 등급:
   * - high: 카피라이팅 품질에 심각한 영향 (20점 감점)
   * - medium: 일부 기능 제한 (10점 감점)
   * - low: 미미한 영향 (5점 감점)
   *
   * @param dto PDP 카피라이팅 요청 DTO
   * @param metadata 파싱된 메타데이터
   * @param imageCount 이미지 개수
   * @return 데이터 품질 평가 리포트
   */
  fun evaluate(
    dto: ProductAnalyze,
    metadata: Map<String, Any>,
    imageCount: Int
  ): DataQualityReport {
    val missingFields = mutableListOf<MissingFieldInfo>()
    val incompleteFields = mutableListOf<IncompleteFieldInfo>()
    val recommendations = mutableListOf<String>()
    val impactAnalysis = mutableMapOf<String, String>()

    // === 1. 필수 필드 검증 ===
    validateRequiredFields(dto, imageCount, missingFields, incompleteFields, impactAnalysis, recommendations)

    // === 2. 선택 필드 검증 (메타데이터) ===
    validateOptionalFields(metadata, missingFields, incompleteFields, recommendations, impactAnalysis)

    // === 3. 전체 품질 등급 산정 ===
    val overallQuality = calculateOverallQuality(missingFields.size, incompleteFields.size)

    // === 4. 기본 권장사항 추가 (모든 필드 완비 시) ===
    if (recommendations.isEmpty()) {
      recommendations.add("현재 데이터 품질이 우수합니다. 추가 개선 사항이 없습니다.")
    }

    return DataQualityReport(
      overallQuality = overallQuality,
      missingFields = missingFields.takeIf { it.isNotEmpty() },
      incompleteFields = incompleteFields.takeIf { it.isNotEmpty() },
      recommendations = recommendations.takeIf { it.isNotEmpty() },
      impactAnalysis = impactAnalysis.takeIf { it.isNotEmpty() }
    )
  }

  /**
   * 필수 필드 검증 (상품명, 이미지)
   */
  private fun validateRequiredFields(
    dto: ProductAnalyze,
    imageCount: Int,
    missingFields: MutableList<MissingFieldInfo>,
    incompleteFields: MutableList<IncompleteFieldInfo>,
    impactAnalysis: MutableMap<String, String>,
    recommendations: MutableList<String>
  ) {
    // 상품명 검증
    if (dto.shopName.isNullOrBlank()) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "shopName",
          fieldType = "required",
          description = "상품명 (브랜드 또는 샵 이름)",
          example = "무신사 스탠다드",
          impact = "high"
        )
      )
      impactAnalysis["shopName"] = "상품명 누락 시 카피라이팅 품질이 크게 저하되며, SEO 최적화가 불가능합니다."
    }

    // 이미지 검증
    if (imageCount == 0) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "images",
          fieldType = "required",
          description = "상품 이미지 (최소 3장 권장)",
          example = "상품 전면, 측면, 디테일 이미지",
          impact = "high"
        )
      )
      impactAnalysis["images"] = "이미지 없이는 VLM 분석이 불가능하여 색상, 소재, 스타일 정보를 추출할 수 없습니다."
      recommendations.add("최소 3장 이상의 고품질 상품 이미지를 업로드하세요 (전면, 측면, 디테일)")
    } else if (imageCount < 3) {
      incompleteFields.add(
        IncompleteFieldInfo(
          fieldName = "images",
          currentValue = "${imageCount}장",
          issue = "이미지 수가 부족합니다 (최소 3장 권장)",
          suggestion = "다양한 각도의 이미지를 추가로 업로드하면 AI 분석 정확도가 향상됩니다"
        )
      )
    }
  }

  /**
   * 선택 필드 검증 (메타데이터 관련)
   */
  private fun validateOptionalFields(
    metadata: Map<String, Any>,
    missingFields: MutableList<MissingFieldInfo>,
    incompleteFields: MutableList<IncompleteFieldInfo>,
    recommendations: MutableList<String>,
    impactAnalysis: MutableMap<String, String>
  ) {
    // 메타데이터 전체 평가
    if (metadata.isEmpty() || metadata.size < 3) {
      incompleteFields.add(
        IncompleteFieldInfo(
          fieldName = "metadata",
          currentValue = "${metadata.size}개 항목",
          issue = "메타데이터가 부족합니다",
          suggestion = "브랜드, 카테고리, 소재, 가격, 타겟 고객 등의 정보를 추가하세요"
        )
      )
      recommendations.add("메타데이터를 풍부하게 입력하면 더 정확한 마케팅 인사이트를 제공받을 수 있습니다")
    }

    // 브랜드 정보 검증
    if (!metadata.containsKey("brand")) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "metadata.brand",
          fieldType = "optional",
          description = "브랜드명",
          example = "나이키, 아디다스, 자체 브랜드",
          impact = "medium"
        )
      )
    }

    // 카테고리 정보 검증
    if (!metadata.containsKey("category")) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "metadata.category",
          fieldType = "optional",
          description = "상품 카테고리",
          example = "여성 의류 > 상의 > 블라우스",
          impact = "medium"
        )
      )
      impactAnalysis["category"] = "카테고리 정보 없이는 타겟 고객 분석과 경쟁 상품 비교가 제한됩니다."
    }

    // 가격 정보 검증
    if (!metadata.containsKey("price") && !metadata.containsKey("originalPrice")) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "metadata.price",
          fieldType = "optional",
          description = "상품 가격 정보",
          example = "{\"price\": 89000, \"originalPrice\": 149000}",
          impact = "medium"
        )
      )
      recommendations.add("가격 정보를 입력하면 가격 포지셔닝 전략을 제공받을 수 있습니다")
    }

    // 리뷰 데이터 검증
    if (!hasReviewData(metadata)) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "metadata.reviews",
          fieldType = "optional",
          description = "리뷰 데이터 (평점, 리뷰 수, 리뷰 내용)",
          example = "{\"rating\": 4.7, \"count\": 342, \"reviews\": [...]}",
          impact = "high"
        )
      )
      impactAnalysis["reviews"] = "리뷰 데이터 없이는 고객 만족도 분석과 리뷰 요약을 제공할 수 없습니다."
      recommendations.add("크롤링한 리뷰 데이터를 메타데이터에 포함하면 고객 만족도 분석을 받을 수 있습니다")
    }
  }

  /**
   * 전체 품질 등급 산정
   *
   * @param missingCount 누락된 필드 개수
   * @param incompleteCount 불완전한 필드 개수
   * @return 품질 등급 (excellent/good/fair/poor)
   */
  private fun calculateOverallQuality(missingCount: Int, incompleteCount: Int): String {
    return when {
      missingCount == 0 && incompleteCount == 0 -> "excellent"
      missingCount <= 2 && incompleteCount <= 2 -> "good"
      missingCount <= 4 || incompleteCount <= 4 -> "fair"
      else -> "poor"
    }
  }

  /**
   * 데이터 품질 점수 계산 (0-100점)
   *
   * ### 감점 기준:
   * - high impact 필드 누락: -20점
   * - medium impact 필드 누락: -10점
   * - low impact 필드 누락: -5점
   * - 불완전한 필드: -5점
   *
   * @param report 데이터 품질 리포트
   * @return 품질 점수 (0-100)
   */
  fun calculateQualityScore(report: DataQualityReport): Int {
    var score = 100

    // 필수 필드 누락 감점
    report.missingFields?.forEach { field ->
      when (field.impact) {
        "high" -> score -= 20
        "medium" -> score -= 10
        "low" -> score -= 5
      }
    }

    // 불완전한 필드 감점
    report.incompleteFields?.forEach {
      score -= 5
    }

    return maxOf(0, score) // 최소 0점
  }

  /**
   * 정제된 분석 옵션을 사용한 데이터 품질 평가
   *
   * ProductAnalysisOptions를 활용하여 더 정확한 데이터 품질을 평가합니다.
   * LLM으로 병합된 데이터를 기반으로 평가하므로 더 정확한 품질 점수를 제공합니다.
   *
   * @param refinedOptions 정제된 분석 옵션 (LLM 병합 후)
   * @param metadata 원본 메타데이터
   * @param imageCount 이미지 개수
   * @return 데이터 품질 평가 리포트
   */
  fun evaluateWithOptions(
    refinedOptions: ProductAnalysisOptions,
    metadata: Map<String, Any>,
    imageCount: Int
  ): DataQualityReport {
    val missingFields = mutableListOf<MissingFieldInfo>()
    val incompleteFields = mutableListOf<IncompleteFieldInfo>()
    val recommendations = mutableListOf<String>()
    val impactAnalysis = mutableMapOf<String, String>()

    // === 1. 제품 기본 정보 평가 ===
    val basicInfo = refinedOptions.productBasicInfo
    if (basicInfo == null) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "productBasicInfo",
          fieldType = "required",
          description = "제품 기본 정보",
          example = "제품명, 카테고리, 브랜드",
          impact = "high"
        )
      )
      impactAnalysis["productBasicInfo"] = "제품 기본 정보가 없으면 카피라이팅 품질이 크게 저하됩니다."
    } else {
      // 제품명 검증
      if (basicInfo.productName.isNullOrBlank()) {
        missingFields.add(
          MissingFieldInfo(
            fieldName = "productName",
            fieldType = "required",
            description = "제품명",
            example = "와이드 코듀로이 팬츠",
            impact = "high"
          )
        )
      }
      // 카테고리 검증
      if (basicInfo.category.isNullOrBlank()) {
        missingFields.add(
          MissingFieldInfo(
            fieldName = "category",
            fieldType = "required",
            description = "제품 카테고리",
            example = "남성 패션 > 바지",
            impact = "medium"
          )
        )
      }
      // 타겟 고객층 검증
      if (basicInfo.targetAudience.isNullOrBlank()) {
        incompleteFields.add(
          IncompleteFieldInfo(
            fieldName = "targetAudience",
            currentValue = "미지정",
            issue = "타겟 고객층이 명확하지 않습니다",
            suggestion = "타겟 고객층을 명시하면 맞춤형 카피라이팅이 가능합니다"
          )
        )
      }
    }

    // === 2. 판매 정보 평가 ===
    val salesInfo = refinedOptions.salesInfo
    if (salesInfo == null) {
      incompleteFields.add(
        IncompleteFieldInfo(
          fieldName = "salesInfo",
          currentValue = "없음",
          issue = "판매 정보가 누락되었습니다",
          suggestion = "판매 채널, 가격 경쟁력 정보를 추가하면 마케팅 인사이트가 향상됩니다"
        )
      )
    }

    // === 3. 고객 피드백 평가 ===
    val feedbackInfo = refinedOptions.customerFeedbackInfo
    if (feedbackInfo == null) {
      incompleteFields.add(
        IncompleteFieldInfo(
          fieldName = "customerFeedbackInfo",
          currentValue = "없음",
          issue = "고객 리뷰 정보가 없습니다",
          suggestion = "리뷰 데이터를 추가하면 고객 관점의 카피라이팅이 가능합니다"
        )
      )
    } else {
      if (feedbackInfo.totalReviews == null || feedbackInfo.totalReviews == 0) {
        recommendations.add("리뷰 데이터를 수집하여 고객 만족도 분석을 개선하세요")
      }
    }

    // === 4. 이미지 평가 ===
    if (imageCount == 0) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "images",
          fieldType = "required",
          description = "제품 이미지",
          example = "최소 3장 이상의 고품질 이미지",
          impact = "high"
        )
      )
      impactAnalysis["images"] = "이미지 없이는 시각적 분석이 불가능합니다."
    } else if (imageCount < 3) {
      incompleteFields.add(
        IncompleteFieldInfo(
          fieldName = "images",
          currentValue = "${imageCount}장",
          issue = "이미지가 부족합니다",
          suggestion = "다양한 각도의 이미지를 3장 이상 업로드하세요"
        )
      )
    }

    // === 5. 분석 초점 평가 ===
    val analysisFocus = refinedOptions.analysisFocus
    if (analysisFocus == null) {
      recommendations.add("분석 목표와 비즈니스 목표를 명시하면 더 전략적인 카피라이팅이 가능합니다")
    }

    // === 6. 전체 품질 등급 산정 ===
    val overallQuality = calculateOverallQuality(missingFields.size, incompleteFields.size)

    // === 7. 최종 권장사항 ===
    if (recommendations.isEmpty() && missingFields.isEmpty() && incompleteFields.isEmpty()) {
      recommendations.add("데이터 품질이 매우 우수합니다. 최적의 카피라이팅 결과를 기대할 수 있습니다.")
    }

    return DataQualityReport(
      overallQuality = overallQuality,
      missingFields = missingFields.takeIf { it.isNotEmpty() },
      incompleteFields = incompleteFields.takeIf { it.isNotEmpty() },
      recommendations = recommendations.takeIf { it.isNotEmpty() },
      impactAnalysis = impactAnalysis.takeIf { it.isNotEmpty() }
    )
  }

  /**
   * 정제된 분석 옵션의 데이터 품질 점수 계산
   *
   * ProductAnalysisOptions 기반으로 더 정확한 점수를 계산합니다.
   *
   * @param refinedOptions 정제된 분석 옵션
   * @return 품질 점수 (0-100)
   */
  fun evaluateDataQuality(refinedOptions: ProductAnalysisOptions): Int {
    var score = 100

    // 제품 기본 정보 평가 (40점)
    val basicInfo = refinedOptions.productBasicInfo
    if (basicInfo == null) {
      score -= 40
    } else {
      if (basicInfo.productName.isNullOrBlank()) score -= 15
      if (basicInfo.category.isNullOrBlank()) score -= 10
      if (basicInfo.brand.isNullOrBlank()) score -= 5
      if (basicInfo.targetAudience.isNullOrBlank()) score -= 5
      if (basicInfo.price == null) score -= 5
    }

    // 판매 정보 평가 (20점)
    val salesInfo = refinedOptions.salesInfo
    if (salesInfo == null) {
      score -= 20
    } else {
      if (salesInfo.salesChannels.isNullOrEmpty()) score -= 10
      if (salesInfo.promotionPlan.isNullOrBlank()) score -= 5
      if (salesInfo.competitorPrices.isNullOrEmpty()) score -= 5
    }

    // 고객 피드백 평가 (20점)
    val feedbackInfo = refinedOptions.customerFeedbackInfo
    if (feedbackInfo == null) {
      score -= 20
    } else {
      if (feedbackInfo.averageRating == null) score -= 5
      if (feedbackInfo.totalReviews == null || feedbackInfo.totalReviews == 0) score -= 5
      if (feedbackInfo.positiveReviews.isNullOrEmpty()) score -= 5
      if (feedbackInfo.negativeReviews.isNullOrEmpty()) score -= 5
    }

    // 분석 초점 평가 (20점)
    val analysisFocus = refinedOptions.analysisFocus
    if (analysisFocus == null) {
      score -= 20
    } else {
      if (analysisFocus.businessGoals.isNullOrEmpty()) score -= 10
      if (analysisFocus.challenges.isNullOrBlank()) score -= 5
      if (analysisFocus.targetMetrics.isNullOrBlank()) score -= 5
    }

    return maxOf(0, score) // 최소 0점
  }

  /**
   * 리뷰 데이터 존재 여부 확인
   *
   * @param metadata 메타데이터 Map
   * @return 리뷰 데이터 존재 여부
   */
  fun hasReviewData(metadata: Map<String, Any>): Boolean {
    return metadata.containsKey("reviews") ||
        metadata.containsKey("rating") ||
        metadata.containsKey("reviewCount")
  }

  /**
   * 이미지 총 크기 계산 (안전한 처리)
   *
   * @param images 이미지 파일 리스트
   * @return 총 크기 (bytes)
   */
  fun calculateTotalImageSize(images: MutableList<MultipartFile>?): Long {
    return try {
      images?.sumOf { it.size } ?: 0L
    } catch (e: Exception) {
      kLogger.debug(e) { "이미지 크기 계산 중 오류" }
      0L
    }
  }
}
