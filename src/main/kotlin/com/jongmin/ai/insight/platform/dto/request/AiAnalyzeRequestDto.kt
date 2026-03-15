package com.jongmin.ai.insight.platform.dto.request

import com.jongmin.ai.insight.AnalysisFocusArea
import com.jongmin.ai.insight.BusinessGoal
import com.jongmin.ai.insight.MaxFileSize
import com.jongmin.ai.insight.ValidImageFile
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length
import org.springframework.web.multipart.MultipartFile

data class LargeInferenceRequestV2(
  @field:Null
  var key: String? = null,

  var canvasId: String? = null,

  var shopName: String? = null,

  @field:NotBlank
  @field:Size(max = 160_000)
  var question: String? = null
)

/**
 * 제품 분석 요청 DTO
 *
 * PDP 카피라이팅 및 제품 분석을 위한 요청 데이터를 담습니다.
 * 이미지와 함께 제품의 기본 정보, 판매 정보, 고객 피드백, 분석 목표 등을 포함할 수 있습니다.
 */
data class ProductAnalyze(
  @field:Null
  var key: String? = null,

  var canvasId: String? = null,

  var shopName: String? = null,

  /**
   * 제품 분석 옵션 (JSON String)
   */
  var analysisOptions: String? = null,

  /**
   * URL 탐색 시 추출된 메타데이터 (JSON String)
   */
  var metadata: String? = null,

  @field:NotNull
  @field:Size(max = 10)
  @field:MaxFileSize(maxSizeInBytes = 5 * 1024 * 1024, message = "각 이미지 파일은 5MB를 초과할 수 없습니다")
  @field:ValidImageFile(message = "유효하지 않은 이미지 파일 형식입니다. 허용된 형식: JPEG, PNG, GIF, WebP, BMP")
  var images: MutableList<MultipartFile>? = null
)

/**
 * 제품 기본 정보
 * AI가 제품을 더 정확하게 분석하기 위한 기본 정보
 */
data class ProductBasicInfo(
  /** 제품명 (필수) */
  @field:NotBlank
  @field:Length(max = 80)
  val productName: String? = null,

  /** 카테고리 (필수, 예: 패션, 전자제품, 식품 등) */
  @field:NotBlank
  @field:Length(max = 120)
  val category: String? = null,

  /** 브랜드명 */
  val brand: String? = null,

  /** 판매 가격 */
  val price: Double? = null,

  /** 정가 (할인 전 가격) */
  val originalPrice: Double? = null,

  /** 제품 스펙/특징 (자유 텍스트) */
  val specifications: String? = null,

  /** 타겟 고객층 (예: 20대 여성, 30-40대 남성 등) */
  val targetAudience: String? = null
)

/**
 * 판매 정보
 * 판매 현황 및 경쟁 환경 분석을 위한 정보
 */
data class SalesInfo(
  /** 판매 채널 (예: ['자사몰', '네이버 스마트스토어', '쿠팡']) */
  val salesChannels: List<String>? = null,

  /** 월 평균 판매량 */
  val monthlySales: Int? = null,

  /** 재고 수량 */
  val inventory: Int? = null,

  /** 경쟁사 가격대 */
  val competitorPrices: List<Double>? = null,

  /** 프로모션 계획 (자유 텍스트) */
  val promotionPlan: String? = null
)

/**
 * 리뷰 및 고객 피드백 정보
 * 고객 만족도 및 개선점 분석을 위한 정보
 */
data class CustomerFeedbackInfo(
  /** 평균 평점 (1-5) */
  val averageRating: Double? = null,

  /** 총 리뷰 수 */
  val totalReviews: Int? = null,

  /** 주요 긍정 리뷰/키워드 */
  val positiveReviews: List<String>? = null,

  /** 주요 부정 리뷰/개선사항 */
  val negativeReviews: List<String>? = null,

  /** 재구매율 (%) */
  val repurchaseRate: Double? = null
)

/**
 * 분석 초점 및 목표
 * AI 분석의 방향성과 목표를 명확히 하기 위한 정보
 */
data class AnalysisFocus(
  /** 분석 초점 영역 */
  val analysisFocus: List<AnalysisFocusArea>? = null,

  /** 비즈니스 목표 */
  val businessGoals: List<BusinessGoal>? = null,

  /** 현재 직면한 문제/과제 (자유 텍스트) */
  val challenges: String? = null,

  /** 목표 지표 (예: "월 매출 2배 증가", "전환율 3% 달성") */
  val targetMetrics: String? = null
)

/**
 * 제품 분석 옵션
 * 마케팅 인사이트 및 판매 전략 분석을 위한 추가 정보
 */
data class ProductAnalysisOptions(
  /** 제품 기본 정보 (필수: productName, category) */
  @field:Valid
  var productBasicInfo: ProductBasicInfo? = null,

  /** 판매 정보 */
  var salesInfo: SalesInfo? = null,

  /** 리뷰 및 고객 피드백 정보 */
  @field:Valid
  var customerFeedbackInfo: CustomerFeedbackInfo? = null,

  /** 분석 초점 및 목표 */
  @field:Valid
  var analysisFocus: AnalysisFocus? = null
)
