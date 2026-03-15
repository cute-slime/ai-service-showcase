package com.jongmin.ai.product_agent.platform.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 마케팅 캠페인 생성 결과 (outputDataJson에 저장)
 *
 * ProductAgentOutput.outputDataJson에 JSON으로 직렬화되어 저장됩니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MarketingCampaignOutputData(
  /** 제품명 */
  val productName: String,

  /** 카테고리 */
  val category: String,

  /** 타겟 고객층 */
  val targetAudience: String?,

  /** 선택된 도구 목록 */
  val selectedTools: List<String>,

  /** 도구별 결과 */
  val toolResults: List<ToolResultData>
)

/**
 * 도구별 결과 데이터
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolResultData(
  /** 도구 ID */
  val toolId: String,

  /** 성공 여부 */
  val success: Boolean,

  /** 도구별 결과 (성공 시) */
  val result: Any? = null,

  /** 에러 메시지 (실패 시) */
  val errorMessage: String? = null
)

// ==================== 배너 광고 결과 ====================

/**
 * 배너 광고 결과
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BannerAdResult(
  /** 결과 고유 ID */
  val id: String,

  /** 생성된 배너 이미지 URL */
  val generatedImageUrl: String?,

  /** 생성된 배너 이미지 영구 key */
  val generatedImageKey: String? = null,

  /** 썸네일 URL */
  val thumbnailUrl: String?,

  /** 메인 헤드라인 */
  val headline: String,

  /** 서브 헤드라인 */
  val subHeadline: String,

  /** CTA 버튼 텍스트 */
  val callToAction: String,

  /** 배너 사이즈 */
  val bannerSize: String,

  /** 배너 크기 (width, height) */
  val dimensions: Dimensions,

  /** 변형 3개 */
  val variations: List<BannerAdVariation>,

  /** 생성 시각 (Unix timestamp) */
  val createdAt: Long
)

/**
 * 배너 크기
 */
data class Dimensions(
  val width: Int,
  val height: Int
)

/**
 * 배너 광고 변형
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BannerAdVariation(
  /** 생성된 이미지 URL */
  val generatedImageUrl: String?,

  /** 헤드라인 */
  val headline: String,

  /** 서브 헤드라인 */
  val subHeadline: String,

  /** CTA 텍스트 */
  val callToAction: String
)

// ==================== 인스타그램 피드 결과 ====================

/**
 * 인스타그램 피드 결과
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InstagramFeedResult(
  /** 결과 고유 ID */
  val id: String,

  /** 생성된 피드 이미지 URL (1080x1080) */
  val generatedImageUrl: String?,

  /** 생성된 피드 이미지 영구 key */
  val generatedImageKey: String? = null,

  /** 썸네일 URL */
  val thumbnailUrl: String?,

  /** 캡션 전문 */
  val caption: String,

  /** 해시태그 배열 */
  val hashtags: List<String>,

  /** 첫 줄 후킹 문구 */
  val hookLine: String,

  /** 미리보기 데이터 */
  val previewData: InstagramFeedPreview,

  /** 변형 3개 */
  val variations: List<InstagramFeedVariation>,

  /** 생성 시각 (Unix timestamp) */
  val createdAt: Long
)

/**
 * 인스타그램 피드 미리보기 데이터
 */
data class InstagramFeedPreview(
  /** 미리보기 캡션 (125자 + "...더 보기") */
  val displayCaption: String,

  /** 해시태그 개수 */
  val hashtagCount: Int
)

/**
 * 인스타그램 피드 변형
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InstagramFeedVariation(
  /** 생성된 이미지 URL */
  val generatedImageUrl: String?,

  /** 캡션 */
  val caption: String,

  /** 후킹 문구 */
  val hookLine: String
)

// ==================== 인스타그램 스토리 결과 ====================

/**
 * 인스타그램 스토리/릴스 결과
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InstagramStoryResult(
  /** 결과 고유 ID */
  val id: String,

  /** 콘텐츠 타입 (story/reels) */
  val contentType: String,

  /** 생성된 스토리 이미지 URL (1080x1920) */
  val generatedImageUrl: String?,

  /** 생성된 스토리 이미지 영구 key */
  val generatedImageKey: String? = null,

  /** 썸네일 URL */
  val thumbnailUrl: String?,

  /** 후킹 텍스트 (최대 15자) */
  val hookText: String,

  /** 메인 텍스트 (최대 30자) */
  val mainText: String,

  /** CTA 텍스트 */
  val ctaText: String,

  /** 변형 3개 */
  val variations: List<InstagramStoryVariation>,

  /** 생성 시각 (Unix timestamp) */
  val createdAt: Long
)

/**
 * 인스타그램 스토리 변형
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InstagramStoryVariation(
  /** 생성된 이미지 URL */
  val generatedImageUrl: String?,

  /** 후킹 텍스트 */
  val hookText: String,

  /** 메인 텍스트 */
  val mainText: String,

  /** CTA 텍스트 */
  val ctaText: String
)

// ==================== 검색 광고 결과 ====================

/**
 * 검색 광고 결과
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SearchAdResult(
  /** 결과 고유 ID */
  val id: String,

  /** 플랫폼 */
  val platform: String,

  /** 네이버 광고 (네이버 선택 시) */
  val naverAd: NaverSearchAd?,

  /** 구글 광고 (구글 선택 시) */
  val googleAd: GoogleSearchAd?,

  /** 변형 3개 */
  val variations: List<SearchAdVariation>,

  /** 미리보기 데이터 */
  val previewData: SearchAdPreview,

  /** 생성 시각 (Unix timestamp) */
  val createdAt: Long
)

/**
 * 네이버 검색광고
 */
data class NaverSearchAd(
  /** 제목 (최대 25자) */
  val title: String,

  /** 설명 (최대 45자) */
  val description: String,

  /** 사이트링크 */
  val sitelinks: List<String>
)

/**
 * 구글 검색광고
 */
data class GoogleSearchAd(
  /** 헤드라인 (최대 30자 x 3개) */
  val headlines: List<String>,

  /** 설명 (최대 90자 x 2개) */
  val descriptions: List<String>,

  /** 콜아웃 확장 */
  val callouts: List<String>
)

/**
 * 검색 광고 변형
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SearchAdVariation(
  /** 네이버 광고 변형 */
  val naverAd: NaverSearchAdVariation?,

  /** 구글 광고 변형 */
  val googleAd: GoogleSearchAdVariation?
)

/**
 * 네이버 검색광고 변형
 */
data class NaverSearchAdVariation(
  val title: String,
  val description: String
)

/**
 * 구글 검색광고 변형
 */
data class GoogleSearchAdVariation(
  val headlines: List<String>,
  val descriptions: List<String>
)

/**
 * 검색 광고 미리보기
 */
data class SearchAdPreview(
  /** 표시 URL */
  val displayUrl: String
)

// ==================== SSE 이벤트 ====================

/**
 * TOOL_RESULT SSE 이벤트 데이터
 *
 * 각 도구의 생성이 완료될 때마다 전송됩니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ToolResultEvent(
  /** 이벤트 타입 */
  val type: String = "TOOL_RESULT",

  /** 도구 ID */
  val toolId: String,

  /** 현재 도구 순번 (1-based) */
  val toolIndex: Int,

  /** 전체 도구 수 */
  val totalTools: Int,

  /** 상태 (COMPLETED / FAILED) */
  val status: String,

  /** 도구별 결과 (성공 시) */
  val result: Any?,

  /** 에러 메시지 (실패 시) */
  val errorMessage: String?,

  /** 타임스탬프 */
  val timestamp: Long = System.currentTimeMillis()
)

/**
 * 마케팅 캠페인 완료 SSE 이벤트 데이터
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MarketingCampaignCompletedEvent(
  /** 이벤트 타입 */
  val type: String = "COMPLETED",

  /** 상태 */
  val status: String = "COMPLETED",

  /** 완료 메시지 */
  val message: String = "마케팅 캠페인 생성이 완료되었습니다.",

  /** 출력물 ID */
  val outputId: Long,

  /** 요약 정보 */
  val summary: MarketingCampaignSummary,

  /** 타임스탬프 */
  val timestamp: Long = System.currentTimeMillis()
)

/**
 * 마케팅 캠페인 요약 정보
 */
data class MarketingCampaignSummary(
  /** 전체 도구 수 */
  val totalTools: Int,

  /** 성공한 도구 수 */
  val successCount: Int,

  /** 실패한 도구 수 */
  val failedCount: Int
)
