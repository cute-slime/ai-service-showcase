package com.jongmin.ai.product_agent

/**
 * 카피라이팅 스타일 타입
 *
 * 생성할 카피라이팅의 톤과 스타일을 지정합니다.
 */
enum class CopywritingStyle {
  /** 감성적/공감형 카피 - 고객의 감정에 호소하는 스타일 */
  EMOTIONAL,

  /** 트렌디한 카피 - 최신 유행과 트렌드를 반영한 스타일 */
  TRENDY,

  /** 전문적/기능 중심 카피 - 제품의 기능과 성능을 강조하는 스타일 */
  PROFESSIONAL,

  /** 친근한/대화형 카피 - 친근하고 편안한 대화체 스타일 */
  FRIENDLY,

  /** 프리미엄/고급스러운 카피 - 고급스럽고 세련된 이미지를 강조하는 스타일 */
  LUXURY,

  /** 환경 친화적 카피 - 친환경, 지속가능성을 강조하는 스타일 */
  ENVIRONMENT,

  /** 가성비 중심 카피 - 합리적인 가격과 효용을 강조하는 스타일 */
  VALUE_FOR_MONEY,

  /** 스토리텔링 카피 - 브랜드나 제품의 이야기를 전달하는 스타일 */
  STORYTELLING,

  /** 유머러스한 카피 - 재치있고 유머러스한 스타일 */
  HUMOROUS,

  /** 미니멀/간결한 카피 - 간결하고 핵심만 전달하는 스타일 */
  MINIMAL
}

/**
 * 설명 스타일
 *
 * 카피라이팅의 설명 방식을 지정합니다.
 */
enum class DescriptionStyle {
  /** 상세형: 구체적 특징과 수치 중심 */
  DETAILED,

  /** 간결형: 간결하고 임팩트 있는 메시지 */
  CONCISE,

  /** 스토리텔링: 스토리텔링 방식 */
  STORYTELLING
}

/**
 * 계절 컨텍스트
 *
 * 상품의 계절적 특성을 반영합니다.
 */
enum class SeasonalContext {
  /** 봄: 가벼운 소재, 파스텔 컬러, 새로운 시작 */
  SPRING,

  /** 여름: 통풍성, 자외선 차단, 휴가 분위기 */
  SUMMER,

  /** 가을: 레이어링, 따뜻한 컬러, 포근함 */
  FALL,

  /** 겨울: 보온성, 체온 유지, 연말 시즌 */
  WINTER,

  /** 계절 무관: 계절 중립적 설명 */
  NONE
}

/**
 * 트렌드 강조
 *
 * 상품의 트렌드 포지셔닝을 지정합니다.
 */
enum class TrendEmphasis {
  /** 40대 여성 인기 상품 (데이터 기반) */
  TRENDING_40S_WOMEN,

  /** 인플루언서/셀럽 착용 */
  CELEBRITY_ENDORSED,

  /** 판매 상위 제품 */
  BESTSELLER,

  /** 신상품 강조 */
  NEW_ARRIVAL,

  /** 한정판 제품 */
  LIMITED_EDITION,

  /** 일반 포지셔닝 */
  NONE
}

/**
 * 가격 포지셔닝
 *
 * 상품의 가격 전략을 지정합니다.
 */
enum class PricePositioning {
  /** 가성비와 할인 강조 */
  VALUE_FOCUSED,

  /** 품질과 장인정신 강조 */
  PREMIUM_JUSTIFIED,

  /** 시장 가격 대비 유리함 강조 */
  COMPETITIVE,

  /** 한정 특가 강조 */
  LIMITED_OFFER,

  /** 가격 언급 최소화 */
  NONE
}

/**
 * 이벤트 강조 레벨
 *
 * 이벤트 정보를 카피에 얼마나 강하게 반영할지 지정합니다.
 */
enum class EventEmphasis {
  /**
   * HIGH: 이벤트를 전면에 내세움
   * - 할인/혜택으로 시작, 긴급성 강조, 반복 메시징
   * - 예: "🔥 오늘만 50% 할인! 프리미엄 캐시미어 니트..."
   */
  HIGH,

  /**
   * MODERATE: 이벤트를 자연스럽게 통합
   * - 이벤트를 부가 혜택으로 언급, 설명 중간에 삽입
   * - 예: "이탈리아산 캐시미어 니트, 블랙프라이데이 50% 할인..."
   */
  MODERATE,

  /**
   * MINIMAL: 은은한 언급
   * - 상품 가치에 집중, 마지막에 이벤트 간략히 언급
   * - 예: "...이탈리아 장인의 손길. 이번 주 50% 할인 중."
   */
  MINIMAL,

  /**
   * NONE: 순수 상품 집중
   * - 상품의 본질적 가치만 설명, 이벤트 정보 생략
   */
  NONE
}

/**
 * 이벤트 유형
 *
 * 프로모션/이벤트의 종류를 지정합니다.
 */
enum class EventType {
  /** 가격 할인 이벤트 */
  DISCOUNT,

  /** 다수 상품 패키지 (1+1, 2+1 등) */
  BUNDLE,

  /** 구매 시 사은품 증정 */
  GIFT,

  /** 회원 전용 혜택 */
  MEMBERSHIP,

  /** 한정 수량/기간 독점 상품 */
  LIMITED_EDITION,

  /** 사전 예약/얼리 액세스 */
  PRESALE,

  /** 이벤트 참여 유도 (이벤트 페이지 트래픽 유도에 집중) */
  LANDING_PAGE
}
