package com.jongmin.ai.product_agent

import jakarta.validation.constraints.*
import org.hibernate.validator.constraints.Length

/**
 * 상품 기본 정보
 *
 * AI가 카피라이팅을 생성하기 위한 상품의 핵심 정보를 포함합니다.
 */
data class ProductBasicInfo(
  /** 상품명 (필수) */
  @field:NotBlank(message = "상품명은 필수입니다")
  @field:Length(max = 100, message = "상품명은 100자를 초과할 수 없습니다")
  val productName: String? = null,

  /** 카테고리 (필수, 예: 패션/의류, 전자제품, 식품/건강 등) */
  @field:NotBlank(message = "카테고리는 필수입니다")
  @field:Length(max = 50, message = "카테고리는 50자를 초과할 수 없습니다")
  val category: String? = null,

  /** 브랜드명 */
  @field:Length(max = 50, message = "브랜드명은 50자를 초과할 수 없습니다")
  val brand: String? = null,

  /** 타겟 고객층 (예: 20대 여성, 30-40대 직장인, MZ세대 등) */
  @field:Length(max = 100, message = "타겟 고객층은 100자를 초과할 수 없습니다")
  val targetAudience: String? = null,

  /** 제품 스펙/특징/강점 (자유 텍스트) */
  @field:Length(max = 2000, message = "제품 스펙은 2000자를 초과할 수 없습니다")
  val specifications: String? = null,

  /** 가격대 (예: "89,000원", "89,000원 (정가 149,000원)") */
  @field:Length(max = 100, message = "가격대는 100자를 초과할 수 없습니다")
  val priceRange: String? = null,

  /** 경쟁 제품 대비 장점 */
  @field:Length(max = 1000, message = "경쟁 우위 정보는 1000자를 초과할 수 없습니다")
  val competitiveAdvantages: String? = null
)

/**
 * 고객 피드백 정보
 *
 * 실제 고객 리뷰와 평가 데이터를 기반으로 카피라이팅에 신뢰도를 더합니다.
 */
data class CustomerFeedbackInfo(
  /** 평균 평점 (1.0 ~ 5.0) */
  @field:DecimalMin(value = "0.0", message = "평균 평점은 0.0 이상이어야 합니다")
  @field:DecimalMax(value = "5.0", message = "평균 평점은 5.0 이하여야 합니다")
  val averageRating: Double? = null,

  /** 총 리뷰 수 */
  @field:Min(value = 0, message = "총 리뷰 수는 0 이상이어야 합니다")
  val totalReviews: Int? = null,

  /** 재구매율 (0.0 ~ 100.0 %) */
  @field:DecimalMin(value = "0.0", message = "재구매율은 0% 이상이어야 합니다")
  @field:DecimalMax(value = "100.0", message = "재구매율은 100% 이하여야 합니다")
  val repurchaseRate: Double? = null,

  /** 주요 긍정 리뷰/키워드 목록 */
  @field:Size(max = 20, message = "긍정 리뷰는 최대 20개까지 입력 가능합니다")
  val positiveReviews: List<@Length(max = 500, message = "각 리뷰는 500자를 초과할 수 없습니다") String>? = null,

  /** 주요 부정 리뷰/개선사항 목록 */
  @field:Size(max = 20, message = "부정 리뷰는 최대 20개까지 입력 가능합니다")
  val negativeReviews: List<@Length(max = 500, message = "각 리뷰는 500자를 초과할 수 없습니다") String>? = null
)

/**
 * 이벤트 정보
 *
 * 프로모션/이벤트 관련 정보를 포함합니다.
 * 이벤트 상품인 경우에만 제공하며, 카피라이팅에 이벤트 혜택을 반영합니다.
 */
data class EventInfo(
  /** 이벤트명 (예: "블랙프라이데이", "연말정산 세일", "신상품 론칭") */
  @field:Length(max = 100, message = "이벤트명은 100자를 초과할 수 없습니다")
  val eventName: String? = null,

  /** 이벤트 유형 */
  val eventType: EventType? = null,

  /** 할인율/혜택 (예: "30% 할인", "1+1", "5만원 쿠폰") */
  @field:Length(max = 100, message = "할인율은 100자를 초과할 수 없습니다")
  val discountRate: String? = null,

  /** 이벤트 기간 (예: "11/20-11/30", "오늘 단 하루", "선착순 100명") */
  @field:Length(max = 100, message = "이벤트 기간은 100자를 초과할 수 없습니다")
  val eventPeriod: String? = null,

  /** 이벤트 혜택 (예: "무료배송", "사은품 증정", "한정 컬러 옵션") */
  @field:Length(max = 500, message = "이벤트 혜택은 500자를 초과할 수 없습니다")
  val eventBenefits: String? = null,

  /** 이벤트 랜딩 URL (landing_page 유형일 때 사용) */
  @field:Length(max = 500, message = "이벤트 랜딩 URL은 500자를 초과할 수 없습니다")
  val eventLandingUrl: String? = null
)

/**
 * 스타일 설정
 *
 * 카피라이팅 생성 시 적용할 스타일 옵션을 지정합니다.
 * 설명 스타일, 계절 컨텍스트, 트렌드 강조, 가격 포지셔닝, 이벤트 강조 등을 설정할 수 있습니다.
 */
data class StylePreferences(
  /** 설명 스타일 (detailed, concise, storytelling) */
  val descriptionStyle: DescriptionStyle? = DescriptionStyle.DETAILED,

  /** 계절 컨텍스트 */
  val seasonalContext: SeasonalContext? = SeasonalContext.NONE,

  /** 트렌드 강조 */
  val trendEmphasis: TrendEmphasis? = TrendEmphasis.NONE,

  /** 가격 포지셔닝 */
  val pricePositioning: PricePositioning? = PricePositioning.NONE,

  /** 이벤트 강조 레벨 (이벤트 정보가 있을 때만 적용) */
  val eventEmphasis: EventEmphasis? = EventEmphasis.MODERATE,

  /** 브랜드 톤앤매너 (예: "친근한", "전문적인", "트렌디한", "고급스러운") */
  @field:Length(max = 100, message = "톤앤매너는 100자를 초과할 수 없습니다")
  val brandTone: String? = null,

  /** 확장 컨텍스트 (추가 지시사항, 브랜드 스토리, 캠페인 테마 등) */
  @field:Length(max = 2000, message = "확장 컨텍스트는 2000자를 초과할 수 없습니다")
  val extendedPrompt: String? = null
)
