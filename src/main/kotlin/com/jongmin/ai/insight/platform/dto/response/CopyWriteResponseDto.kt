package com.jongmin.ai.insight.platform.dto.response

/**
 * PDP 카피라이팅 AI 분석 통합 응답 DTO
 * VLM과 LLM을 통한 상품 이미지 및 메타데이터 분석 결과를 포함
 *
 * @property success 분석 성공 여부
 * @property qualityScore 데이터 품질 점수 (0-100, 입력 데이터의 완성도)
 * @property copywriting 상품 카피라이팅 정보
 * @property reviewSummary 리뷰 요약 및 인사이트 (데이터 부족 시 null)
 * @property metadata 확장된 메타데이터 (사용자 입력 + AI 분석)
 * @property marketingInsights 마케팅 인사이트 분석 (데이터 부족 시 제한적)
 * @property customerSatisfaction 고객 만족도 분석 (데이터 부족 시 null)
 * @property dataQuality 데이터 품질 평가 및 개선 가이드
 * @property generatedAt 생성 시간 (timestamp)
 */
data class PdpCopyGenerationResponse(
  val success: Boolean,
  val qualityScore: Int,
  val copywriting: ProductCopywriting,
  val reviewSummary: ReviewSummary?,
  val metadata: EnrichedMetadata,
  val marketingInsights: MarketingInsights?,
  val customerSatisfaction: CustomerSatisfactionAnalysis?,
  val dataQuality: DataQualityReport,
  val generatedAt: Long
)

/**
 * 데이터 품질 평가 및 개선 가이드
 *
 * @property overallQuality 전체 데이터 품질 수준 (excellent/good/fair/poor)
 * @property missingFields 누락된 필수 필드 목록 (없으면 null)
 * @property incompleteFields 불완전한 선택 필드 목록 (없으면 null)
 * @property recommendations 데이터 개선 권장사항 (없으면 null)
 * @property impactAnalysis 누락된 데이터가 미치는 영향 분석 (없으면 null)
 */
data class DataQualityReport(
  val overallQuality: String,
  val missingFields: List<MissingFieldInfo>?,
  val incompleteFields: List<IncompleteFieldInfo>?,
  val recommendations: List<String>?,
  val impactAnalysis: Map<String, String>?
)

/**
 * 누락된 필드 정보
 *
 * @property fieldName 필드명
 * @property fieldType 필드 타입 (required/optional)
 * @property description 필드 설명
 * @property example 입력 예시
 * @property impact 누락 시 영향도 (high/medium/low)
 */
data class MissingFieldInfo(
  val fieldName: String,
  val fieldType: String,
  val description: String,
  val example: String,
  val impact: String
)

/**
 * 불완전한 필드 정보
 *
 * @property fieldName 필드명
 * @property currentValue 현재 값
 * @property issue 문제점
 * @property suggestion 개선 제안
 */
data class IncompleteFieldInfo(
  val fieldName: String,
  val currentValue: String?,
  val issue: String,
  val suggestion: String
)

/**
 * 상품 카피라이팅 정보
 * commerce_pdp_ai_copywriting_guide.md 가이드라인 준수
 *
 * @property mainCopy 메인 카피 (Hook + 핵심 베네핏)
 * @property subCopy 서브 카피 (차별화 포인트 + 신뢰 요소) - 데이터 부족 시 null
 * @property eventCopy 이벤트 카피 (긴급성/희소성 강조) - 선택적
 * @property seoTitle SEO 최적화 제목
 * @property seoDescription SEO 최적화 설명
 * @property keywords 핵심 키워드 목록 (생성 불가 시 null)
 * @property hashtags 추천 해시태그 목록 (생성 불가 시 null)
 * @property tone 카피 톤앤매너 - 데이터 부족 시 null
 * @property style 적용된 스타일 (detailed/concise/storytelling) - 데이터 부족 시 null
 * @property confidence 카피 생성 신뢰도 (0.0 - 1.0)
 */
data class ProductCopywriting(
  val mainCopy: String,
  val subCopy: String?,
  val eventCopy: String? = null,
  val seoTitle: String,
  val seoDescription: String,
  val keywords: List<String>?,
  val hashtags: List<String>?,
  val tone: String?,
  val style: String?,
  val confidence: Double
)

/**
 * 리뷰 요약 및 인사이트
 *
 * @property overallRating 평균 평점
 * @property totalReviews 총 리뷰 수
 * @property highlightedReviews 대표 리뷰 (긍정/부정) - 없으면 null
 * @property keyStrengths 주요 강점 (자주 언급되는 장점) - 없으면 null
 * @property keyWeaknesses 개선 필요 사항 (자주 언급되는 단점) - 없으면 null
 * @property customerProfile 주요 구매 고객층 프로필
 * @property usageScenarios 주요 사용 시나리오 - 없으면 null
 */
data class ReviewSummary(
  val overallRating: Double?,
  val totalReviews: Int?,
  val highlightedReviews: List<HighlightedReview>?,
  val keyStrengths: List<String>?,
  val keyWeaknesses: List<String>?,
  val customerProfile: String?,
  val usageScenarios: List<String>?
)

/**
 * 대표 리뷰
 *
 * @property content 리뷰 내용 (요약)
 * @property sentiment 감정 분석 (positive/negative/neutral)
 * @property helpfulness 도움됨 점수
 */
data class HighlightedReview(
  val content: String,
  val sentiment: String,
  val helpfulness: Int? = null
)

/**
 * 확장된 메타데이터 (사용자 입력 + AI 추출)
 *
 * @property original 사용자가 입력한 원본 메타데이터
 * @property extracted AI가 이미지/텍스트에서 추출한 추가 정보
 * @property enriched 통합 및 정제된 최종 메타데이터
 */
data class EnrichedMetadata(
  val original: Map<String, Any>,
  val extracted: ExtractedMetadata,
  val enriched: Map<String, Any>
)

/**
 * AI가 추출한 메타데이터
 *
 * @property colors 감지된 색상 정보 (감지 불가 시 null)
 * @property materials 감지된 소재 정보 (감지 불가 시 null)
 * @property patterns 감지된 패턴/디자인 (감지 불가 시 null)
 * @property category 자동 분류된 카테고리
 * @property season 추천 시즌
 * @property targetAge 타겟 연령대
 * @property style 스타일 분류 (분류 불가 시 null)
 * @property brandAttributes 브랜드 속성 (추출 불가 시 null)
 */
data class ExtractedMetadata(
  val colors: List<String>?,
  val materials: List<String>?,
  val patterns: List<String>?,
  val category: String?,
  val season: String?,
  val targetAge: String?,
  val style: List<String>?,
  val brandAttributes: List<String>?
)

/**
 * 마케팅 인사이트 분석
 *
 * @property targetingStrategy 타겟팅 전략 제안
 * @property promotionTiming 프로모션 타이밍 제안
 * @property competitiveAdvantages 경쟁 우위 요소 (분석 불가 시 null)
 * @property marketingChannels 추천 마케팅 채널 (분석 불가 시 null)
 * @property contentStrategy 콘텐츠 전략 제안
 * @property pricingStrategy 가격 전략 분석
 * @property searchTrends 검색 트렌드 관련성
 * @property celebrityEndorsement 셀럽/인플루언서 마케팅 기회
 */
data class MarketingInsights(
  val targetingStrategy: String,
  val promotionTiming: String,
  val competitiveAdvantages: List<String>?,
  val marketingChannels: List<MarketingChannel>?,
  val contentStrategy: String,
  val pricingStrategy: String,
  val searchTrends: SearchTrends?,
  val celebrityEndorsement: CelebrityEndorsement?
)

/**
 * 마케팅 채널 정보
 *
 * @property channel 채널명 (SNS, 검색광고, 디스플레이 등)
 * @property priority 우선순위 (high/medium/low)
 * @property recommendation 구체적 실행 제안
 */
data class MarketingChannel(
  val channel: String,
  val priority: String,
  val recommendation: String
)

/**
 * 검색 트렌드 정보
 *
 * @property trending 트렌드 여부
 * @property relatedKeywords 관련 검색어 (데이터 없으면 null)
 * @property searchVolume 검색량 수준 (high/medium/low)
 */
data class SearchTrends(
  val trending: Boolean,
  val relatedKeywords: List<String>?,
  val searchVolume: String
)

/**
 * 셀럽/인플루언서 관련 정보
 *
 * @property celebrityName 관련 셀럽/인플루언서
 * @property impactLevel 영향력 수준
 * @property recommendation 활용 방안
 */
data class CelebrityEndorsement(
  val celebrityName: String?,
  val impactLevel: String,
  val recommendation: String
)

/**
 * 고객 만족도 분석
 *
 * @property satisfactionScore 만족도 점수 (0-100)
 * @property npsScore NPS(Net Promoter Score) - 선택적
 * @property repeatPurchaseRate 재구매율 (추정치)
 * @property satisfactionFactors 만족 요인 분석 (데이터 없으면 null)
 * @property dissatisfactionFactors 불만족 요인 분석 (데이터 없으면 null)
 * @property improvementSuggestions 개선 제안사항 (제안 없으면 null)
 * @property customerSegments 고객 세그먼트별 만족도 (데이터 없으면 null)
 */
data class CustomerSatisfactionAnalysis(
  val satisfactionScore: Int,
  val npsScore: Int?,
  val repeatPurchaseRate: String?,
  val satisfactionFactors: List<SatisfactionFactor>?,
  val dissatisfactionFactors: List<SatisfactionFactor>?,
  val improvementSuggestions: List<String>?,
  val customerSegments: List<CustomerSegment>?
)

/**
 * 만족/불만족 요인
 *
 * @property factor 요인 항목
 * @property impact 영향도 (high/medium/low)
 * @property frequency 언급 빈도 (%)
 */
data class SatisfactionFactor(
  val factor: String,
  val impact: String,
  val frequency: Int
)

/**
 * 고객 세그먼트별 만족도
 *
 * @property segment 고객 세그먼트 (연령대, 성별 등)
 * @property satisfactionScore 해당 세그먼트 만족도
 * @property comments 주요 의견 (없으면 null)
 */
data class CustomerSegment(
  val segment: String,
  val satisfactionScore: Int,
  val comments: List<String>?
)
