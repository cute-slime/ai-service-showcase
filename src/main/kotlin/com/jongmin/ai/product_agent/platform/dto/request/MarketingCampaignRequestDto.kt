package com.jongmin.ai.product_agent.platform.dto.request

import com.jongmin.ai.product_agent.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size
import org.springframework.web.multipart.MultipartFile

/**
 * 마케팅 캠페인 생성 요청 DTO (multipart/form-data)
 *
 * 여러 마케팅 도구를 한 번에 실행하여 다양한 마케팅 콘텐츠를 생성합니다.
 *
 * Content-Type: multipart/form-data
 * - data: JSON 문자열 (MarketingCampaignData)
 * - productImage: 제품 이미지 파일 (선택, 이미지 생성 도구 선택 시 필수)
 */
data class MarketingCampaignGenerateRequest(
  /**
   * 요청 고유 키 (서버에서 자동 설정)
   */
  @field:Null(message = "key는 서버에서 자동 생성됩니다")
  var key: String? = null,

  /**
   * 마케팅 캠페인 요청 데이터 (JSON String)
   *
   * MarketingCampaignData 형식의 JSON 문자열
   */
  @field:NotBlank(message = "요청 데이터는 필수입니다")
  var data: String? = null,

  /**
   * 제품 이미지 파일 (선택)
   *
   * 이미지가 필요한 도구(banner-ad, instagram-feed, instagram-story)에서
   * 참조 이미지로 사용됩니다.
   * 지원 형식: PNG, JPEG, WEBP (최대 10MB)
   */
  var productImage: MultipartFile? = null
)

/**
 * 마케팅 캠페인 요청 데이터 (JSON 파싱용)
 *
 * MarketingCampaignGenerateRequest.data 필드의 JSON을 파싱할 때 사용합니다.
 */
data class MarketingCampaignData(
  /**
   * 선택된 도구 목록 (필수, 1~4개)
   */
  @field:NotNull(message = "도구 선택은 필수입니다")
  @field:Size(min = 1, max = 4, message = "도구는 1~4개를 선택해야 합니다")
  val selectedTools: List<MarketingCampaignToolId>? = null,

  /**
   * 공통 입력 정보 (필수)
   */
  @field:NotNull(message = "공통 입력 정보는 필수입니다")
  @field:Valid
  val commonInput: MarketingCampaignCommonInput? = null,

  /**
   * 배너 광고 옵션 (banner-ad 선택 시 필수)
   */
  @field:Valid
  val bannerAdOptions: BannerAdOptions? = null,

  /**
   * 인스타그램 피드 옵션 (instagram-feed 선택 시 필수)
   */
  @field:Valid
  val instagramFeedOptions: InstagramFeedOptions? = null,

  /**
   * 인스타그램 스토리 옵션 (instagram-story 선택 시 필수)
   */
  @field:Valid
  val instagramStoryOptions: InstagramStoryOptions? = null,

  /**
   * 검색 광고 옵션 (search-ad 선택 시 필수)
   */
  @field:Valid
  val searchAdOptions: SearchAdOptions? = null
)

/**
 * 마케팅 캠페인 공통 입력 정보
 *
 * 모든 마케팅 도구에서 공통으로 사용되는 상품 정보입니다.
 */
data class MarketingCampaignCommonInput(
  /**
   * 제품명 (필수)
   */
  @field:NotBlank(message = "제품명은 필수입니다")
  @field:Size(max = 100, message = "제품명은 100자를 초과할 수 없습니다")
  val productName: String? = null,

  /**
   * 카테고리 (필수)
   */
  @field:NotNull(message = "카테고리는 필수입니다")
  val category: MarketingCategoryId? = null,

  /**
   * 짧은 설명/USP (선택)
   */
  @field:Size(max = 500, message = "짧은 설명은 500자를 초과할 수 없습니다")
  val shortDescription: String? = null,

  /**
   * 가격 정보 (선택)
   */
  @field:Size(max = 50, message = "가격 정보는 50자를 초과할 수 없습니다")
  val price: String? = null,

  /**
   * CTA 문구 (선택)
   * 예: "지금 구매하기", "자세히 보기"
   */
  @field:Size(max = 50, message = "CTA 문구는 50자를 초과할 수 없습니다")
  val ctaText: String? = null,

  /**
   * 타겟 고객층 (선택)
   * 예: "20-30대 여성", "MZ세대"
   */
  @field:Size(max = 200, message = "타겟 고객층은 200자를 초과할 수 없습니다")
  val targetAudience: String? = null,

  /**
   * 프로모션 정보 (선택)
   * 예: "론칭 기념 20% 할인"
   */
  @field:Size(max = 500, message = "프로모션 정보는 500자를 초과할 수 없습니다")
  val promotionInfo: String? = null
)

/**
 * 배너 광고 옵션
 */
data class BannerAdOptions(
  /**
   * 배너 사이즈 (필수)
   */
  @field:NotNull(message = "배너 사이즈는 필수입니다")
  val bannerSize: BannerSizeId? = null,

  /**
   * 톤앤매너 (필수)
   */
  @field:NotNull(message = "톤앤매너는 필수입니다")
  val toneAndManner: ToneAndMannerId? = null
)

/**
 * 인스타그램 피드 옵션
 */
data class InstagramFeedOptions(
  /**
   * 포함할 해시태그 (선택, 쉼표 구분)
   */
  @field:Size(max = 500, message = "해시태그는 500자를 초과할 수 없습니다")
  val hashtags: String? = null,

  /**
   * 이모지 포함 여부 (필수, 기본값 true)
   */
  val includeEmoji: Boolean = true,

  /**
   * 캡션 길이 (필수)
   */
  @field:NotNull(message = "캡션 길이는 필수입니다")
  val captionLength: CaptionLengthId? = null
)

/**
 * 인스타그램 스토리/릴스 옵션
 */
data class InstagramStoryOptions(
  /**
   * 콘텐츠 타입 (필수)
   */
  @field:NotNull(message = "콘텐츠 타입은 필수입니다")
  val contentType: StoryContentTypeId? = null,

  /**
   * 분위기 (필수)
   */
  @field:NotNull(message = "분위기는 필수입니다")
  val mood: MoodId? = null
)

/**
 * 검색 광고 옵션
 */
data class SearchAdOptions(
  /**
   * 광고 플랫폼 (필수)
   */
  @field:NotNull(message = "플랫폼은 필수입니다")
  val platform: SearchAdPlatformId? = null,

  /**
   * 타겟 키워드 (필수, 쉼표 구분)
   */
  @field:NotBlank(message = "타겟 키워드는 필수입니다")
  @field:Size(max = 500, message = "타겟 키워드는 500자를 초과할 수 없습니다")
  val targetKeywords: String? = null
)
