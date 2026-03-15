package com.jongmin.ai.product_agent

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 마케팅 캠페인 도구 ID
 *
 * 마케팅 캠페인에서 사용 가능한 콘텐츠 생성 도구를 정의합니다.
 * 각 도구는 특정 마케팅 채널에 최적화된 콘텐츠를 생성합니다.
 *
 * @property toolCode 도구 코드 (API 요청/응답에 사용)
 * @property description 도구 설명
 * @property requiresImage 이미지 생성 필요 여부
 */
enum class MarketingCampaignToolId(
  private val toolCode: String,
  val description: String,
  val requiresImage: Boolean
) {
  /** 배너 광고 (이미지 + 헤드라인/서브헤드라인 카피) */
  BANNER_AD("banner-ad", "배너 광고", true),

  /** 인스타그램 피드 (이미지 + 캡션 + 해시태그) */
  INSTAGRAM_FEED("instagram-feed", "인스타그램 피드", true),

  /** 인스타그램 스토리/릴스 (세로형 이미지 + 후킹 문구) */
  INSTAGRAM_STORY("instagram-story", "인스타그램 스토리/릴스", true),

  /** 검색 광고 (네이버/구글 검색광고용 텍스트 카피) */
  SEARCH_AD("search-ad", "검색 광고", false),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.toolCode.lowercase() }

    /**
     * 도구 코드로 MarketingCampaignToolId 조회
     *
     * @param code 도구 코드 (대소문자 무관)
     * @return 매칭되는 MarketingCampaignToolId, 없으면 null
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): MarketingCampaignToolId? = code?.let { codeMap[it.lowercase()] }
  }

  @JsonValue
  fun code(): String = toolCode

  override fun toString(): String = toolCode
}

/**
 * 마케팅 카테고리 ID
 *
 * 상품의 카테고리를 정의합니다.
 * 카테고리에 따라 톤앤매너와 마케팅 전략이 조정됩니다.
 *
 * @property categoryCode 카테고리 코드
 * @property description 카테고리 설명
 */
enum class MarketingCategoryId(
  private val categoryCode: String,
  val description: String
) {
  /** 패션/의류 */
  FASHION("fashion", "패션/의류"),

  /** 뷰티/화장품 */
  BEAUTY("beauty", "뷰티/화장품"),

  /** 식품/음료 */
  FOOD("food", "식품/음료"),

  /** 전자기기 */
  ELECTRONICS("electronics", "전자기기"),

  /** 홈/리빙 */
  HOME("home", "홈/리빙"),

  /** 스포츠/레저 */
  SPORTS("sports", "스포츠/레저"),

  /** 유아/아동 */
  BABY("baby", "유아/아동"),

  /** 반려동물 */
  PET("pet", "반려동물"),

  /** 건강/의료 */
  HEALTH("health", "건강/의료"),

  /** 기타 */
  OTHER("other", "기타"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.categoryCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): MarketingCategoryId = code?.let { codeMap[it.lowercase()] } ?: OTHER
  }

  @JsonValue
  fun code(): String = categoryCode

  override fun toString(): String = categoryCode
}

/**
 * 배너 사이즈 ID
 *
 * 배너 광고의 사이즈 프리셋을 정의합니다.
 * 각 사이즈는 특정 플랫폼이나 광고 형식에 최적화되어 있습니다.
 *
 * @property sizeCode 사이즈 코드
 * @property width 너비 (px)
 * @property height 높이 (px)
 * @property description 사이즈 설명
 */
enum class BannerSizeId(
  private val sizeCode: String,
  val width: Int,
  val height: Int,
  val description: String
) {
  /** 정사각형 (1080x1080) - SNS 공용 */
  SQUARE("square", 1080, 1080, "정사각형 (SNS 공용)"),

  /** 가로형 (1200x628) - 페이스북/링크드인 광고 */
  LANDSCAPE("landscape", 1200, 628, "가로형 (페이스북/링크드인)"),

  /** 세로형 (1080x1350) - 인스타그램 세로 광고 */
  PORTRAIT("portrait", 1080, 1350, "세로형 (인스타그램 세로)"),

  /** 와이드 (1200x600) - 웹 배너 */
  WIDE("wide", 1200, 600, "와이드 (웹 배너)"),

  /** 스토리 (1080x1920) - 스토리형 배너 */
  STORY("story", 1080, 1920, "스토리 (스토리형 배너)"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.sizeCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): BannerSizeId = code?.let { codeMap[it.lowercase()] } ?: SQUARE
  }

  @JsonValue
  fun code(): String = sizeCode

  override fun toString(): String = sizeCode
}

/**
 * 톤앤매너 ID
 *
 * 마케팅 콘텐츠의 톤앤매너를 정의합니다.
 * 브랜드 이미지와 타겟 고객에 맞는 톤을 선택합니다.
 *
 * @property toneCode 톤 코드
 * @property description 톤 설명
 */
enum class ToneAndMannerId(
  private val toneCode: String,
  val description: String
) {
  /** 친근한 */
  FRIENDLY("friendly", "친근한"),

  /** 고급스러운 */
  PREMIUM("premium", "고급스러운"),

  /** 트렌디한 */
  TRENDY("trendy", "트렌디한"),

  /** 전문적인 */
  PROFESSIONAL("professional", "전문적인"),

  /** 재미있는 */
  PLAYFUL("playful", "재미있는"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.toneCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): ToneAndMannerId = code?.let { codeMap[it.lowercase()] } ?: FRIENDLY
  }

  @JsonValue
  fun code(): String = toneCode

  override fun toString(): String = toneCode
}

/**
 * 캡션 길이 ID
 *
 * 인스타그램 피드 캡션의 길이를 정의합니다.
 *
 * @property lengthCode 길이 코드
 * @property maxLength 최대 글자수
 * @property description 길이 설명
 */
enum class CaptionLengthId(
  private val lengthCode: String,
  val maxLength: Int,
  val description: String
) {
  /** 짧은 캡션 (최대 150자) */
  SHORT("short", 150, "짧은 캡션"),

  /** 중간 캡션 (최대 500자) */
  MEDIUM("medium", 500, "중간 캡션"),

  /** 긴 캡션 (최대 2200자) */
  LONG("long", 2200, "긴 캡션"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.lengthCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): CaptionLengthId = code?.let { codeMap[it.lowercase()] } ?: MEDIUM
  }

  @JsonValue
  fun code(): String = lengthCode

  override fun toString(): String = lengthCode
}

/**
 * 스토리 콘텐츠 타입 ID
 *
 * 인스타그램 스토리/릴스 콘텐츠 타입을 정의합니다.
 *
 * @property typeCode 타입 코드
 * @property description 타입 설명
 */
enum class StoryContentTypeId(
  private val typeCode: String,
  val description: String
) {
  /** 스토리 */
  STORY("story", "스토리"),

  /** 릴스 */
  REELS("reels", "릴스"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.typeCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): StoryContentTypeId = code?.let { codeMap[it.lowercase()] } ?: STORY
  }

  @JsonValue
  fun code(): String = typeCode

  override fun toString(): String = typeCode
}

/**
 * 분위기 ID
 *
 * 인스타그램 스토리/릴스의 분위기를 정의합니다.
 *
 * @property moodCode 분위기 코드
 * @property description 분위기 설명
 */
enum class MoodId(
  private val moodCode: String,
  val description: String
) {
  /** 재미있는 */
  FUN("fun", "재미있는"),

  /** 감성적인 */
  EMOTIONAL("emotional", "감성적인"),

  /** 정보성 */
  INFORMATIVE("informative", "정보성"),

  /** 트렌디한 */
  TRENDY("trendy", "트렌디한"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.moodCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): MoodId = code?.let { codeMap[it.lowercase()] } ?: TRENDY
  }

  @JsonValue
  fun code(): String = moodCode

  override fun toString(): String = moodCode
}

/**
 * 검색 광고 플랫폼 ID
 *
 * 검색 광고 플랫폼을 정의합니다.
 * 플랫폼별로 광고 형식과 글자수 제한이 다릅니다.
 *
 * @property platformCode 플랫폼 코드
 * @property description 플랫폼 설명
 */
enum class SearchAdPlatformId(
  private val platformCode: String,
  val description: String
) {
  /** 네이버 검색광고 (제목 25자, 설명 45자) */
  NAVER("naver", "네이버"),

  /** 구글 검색광고 (헤드라인 30자x3, 설명 90자x2) */
  GOOGLE("google", "구글"),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.platformCode.lowercase() }

    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): SearchAdPlatformId = code?.let { codeMap[it.lowercase()] } ?: NAVER
  }

  @JsonValue
  fun code(): String = platformCode

  override fun toString(): String = platformCode
}
