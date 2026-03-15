package com.jongmin.ai.product_agent.platform.component

import com.jongmin.ai.product_agent.EventEmphasis
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingData
import com.jongmin.ai.product_agent.platform.dto.response.*
import org.springframework.stereotype.Component

/**
 * 카피라이팅 결과 빌더
 *
 * 카피라이팅 생성 결과를 최종 응답 형식으로 변환하는 컴포넌트입니다.
 *
 * ### 주요 책임:
 * - 데이터 품질 평가 (evaluateDataQuality)
 * - 신뢰도 계산 (calculateConfidence)
 * - 최종 응답 생성 (buildFinalResponse)
 * - 메타데이터 생성 (buildEnrichedMetadata)
 * - 마케팅 인사이트 변환 (convertToMarketingInsights)
 */
@Component
class CopywritingResultBuilder {

  /**
   * 데이터 품질 평가
   *
   * 입력 데이터의 완성도를 평가하여 품질 리포트를 생성합니다.
   *
   * @param data 카피라이팅 요청 데이터
   * @return 데이터 품질 리포트
   */
  fun evaluateDataQuality(data: CopywritingData): DataQualityReport {
    val missingFields = mutableListOf<MissingFieldInfo>()
    val recommendations = mutableListOf<String>()
    var qualityScore = 100

    val basicInfo = data.productBasicInfo

    // 필수 필드 검사
    if (basicInfo?.brand.isNullOrBlank()) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "brand",
          fieldType = FieldType.optional,
          description = "브랜드명",
          example = "나이키, 삼성, 애플",
          impact = PriorityLevel.medium
        )
      )
      qualityScore -= 10
      recommendations.add("브랜드명을 입력하면 브랜드 신뢰도를 활용한 카피를 생성할 수 있습니다.")
    }

    if (basicInfo?.targetAudience.isNullOrBlank()) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "targetAudience",
          fieldType = FieldType.optional,
          description = "타겟 고객층",
          example = "20-30대 여성, MZ세대, 40대 직장인",
          impact = PriorityLevel.high
        )
      )
      qualityScore -= 15
      recommendations.add("타겟 고객층을 지정하면 더 정확한 타겟팅 카피를 생성할 수 있습니다.")
    }

    if (basicInfo?.priceRange.isNullOrBlank()) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "priceRange",
          fieldType = FieldType.optional,
          description = "가격대",
          example = "89,000원, 89,000원 (정가 149,000원)",
          impact = PriorityLevel.medium
        )
      )
      qualityScore -= 10
      recommendations.add("가격 정보를 입력하면 가성비/프리미엄 전략에 맞는 카피를 생성할 수 있습니다.")
    }

    // 고객 피드백 검사
    if (data.customerFeedbackInfo == null) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "customerFeedbackInfo",
          fieldType = FieldType.optional,
          description = "고객 피드백 정보",
          example = "평점 4.5, 리뷰 1,234건, 재구매율 85%",
          impact = PriorityLevel.high
        )
      )
      qualityScore -= 20
      recommendations.add("고객 리뷰/평점 정보를 추가하면 사회적 증명을 활용한 설득력 있는 카피를 생성할 수 있습니다.")
    }

    // 이벤트 정보 검사 (이벤트 강조 설정이 있는데 이벤트 정보가 없는 경우)
    val eventEmphasis = data.stylePreferences?.eventEmphasis
    if (eventEmphasis != null && eventEmphasis != EventEmphasis.NONE && data.eventInfo == null) {
      missingFields.add(
        MissingFieldInfo(
          fieldName = "eventInfo",
          fieldType = FieldType.optional,
          description = "이벤트 정보",
          example = "블랙프라이데이 30% 할인, 11/29 단 하루",
          impact = PriorityLevel.medium
        )
      )
      qualityScore -= 10
      recommendations.add("이벤트 정보를 추가하면 긴급성과 희소성을 활용한 CTA를 생성할 수 있습니다.")
    }

    // 품질 레벨 결정
    val overallQuality = when {
      qualityScore >= 90 -> DataQualityLevel.excellent
      qualityScore >= 70 -> DataQualityLevel.good
      qualityScore >= 50 -> DataQualityLevel.fair
      else -> DataQualityLevel.poor
    }

    return DataQualityReport(
      overallQuality = overallQuality,
      missingFields = missingFields.ifEmpty { null },
      recommendations = recommendations.ifEmpty { null }
    )
  }

  /**
   * 신뢰도 계산
   *
   * 데이터 품질과 생성된 토큰 수를 기반으로 신뢰도를 계산합니다.
   *
   * @param dataQuality 데이터 품질 리포트
   * @param tokenCount 생성된 토큰 수
   * @return 신뢰도 (0.0 - 1.0)
   */
  fun calculateConfidence(dataQuality: DataQualityReport, tokenCount: Int): Double {
    // 기본 신뢰도
    var confidence = when (dataQuality.overallQuality) {
      DataQualityLevel.excellent -> 0.95
      DataQualityLevel.good -> 0.85
      DataQualityLevel.fair -> 0.70
      DataQualityLevel.poor -> 0.55
    }

    // 토큰 수에 따른 보정 (적정 범위: 100-500 토큰)
    if (tokenCount < 50) {
      confidence -= 0.1 // 너무 짧은 응답
    } else if (tokenCount > 1000) {
      confidence -= 0.05 // 너무 긴 응답
    }

    return confidence.coerceIn(0.3, 0.98)
  }

  /**
   * 최종 응답 생성 (카피라이팅 + 마케팅 인사이트 병합)
   *
   * 1차 LLM에서 생성된 카피라이팅과 2차 LLM에서 생성된 마케팅 인사이트를
   * 병합하여 최종 PdpCopyGenerationResponse를 생성합니다.
   *
   * @param copywriting 파싱된 카피라이팅 응답
   * @param marketingInsights 파싱된 마케팅 인사이트 응답
   * @param copywritingData 원본 요청 데이터
   * @param dataQuality 데이터 품질 리포트
   * @param copywritingTokenCount 카피라이팅 생성 토큰 수
   * @param marketingTokenCount 마케팅 인사이트 생성 토큰 수
   * @return 최종 구조화된 응답
   */
  fun buildFinalResponse(
    copywriting: AiCopyOnlyResponse,
    marketingInsights: AiMarketingInsightsOnlyResponse,
    copywritingData: CopywritingData,
    dataQuality: DataQualityReport,
    copywritingTokenCount: Int,
    marketingTokenCount: Int
  ): PdpCopyGenerationResponse {
    // 데이터 품질 점수 계산
    val qualityScore = when (dataQuality.overallQuality) {
      DataQualityLevel.excellent -> 95
      DataQualityLevel.good -> 80
      DataQualityLevel.fair -> 60
      DataQualityLevel.poor -> 40
    }

    // ProductCopywriting 생성
    val productCopywriting = ProductCopywriting(
      mainCopy = copywriting.mainCopy,
      subCopy = copywriting.subCopy
    )

    // 리뷰 요약 생성 (입력 데이터가 있는 경우)
    val reviewSummary = copywritingData.customerFeedbackInfo?.let { feedback ->
      ReviewSummary(
        overallRating = feedback.averageRating,
        totalReviews = feedback.totalReviews,
        keyStrengths = feedback.positiveReviews,
        keyWeaknesses = feedback.negativeReviews,
        customerProfile = copywritingData.productBasicInfo?.targetAudience
      )
    }

    // 메타데이터 생성
    val metadata = buildEnrichedMetadataFromCopyOnly(copywritingData, copywriting)

    // 마케팅 인사이트 변환 (2차 LLM 결과에서)
    val convertedMarketingInsights = convertToMarketingInsights(marketingInsights)

    // 고객 만족도 분석 (피드백 데이터가 있는 경우)
    val customerSatisfaction = copywritingData.customerFeedbackInfo?.let { feedback ->
      val satisfactionScore = ((feedback.averageRating ?: 0.0) * 20).toInt().coerceIn(0, 100)
      CustomerSatisfactionAnalysis(
        satisfactionScore = satisfactionScore,
        repeatPurchaseRate = feedback.repurchaseRate?.let { "${it}%" }
      )
    }

    return PdpCopyGenerationResponse(
      success = true,
      qualityScore = qualityScore,
      copywriting = productCopywriting,
      reviewSummary = reviewSummary,
      metadata = metadata,
      marketingInsights = convertedMarketingInsights,
      customerSatisfaction = customerSatisfaction,
      dataQuality = dataQuality,
      generatedAt = System.currentTimeMillis()
    )
  }

  /**
   * 마케팅 인사이트 없이 최종 응답 생성 (2차 LLM 실패 시 폴백)
   *
   * 2차 LLM 호출이 실패한 경우, 카피라이팅 결과만으로 응답을 생성합니다.
   *
   * @param copywriting 파싱된 카피라이팅 응답
   * @param copywritingData 원본 요청 데이터
   * @param dataQuality 데이터 품질 리포트
   * @param copywritingTokenCount 카피라이팅 생성 토큰 수
   * @return 마케팅 인사이트 없는 응답
   */
  fun buildFinalResponseWithoutMarketingInsights(
    copywriting: AiCopyOnlyResponse,
    copywritingData: CopywritingData,
    dataQuality: DataQualityReport,
    @Suppress("UNUSED_PARAMETER") copywritingTokenCount: Int
  ): PdpCopyGenerationResponse {
    val qualityScore = when (dataQuality.overallQuality) {
      DataQualityLevel.excellent -> 90 // 마케팅 인사이트 없으므로 약간 감점
      DataQualityLevel.good -> 75
      DataQualityLevel.fair -> 55
      DataQualityLevel.poor -> 35
    }

    val productCopywriting = ProductCopywriting(
      mainCopy = copywriting.mainCopy,
      subCopy = copywriting.subCopy
    )

    val reviewSummary = copywritingData.customerFeedbackInfo?.let { feedback ->
      ReviewSummary(
        overallRating = feedback.averageRating,
        totalReviews = feedback.totalReviews,
        keyStrengths = feedback.positiveReviews,
        keyWeaknesses = feedback.negativeReviews,
        customerProfile = copywritingData.productBasicInfo?.targetAudience
      )
    }

    val metadata = buildEnrichedMetadataFromCopyOnly(copywritingData, copywriting)

    val customerSatisfaction = copywritingData.customerFeedbackInfo?.let { feedback ->
      val satisfactionScore = ((feedback.averageRating ?: 0.0) * 20).toInt().coerceIn(0, 100)
      CustomerSatisfactionAnalysis(
        satisfactionScore = satisfactionScore,
        repeatPurchaseRate = feedback.repurchaseRate?.let { "${it}%" }
      )
    }

    // 데이터 품질 리포트에 마케팅 인사이트 실패 정보 추가
    val updatedDataQuality = dataQuality.copy(
      recommendations = (dataQuality.recommendations ?: emptyList()) +
          "마케팅 인사이트 생성에 실패했습니다. 다시 시도하시면 더 풍부한 분석을 받을 수 있습니다."
    )

    return PdpCopyGenerationResponse(
      success = true,
      qualityScore = qualityScore,
      copywriting = productCopywriting,
      reviewSummary = reviewSummary,
      metadata = metadata,
      marketingInsights = null, // 마케팅 인사이트 없음
      customerSatisfaction = customerSatisfaction,
      dataQuality = updatedDataQuality,
      generatedAt = System.currentTimeMillis()
    )
  }

  /**
   * AiCopyOnlyResponse 기반 확장 메타데이터 생성
   *
   * @param copywritingData 원본 요청 데이터
   * @param copyResponse 카피라이팅 응답
   * @return 확장 메타데이터
   */
  private fun buildEnrichedMetadataFromCopyOnly(
    copywritingData: CopywritingData,
    @Suppress("UNUSED_PARAMETER") copyResponse: AiCopyOnlyResponse
  ): EnrichedMetadata {
    val basicInfo = copywritingData.productBasicInfo

    // 원본 메타데이터
    val original = mutableMapOf<String, Any?>()
    original["productName"] = basicInfo?.productName
    original["category"] = basicInfo?.category
    original["brand"] = basicInfo?.brand
    original["targetAudience"] = basicInfo?.targetAudience
    original["priceRange"] = basicInfo?.priceRange
    original["specifications"] = basicInfo?.specifications
    original["copyStyle"] = copywritingData.copyStyle?.name

    // AI 추출 메타데이터 (키워드/해시태그 기반 추론)
    val extracted = ExtractedMetadata(
      category = basicInfo?.category,
      targetAge = basicInfo?.targetAudience,
      season = copywritingData.stylePreferences?.seasonalContext?.name?.lowercase()
    )

    // 통합 메타데이터
    val enriched = mutableMapOf<String, Any?>()
    enriched.putAll(original)

    return EnrichedMetadata(
      original = original,
      extracted = extracted,
      enriched = enriched
    )
  }

  /**
   * AiMarketingInsightsOnlyResponse를 MarketingInsights로 변환
   *
   * 2차 LLM에서 생성된 상세한 마케팅 인사이트를
   * 프론트엔드가 기대하는 MarketingInsights 형식으로 변환합니다.
   *
   * @param aiInsights AI가 생성한 마케팅 인사이트
   * @return 변환된 MarketingInsights
   */
  private fun convertToMarketingInsights(aiInsights: AiMarketingInsightsOnlyResponse): MarketingInsights {
    // 마케팅 채널 변환
    val marketingChannels = aiInsights.recommendedChannels?.map { channel ->
      MarketingChannel(
        channel = channel.channelName,
        priority = when (channel.priority.lowercase()) {
          "high" -> PriorityLevel.high
          "medium" -> PriorityLevel.medium
          else -> PriorityLevel.low
        },
        recommendation = channel.actionPlan
      )
    }

    // 검색 트렌드 변환
    val searchTrends = aiInsights.searchTrendAnalysis?.let { trends ->
      SearchTrends(
        trending = trends.isTrending,
        relatedKeywords = trends.relatedTrendingKeywords,
        searchVolume = when (trends.searchVolumeLevel.lowercase()) {
          "high" -> PriorityLevel.high
          "medium" -> PriorityLevel.medium
          else -> PriorityLevel.low
        }
      )
    }

    return MarketingInsights(
      targetingStrategy = aiInsights.targetingStrategy,
      promotionTiming = aiInsights.promotionTiming,
      competitiveAdvantages = aiInsights.competitiveAdvantages,
      marketingChannels = marketingChannels,
      contentStrategy = aiInsights.contentStrategy,
      pricingStrategy = aiInsights.pricingStrategy,
      searchTrends = searchTrends
    )
  }
}
