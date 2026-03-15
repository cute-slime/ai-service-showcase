package com.jongmin.ai.insight

/**
 * 분석 초점 영역 타입
 *
 * AI 분석 시 특별히 강조할 영역을 지정합니다.
 */
enum class AnalysisFocusArea {
  /** 마케팅 전략 */
  MARKETING_STRATEGY,

  /** 가격 전략 */
  PRICING_STRATEGY,

  /** 타겟팅 전략 */
  TARGETING,

  /** 콘텐츠 전략 */
  CONTENT_STRATEGY,

  /** 채널 최적화 */
  CHANNEL_OPTIMIZATION,

  /** 경쟁사 분석 */
  COMPETITOR_ANALYSIS
}

/**
 * 비즈니스 목표 타입
 *
 * 제품 분석의 최종 비즈니스 목표를 정의합니다.
 */
enum class BusinessGoal {
  /** 매출 증대 */
  INCREASE_SALES,

  /** 전환율 개선 */
  IMPROVE_CONVERSION,

  /** 시장 확대 */
  EXPAND_MARKET,

  /** 브랜드 인지도 향상 */
  BRAND_AWARENESS,

  /** 고객 유지율 향상 */
  CUSTOMER_RETENTION
}
