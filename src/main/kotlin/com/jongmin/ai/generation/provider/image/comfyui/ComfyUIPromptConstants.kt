package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.ai.generation.dto.BackgroundVariant
import com.jongmin.ai.generation.dto.StylePreset

/**
 * ComfyUI 프롬프트 빌더 상수 정의
 *
 * 품질 태그, 네거티브 프롬프트, 레거시 스타일 프리셋,
 * 장소 유형별 스타일 매핑 등 프롬프트 생성에 사용하는 모든 상수를 관리한다.
 *
 * @author Claude Code
 * @since 2026.02.19 (ComfyUIPromptBuilder에서 분리)
 */
object ComfyUIPromptConstants {

  // ========== 품질 태그 (고정) ==========

  /** 필수 품질 태그 - 모든 프롬프트에 포함 */
  const val QUALITY_TAGS = "masterpiece, best quality, amazing quality, very aesthetic, absurdres"

  /** 배경 전용 태그 - 인물 제외 및 풍경 지정 */
  const val BACKGROUND_TAGS = "no humans, scenery"

  // ========== 네거티브 프롬프트 (기본) ==========

  /** 기본 네거티브 프롬프트 */
  const val BASE_NEGATIVE = """
      lowres, {bad}, error, fewer, extra, missing, worst quality, jpeg artifacts,
      bad quality, watermark, unfinished, displeasing, chromatic aberration,
      signature, extra digits, artistic error, username, scan, [abstract],
      person, people, human, character, portrait, face, blurry, text
    """

  /** V2 (긴장) 추가 네거티브 */
  const val V2_ADDITIONAL_NEGATIVE = "bright, cheerful, vibrant colors"

  /** V3 (공포) 추가 네거티브 */
  const val V3_ADDITIONAL_NEGATIVE = "calm, peaceful, bright, cheerful"

  // ========== 촬영 태그 ==========

  /** 기본 촬영 태그 */
  const val COMPOSITION_TAGS = "wide shot, detailed background, cinematic lighting, atmospheric"

  // ========== 레거시 스타일 프리셋 정의 (하드코딩 Fallback) ==========

  /**
   * Variant별 스타일 프리셋 정의 (레거시 Fallback용)
   *
   * DB에 프리셋이 없을 때 사용하는 기본 프리셋.
   * 새로운 프리셋은 DB(MultimediaModelPreset)에 추가해야 한다.
   *
   * @deprecated Phase 5에서 DB 기반 프리셋 사용 권장
   */
  val LEGACY_STYLE_PRESETS: Map<BackgroundVariant, StylePreset> = mapOf(
    // V1: 평화로운 낮/오후
    BackgroundVariant.V1_PEACEFUL to StylePreset(
      name = "peaceful_warm",
      artistTag = "{makoto shinkai}",
      styleTag = "warm lighting, golden hour, soft shadows, atmospheric perspective, god rays",
      timeOfDay = "afternoon, daytime",
      mood = "peaceful, serene, tranquil, cozy",
      additionalNegative = null
    ),
    // V2: 긴장감 있는 저녁/황혼
    BackgroundVariant.V2_TENSE to StylePreset(
      name = "tense_noir",
      artistTag = null,
      styleTag = "film noir, dramatic shadows, low key lighting, high contrast, muted colors",
      timeOfDay = "evening, dusk, twilight",
      mood = "tense, ominous, foreboding, mysterious",
      additionalNegative = V2_ADDITIONAL_NEGATIVE
    ),
    // V3: 공포 클라이맥스
    BackgroundVariant.V3_HORROR to StylePreset(
      name = "horror_atmospheric",
      artistTag = "{zdzislaw beksinski}",
      styleTag = "horror, chiaroscuro, harsh shadows, rim lighting, volumetric fog, dramatic contrast",
      timeOfDay = "night, stormy, lightning, thunder",
      mood = "horror, dread, unsettling, ominous, terrifying",
      additionalNegative = V3_ADDITIONAL_NEGATIVE
    )
  )

  // ========== 장소 유형별 스타일 매핑 ==========

  /**
   * 특수 장소 유형에 따른 추가 작가/스타일 태그
   */
  val LOCATION_TYPE_STYLES: Map<String, Pair<String?, String>> = mapOf(
    "jungle" to ("{henri rousseau}" to "tropical, lush vegetation, primordial atmosphere"),
    "tropical" to ("{paul gauguin}" to "tropical colors, exotic plants"),
    "ruins" to ("{greg rutkowski}" to "ancient, mystical, epic scale"),
    "temple" to ("{greg rutkowski}" to "sacred architecture, mysterious, grand scale"),
    "tomb" to (null to "ancient, stone textures, archaeological"),
    "futuristic" to ("{syd mead}" to "sci-fi architecture, sleek surfaces"),
    "mountain" to ("{caspar david friedrich}" to "majestic peaks, romantic landscape"),
    "forest" to ("{bob ross}" to "peaceful nature, serene trees")
  )
}

