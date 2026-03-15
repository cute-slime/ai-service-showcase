package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.ai.generation.dto.*
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptConstants.BACKGROUND_TAGS
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptConstants.BASE_NEGATIVE
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptConstants.COMPOSITION_TAGS
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptConstants.LEGACY_STYLE_PRESETS
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptConstants.QUALITY_TAGS
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPrompt
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPromptFromParams
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPromptKo
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPromptKoFromParams
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPromptKoWithLlm
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.buildNaturalPromptWithLlm
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.findLocationTypeStyle
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.normalizeEra
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptNaturalBuilder.toNovelAITags
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * ComfyUI/NovelAI 프롬프트 빌더
 *
 * gen.md 규칙 기반으로 DraftScenario 데이터를 NovelAI 프롬프트로 변환한다.
 *
 * ### Phase 5: DB 기반 동적 스타일 프리셋 지원
 *
 * DB 우선 조회 후 Fallback으로 하드코딩된 프리셋 사용.
 * - `buildBackgroundPromptByPresetCode()`: DB 프리셋 기반 (권장)
 * - `buildBackgroundPrompt()`: 레거시 variant 기반 (하위 호환)
 *
 * ### 프롬프트 구조 (순서 중요):
 * ```
 * [작가/스타일], [품질 태그], [배경 기본], [장소 유형], [시대/지역],
 * [시간대], [날씨], [조명], [주요 요소], [분위기], [세부 디테일]
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 * @modified 2026.02.19 SRP 분리 (Constants, PresetResolver, NaturalBuilder)
 */
@Component
class ComfyUIPromptBuilder(
  private val presetResolver: ComfyUIPromptPresetResolver,
  private val llmGenerator: BackgroundPromptLlmGenerator,
) {

  private val kLogger = KotlinLogging.logger {}

  // ========== Phase 5: presetCode 기반 프롬프트 생성 (권장) ==========

  /**
   * DB 프리셋 코드 기반 배경 이미지 프롬프트 생성
   *
   * DB에서 프리셋을 먼저 조회하고, 없으면 레거시 매핑으로 Fallback한다.
   */
  fun buildBackgroundPromptByPresetCode(
    location: LocationData,
    presetCode: String,
    modelId: Long,
    metadata: ScenarioMetadata,
  ): PromptOutput {
    // 1. DB에서 프리셋 조회
    val dbParams = presetResolver.getStylePresetFromDb(modelId, presetCode)

    if (dbParams != null) {
      kLogger.debug { "[PromptBuilder] DB 프리셋 사용 - code: $presetCode, modelId: $modelId" }
      return buildPromptFromParams(location, presetCode, dbParams, metadata)
    }

    // 2. DB에 없으면 레거시 매핑 시도
    val legacyVariant = BackgroundVariant.fromPresetCode(presetCode)
    if (legacyVariant != null) {
      kLogger.debug { "[PromptBuilder] 레거시 Fallback 사용 - code: $presetCode → variant: $legacyVariant" }
      return buildBackgroundPrompt(location, legacyVariant, metadata)
    }

    // 3. 완전히 매칭 안되면 기본 프리셋 사용
    kLogger.warn { "[PromptBuilder] 알 수 없는 프리셋 코드, 기본 사용 - code: $presetCode" }
    val defaultPreset = presetResolver.getDefaultStylePresetFromDb(modelId)
    return if (defaultPreset != null) {
      buildPromptFromParams(location, defaultPreset.first, defaultPreset.second, metadata)
    } else {
      buildBackgroundPrompt(location, BackgroundVariant.V1_PEACEFUL, metadata)
    }
  }

  // ========== Phase 6: LLM 기반 동적 프롬프트 생성 ==========

  /**
   * LLM 기반 동적 프롬프트 생성 (Phase 6)
   *
   * LLM이 장소/시나리오 컨텍스트를 분석하여 핵심 프롬프트를 생성하고,
   * 프리셋 태그는 스타일 가이드로서 append된다.
   */
  fun buildPromptWithLlm(
    location: LocationData,
    presetCode: String,
    modelId: Long,
    metadata: ScenarioMetadata,
    assistantId: Long? = null,
  ): Phase6PromptOutput {
    kLogger.info {
      "[PromptBuilder Phase6] LLM 기반 프롬프트 생성 시작 - " +
          "location: ${location.name}, presetCode: $presetCode"
    }

    // 1. DB에서 프리셋 조회 (스타일 가이드 목적)
    val presetParams = presetResolver.getStylePresetFromDb(modelId, presetCode)
      ?: presetResolver.getDefaultStylePresetFromDb(modelId)?.second
      ?: StylePresetParams(
        artistTag = "{makoto shinkai}",
        timeOfDay = "afternoon, daytime",
        mood = "peaceful, serene",
        lighting = "warm lighting, golden hour"
      )

    // 2. LLM을 통해 핵심 프롬프트 생성
    val llmResult = llmGenerator.generatePrompt(
      location = location,
      metadata = metadata,
      assistantId = assistantId
    )

    // 3. LLM 프롬프트 + 프리셋 태그 조합
    val combinedPrompt = llmGenerator.combineWithPreset(
      llmPrompt = llmResult.prompt,
      preset = presetParams
    )

    // 4. 네거티브 프롬프트 (프리셋 기반)
    val negativePrompt = buildString {
      append(BASE_NEGATIVE.trimIndent().replace("\n", " ").trim())
      presetParams.additionalNegative?.let { append(", $it") }
    }

    // 5. 자연어 프롬프트 (LLM 결과 기반으로 재구성)
    val naturalPrompt = buildNaturalPromptWithLlm(location, llmResult.prompt, presetParams, metadata)
    val naturalPromptKo = buildNaturalPromptKoWithLlm(location, llmResult.prompt, presetParams, metadata)

    kLogger.info {
      "[PromptBuilder Phase6] 생성 완료 - " +
          "llmDuration: ${llmResult.durationMs}ms, " +
          "combinedLength: ${combinedPrompt.length}, " +
          "assistant: ${llmResult.assistantName}"
    }

    return Phase6PromptOutput(
      prompt = combinedPrompt,
      negativePrompt = negativePrompt,
      naturalPrompt = naturalPrompt,
      naturalPromptKo = naturalPromptKo,
      stylePreset = presetCode,
      artistTag = presetParams.artistTag,
      presetCode = presetCode,
      llmGeneratedPrompt = llmResult.prompt,
      llmDurationMs = llmResult.durationMs,
      llmAssistantId = llmResult.assistantId,
      llmAssistantName = llmResult.assistantName
    )
  }

  // ========== 레거시: Variant 기반 프롬프트 생성 ==========

  /**
   * 배경 이미지용 프롬프트 생성
   */
  fun buildBackgroundPrompt(
    location: LocationData,
    variant: BackgroundVariant,
    metadata: ScenarioMetadata,
  ): PromptOutput {
    val preset = LEGACY_STYLE_PRESETS[variant] ?: LEGACY_STYLE_PRESETS[BackgroundVariant.V1_PEACEFUL]!!

    // 장소 유형에 따른 추가 스타일 (V1에서만 적용)
    val locationStyle = if (variant == BackgroundVariant.V1_PEACEFUL) {
      findLocationTypeStyle(location.locationType)
    } else {
      null
    }

    val prompt = buildPromptString(preset, location, metadata, locationStyle)
    val negativePrompt = buildNegativePrompt(preset)

    kLogger.debug {
      "[PromptBuilder] 프롬프트 생성 완료 - location: ${location.locationId}, " +
          "variant: $variant, preset: ${preset.name}, " +
          "prompt length: ${prompt.length}"
    }

    return PromptOutput(
      prompt = prompt,
      negativePrompt = negativePrompt,
      naturalPrompt = buildNaturalPrompt(location, variant, metadata),
      naturalPromptKo = buildNaturalPromptKo(location, variant, metadata),
      stylePreset = preset.name,
      artistTag = preset.artistTag ?: locationStyle?.first,
      variant = variant
    )
  }

  /**
   * 단순 프롬프트 생성 (location 정보 없이)
   */
  fun buildSimplePrompt(
    description: String,
    variant: BackgroundVariant,
    era: String = "modern",
    region: String = "",
  ): PromptOutput {
    val preset = LEGACY_STYLE_PRESETS[variant]!!

    val prompt = buildString {
      preset.artistTag?.let { append("$it, ") }
      append("$QUALITY_TAGS, $BACKGROUND_TAGS, ")
      if (era.isNotBlank()) append("$era, ")
      if (region.isNotBlank()) append("$region, ")
      append("${preset.timeOfDay}, ")
      append("${preset.styleTag}, ")
      append("${preset.mood}, ")
      append(toNovelAITags(description))
      append(", $COMPOSITION_TAGS")
    }

    return PromptOutput(
      prompt = prompt,
      negativePrompt = buildNegativePrompt(preset),
      stylePreset = preset.name,
      artistTag = preset.artistTag,
      variant = variant
    )
  }

  // ========== 팩토리 메서드 ==========

  /**
   * 모든 변형에 대한 프롬프트 일괄 생성 (레거시)
   *
   * @deprecated Phase 5에서 buildAllPresets() 사용 권장
   */
  fun buildAllVariants(
    location: LocationData,
    metadata: ScenarioMetadata,
  ): List<PromptOutput> {
    return BackgroundVariant.all().map { variant ->
      buildBackgroundPrompt(location, variant, metadata)
    }
  }

  /**
   * 모든 DB 프리셋에 대한 프롬프트 일괄 생성 (Phase 5)
   */
  fun buildAllPresets(
    location: LocationData,
    modelId: Long,
    metadata: ScenarioMetadata,
  ): List<PromptOutput> {
    val dbPresets = presetResolver.getStylePresetsFromDb(modelId)

    return if (dbPresets.isNotEmpty()) {
      dbPresets.map { (preset, params) ->
        buildPromptFromParams(location, preset.code, params, metadata)
      }
    } else {
      kLogger.debug { "[PromptBuilder] DB 프리셋 없음, 레거시 사용 - modelId: $modelId" }
      buildAllVariants(location, metadata)
    }
  }

  /**
   * 레거시 스타일 프리셋 이름 목록 조회
   *
   * @deprecated Phase 5에서 getAvailablePresetCodes() 사용 권장
   */
  fun getAvailablePresets(): List<String> {
    return LEGACY_STYLE_PRESETS.values.map { it.name }
  }

  /**
   * DB 스타일 프리셋 코드 목록 조회 (Phase 5)
   */
  fun getAvailablePresetCodes(modelId: Long): List<String> {
    val dbPresets = presetResolver.getStylePresetsFromDb(modelId)

    return if (dbPresets.isNotEmpty()) {
      dbPresets.map { it.first.code }
    } else {
      BackgroundVariant.LEGACY_PRESET_MAPPING.values.toList()
    }
  }

  /**
   * 특정 Variant의 레거시 프리셋 조회
   *
   * @deprecated Phase 5에서 DB 기반 프리셋 사용 권장
   */
  fun getPreset(variant: BackgroundVariant): StylePreset? {
    return LEGACY_STYLE_PRESETS[variant]
  }

  // ========== DB 프리셋 조회 위임 (하위 호환) ==========

  fun getStylePresetFromDb(modelId: Long, presetCode: String): StylePresetParams? =
    presetResolver.getStylePresetFromDb(modelId, presetCode)

  fun getDefaultStylePresetFromDb(modelId: Long): Pair<String, StylePresetParams>? =
    presetResolver.getDefaultStylePresetFromDb(modelId)

  fun getStylePresetsFromDb(modelId: Long) =
    presetResolver.getStylePresetsFromDb(modelId)

  // ========== 내부 빌드 메서드 ==========

  /**
   * StylePresetParams 기반 프롬프트 생성
   */
  private fun buildPromptFromParams(
    location: LocationData,
    presetCode: String,
    params: StylePresetParams,
    metadata: ScenarioMetadata,
  ): PromptOutput {
    val locationOverride = params.locationTypeOverrides?.get(location.locationType.lowercase())

    val prompt = buildString {
      val artistTag = locationOverride ?: params.artistTag
      artistTag?.let { append("$it, ") }
      append("$QUALITY_TAGS, $BACKGROUND_TAGS, ")
      append("${location.locationType}, ")
      if (metadata.era.isNotBlank()) {
        append("${normalizeEra(metadata.era)}, ")
      }
      if (metadata.region.isNotBlank()) {
        append("${metadata.region}, ")
      }
      params.timeOfDay?.let { append("$it, ") }
      val weather = params.weather ?: metadata.weather
      weather?.let { if (it.isNotBlank()) append("$it, ") }
      params.lighting?.let { append("$it, ") }
      params.styleTag?.let { append("$it, ") }
      params.mood?.let { append("$it, ") }
      val descriptionTags = toNovelAITags(location.description)
      if (descriptionTags.isNotBlank()) {
        append("$descriptionTags, ")
      }
      if (location.detailElements.isNotEmpty()) {
        append(location.detailElements.joinToString(", ") { "{$it}" })
        append(", ")
      }
      params.additionalTags?.let { append("$it, ") }
      append(COMPOSITION_TAGS)
    }

    val negativePrompt = buildString {
      append(BASE_NEGATIVE.trimIndent().replace("\n", " ").trim())
      params.additionalNegative?.let { append(", $it") }
    }

    kLogger.debug {
      "[PromptBuilder] 프롬프트 생성 완료 (DB) - location: ${location.locationId}, " +
          "presetCode: $presetCode, prompt length: ${prompt.length}"
    }

    return PromptOutput(
      prompt = prompt,
      negativePrompt = negativePrompt,
      naturalPrompt = buildNaturalPromptFromParams(location, params, metadata),
      naturalPromptKo = buildNaturalPromptKoFromParams(location, params, metadata),
      stylePreset = presetCode,
      artistTag = params.artistTag,
      variant = BackgroundVariant.fromPresetCode(presetCode),
      presetCode = presetCode
    )
  }

  /**
   * 프롬프트 문자열 빌드 (레거시)
   */
  private fun buildPromptString(
    preset: StylePreset,
    location: LocationData,
    metadata: ScenarioMetadata,
    locationStyle: Pair<String?, String>?,
  ): String {
    return buildString {
      val artistTag = preset.artistTag ?: locationStyle?.first
      artistTag?.let { append("$it, ") }
      append("$QUALITY_TAGS, $BACKGROUND_TAGS, ")
      append("${location.locationType}, ")
      if (metadata.era.isNotBlank()) {
        append("${normalizeEra(metadata.era)}, ")
      }
      if (metadata.region.isNotBlank()) {
        append("${metadata.region}, ")
      }
      append("${preset.timeOfDay}, ")
      metadata.weather?.let {
        if (it.isNotBlank()) append("$it, ")
      }
      append("${preset.styleTag}, ")
      locationStyle?.second?.let { append("$it, ") }
      append("${preset.mood}, ")
      val descriptionTags = toNovelAITags(location.description)
      if (descriptionTags.isNotBlank()) {
        append("$descriptionTags, ")
      }
      if (location.detailElements.isNotEmpty()) {
        append(location.detailElements.joinToString(", ") { "{$it}" })
        append(", ")
      }
      append(COMPOSITION_TAGS)
    }
  }

  /**
   * 네거티브 프롬프트 빌드
   */
  private fun buildNegativePrompt(preset: StylePreset): String {
    val base = BASE_NEGATIVE.trimIndent().replace("\n", " ").trim()
    return if (preset.additionalNegative != null) {
      "$base, ${preset.additionalNegative}"
    } else {
      base
    }
  }
}
