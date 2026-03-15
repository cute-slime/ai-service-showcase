package com.jongmin.ai.product_agent.platform.component.marketing

import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.jspring.core.util.generateUuid
import com.jongmin.ai.product_agent.SearchAdPlatformId
import com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignData
import com.jongmin.ai.product_agent.platform.dto.response.*
import com.jongmin.ai.storage.StorageServiceClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 마케팅 캠페인 도구 실행기 (오케스트레이터)
 *
 * 각 마케팅 도구(배너, 인스타 피드/스토리, 검색광고)의 콘텐츠 생성을 총괄합니다.
 * 도구별로 LLM 카피 생성 → 이미지 생성(필요 시) → 결과 조립 순서로 진행됩니다.
 *
 * ### 주요 기능:
 * - 배너 광고: 헤드라인/서브헤드라인 카피 + 이미지 생성
 * - 인스타그램 피드: 캡션/해시태그 + 1080x1080 이미지 생성
 * - 인스타그램 스토리: 후킹 텍스트 + 1080x1920 이미지 생성
 * - 검색 광고: 플랫폼별(네이버/구글) 텍스트 카피만 생성
 *
 * ### 테스트 모드:
 * `TEST_MODE = true` 시 LLM/이미지 생성 없이 더미 데이터를 반환합니다.
 *
 * @property copyGenerator LLM 기반 마케팅 카피 생성기
 * @property imageGenerator 마케팅 이미지 생성기
 */
@Component
class MarketingCampaignToolExecutor(
  private val copyGenerator: MarketingCopyGenerator,
  private val imageGenerator: MarketingImageGenerator,
  private val storageServiceClient: StorageServiceClient,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 테스트 모드 플래그 - true면 더미 데이터 반환, 실제 구현 완료 후 false로 변경 */
    const val TEST_MODE = true

    /** 변형(Variation) 생성 개수 */
    private const val VARIATION_COUNT = 3

    private const val SYSTEM_ACCOUNT_ID = 0L
  }

  // ==================== 배너 광고 ====================

  /**
   * 배너 광고 생성
   *
   * @param data 마케팅 캠페인 데이터
   * @param productImageKey 제품 이미지 S3 키 (참조용)
   * @return 배너 광고 생성 결과
   */
  fun executeBannerAd(
    data: MarketingCampaignData,
    productImageKey: String?,
  ): BannerAdResult {
    val startTime = System.currentTimeMillis()
    val resultId = generateUuid()
    val commonInput = data.commonInput!!
    val options = data.bannerAdOptions!!

    kLogger.info { "배너 광고 생성 시작 - productName: ${commonInput.productName}, size: ${options.bannerSize}" }

    // 테스트 모드인 경우 더미 데이터 반환
    if (TEST_MODE) {
      return createDummyBannerAdResult(resultId, commonInput, options)
    }

    // 1. LLM으로 카피 생성
    val copyResult = copyGenerator.generateBannerAdCopy(
      productName = commonInput.productName!!,
      category = commonInput.category!!,
      shortDescription = commonInput.shortDescription,
      targetAudience = commonInput.targetAudience,
      promotionInfo = commonInput.promotionInfo,
      ctaText = commonInput.ctaText,
      toneAndManner = options.toneAndManner!!,
      variationCount = VARIATION_COUNT
    )

    // 2. 이미지 생성
    val (width, height) = options.bannerSize!!.let { it.width to it.height }
    val imageResult = imageGenerator.generateBannerImage(
      productName = commonInput.productName,
      productImageKey = productImageKey,
      copyHeadline = copyResult.headline,
      width = width,
      height = height,
      toneAndManner = options.toneAndManner
    )
    val committedImageResult = commitGeneratedImageIfNeeded(imageResult)

    // 3. 결과 조립
    val duration = System.currentTimeMillis() - startTime
    kLogger.info { "배너 광고 생성 완료 - duration: ${duration}ms" }

    return BannerAdResult(
      id = resultId,
      generatedImageUrl = committedImageResult.imageUrl,
      generatedImageKey = committedImageResult.s3Key,
      thumbnailUrl = committedImageResult.thumbnailUrl,
      headline = copyResult.headline,
      subHeadline = copyResult.subHeadline,
      callToAction = copyResult.callToAction,
      bannerSize = options.bannerSize.code(),
      dimensions = Dimensions(width, height),
      variations = copyResult.variations.map { variation ->
        BannerAdVariation(
          generatedImageUrl = null, // 변형은 이미지 생성 안 함
          headline = variation.headline,
          subHeadline = variation.subHeadline,
          callToAction = variation.callToAction
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  // ==================== 인스타그램 피드 ====================

  /**
   * 인스타그램 피드 생성
   *
   * @param data 마케팅 캠페인 데이터
   * @param productImageKey 제품 이미지 S3 키
   * @return 인스타그램 피드 생성 결과
   */
  fun executeInstagramFeed(
    data: MarketingCampaignData,
    productImageKey: String?,
  ): InstagramFeedResult {
    val startTime = System.currentTimeMillis()
    val resultId = generateUuid()
    val commonInput = data.commonInput!!
    val options = data.instagramFeedOptions!!

    kLogger.info { "인스타그램 피드 생성 시작 - productName: ${commonInput.productName}" }

    // 테스트 모드인 경우 더미 데이터 반환
    if (TEST_MODE) {
      return createDummyInstagramFeedResult(resultId, commonInput, options)
    }

    // 1. LLM으로 캡션/해시태그 생성
    val copyResult = copyGenerator.generateInstagramFeedCopy(
      productName = commonInput.productName!!,
      category = commonInput.category!!,
      shortDescription = commonInput.shortDescription,
      targetAudience = commonInput.targetAudience,
      promotionInfo = commonInput.promotionInfo,
      hashtags = options.hashtags,
      includeEmoji = options.includeEmoji,
      captionLength = options.captionLength!!,
      variationCount = VARIATION_COUNT
    )

    // 2. 이미지 생성 (1080x1080 고정)
    val imageResult = imageGenerator.generateInstagramFeedImage(
      productName = commonInput.productName,
      productImageKey = productImageKey,
      hookLine = copyResult.hookLine
    )
    val committedImageResult = commitGeneratedImageIfNeeded(imageResult)

    // 3. 결과 조립
    val duration = System.currentTimeMillis() - startTime
    kLogger.info { "인스타그램 피드 생성 완료 - duration: ${duration}ms" }

    return InstagramFeedResult(
      id = resultId,
      generatedImageUrl = committedImageResult.imageUrl,
      generatedImageKey = committedImageResult.s3Key,
      thumbnailUrl = committedImageResult.thumbnailUrl,
      caption = copyResult.caption,
      hashtags = copyResult.hashtags,
      hookLine = copyResult.hookLine,
      previewData = InstagramFeedPreview(
        displayCaption = truncateCaption(copyResult.caption, 125),
        hashtagCount = copyResult.hashtags.size
      ),
      variations = copyResult.variations.map { variation ->
        InstagramFeedVariation(
          generatedImageUrl = null,
          caption = variation.caption,
          hookLine = variation.hookLine
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  // ==================== 인스타그램 스토리 ====================

  /**
   * 인스타그램 스토리/릴스 생성
   *
   * @param data 마케팅 캠페인 데이터
   * @param productImageKey 제품 이미지 S3 키
   * @return 인스타그램 스토리 생성 결과
   */
  fun executeInstagramStory(
    data: MarketingCampaignData,
    productImageKey: String?,
  ): InstagramStoryResult {
    val startTime = System.currentTimeMillis()
    val resultId = generateUuid()
    val commonInput = data.commonInput!!
    val options = data.instagramStoryOptions!!

    kLogger.info { "인스타그램 스토리 생성 시작 - productName: ${commonInput.productName}, type: ${options.contentType}" }

    // 테스트 모드인 경우 더미 데이터 반환
    if (TEST_MODE) {
      return createDummyInstagramStoryResult(resultId, commonInput, options)
    }

    // 1. LLM으로 후킹 텍스트 생성
    val copyResult = copyGenerator.generateInstagramStoryCopy(
      productName = commonInput.productName!!,
      category = commonInput.category!!,
      shortDescription = commonInput.shortDescription,
      targetAudience = commonInput.targetAudience,
      promotionInfo = commonInput.promotionInfo,
      ctaText = commonInput.ctaText,
      contentType = options.contentType!!,
      mood = options.mood!!,
      variationCount = VARIATION_COUNT
    )

    // 2. 이미지 생성 (1080x1920 고정)
    val imageResult = imageGenerator.generateInstagramStoryImage(
      productName = commonInput.productName,
      productImageKey = productImageKey,
      hookText = copyResult.hookText,
      mood = options.mood
    )
    val committedImageResult = commitGeneratedImageIfNeeded(imageResult)

    // 3. 결과 조립
    val duration = System.currentTimeMillis() - startTime
    kLogger.info { "인스타그램 스토리 생성 완료 - duration: ${duration}ms" }

    return InstagramStoryResult(
      id = resultId,
      contentType = options.contentType.code(),
      generatedImageUrl = committedImageResult.imageUrl,
      generatedImageKey = committedImageResult.s3Key,
      thumbnailUrl = committedImageResult.thumbnailUrl,
      hookText = copyResult.hookText,
      mainText = copyResult.mainText,
      ctaText = copyResult.ctaText,
      variations = copyResult.variations.map { variation ->
        InstagramStoryVariation(
          generatedImageUrl = null,
          hookText = variation.hookText,
          mainText = variation.mainText,
          ctaText = variation.ctaText
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  private fun commitGeneratedImageIfNeeded(imageResult: MarketingImageResult): MarketingImageResult {
    val stagedKey = imageResult.s3Key?.takeIf { it.isNotBlank() } ?: return imageResult
    if (!stagedKey.startsWith("_tmp/")) {
      return imageResult
    }

    val response = storageServiceClient.commit(
      keys = listOf(stagedKey),
      accountId = SYSTEM_ACCOUNT_ID,
      referenceType = ProductAgentOutputType.MARKETING_CAMPAIGN.name
    )
    val permanentKey = response.committed
      .firstOrNull { it.key == stagedKey && it.success }
      ?.permanentKey
      ?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("마케팅 캠페인 이미지 스토리지 확정 실패: $stagedKey")

    return imageResult.copy(
      s3Key = permanentKey,
      imageUrl = storageServiceClient.issueAccessUrl(permanentKey)
    )
  }

  // ==================== 검색 광고 ====================

  /**
   * 검색 광고 생성 (이미지 없음)
   *
   * @param data 마케팅 캠페인 데이터
   * @return 검색 광고 생성 결과
   */
  fun executeSearchAd(data: MarketingCampaignData): SearchAdResult {
    val startTime = System.currentTimeMillis()
    val resultId = generateUuid()
    val commonInput = data.commonInput!!
    val options = data.searchAdOptions!!

    kLogger.info { "검색 광고 생성 시작 - productName: ${commonInput.productName}, platform: ${options.platform}" }

    // 테스트 모드인 경우 더미 데이터 반환
    if (TEST_MODE) {
      return createDummySearchAdResult(resultId, commonInput, options)
    }

    // 1. LLM으로 검색 광고 카피 생성
    val copyResult = copyGenerator.generateSearchAdCopy(
      productName = commonInput.productName!!,
      category = commonInput.category!!,
      shortDescription = commonInput.shortDescription,
      targetAudience = commonInput.targetAudience,
      promotionInfo = commonInput.promotionInfo,
      ctaText = commonInput.ctaText,
      platform = options.platform!!,
      targetKeywords = options.targetKeywords!!,
      variationCount = VARIATION_COUNT
    )

    // 2. 결과 조립
    val duration = System.currentTimeMillis() - startTime
    kLogger.info { "검색 광고 생성 완료 - duration: ${duration}ms" }

    return SearchAdResult(
      id = resultId,
      platform = options.platform.code(),
      naverAd = if (options.platform == SearchAdPlatformId.NAVER) {
        copyResult.naverAd
      } else null,
      googleAd = if (options.platform == SearchAdPlatformId.GOOGLE) {
        copyResult.googleAd
      } else null,
      variations = copyResult.variations,
      previewData = SearchAdPreview(
        displayUrl = "www.example.com/${commonInput.productName.take(10).replace(" ", "-").lowercase()}"
      ),
      createdAt = System.currentTimeMillis()
    )
  }

  // ==================== 더미 데이터 생성 (테스트 모드용) ====================

  /**
   * 배너 광고 더미 결과 생성
   */
  private fun createDummyBannerAdResult(
    resultId: String,
    commonInput: com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignCommonInput,
    options: com.jongmin.ai.product_agent.platform.dto.request.BannerAdOptions,
  ): BannerAdResult {
    val productName = commonInput.productName ?: "상품"
    val (width, height) = options.bannerSize!!.let { it.width to it.height }

    return BannerAdResult(
      id = resultId,
      generatedImageUrl = "https://placehold.co/${width}x${height}/png?text=Banner+Ad",
      thumbnailUrl = "https://placehold.co/200x200/png?text=Thumb",
      headline = "${productName}의 새로운 발견",
      subHeadline = commonInput.shortDescription ?: "지금 바로 만나보세요",
      callToAction = commonInput.ctaText ?: "자세히 보기",
      bannerSize = options.bannerSize.code(),
      dimensions = Dimensions(width, height),
      variations = (1..VARIATION_COUNT).map { idx ->
        BannerAdVariation(
          generatedImageUrl = null,
          headline = "[변형$idx] ${productName} 특별 혜택",
          subHeadline = "[변형$idx] ${commonInput.promotionInfo ?: "놓치지 마세요"}",
          callToAction = listOf("지금 구매", "더 알아보기", "바로가기")[idx - 1]
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  /**
   * 인스타그램 피드 더미 결과 생성
   */
  private fun createDummyInstagramFeedResult(
    resultId: String,
    commonInput: com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignCommonInput,
    options: com.jongmin.ai.product_agent.platform.dto.request.InstagramFeedOptions,
  ): InstagramFeedResult {
    val productName = commonInput.productName ?: "상품"
    val dummyCaption = buildString {
      if (options.includeEmoji) append("✨ ")
      append("${productName}을 소개합니다!\n\n")
      append(commonInput.shortDescription ?: "당신의 일상을 더 특별하게 만들어줄 제품이에요.")
      append("\n\n")
      append(commonInput.promotionInfo ?: "지금 바로 확인해보세요!")
    }
    val dummyHashtags = listOf("#${productName.replace(" ", "")}", "#신상", "#추천", "#데일리", "#인스타그램")

    return InstagramFeedResult(
      id = resultId,
      generatedImageUrl = "https://placehold.co/1080x1080/png?text=Instagram+Feed",
      thumbnailUrl = "https://placehold.co/200x200/png?text=Thumb",
      caption = dummyCaption,
      hashtags = dummyHashtags,
      hookLine = "✨ ${productName}의 새로운 매력",
      previewData = InstagramFeedPreview(
        displayCaption = truncateCaption(dummyCaption, 125),
        hashtagCount = dummyHashtags.size
      ),
      variations = (1..VARIATION_COUNT).map { idx ->
        InstagramFeedVariation(
          generatedImageUrl = null,
          caption = "[변형$idx] $dummyCaption",
          hookLine = "[변형$idx] ${productName} 꼭 써봐야 해요!"
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  /**
   * 인스타그램 스토리 더미 결과 생성
   */
  private fun createDummyInstagramStoryResult(
    resultId: String,
    commonInput: com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignCommonInput,
    options: com.jongmin.ai.product_agent.platform.dto.request.InstagramStoryOptions,
  ): InstagramStoryResult {
    val productName = commonInput.productName ?: "상품"

    return InstagramStoryResult(
      id = resultId,
      contentType = options.contentType!!.code(),
      generatedImageUrl = "https://placehold.co/1080x1920/png?text=Instagram+Story",
      thumbnailUrl = "https://placehold.co/200x355/png?text=Thumb",
      hookText = "이거 봤어? 👀",
      mainText = "${productName} 대박템!",
      ctaText = commonInput.ctaText ?: "위로 스와이프",
      variations = (1..VARIATION_COUNT).map { idx ->
        InstagramStoryVariation(
          generatedImageUrl = null,
          hookText = listOf("필수템 발견!", "놓치면 후회해요", "이건 사야해")[idx - 1],
          mainText = "[변형$idx] ${productName}",
          ctaText = listOf("바로가기", "더보기", "구매하기")[idx - 1]
        )
      },
      createdAt = System.currentTimeMillis()
    )
  }

  /**
   * 검색 광고 더미 결과 생성
   */
  private fun createDummySearchAdResult(
    resultId: String,
    commonInput: com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignCommonInput,
    options: com.jongmin.ai.product_agent.platform.dto.request.SearchAdOptions,
  ): SearchAdResult {
    val productName = commonInput.productName ?: "상품"
    val isNaver = options.platform == SearchAdPlatformId.NAVER

    return SearchAdResult(
      id = resultId,
      platform = options.platform!!.code(),
      naverAd = if (isNaver) {
        NaverSearchAd(
          title = "${productName} | 공식 스토어",
          description = commonInput.shortDescription?.take(45) ?: "지금 바로 만나보세요. 특별한 혜택까지!",
          sitelinks = listOf("상품 보기", "리뷰", "이벤트")
        )
      } else null,
      googleAd = if (!isNaver) {
        GoogleSearchAd(
          headlines = listOf(
            "${productName} - 공식 스토어",
            commonInput.promotionInfo?.take(30) ?: "특별 할인 진행 중",
            commonInput.ctaText ?: "지금 바로 구매하세요"
          ),
          descriptions = listOf(
            commonInput.shortDescription?.take(90) ?: "당신의 라이프스타일을 업그레이드할 ${productName}을 만나보세요.",
            commonInput.promotionInfo?.take(90) ?: "공식 스토어에서만 제공되는 특별한 혜택을 놓치지 마세요."
          ),
          callouts = listOf("무료배송", "정품보장", "빠른배송", "포인트적립")
        )
      } else null,
      variations = (1..VARIATION_COUNT).map { idx ->
        SearchAdVariation(
          naverAd = if (isNaver) {
            NaverSearchAdVariation(
              title = "[변형$idx] ${productName}",
              description = "[변형$idx] 지금 확인하세요"
            )
          } else null,
          googleAd = if (!isNaver) {
            GoogleSearchAdVariation(
              headlines = listOf("[변형$idx] ${productName}", "특별 혜택", "지금 구매"),
              descriptions = listOf("[변형$idx] 설명문구입니다.", "추가 설명문구입니다.")
            )
          } else null
        )
      },
      previewData = SearchAdPreview(
        displayUrl = "www.example.com/${productName.take(10).replace(" ", "-").lowercase()}"
      ),
      createdAt = System.currentTimeMillis()
    )
  }

  // ==================== 유틸리티 ====================

  /**
   * 캡션을 지정된 길이로 잘라 "...더 보기" 추가
   */
  private fun truncateCaption(caption: String, maxLength: Int): String {
    return if (caption.length <= maxLength) {
      caption
    } else {
      "${caption.take(maxLength)}...더 보기"
    }
  }
}
