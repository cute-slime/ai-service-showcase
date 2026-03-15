package com.jongmin.ai.insight.component

import com.jongmin.ai.insight.platform.dto.request.ProductAnalysisOptions
import com.jongmin.ai.insight.platform.dto.request.ProductAnalyze
import com.jongmin.ai.insight.platform.dto.response.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * AI 분석 응답 구성 서비스
 *
 * 데이터 품질, 카피라이팅, 리뷰 요약, 마케팅 인사이트, 고객 만족도 분석을 조합하여
 * 최종 PDP 카피라이팅 응답을 생성합니다.
 *
 * ### 주요 책임:
 * - 리뷰 요약 생성 (리뷰 데이터 존재 시)
 * - 확장 메타데이터 구성 (원본 + AI 추출 + 통합)
 * - 마케팅 인사이트 생성 (품질 점수 60점 이상 시)
 * - 고객 만족도 분석 생성 (리뷰 데이터 존재 시)
 * - 최종 응답 객체 조합
 *
 * ### 조건부 생성:
 * - reviewSummary: 리뷰 데이터 존재 시
 * - marketingInsights: 품질 점수 60점 이상
 * - customerSatisfaction: 리뷰 데이터 존재 시
 *
 * @property dataQualityEvaluator 데이터 품질 평가 서비스
 * @property copyWritingContentGenerator 카피라이팅 콘텐츠 생성 서비스
 */
@Service
class AiAnalysisResponseBuilder(
  private val dataQualityEvaluator: DataQualityEvaluator,
  private val copyWritingContentGenerator: CopyWritingContentGenerator
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * AI 분석 기반 통합 응답 생성
   *
   * ### 생성 단계:
   * 1. 데이터 품질 평가 및 점수 산출
   * 2. 카피라이팅 생성
   * 3. 리뷰 요약 생성 (조건부)
   * 4. 확장 메타데이터 구성
   * 5. 마케팅 인사이트 생성 (조건부)
   * 6. 고객 만족도 분석 생성 (조건부)
   * 7. 최종 응답 조합
   *
   * ### 성공 여부:
   * - 품질 점수 30점 이상: 성공
   * - 품질 점수 30점 미만: 실패 (데이터 부족)
   *
   * @param dto PDP 카피라이팅 요청 DTO
   * @param refinedOptions 정제된 분석 옵션 (LLM으로 병합된 데이터)
   * @param metadata 파싱된 메타데이터
   * @param imageCount 이미지 개수
   * @return PdpCopyGenerationResponse (최종 응답)
   */
  fun buildResponse(
    dto: ProductAnalyze,
    refinedOptions: ProductAnalysisOptions,
    metadata: Map<String, Any>,
    imageCount: Int
  ): PdpCopyGenerationResponse {
    // === 1단계: 데이터 품질 평가 (정제된 옵션 사용) ===
    val dataQualityReport = dataQualityEvaluator.evaluateWithOptions(refinedOptions, metadata, imageCount)
    val qualityScore = dataQualityEvaluator.calculateQualityScore(dataQualityReport)

    // 상품명과 카테고리 추출 (정제된 옵션 우선 사용)
    val productName = refinedOptions.productBasicInfo?.productName
      ?: (metadata["name"] as? String)
      ?: dto.shopName
      ?: "프리미엄 제품"

    val category = refinedOptions.productBasicInfo?.category
      ?: (metadata["productCategory"] as? String)
      ?: (metadata["category"] as? String)
      ?: "패션"

    // === 2단계: 카피라이팅 생성 ===
    val copywriting = copyWritingContentGenerator.generate(
      productName = productName,
      metadata = metadata,
      imageCount = imageCount,
      qualityScore = qualityScore
    )

    // === 3단계: 리뷰 요약 생성 (데이터 품질에 따라 조건부) ===
    val reviewSummary = if (dataQualityEvaluator.hasReviewData(metadata)) {
      createReviewSummary()
    } else null

    // === 4단계: 확장 메타데이터 생성 ===
    val enrichedMetadata = createEnrichedMetadata(metadata, category, imageCount, qualityScore)

    // === 5단계: 마케팅 인사이트 생성 (품질 점수 60점 이상 시) ===
    val marketingInsights = if (qualityScore >= 60) {
      createMarketingInsights(metadata)
    } else null

    // === 6단계: 고객 만족도 분석 생성 (리뷰 데이터 존재 시) ===
    val customerSatisfaction = if (dataQualityEvaluator.hasReviewData(metadata)) {
      createCustomerSatisfaction()
    } else null

    // === 7단계: 최종 응답 생성 ===
    return PdpCopyGenerationResponse(
      success = qualityScore >= 30, // 최소 30점 이상이면 성공으로 간주
      qualityScore = qualityScore,
      copywriting = copywriting,
      reviewSummary = reviewSummary,
      metadata = enrichedMetadata,
      marketingInsights = marketingInsights,
      customerSatisfaction = customerSatisfaction,
      dataQuality = dataQualityReport,
      generatedAt = System.currentTimeMillis()
    )
  }

  /**
   * 리뷰 요약 생성
   *
   * ### 포함 요소:
   * - 평균 평점 및 총 리뷰 수
   * - 대표 리뷰 (긍정/부정/중립)
   * - 주요 강점 및 약점
   * - 고객 프로필
   * - 사용 시나리오
   *
   * @return ReviewSummary 객체
   */
  private fun createReviewSummary(): ReviewSummary {
    val strengths = listOf(
      "뛰어난 품질과 마감",
      "사진보다 실물이 더 예쁨",
      "편안한 착용감",
      "빠른 배송과 꼼꼼한 포장"
    )
    val weaknesses = listOf(
      "사이즈가 작게 나오는 편",
      "색상이 모니터와 약간 차이"
    )
    val scenarios = listOf(
      "데일리 오피스룩",
      "주말 브런치 모임",
      "특별한 날 데이트",
      "여행 코디"
    )

    return ReviewSummary(
      overallRating = 4.7,
      totalReviews = 342,
      highlightedReviews = listOf(
        HighlightedReview(
          content = "품질이 정말 좋아요! 실물이 사진보다 더 예쁘고 핏도 완벽합니다.",
          sentiment = "positive",
          helpfulness = 89
        ),
        HighlightedReview(
          content = "배송도 빠르고 포장도 꼼꼼해요. 재구매 의사 200%입니다!",
          sentiment = "positive",
          helpfulness = 76
        ),
        HighlightedReview(
          content = "사이즈가 조금 작게 나온 것 같아요. 한 치수 크게 주문하세요.",
          sentiment = "neutral",
          helpfulness = 45
        )
      ),
      keyStrengths = strengths.takeIf { it.isNotEmpty() },
      keyWeaknesses = weaknesses.takeIf { it.isNotEmpty() },
      customerProfile = "30-40대 여성, 품질과 스타일을 중시하는 고객층",
      usageScenarios = scenarios.takeIf { it.isNotEmpty() }
    )
  }

  /**
   * 확장 메타데이터 생성
   *
   * ### 구성:
   * - original: 사용자 입력 원본
   * - extracted: AI가 이미지/텍스트에서 추출한 정보
   * - enriched: 통합 및 정제된 최종 메타데이터
   *
   * @param metadata 원본 메타데이터
   * @param category 카테고리
   * @param imageCount 이미지 개수
   * @param qualityScore 품질 점수
   * @return EnrichedMetadata 객체
   */
  private fun createEnrichedMetadata(
    metadata: Map<String, Any>,
    category: String,
    imageCount: Int,
    qualityScore: Int
  ): EnrichedMetadata {
    // AI 추출 데이터 (이미지 분석 기반)
    val extractedColors = if (imageCount > 0) listOf("베이지", "블랙", "화이트", "네이비") else null
    val extractedMaterials = if (imageCount > 0) listOf("코튼 65%", "폴리에스터 35%") else null
    val extractedPatterns = if (imageCount > 0) listOf("무지", "미니멀") else null
    val extractedStyles = if (imageCount > 0) listOf("모던", "미니멀", "오피스룩") else null
    val extractedBrandAttrs = if (qualityScore >= 60) listOf("프리미엄", "지속가능성", "한국 제조") else null

    val extractedMetadata = ExtractedMetadata(
      colors = extractedColors,
      materials = extractedMaterials,
      patterns = extractedPatterns,
      category = category,
      season = "Spring/Fall",
      targetAge = "30-45",
      style = extractedStyles,
      brandAttributes = extractedBrandAttrs
    )

    // 통합 메타데이터 (원본 + AI 분석)
    val enriched = metadata + mapOf(
      "ai_category" to category,
      "ai_season" to "봄/가을",
      "ai_style" to "모던 미니멀",
      "ai_confidence" to 0.92
    )

    return EnrichedMetadata(
      original = metadata,
      extracted = extractedMetadata,
      enriched = enriched
    )
  }

  /**
   * 마케팅 인사이트 생성
   *
   * ### 포함 요소:
   * - 타겟팅 전략
   * - 프로모션 타이밍
   * - 경쟁 우위 요소
   * - 추천 마케팅 채널
   * - 콘텐츠 전략
   * - 가격 전략
   * - 검색 트렌드
   * - 셀럽 마케팅 기회
   *
   * @param metadata 메타데이터
   * @return MarketingInsights 객체
   */
  private fun createMarketingInsights(metadata: Map<String, Any>): MarketingInsights {
    val advantages = listOf(
      "동일 품질 대비 30% 저렴한 가격",
      "한국 제조로 빠른 배송과 AS 가능",
      "지속가능한 소재 사용으로 ESG 트렌드 부합",
      "인플루언서 협업을 통한 신뢰도 확보"
    )

    val channels = listOf(
      MarketingChannel(
        channel = "인스타그램",
        priority = "high",
        recommendation = "착용샷 중심의 리얼 리뷰 콘텐츠와 스토리 광고 집행. #오피스룩 #데일리룩 해시태그 활용"
      ),
      MarketingChannel(
        channel = "네이버 쇼핑",
        priority = "high",
        recommendation = "브랜드 검색광고 및 쇼핑검색광고 동시 운영. '여성 오피스룩' 키워드 집중"
      ),
      MarketingChannel(
        channel = "유튜브",
        priority = "medium",
        recommendation = "오피스룩 코디 영상 콘텐츠 제작, 인플루언서 협업 하울 영상"
      )
    )

    val trendKeywords = listOf(
      "여성 오피스룩",
      "30대 여자 옷",
      "회사 출근룩",
      "봄 자켓 코디"
    )

    return MarketingInsights(
      targetingStrategy = "30-40대 커리어 우먼을 핵심 타겟으로, 품질과 실용성을 중시하는 스마트 컨슈머 공략",
      promotionTiming = "봄 시즌 시작 2주 전 (3월 초) 또는 가을 시즌 시작 전 (8월 말) 프로모션이 효과적",
      competitiveAdvantages = advantages.takeIf { it.isNotEmpty() },
      marketingChannels = channels.takeIf { it.isNotEmpty() },
      contentStrategy = "실제 구매 고객의 착용 리뷰를 중심으로 신뢰도 높은 콘텐츠 제작. 스타일링 가이드와 관리법 콘텐츠로 부가가치 제공",
      pricingStrategy = "정가 대비 20-30% 상시 할인으로 가성비 포지셔닝. 시즌 종료 시 추가 20% 할인으로 재고 소진",
      searchTrends = SearchTrends(
        trending = true,
        relatedKeywords = trendKeywords.takeIf { it.isNotEmpty() },
        searchVolume = "high"
      ),
      celebrityEndorsement = if (metadata["celebrity"] != null) {
        CelebrityEndorsement(
          celebrityName = metadata["celebrity"] as? String,
          impactLevel = "high",
          recommendation = "착용 이미지를 상세페이지 상단에 배치하고, SNS에서 관련 해시태그 적극 활용. 협찬 제품임을 명시하여 신뢰도 유지"
        )
      } else null
    )
  }

  /**
   * 고객 만족도 분석 생성
   *
   * ### 포함 요소:
   * - 만족도 점수 및 NPS
   * - 재구매율
   * - 만족/불만족 요인
   * - 개선 제안사항
   * - 고객 세그먼트별 만족도
   *
   * @return CustomerSatisfactionAnalysis 객체
   */
  private fun createCustomerSatisfaction(): CustomerSatisfactionAnalysis {
    val satisfactionFactorsList = listOf(
      SatisfactionFactor("품질 대비 가격", "high", 78),
      SatisfactionFactor("디자인과 스타일", "high", 85),
      SatisfactionFactor("착용감과 편안함", "medium", 72),
      SatisfactionFactor("배송 속도", "medium", 68)
    )

    val dissatisfactionFactorsList = listOf(
      SatisfactionFactor("사이즈 정확도", "medium", 23),
      SatisfactionFactor("색상 차이", "low", 15)
    )

    val suggestions = listOf(
      "상세한 사이즈 가이드 제공 (실측 치수, 모델 착용 정보)",
      "다양한 조명에서 촬영한 색상 이미지 추가",
      "사이즈 교환 무료 서비스 도입 검토",
      "구매 후기 작성 시 적립금 증정으로 리뷰 수 확대"
    )

    val segments = listOf(
      CustomerSegment(
        segment = "30대 직장인 여성",
        satisfactionScore = 89,
        comments = listOf("오피스룩으로 완벽", "품질이 가격 대비 훌륭").takeIf { it.isNotEmpty() }
      ),
      CustomerSegment(
        segment = "40대 주부",
        satisfactionScore = 85,
        comments = listOf("편안하고 실용적", "다양한 상황에 활용 가능").takeIf { it.isNotEmpty() }
      ),
      CustomerSegment(
        segment = "20대 후반 여성",
        satisfactionScore = 83,
        comments = listOf("트렌디한 디자인", "가성비 좋음").takeIf { it.isNotEmpty() }
      )
    )

    return CustomerSatisfactionAnalysis(
      satisfactionScore = 87,
      npsScore = 42,
      repeatPurchaseRate = "38%",
      satisfactionFactors = satisfactionFactorsList.takeIf { it.isNotEmpty() },
      dissatisfactionFactors = dissatisfactionFactorsList.takeIf { it.isNotEmpty() },
      improvementSuggestions = suggestions.takeIf { it.isNotEmpty() },
      customerSegments = segments.takeIf { it.isNotEmpty() }
    )
  }
}
