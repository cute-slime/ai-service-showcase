package com.jongmin.ai.product_agent

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 상품 이미지 스타일
 *
 * AI 이미지 생성 시 적용할 스타일을 정의합니다.
 * 각 스타일은 이미지의 전체적인 분위기와 촬영 방식을 결정합니다.
 *
 * @property styleCode 스타일 코드 (API 요청/응답에 사용)
 * @property description 스타일 설명
 */
enum class ImageStyle(
  private val styleCode: String,
  val description: String
) {
  /** 스튜디오 촬영 (깔끔한 배경, 전문적인 조명) */
  STUDIO("studio", "스튜디오 촬영 (깔끔한 배경, 전문적인 조명)"),

  /** 라이프스타일 (사용 환경에서 촬영된 느낌) */
  LIFESTYLE("lifestyle", "라이프스타일 (사용 환경에서 촬영된 느낌)"),

  /** 미니멀 (단순하고 깔끔한 구성) */
  MINIMAL("minimal", "미니멀 (단순하고 깔끔한 구성)"),

  /** 비비드 (밝고 화려한 색감) */
  VIBRANT("vibrant", "비비드 (밝고 화려한 색감)"),

  /** 자연스러움 (자연광, 편안한 분위기) */
  NATURAL("natural", "자연스러움 (자연광, 편안한 분위기)"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.styleCode.lowercase() }

    /**
     * 스타일 코드로 ImageStyle 조회
     *
     * @param code 스타일 코드 (대소문자 무관)
     * @return 매칭되는 ImageStyle, 없으면 null
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): ImageStyle? = code?.let { codeMap[it.lowercase()] }
  }

  @JsonValue
  fun code(): String = styleCode

  override fun toString(): String = styleCode
}

/**
 * 이미지 비율
 *
 * AI 이미지 생성 시 적용할 이미지 비율을 정의합니다.
 * 각 비율은 특정 플랫폼이나 용도에 최적화되어 있습니다.
 *
 * @property ratioCode 비율 코드 (API 요청/응답에 사용)
 * @property description 비율 설명 및 용도
 */
enum class AspectRatio(
  private val ratioCode: String,
  val description: String
) {
  /** 정사각형 (인스타그램, 썸네일) */
  SQUARE("1:1", "정사각형 (인스타그램, 썸네일)"),

  /** 가로형 와이드 (유튜브 썸네일, 배너) */
  WIDE_16_9("16:9", "가로형 와이드 (유튜브 썸네일, 배너)"),

  /** 세로형 (스토리, 릴스) */
  TALL_9_16("9:16", "세로형 (스토리, 릴스)"),

  /** 가로형 (블로그, 상세페이지) */
  LANDSCAPE_4_3("4:3", "가로형 (블로그, 상세페이지)"),

  /** 세로형 (쇼핑몰 상품 이미지) */
  PORTRAIT_3_4("3:4", "세로형 (쇼핑몰 상품 이미지)"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.ratioCode }

    /**
     * 비율 코드로 AspectRatio 조회
     *
     * @param code 비율 코드 (예: "1:1", "16:9")
     * @return 매칭되는 AspectRatio, 없으면 SQUARE (기본값)
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): AspectRatio = code?.let { codeMap[it] } ?: SQUARE
  }

  @JsonValue
  fun code(): String = ratioCode

  override fun toString(): String = ratioCode
}

/**
 * 참조 이미지 역할 프리셋
 *
 * 이미지 합성 시 각 참조 이미지가 어떤 역할을 하는지 정의합니다.
 * 워크플로우에서 각 역할에 맞는 노드로 이미지를 라우팅하는데 사용됩니다.
 *
 * @property roleCode 역할 코드 (API 요청/응답에 사용)
 * @property description 역할 설명
 */
enum class ReferenceImageRolePreset(
  private val roleCode: String,
  val description: String
) {
  /** 합성할 메인 제품 이미지 (예: 가방, 신발, 화장품) */
  PRODUCT("product", "합성할 메인 제품 이미지"),

  /** 제품을 착용/사용할 인물 이미지 (예: 모델 포즈, 손 이미지) */
  MODEL("model", "제품을 착용/사용할 인물 이미지"),

  /** 최종 이미지의 배경 (예: 카페, 거실, 야외) */
  BACKGROUND("background", "최종 이미지의 배경"),

  /** 함께 배치할 소품 (예: 커피잔, 책, 식물) */
  PROPS("props", "함께 배치할 소품"),

  /** 원하는 분위기/스타일의 참조 (예: 무드보드, 참조 광고 이미지) */
  STYLE_REF("style-ref", "원하는 분위기/스타일의 참조"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.roleCode.lowercase() }

    /**
     * 역할 코드로 ReferenceImageRolePreset 조회
     *
     * @param code 역할 코드 (대소문자 무관)
     * @return 매칭되는 ReferenceImageRolePreset, 없으면 null
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): ReferenceImageRolePreset? = code?.let { codeMap[it.lowercase()] }
  }

  @JsonValue
  fun code(): String = roleCode

  override fun toString(): String = roleCode
}
