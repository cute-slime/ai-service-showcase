package com.jongmin.ai.core.backoffice.validator

import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationModelPresetRepository
import com.jongmin.ai.core.GenerationProviderModelRepository
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.core.backoffice.dto.response.GenerationConfig
import com.jongmin.ai.core.backoffice.dto.response.MediaGenerationConfigItem
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/**
 * GenerationConfig 유효성 검증기
 *
 * Provider/Model/Preset 계층 구조 및 미디어 타입 호환성을 DB 기반으로 검증
 * 4단계 계층: Provider -> Model -> Preset -> Count
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Component
class GenerationConfigValidator(
  private val generationProviderRepository: GenerationProviderRepository,
  private val generationProviderModelRepository: GenerationProviderModelRepository,
  private val generationModelPresetRepository: GenerationModelPresetRepository,
) {

  private val jsonMapper = JsonMapper.builder().build()

  /**
   * GenerationConfig 유효성 검증
   *
   * Provider/Model/Preset 존재 여부 및 계층 관계 검증
   *
   * @param config 검증할 GenerationConfig
   * @throws BadRequestException 유효하지 않은 설정 발견 시
   */
  fun validate(config: GenerationConfig) {
    val errors = mutableListOf<String>()

    // 각 미디어 타입별 검증
    config.image?.let { items ->
      validateMediaItems("IMAGE", items, errors)
    }

    config.video?.let { items ->
      validateMediaItems("VIDEO", items, errors)
    }

    config.bgm?.let { items ->
      validateMediaItems("BGM", items, errors)
    }

    config.ost?.let { items ->
      validateMediaItems("OST", items, errors)
    }

    config.sfx?.let { items ->
      validateMediaItems("SFX", items, errors)
    }

    if (errors.isNotEmpty()) {
      throw BadRequestException(
        "GenerationConfig 검증 실패: ${errors.joinToString(", ")}"
      )
    }
  }

  /**
   * 미디어 타입별 설정 항목 검증
   */
  private fun validateMediaItems(
    mediaType: String,
    items: List<MediaGenerationConfigItem>,
    errors: MutableList<String>
  ) {
    items.forEach { item ->
      // 1. Provider 존재 확인
      val provider = generationProviderRepository.findById(item.providerId).orElse(null)
      if (provider == null) {
        errors.add("Provider not found: ${item.providerId}")
        return@forEach
      }

      // 2. Provider가 해당 미디어 타입 지원하는지 확인
      val supportedTypes = parseMediaTypes(provider.supportedMediaTypes)
      if (mediaType !in supportedTypes) {
        errors.add("${provider.code}는 ${mediaType}를 지원하지 않습니다")
      }

      // 3. Model 존재 및 Provider 소속 확인
      val model = generationProviderModelRepository.findById(item.modelId).orElse(null)
      if (model == null) {
        errors.add("Model not found: ${item.modelId}")
        return@forEach
      }
      if (model.providerId != item.providerId) {
        errors.add("Model ${item.modelId}은 Provider ${item.providerId} 소속이 아닙니다")
      }

      // 4. Model이 해당 미디어 타입 지원하는지 확인
      val modelSupportedTypes = parseMediaTypes(model.supportedMediaTypes)
      if (mediaType !in modelSupportedTypes) {
        errors.add("Model ${model.code}는 ${mediaType}를 지원하지 않습니다")
      }

      // 5. Preset 존재 및 Model 소속 확인
      val preset = generationModelPresetRepository.findById(item.presetId).orElse(null)
      if (preset == null) {
        errors.add("Preset not found: ${item.presetId}")
        return@forEach
      }
      if (preset.modelId != item.modelId) {
        errors.add("Preset ${item.presetId}은 Model ${item.modelId} 소속이 아닙니다")
      }

      // 6. Preset의 미디어 타입 일치 확인
      val presetMediaType = try {
        GenerationMediaType.valueOf(mediaType)
      } catch (e: IllegalArgumentException) {
        null
      }
      if (presetMediaType != null && preset.mediaType != presetMediaType) {
        errors.add("Preset ${item.presetId}의 미디어 타입(${preset.mediaType})이 ${mediaType}와 일치하지 않습니다")
      }

      // 7. Count 범위 검증
      val maxCount = when (mediaType) {
        "IMAGE" -> 10
        "VIDEO" -> 5
        "BGM" -> 5
        "OST" -> 5
        "SFX" -> 10
        else -> 10
      }
      if (item.count < 0) {
        errors.add("$mediaType count는 0 이상이어야 합니다")
      }
      if (item.count > maxCount) {
        errors.add("$mediaType count는 $maxCount 이하여야 합니다")
      }
    }
  }

  /**
   * supportedMediaTypes JSON 문자열 파싱
   */
  private fun parseMediaTypes(json: String): Set<String> {
    return try {
      jsonMapper.readValue(json, object : TypeReference<List<String>>() {}).toSet()
    } catch (e: Exception) {
      emptySet()
    }
  }
}
