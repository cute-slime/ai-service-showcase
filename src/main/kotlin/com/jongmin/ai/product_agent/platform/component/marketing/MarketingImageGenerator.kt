package com.jongmin.ai.product_agent.platform.component.marketing

import com.jongmin.ai.core.platform.component.gateway.ImageGenerationGateway
import com.jongmin.ai.product_agent.MoodId
import com.jongmin.ai.product_agent.ToneAndMannerId
import com.jongmin.ai.product_agent.platform.component.prompt.ImagePromptGenerator
import com.jongmin.ai.storage.S3Service
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 마케팅 이미지 생성기
 *
 * 마케팅 캠페인의 각 도구별 이미지를 생성합니다.
 * ImagePromptGenerator와 ImageGenerationGateway를 활용합니다.
 *
 * ### 지원 이미지:
 * - 배너 광고: 다양한 사이즈 (square, landscape, portrait, wide, story)
 * - 인스타그램 피드: 1080x1080 고정
 * - 인스타그램 스토리: 1080x1920 고정
 *
 * ### 이미지 생성 흐름:
 * 1. 상품 정보 + 카피 기반으로 프롬프트 생성 (ImagePromptGenerator)
 * 2. ImageGenerationGateway 경유 이미지 생성 (자동 추적)
 * 3. S3 업로드 및 URL 반환
 *
 * @property promptGenerator 이미지 프롬프트 생성기
 * @property imageGenerationGateway 이미지 생성 게이트웨이 (추적 자동 적용)
 * @property s3Service S3 업로드 서비스
 */
@Component
class MarketingImageGenerator(
  private val promptGenerator: ImagePromptGenerator,
  private val imageGenerationGateway: ImageGenerationGateway,
  private val s3Service: S3Service,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 마케팅 이미지 저장 경로 프리픽스 */
    private const val OUTPUT_PATH_PREFIX = "marketing-campaign/generated"

    /** 인스타그램 피드 사이즈 */
    private const val INSTAGRAM_FEED_WIDTH = 1080
    private const val INSTAGRAM_FEED_HEIGHT = 1080

    /** 인스타그램 스토리 사이즈 */
    private const val INSTAGRAM_STORY_WIDTH = 1080
    private const val INSTAGRAM_STORY_HEIGHT = 1920

    /** 썸네일 사이즈 */
    private const val THUMBNAIL_WIDTH = 200
    private const val THUMBNAIL_HEIGHT = 200
  }

  // ==================== 배너 이미지 생성 ====================

  /**
   * 배너 광고 이미지 생성
   *
   * @param productName 상품명
   * @param productImageKey 참조 상품 이미지 S3 키 (선택)
   * @param copyHeadline 헤드라인 카피 (프롬프트에 반영)
   * @param width 이미지 너비
   * @param height 이미지 높이
   * @param toneAndManner 톤앤매너
   * @return 이미지 생성 결과
   */
  fun generateBannerImage(
    productName: String,
    productImageKey: String?,
    copyHeadline: String,
    width: Int,
    height: Int,
    toneAndManner: ToneAndMannerId,
  ): MarketingImageResult {
    kLogger.info { "배너 이미지 생성 시작 - productName: $productName, size: ${width}x${height}" }

    return try {
      // 1. 프롬프트 생성
      val userPrompt = buildBannerImagePrompt(productName, copyHeadline, toneAndManner)
      val promptResult = promptGenerator.generate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = toneAndMannerToStyle(toneAndManner),
        aspectRatio = calculateAspectRatio(width, height)
      )

      // 2. ImageGenerationGateway 경유 이미지 생성 (자동 추적)
      val result = imageGenerationGateway.generate(
        prompt = promptResult.positivePrompt,
        negativePrompt = promptResult.negativePrompt,
        width = width,
        height = height,
        callerComponent = "MarketingImageGenerator.generateBannerImage",
        metadata = mapOf(
          "type" to "banner_ad",
          "productName" to productName,
          "toneAndManner" to toneAndManner.code()
        )
      )

      if (result.success && result.hasImage) {
        // 3. S3 업로드
        val s3Key = s3Service.uploadImageToTempAndGetKey(
          bytes = result.imageBytes!!,
          pathPrefix = "$OUTPUT_PATH_PREFIX/banner",
          contentType = "image/png"
        )
        val imageUrl = s3Service.generateGetPresignedUrl(s3Key)

        kLogger.info { "배너 이미지 생성 완료 - s3Key: $s3Key" }

        MarketingImageResult(
          success = true,
          s3Key = s3Key,
          imageUrl = imageUrl,
          thumbnailUrl = generateThumbnailUrl(width, height),
          width = width,
          height = height
        )
      } else {
        kLogger.warn { "배너 이미지 생성 실패 - error: ${result.errorMessage}" }
        MarketingImageResult.failure(result.errorMessage ?: "이미지 생성 실패")
      }
    } catch (e: Exception) {
      kLogger.error(e) { "배너 이미지 생성 중 오류 발생" }
      MarketingImageResult.failure(e.message ?: "알 수 없는 오류")
    }
  }

  // ==================== 인스타그램 피드 이미지 생성 ====================

  /**
   * 인스타그램 피드 이미지 생성 (1080x1080)
   *
   * @param productName 상품명
   * @param productImageKey 참조 상품 이미지 S3 키 (선택)
   * @param hookLine 후킹 문구 (프롬프트에 반영)
   * @return 이미지 생성 결과
   */
  fun generateInstagramFeedImage(
    productName: String,
    productImageKey: String?,
    hookLine: String,
  ): MarketingImageResult {
    kLogger.info { "인스타그램 피드 이미지 생성 시작 - productName: $productName" }

    return try {
      // 1. 프롬프트 생성
      val userPrompt = buildInstagramFeedImagePrompt(productName, hookLine)
      val promptResult = promptGenerator.generate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = "lifestyle",
        aspectRatio = "1:1"
      )

      // 2. ImageGenerationGateway 경유 이미지 생성 (자동 추적)
      val result = imageGenerationGateway.generate(
        prompt = promptResult.positivePrompt,
        negativePrompt = promptResult.negativePrompt,
        width = INSTAGRAM_FEED_WIDTH,
        height = INSTAGRAM_FEED_HEIGHT,
        callerComponent = "MarketingImageGenerator.generateInstagramFeedImage",
        metadata = mapOf(
          "type" to "instagram_feed",
          "productName" to productName
        )
      )

      if (result.success && result.hasImage) {
        // 3. S3 업로드
        val s3Key = s3Service.uploadImageToTempAndGetKey(
          bytes = result.imageBytes!!,
          pathPrefix = "$OUTPUT_PATH_PREFIX/instagram-feed",
          contentType = "image/png"
        )
        val imageUrl = s3Service.generateGetPresignedUrl(s3Key)

        kLogger.info { "인스타그램 피드 이미지 생성 완료 - s3Key: $s3Key" }

        MarketingImageResult(
          success = true,
          s3Key = s3Key,
          imageUrl = imageUrl,
          thumbnailUrl = generateThumbnailUrl(INSTAGRAM_FEED_WIDTH, INSTAGRAM_FEED_HEIGHT),
          width = INSTAGRAM_FEED_WIDTH,
          height = INSTAGRAM_FEED_HEIGHT
        )
      } else {
        kLogger.warn { "인스타그램 피드 이미지 생성 실패 - error: ${result.errorMessage}" }
        MarketingImageResult.failure(result.errorMessage ?: "이미지 생성 실패")
      }
    } catch (e: Exception) {
      kLogger.error(e) { "인스타그램 피드 이미지 생성 중 오류 발생" }
      MarketingImageResult.failure(e.message ?: "알 수 없는 오류")
    }
  }

  // ==================== 인스타그램 스토리 이미지 생성 ====================

  /**
   * 인스타그램 스토리 이미지 생성 (1080x1920)
   *
   * @param productName 상품명
   * @param productImageKey 참조 상품 이미지 S3 키 (선택)
   * @param hookText 후킹 텍스트 (프롬프트에 반영)
   * @param mood 분위기
   * @return 이미지 생성 결과
   */
  fun generateInstagramStoryImage(
    productName: String,
    productImageKey: String?,
    hookText: String,
    mood: MoodId,
  ): MarketingImageResult {
    kLogger.info { "인스타그램 스토리 이미지 생성 시작 - productName: $productName, mood: $mood" }

    return try {
      // 1. 프롬프트 생성
      val userPrompt = buildInstagramStoryImagePrompt(productName, hookText, mood)
      val promptResult = promptGenerator.generate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = moodToStyle(mood),
        aspectRatio = "9:16"
      )

      // 2. ImageGenerationGateway 경유 이미지 생성 (자동 추적)
      val result = imageGenerationGateway.generate(
        prompt = promptResult.positivePrompt,
        negativePrompt = promptResult.negativePrompt,
        width = INSTAGRAM_STORY_WIDTH,
        height = INSTAGRAM_STORY_HEIGHT,
        callerComponent = "MarketingImageGenerator.generateInstagramStoryImage",
        metadata = mapOf(
          "type" to "instagram_story",
          "productName" to productName,
          "mood" to mood.code()
        )
      )

      if (result.success && result.hasImage) {
        // 3. S3 업로드
        val s3Key = s3Service.uploadImageToTempAndGetKey(
          bytes = result.imageBytes!!,
          pathPrefix = "$OUTPUT_PATH_PREFIX/instagram-story",
          contentType = "image/png"
        )
        val imageUrl = s3Service.generateGetPresignedUrl(s3Key)

        // 스토리용 썸네일 (9:16 비율 유지)
        val thumbnailHeight = (THUMBNAIL_WIDTH * 16) / 9

        kLogger.info { "인스타그램 스토리 이미지 생성 완료 - s3Key: $s3Key" }

        MarketingImageResult(
          success = true,
          s3Key = s3Key,
          imageUrl = imageUrl,
          thumbnailUrl = "https://placehold.co/${THUMBNAIL_WIDTH}x${thumbnailHeight}/png?text=Story",
          width = INSTAGRAM_STORY_WIDTH,
          height = INSTAGRAM_STORY_HEIGHT
        )
      } else {
        kLogger.warn { "인스타그램 스토리 이미지 생성 실패 - error: ${result.errorMessage}" }
        MarketingImageResult.failure(result.errorMessage ?: "이미지 생성 실패")
      }
    } catch (e: Exception) {
      kLogger.error(e) { "인스타그램 스토리 이미지 생성 중 오류 발생" }
      MarketingImageResult.failure(e.message ?: "알 수 없는 오류")
    }
  }

  // ==================== 프롬프트 빌더 ====================

  /**
   * 배너 이미지용 프롬프트 빌드
   */
  private fun buildBannerImagePrompt(
    productName: String,
    copyHeadline: String,
    toneAndManner: ToneAndMannerId,
  ): String {
    val toneDesc = when (toneAndManner) {
      ToneAndMannerId.FRIENDLY -> "warm, approachable, soft colors"
      ToneAndMannerId.PREMIUM -> "luxurious, elegant, sophisticated lighting"
      ToneAndMannerId.TRENDY -> "modern, bold, dynamic composition"
      ToneAndMannerId.PROFESSIONAL -> "clean, corporate, minimal design"
      ToneAndMannerId.PLAYFUL -> "fun, colorful, energetic mood"
    }

    return buildString {
      appendLine("Create a banner advertisement image for $productName.")
      appendLine("The banner should convey: \"$copyHeadline\"")
      appendLine("Style: $toneDesc")
      appendLine("Focus on the product with clean background, suitable for text overlay.")
      appendLine("Leave space for headline text placement.")
    }
  }

  /**
   * 인스타그램 피드 이미지용 프롬프트 빌드
   */
  private fun buildInstagramFeedImagePrompt(
    productName: String,
    hookLine: String,
  ): String {
    return buildString {
      appendLine("Create an Instagram feed image for $productName.")
      appendLine("The image should match the mood: \"$hookLine\"")
      appendLine("Style: lifestyle photography, Instagram-worthy aesthetic")
      appendLine("Square format (1:1), vibrant but not oversaturated.")
      appendLine("Show the product in an aspirational context.")
    }
  }

  /**
   * 인스타그램 스토리 이미지용 프롬프트 빌드
   */
  private fun buildInstagramStoryImagePrompt(
    productName: String,
    hookText: String,
    mood: MoodId,
  ): String {
    val moodDesc = when (mood) {
      MoodId.FUN -> "playful, energetic, bright colors"
      MoodId.EMOTIONAL -> "warm, touching, soft lighting"
      MoodId.INFORMATIVE -> "clean, structured, professional"
      MoodId.TRENDY -> "modern, bold, dynamic"
    }

    return buildString {
      appendLine("Create a vertical Instagram Story image for $productName.")
      appendLine("Hook message: \"$hookText\"")
      appendLine("Mood: $moodDesc")
      appendLine("Vertical format (9:16), story-optimized composition.")
      appendLine("Leave space at top and bottom for text overlays.")
    }
  }

  // ==================== 유틸리티 ====================

  /**
   * 톤앤매너를 이미지 스타일로 변환
   */
  private fun toneAndMannerToStyle(tone: ToneAndMannerId): String {
    return when (tone) {
      ToneAndMannerId.FRIENDLY -> "natural"
      ToneAndMannerId.PREMIUM -> "studio"
      ToneAndMannerId.TRENDY -> "vibrant"
      ToneAndMannerId.PROFESSIONAL -> "minimal"
      ToneAndMannerId.PLAYFUL -> "vibrant"
    }
  }

  /**
   * 분위기를 이미지 스타일로 변환
   */
  private fun moodToStyle(mood: MoodId): String {
    return when (mood) {
      MoodId.FUN -> "vibrant"
      MoodId.EMOTIONAL -> "natural"
      MoodId.INFORMATIVE -> "minimal"
      MoodId.TRENDY -> "vibrant"
    }
  }

  /**
   * 너비/높이에서 종횡비 문자열 계산
   */
  private fun calculateAspectRatio(width: Int, height: Int): String {
    val ratio = width.toDouble() / height.toDouble()
    return when {
      ratio > 1.7 -> "16:9"
      ratio > 1.2 -> "4:3"
      ratio > 0.9 -> "1:1"
      ratio > 0.7 -> "3:4"
      else -> "9:16"
    }
  }

  /**
   * 플레이스홀더 썸네일 URL 생성
   */
  private fun generateThumbnailUrl(width: Int, height: Int): String {
    // 종횡비 유지하면서 썸네일 사이즈 계산
    val ratio = width.toDouble() / height.toDouble()
    val thumbWidth: Int
    val thumbHeight: Int

    if (ratio >= 1.0) {
      thumbWidth = THUMBNAIL_WIDTH
      thumbHeight = (THUMBNAIL_WIDTH / ratio).toInt()
    } else {
      thumbHeight = THUMBNAIL_HEIGHT
      thumbWidth = (THUMBNAIL_HEIGHT * ratio).toInt()
    }

    return "https://placehold.co/${thumbWidth}x${thumbHeight}/png?text=Thumb"
  }
}

/**
 * 마케팅 이미지 생성 결과
 */
data class MarketingImageResult(
  val success: Boolean,
  val s3Key: String? = null,
  val imageUrl: String? = null,
  val thumbnailUrl: String? = null,
  val width: Int = 0,
  val height: Int = 0,
  val errorMessage: String? = null,
) {
  companion object {
    fun failure(errorMessage: String): MarketingImageResult {
      return MarketingImageResult(
        success = false,
        errorMessage = errorMessage
      )
    }
  }
}
