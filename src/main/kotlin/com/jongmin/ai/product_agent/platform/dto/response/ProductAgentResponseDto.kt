package com.jongmin.ai.product_agent.platform.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * PDP 카피라이팅 AI 분석 통합 응답 DTO
 *
 * VLM과 LLM을 통한 상품 이미지 및 메타데이터 분석 결과를 포함합니다.
 * 프론트엔드의 PdpCopyGenerationResponse 타입과 매칭됩니다.
 *
 * @property success 분석 성공 여부
 * @property qualityScore 데이터 품질 점수 (0-100)
 * @property copywriting 상품 카피라이팅 정보
 * @property reviewSummary 리뷰 요약 및 인사이트 (데이터 부족 시 null)
 * @property metadata 확장된 메타데이터 (사용자 입력 + AI 분석)
 * @property marketingInsights 마케팅 인사이트 분석 (데이터 부족 시 null)
 * @property customerSatisfaction 고객 만족도 분석 (데이터 부족 시 null)
 * @property dataQuality 데이터 품질 평가 및 개선 가이드
 * @property generatedAt 생성 시간 (timestamp)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PdpCopyGenerationResponse(
  val success: Boolean,
  val qualityScore: Int,
  val copywriting: ProductCopywriting,
  val reviewSummary: ReviewSummary? = null,
  val metadata: EnrichedMetadata,
  val marketingInsights: MarketingInsights? = null,
  val customerSatisfaction: CustomerSatisfactionAnalysis? = null,
  val dataQuality: DataQualityReport,
  val generatedAt: Long = System.currentTimeMillis()
)

/**
 * 상품 카피라이팅 정보
 *
 * commerce_pdp_ai_copywriting_guide.md 가이드라인을 준수하여 생성된 카피입니다.
 *
 * @property mainCopy 메인 카피 (Hook + 핵심 베네핏)
 * @property subCopy 서브 카피 (차별화 포인트 + 신뢰 요소)
 * @property eventCopy 이벤트 카피 (긴급성/희소성 강조)
 * @property seoTitle SEO 최적화 제목
 * @property seoDescription SEO 최적화 설명
 * @property keywords 핵심 키워드 목록
 * @property hashtags 추천 해시태그 목록
 * @property tone 카피 톤앤매너
 * @property style 적용된 스타일 (detailed/concise/storytelling)
 * @property confidence 카피 생성 신뢰도 (0.0 - 1.0)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductCopywriting(
  val mainCopy: String,
  val subCopy: String? = null,
//  val eventCopy: String? = null,
//  val seoTitle: String,
//  val seoDescription: String,
//  val keywords: List<String>? = null,
//  val hashtags: List<String>? = null,
//  val tone: String? = null,
//  val style: CopywritingOutputStyle? = null,
//  val confidence: Double = 0.8
)

/**
 * 카피라이팅 출력 스타일 Enum
 */
enum class CopywritingOutputStyle {
  detailed,
  concise,
  storytelling
}

/**
 * 리뷰 요약 및 인사이트
 *
 * @property overallRating 평균 평점
 * @property totalReviews 총 리뷰 수
 * @property highlightedReviews 대표 리뷰 (긍정/부정)
 * @property keyStrengths 주요 강점 (자주 언급되는 장점)
 * @property keyWeaknesses 개선 필요 사항 (자주 언급되는 단점)
 * @property customerProfile 주요 구매 고객층 프로필
 * @property usageScenarios 주요 사용 시나리오
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReviewSummary(
  val overallRating: Double? = null,
  val totalReviews: Int? = null,
  val highlightedReviews: List<HighlightedReview>? = null,
  val keyStrengths: List<String>? = null,
  val keyWeaknesses: List<String>? = null,
  val customerProfile: String? = null,
  val usageScenarios: List<String>? = null
)

/**
 * 대표 리뷰
 *
 * @property content 리뷰 내용 (요약)
 * @property sentiment 감정 분석 (positive/negative/neutral)
 * @property helpfulness 도움됨 점수
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HighlightedReview(
  val content: String,
  val sentiment: ReviewSentiment,
  val helpfulness: Int? = null
)

/**
 * 리뷰 감정 분석 Enum
 */
enum class ReviewSentiment {
  positive,
  negative,
  neutral
}

/**
 * 확장된 메타데이터 (사용자 입력 + AI 추출)
 *
 * @property original 사용자가 입력한 원본 메타데이터
 * @property extracted AI가 이미지/텍스트에서 추출한 추가 정보
 * @property enriched 통합 및 정제된 최종 메타데이터
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class EnrichedMetadata(
  val original: Map<String, Any?>,
  val extracted: ExtractedMetadata,
  val enriched: Map<String, Any?>
)

/**
 * AI가 추출한 메타데이터
 *
 * @property colors 감지된 색상 정보
 * @property materials 감지된 소재 정보
 * @property patterns 감지된 패턴/디자인
 * @property category 자동 분류된 카테고리
 * @property season 추천 시즌
 * @property targetAge 타겟 연령대
 * @property style 스타일 분류
 * @property brandAttributes 브랜드 속성
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtractedMetadata(
  val colors: List<String>? = null,
  val materials: List<String>? = null,
  val patterns: List<String>? = null,
  val category: String? = null,
  val season: String? = null,
  val targetAge: String? = null,
  val style: List<String>? = null,
  val brandAttributes: List<String>? = null
)

/**
 * 마케팅 인사이트 분석
 *
 * @property targetingStrategy 타겟팅 전략 제안
 * @property promotionTiming 프로모션 타이밍 제안
 * @property competitiveAdvantages 경쟁 우위 요소
 * @property marketingChannels 추천 마케팅 채널
 * @property contentStrategy 콘텐츠 전략 제안
 * @property pricingStrategy 가격 전략 분석
 * @property searchTrends 검색 트렌드 관련성
 * @property celebrityEndorsement 셀럽/인플루언서 마케팅 기회
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MarketingInsights(
  val targetingStrategy: String,
  val promotionTiming: String,
  val competitiveAdvantages: List<String>? = null,
  val marketingChannels: List<MarketingChannel>? = null,
  val contentStrategy: String,
  val pricingStrategy: String,
  val searchTrends: SearchTrends? = null,
  val celebrityEndorsement: CelebrityEndorsement? = null
)

/**
 * 마케팅 채널 정보
 *
 * @property channel 채널명
 * @property priority 우선순위 (high/medium/low)
 * @property recommendation 구체적 실행 제안
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MarketingChannel(
  val channel: String,
  val priority: PriorityLevel,
  val recommendation: String
)

/**
 * 우선순위/영향도 레벨 Enum
 */
enum class PriorityLevel {
  high,
  medium,
  low
}

/**
 * 검색 트렌드 정보
 *
 * @property trending 트렌드 여부
 * @property relatedKeywords 관련 검색어
 * @property searchVolume 검색량 수준
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SearchTrends(
  val trending: Boolean,
  val relatedKeywords: List<String>? = null,
  val searchVolume: PriorityLevel
)

/**
 * 셀럽/인플루언서 관련 정보
 *
 * @property celebrityName 관련 셀럽/인플루언서
 * @property impactLevel 영향력 수준
 * @property recommendation 활용 방안
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CelebrityEndorsement(
  val celebrityName: String? = null,
  val impactLevel: String,
  val recommendation: String
)

/**
 * 고객 만족도 분석
 *
 * @property satisfactionScore 만족도 점수 (0-100)
 * @property npsScore NPS(Net Promoter Score)
 * @property repeatPurchaseRate 재구매율 (추정치)
 * @property satisfactionFactors 만족 요인 분석
 * @property dissatisfactionFactors 불만족 요인 분석
 * @property improvementSuggestions 개선 제안사항
 * @property customerSegments 고객 세그먼트별 만족도
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CustomerSatisfactionAnalysis(
  val satisfactionScore: Int,
  val npsScore: Int? = null,
  val repeatPurchaseRate: String? = null,
  val satisfactionFactors: List<SatisfactionFactor>? = null,
  val dissatisfactionFactors: List<SatisfactionFactor>? = null,
  val improvementSuggestions: List<String>? = null,
  val customerSegments: List<CustomerSegment>? = null
)

/**
 * 만족/불만족 요인
 *
 * @property factor 요인 항목
 * @property impact 영향도 (high/medium/low)
 * @property frequency 언급 빈도 (%)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SatisfactionFactor(
  val factor: String,
  val impact: PriorityLevel,
  val frequency: Double
)

/**
 * 고객 세그먼트별 만족도
 *
 * @property segment 고객 세그먼트 (연령대, 성별 등)
 * @property satisfactionScore 해당 세그먼트 만족도
 * @property comments 주요 의견
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CustomerSegment(
  val segment: String,
  val satisfactionScore: Int,
  val comments: List<String>? = null
)

/**
 * 데이터 품질 평가 및 개선 가이드
 *
 * @property overallQuality 전체 데이터 품질 수준
 * @property missingFields 누락된 필수 필드 목록
 * @property incompleteFields 불완전한 선택 필드 목록
 * @property recommendations 데이터 개선 권장사항
 * @property impactAnalysis 누락된 데이터가 미치는 영향 분석
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DataQualityReport(
  val overallQuality: DataQualityLevel,
  val missingFields: List<MissingFieldInfo>? = null,
  val incompleteFields: List<IncompleteFieldInfo>? = null,
  val recommendations: List<String>? = null,
  val impactAnalysis: Map<String, String>? = null
)

/**
 * 데이터 품질 수준 Enum
 */
enum class DataQualityLevel {
  excellent,
  good,
  fair,
  poor
}

/**
 * 누락된 필드 정보
 *
 * @property fieldName 필드명
 * @property fieldType 필드 타입 (required/optional)
 * @property description 필드 설명
 * @property example 입력 예시
 * @property impact 누락 시 영향도
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MissingFieldInfo(
  val fieldName: String,
  val fieldType: FieldType,
  val description: String,
  val example: String,
  val impact: PriorityLevel
)

/**
 * 필드 타입 Enum
 */
enum class FieldType {
  required,
  optional
}

/**
 * 불완전한 필드 정보
 *
 * @property fieldName 필드명
 * @property currentValue 현재 값
 * @property issue 문제점
 * @property suggestion 개선 제안
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IncompleteFieldInfo(
  val fieldName: String,
  val currentValue: String? = null,
  val issue: String,
  val suggestion: String
)

/**
 * AI 생성 카피 원본 응답 (LLM에서 직접 생성되는 JSON 구조)
 *
 * 이 DTO는 LLM이 직접 출력하는 JSON 형식과 매칭됩니다.
 * 서비스에서 이 응답을 파싱하여 PdpCopyGenerationResponse로 변환합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiGeneratedCopyResponse(
  /** 메인 카피 (Hook + 핵심 베네핏, 임팩트 있는 첫 문장) */
  val mainCopy: String,

  /** 서브 카피 (차별화 포인트 + 신뢰 요소 상세 설명) */
  val subCopy: String? = null,

  /** 이벤트 카피 (긴급성/희소성 강조 CTA) - 이벤트 정보가 있을 때만 */
  val eventCopy: String? = null,

  /** SEO 최적화 제목 (검색 노출용, 50자 이내) */
  val seoTitle: String,

  /** SEO 최적화 설명 (메타 디스크립션, 160자 이내) */
  val seoDescription: String,

  /** 핵심 키워드 목록 (5-10개) */
  val keywords: List<String>? = null,

  /** 추천 해시태그 목록 (5-10개, # 포함) */
  val hashtags: List<String>? = null,

  /** 적용된 톤앤매너 설명 */
  val tone: String? = null,

  /** 마케팅 인사이트 (타겟팅, 채널, 프로모션 전략) */
  val marketingInsights: AiMarketingInsights? = null
)

/**
 * AI 생성 마케팅 인사이트 (LLM 직접 출력용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiMarketingInsights(
  /** 타겟팅 전략 제안 */
  val targetingStrategy: String? = null,

  /** 프로모션 타이밍 제안 */
  val promotionTiming: String? = null,

  /** 경쟁 우위 요소 (3-5개) */
  val competitiveAdvantages: List<String>? = null,

  /** 콘텐츠 전략 제안 */
  val contentStrategy: String? = null,

  /** 가격 전략 분석 */
  val pricingStrategy: String? = null
)

/**
 * AI 카피라이팅 전용 응답 (1차 LLM 호출용)
 *
 * 카피라이팅 생성에만 집중하는 분리된 DTO입니다.
 * 마케팅 인사이트는 별도의 2차 LLM 호출에서 생성됩니다.
 *
 * ### 포함 내용:
 * - 메인/서브/이벤트 카피
 * - SEO 최적화 제목/설명
 * - 키워드 및 해시태그
 * - 톤앤매너
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiCopyOnlyResponse(
  /** 메인 카피 (Hook + 핵심 베네핏, 임팩트 있는 첫 문장) */
  val mainCopy: String,

  /** 서브 카피 (차별화 포인트 + 신뢰 요소 상세 설명) */
  val subCopy: String? = null,

//  /** 이벤트 카피 (긴급성/희소성 강조 CTA) - 이벤트 정보가 있을 때만 */
//  val eventCopy: String? = null,
//
//  /** SEO 최적화 제목 (검색 노출용, 50자 이내) */
//  val seoTitle: String,
//
//  /** SEO 최적화 설명 (메타 디스크립션, 160자 이내) */
//  val seoDescription: String,
//
//  /** 핵심 키워드 목록 (5-10개) */
//  val keywords: List<String>? = null,
//
//  /** 추천 해시태그 목록 (5-10개, # 포함) */
//  val hashtags: List<String>? = null,
//
//  /** 적용된 톤앤매너 설명 */
//  val tone: String? = null
)

/**
 * AI 마케팅 인사이트 전용 응답 (2차 LLM 호출용)
 *
 * 마케팅 인사이트 생성에만 집중하는 분리된 DTO입니다.
 * 상품 정보와 생성된 카피라이팅을 기반으로 심층적인 마케팅 전략을 분석합니다.
 *
 * ### 포함 내용:
 * - 타겟팅 전략
 * - 프로모션 타이밍
 * - 경쟁 우위 요소
 * - 콘텐츠 전략
 * - 가격 전략
 * - 마케팅 채널 추천
 * - 검색 트렌드 분석
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiMarketingInsightsOnlyResponse(
  /** 타겟팅 전략 제안 (상세한 타겟 고객층 분석 및 접근 전략) */
  val targetingStrategy: String,

  /** 프로모션 타이밍 제안 (최적의 프로모션 시기 및 이벤트 연계 전략) */
  val promotionTiming: String,

  /** 경쟁 우위 요소 (3-5개의 명확한 차별화 포인트) */
  val competitiveAdvantages: List<String>,

  /** 콘텐츠 전략 제안 (SNS, 블로그, 광고 등 콘텐츠 활용 전략) */
  val contentStrategy: String,

  /** 가격 전략 분석 (가격 포지셔닝 및 할인 전략 제안) */
  val pricingStrategy: String,

  /** 추천 마케팅 채널 목록 (우선순위별 채널 및 활용 방안) */
  val recommendedChannels: List<AiMarketingChannelRecommendation>? = null,

  /** 검색 트렌드 분석 (키워드 트렌드 및 SEO 전략) */
  val searchTrendAnalysis: AiSearchTrendAnalysis? = null,

  /** 고객 세그먼트별 접근 전략 */
  val customerSegmentStrategies: List<AiCustomerSegmentStrategy>? = null
)

/**
 * AI 마케팅 채널 추천
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiMarketingChannelRecommendation(
  /** 채널명 (예: 인스타그램, 네이버 블로그, 유튜브 등) */
  val channelName: String,

  /** 우선순위 (high, medium, low) */
  val priority: String,

  /** 구체적 실행 방안 */
  val actionPlan: String,

  /** 예상 효과 */
  val expectedImpact: String? = null
)

/**
 * AI 검색 트렌드 분석
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiSearchTrendAnalysis(
  /** 트렌드 상승 여부 */
  val isTrending: Boolean,

  /** 관련 인기 검색어 */
  val relatedTrendingKeywords: List<String>? = null,

  /** 검색량 수준 (high, medium, low) */
  val searchVolumeLevel: String,

  /** 시즌 연관성 분석 */
  val seasonalRelevance: String? = null
)

/**
 * AI 고객 세그먼트별 전략
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiCustomerSegmentStrategy(
  /** 세그먼트명 (예: 20대 여성, 30-40대 직장인 등) */
  val segmentName: String,

  /** 해당 세그먼트에 어필하는 핵심 메시지 */
  val keyMessage: String,

  /** 추천 접근 채널 */
  val recommendedChannel: List<String>? = null
)
