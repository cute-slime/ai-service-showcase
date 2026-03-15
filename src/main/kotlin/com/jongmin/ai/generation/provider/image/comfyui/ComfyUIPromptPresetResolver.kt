package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationModelPresetRepository
import com.jongmin.ai.core.GenerationPresetType
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaModelPreset
import com.jongmin.ai.generation.dto.StylePresetParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * ComfyUI 스타일 프리셋 DB 조회기
 *
 * MultimediaModelPreset 테이블에서 배경 이미지 스타일 프리셋을 조회하고
 * params JSON을 StylePresetParams로 파싱하는 역할을 담당한다.
 *
 * @author Claude Code
 * @since 2026.02.19 (ComfyUIPromptBuilder에서 분리)
 */
@Component
class ComfyUIPromptPresetResolver(
  private val presetRepository: GenerationModelPresetRepository,
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * DB에서 스타일 프리셋 조회
   *
   * @param modelId 모델 ID
   * @param presetCode 프리셋 코드 (예: "PEACEFUL_DAY")
   * @return StylePresetParams 또는 null (미존재 시)
   */
  fun getStylePresetFromDb(modelId: Long, presetCode: String): StylePresetParams? {
    val preset = presetRepository.findByModelIdAndMediaTypeAndPresetTypeAndCodeAndStatus(
      modelId = modelId,
      mediaType = GenerationMediaType.IMAGE,
      presetType = GenerationPresetType.BACKGROUND,
      code = presetCode,
      status = StatusType.ACTIVE
    ) ?: return null

    return parsePresetParams(preset)
  }

  /**
   * DB에서 기본 스타일 프리셋 조회
   *
   * @param modelId 모델 ID
   * @return isDefault=true인 프리셋, 없으면 null
   */
  fun getDefaultStylePresetFromDb(modelId: Long): Pair<String, StylePresetParams>? {
    val preset = presetRepository.findByModelIdAndMediaTypeAndPresetTypeAndIsDefaultTrueAndStatus(
      modelId = modelId,
      mediaType = GenerationMediaType.IMAGE,
      presetType = GenerationPresetType.BACKGROUND,
      status = StatusType.ACTIVE
    ) ?: return null

    return preset.code to (parsePresetParams(preset) ?: return null)
  }

  /**
   * DB에서 스타일 프리셋 목록 조회
   *
   * @param modelId 모델 ID
   * @return 활성화된 스타일 프리셋 목록 (sortOrder 순)
   */
  fun getStylePresetsFromDb(modelId: Long): List<Pair<MultimediaModelPreset, StylePresetParams>> {
    val presets = presetRepository.findByModelIdAndMediaTypeAndPresetTypeAndStatusOrderBySortOrderAsc(
      modelId = modelId,
      mediaType = GenerationMediaType.IMAGE,
      presetType = GenerationPresetType.BACKGROUND,
      status = StatusType.ACTIVE
    )

    return presets.mapNotNull { preset ->
      parsePresetParams(preset)?.let { params ->
        preset to params
      }
    }
  }

  /**
   * MultimediaModelPreset.params JSON을 StylePresetParams로 파싱
   */
  private fun parsePresetParams(preset: MultimediaModelPreset): StylePresetParams? {
    return try {
      objectMapper.readValue(preset.params, StylePresetParams::class.java)
    } catch (e: Exception) {
      kLogger.warn(e) { "[PresetResolver] 프리셋 파라미터 파싱 실패 - id: ${preset.id}, code: ${preset.code}" }
      null
    }
  }
}

