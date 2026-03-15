package com.jongmin.ai.product_agent.platform.component.marketing

import com.jongmin.ai.product_agent.MarketingCategoryId

/**
 * 마케팅 카피 생성용 시스템/유저 프롬프트 상수 및 빌더
 *
 * @author Claude Code
 * @since 2026.02.19 (MarketingCopyGenerator에서 분리)
 */
object MarketingCopyPrompts {

  /** LLM 호출 타임아웃 (초) */
  const val GENERATION_TIMEOUT_SECONDS = 60L

  /** 배너 광고 시스템 프롬프트 */
  val BANNER_AD_SYSTEM = """
You are an expert marketing copywriter specializing in banner advertisement copy.
Create compelling, concise copy that captures attention and drives action.

## Guidelines:
1. Headlines should be punchy and memorable (max 20 characters for Korean)
2. Sub-headlines provide supporting context (max 40 characters for Korean)
3. CTAs should be action-oriented and urgent

## Response Format (JSON only):
{
  "headline": "메인 헤드라인",
  "subHeadline": "서브 헤드라인",
  "callToAction": "CTA 버튼 텍스트",
  "variations": [
    {"headline": "변형1 헤드라인", "subHeadline": "변형1 서브헤드라인", "callToAction": "CTA1"},
    {"headline": "변형2 헤드라인", "subHeadline": "변형2 서브헤드라인", "callToAction": "CTA2"},
    {"headline": "변형3 헤드라인", "subHeadline": "변형3 서브헤드라인", "callToAction": "CTA3"}
  ]
}
  """.trimIndent()

  /** 인스타그램 피드 시스템 프롬프트 */
  val INSTAGRAM_FEED_SYSTEM = """
You are an expert social media copywriter specializing in Instagram feed content.
Create engaging captions that resonate with the target audience and drive engagement.

## Guidelines:
1. Hook line should grab attention in the first line (shown in preview)
2. Caption should tell a story and connect emotionally
3. Include relevant hashtags (5-10) for discoverability
4. Use emojis strategically if requested

## Response Format (JSON only):
{
  "caption": "전체 캡션 텍스트",
  "hashtags": ["해시태그1", "해시태그2", "해시태그3"],
  "hookLine": "첫 줄 후킹 문구",
  "variations": [
    {"caption": "변형1 캡션", "hookLine": "변형1 후킹"},
    {"caption": "변형2 캡션", "hookLine": "변형2 후킹"},
    {"caption": "변형3 캡션", "hookLine": "변형3 후킹"}
  ]
}
  """.trimIndent()

  /** 인스타그램 스토리 시스템 프롬프트 */
  val INSTAGRAM_STORY_SYSTEM = """
You are an expert social media copywriter specializing in Instagram Stories and Reels.
Create short, impactful text overlays that capture attention in seconds.

## Guidelines:
1. Hook text: Maximum 15 characters, must grab attention instantly
2. Main text: Maximum 30 characters, convey the key message
3. CTA text: Clear call-to-action for swipe-up or link clicks

## Response Format (JSON only):
{
  "hookText": "후킹 텍스트 (15자 이내)",
  "mainText": "메인 텍스트 (30자 이내)",
  "ctaText": "CTA 텍스트",
  "variations": [
    {"hookText": "변형1 후킹", "mainText": "변형1 메인", "ctaText": "변형1 CTA"},
    {"hookText": "변형2 후킹", "mainText": "변형2 메인", "ctaText": "변형2 CTA"},
    {"hookText": "변형3 후킹", "mainText": "변형3 메인", "ctaText": "변형3 CTA"}
  ]
}
  """.trimIndent()

  /** 검색 광고 시스템 프롬프트 */
  val SEARCH_AD_SYSTEM = """
You are an expert SEM (Search Engine Marketing) copywriter.
Create high-converting search ad copy optimized for click-through rates.

## Platform Specifications:
### Naver Search Ad:
- Title: Maximum 25 characters (Korean)
- Description: Maximum 45 characters (Korean)
- Sitelinks: 3 short link texts

### Google Search Ad:
- Headlines: 3 headlines, each max 30 characters
- Descriptions: 2 descriptions, each max 90 characters
- Callouts: 4 short benefit highlights

## Response Format (JSON only):
{
  "naverAd": {
    "title": "네이버 제목",
    "description": "네이버 설명",
    "sitelinks": ["링크1", "링크2", "링크3"]
  },
  "googleAd": {
    "headlines": ["헤드라인1", "헤드라인2", "헤드라인3"],
    "descriptions": ["설명1", "설명2"],
    "callouts": ["콜아웃1", "콜아웃2", "콜아웃3", "콜아웃4"]
  },
  "variations": [
    {
      "naverAd": {"title": "변형1 제목", "description": "변형1 설명"},
      "googleAd": {"headlines": ["변형1 헤드라인1", "변형1 헤드라인2", "변형1 헤드라인3"], "descriptions": ["변형1 설명1", "변형1 설명2"]}
    }
  ]
}
  """.trimIndent()

  /**
   * 공통 상품 정보 프롬프트 빌더
   *
   * 모든 광고 타입에서 공유하는 상품 기본 정보 섹션
   */
  fun buildProductInfoSection(
    productName: String,
    category: MarketingCategoryId,
    shortDescription: String?,
    targetAudience: String?,
    promotionInfo: String?,
    variationCount: Int,
  ): String {
    return buildString {
      appendLine("**Product Name:** $productName")
      appendLine("**Category:** ${category.description}")
      shortDescription?.let { appendLine("**Short Description:** $it") }
      targetAudience?.let { appendLine("**Target Audience:** $it") }
      promotionInfo?.let { appendLine("**Promotion Info:** $it") }
      appendLine()
      appendLine("Generate 1 main copy + $variationCount variations.")
      appendLine("Write all copy in Korean.")
    }
  }
}
