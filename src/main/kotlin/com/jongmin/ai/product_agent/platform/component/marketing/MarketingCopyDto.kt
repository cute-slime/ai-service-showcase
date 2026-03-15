package com.jongmin.ai.product_agent.platform.component.marketing

import com.jongmin.ai.product_agent.SearchAdPlatformId
import com.jongmin.ai.product_agent.platform.dto.response.*

/**
 * 마케팅 카피 생성 결과 데이터 클래스
 *
 * MarketingCopyGenerator가 반환하는 도구별 카피 결과 DTO
 *
 * @author Claude Code
 * @since 2026.02.19 (MarketingCopyGenerator에서 분리)
 */

// ==================== 배너 광고 ====================

data class BannerAdCopyResult(
  val headline: String,
  val subHeadline: String,
  val callToAction: String,
  val variations: List<BannerAdCopyVariation>,
) {
  companion object {
    fun fallback(productName: String, defaultCta: String?): BannerAdCopyResult {
      return BannerAdCopyResult(
        headline = "${productName}의 새로운 발견",
        subHeadline = "지금 바로 만나보세요",
        callToAction = defaultCta ?: "자세히 보기",
        variations = listOf(
          BannerAdCopyVariation("$productName 특별 혜택", "놓치지 마세요", "지금 구매"),
          BannerAdCopyVariation("$productName 추천", "베스트셀러", "더 알아보기"),
          BannerAdCopyVariation("NEW $productName", "신상품 출시", "바로가기")
        )
      )
    }
  }
}

data class BannerAdCopyVariation(
  val headline: String,
  val subHeadline: String,
  val callToAction: String,
)

// ==================== 인스타그램 피드 ====================

data class InstagramFeedCopyResult(
  val caption: String,
  val hashtags: List<String>,
  val hookLine: String,
  val variations: List<InstagramFeedCopyVariation>,
) {
  companion object {
    fun fallback(productName: String, includeEmoji: Boolean): InstagramFeedCopyResult {
      val emoji = if (includeEmoji) "✨ " else ""
      return InstagramFeedCopyResult(
        caption = "${emoji}${productName}을 소개합니다!\n\n당신의 일상을 더 특별하게 만들어줄 제품이에요.",
        hashtags = listOf("#$productName", "#신상", "#추천", "#데일리"),
        hookLine = "${emoji}${productName}의 새로운 매력",
        variations = listOf(
          InstagramFeedCopyVariation("${emoji}${productName} 꼭 써봐야 해요!", "${emoji}대박템 발견"),
          InstagramFeedCopyVariation("${emoji}${productName} 리뷰!", "${emoji}솔직 후기"),
          InstagramFeedCopyVariation("${emoji}${productName} 언박싱", "${emoji}드디어 도착")
        )
      )
    }
  }
}

data class InstagramFeedCopyVariation(
  val caption: String,
  val hookLine: String,
)

// ==================== 인스타그램 스토리 ====================

data class InstagramStoryCopyResult(
  val hookText: String,
  val mainText: String,
  val ctaText: String,
  val variations: List<InstagramStoryCopyVariation>,
) {
  companion object {
    fun fallback(productName: String, defaultCta: String?): InstagramStoryCopyResult {
      return InstagramStoryCopyResult(
        hookText = "이거 봤어? 👀",
        mainText = "$productName 대박템!",
        ctaText = defaultCta ?: "위로 스와이프",
        variations = listOf(
          InstagramStoryCopyVariation("필수템 발견!", productName, "바로가기"),
          InstagramStoryCopyVariation("놓치면 후회", "$productName!", "더보기"),
          InstagramStoryCopyVariation("이건 사야해", "NEW $productName", "구매하기")
        )
      )
    }
  }
}

data class InstagramStoryCopyVariation(
  val hookText: String,
  val mainText: String,
  val ctaText: String,
)

// ==================== 검색 광고 ====================

data class SearchAdCopyResult(
  val naverAd: NaverSearchAd?,
  val googleAd: GoogleSearchAd?,
  val variations: List<SearchAdVariation>,
) {
  companion object {
    fun fallback(productName: String, platform: SearchAdPlatformId): SearchAdCopyResult {
      val isNaver = platform == SearchAdPlatformId.NAVER
      return SearchAdCopyResult(
        naverAd = if (isNaver) NaverSearchAd(
          title = "$productName | 공식 스토어",
          description = "지금 바로 만나보세요. 특별한 혜택까지!",
          sitelinks = listOf("상품 보기", "리뷰", "이벤트")
        ) else null,
        googleAd = if (!isNaver) GoogleSearchAd(
          headlines = listOf("$productName - 공식 스토어", "특별 할인 진행 중", "지금 바로 구매하세요"),
          descriptions = listOf("$productName 을 만나보세요.", "공식 스토어에서만 제공되는 특별한 혜택"),
          callouts = listOf("무료배송", "정품보장", "빠른배송", "포인트적립")
        ) else null,
        variations = (1..3).map { idx ->
          SearchAdVariation(
            naverAd = if (isNaver) NaverSearchAdVariation("[변형$idx] $productName", "[변형$idx] 지금 확인") else null,
            googleAd = if (!isNaver) GoogleSearchAdVariation(
              listOf("[변형$idx] $productName", "특별 혜택", "지금 구매"),
              listOf("[변형$idx] 설명문구", "추가 설명")
            ) else null
          )
        }
      )
    }
  }
}
