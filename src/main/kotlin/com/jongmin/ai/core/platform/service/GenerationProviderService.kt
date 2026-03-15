package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.ai.core.*
import com.jongmin.ai.core.platform.dto.response.*
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaModelMediaConfig
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaModelPreset
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaProvider
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaProviderModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * (플랫폼) AI 미디어 생성 프로바이더 조회 서비스
 *
 * FE에서 프로바이더/모델 선택에 필요한 정보를 제공한다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class GenerationProviderService(
  private val objectMapper: ObjectMapper,
  private val providerRepository: GenerationProviderRepository,
  private val modelRepository: GenerationProviderModelRepository,
  private val mediaConfigRepository: GenerationModelMediaConfigRepository,
  private val presetRepository: GenerationModelPresetRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 활성 프로바이더 목록 조회 (간략)
   */
  fun findActiveProviders(): List<GenerationProviderSimple> {
    kLogger.debug { "활성 프로바이더 목록 조회" }

    val providers = providerRepository.findByStatusOrderBySortOrderAsc(GenerationProviderStatus.ACTIVE)
    if (providers.isEmpty()) {
      return emptyList()
    }

    val providerIds = providers.map { it.id }
    val modelsByProviderId = modelRepository.findByProviderIdInAndStatus(providerIds, GenerationModelStatus.ACTIVE)
      .groupBy { it.providerId }

    return providers
      .groupBy { it.code.uppercase() }
      .values
      .map { sameCodeProviders ->
        val primary = sameCodeProviders.sortedWith(providerOrderComparator()).first()
        val modelCount = sameCodeProviders
          .flatMap { provider -> modelsByProviderId[provider.id].orEmpty() }
          .map { model -> model.code.uppercase() }
          .distinct()
          .size

      GenerationProviderSimple(
        code = primary.code,
        name = primary.name,
        description = primary.description,
        logoUrl = primary.logoUrl,
        modelCount = modelCount,
      )
    }.sortedBy { it.code }
  }

  /**
   * 프로바이더별 활성 모델 목록 조회
   */
  fun findActiveModelsByProviderCode(providerCode: String): List<GenerationModelSimple> {
    kLogger.debug { "프로바이더별 활성 모델 목록 조회 - providerCode: $providerCode" }

    val providers = findActiveProvidersByCode(providerCode)
    val providerOrder = providers.mapIndexed { index, provider -> provider.id to index }.toMap()
    val providerIds = providers.map { it.id }

    val models = modelRepository.findByProviderIdInAndStatus(providerIds, GenerationModelStatus.ACTIVE)
      .sortedWith(
        compareBy<MultimediaProviderModel>(
          { providerOrder[it.providerId] ?: Int.MAX_VALUE },
          { it.sortOrder },
          { it.id }
        )
      )

    return models
      .groupBy { it.code.uppercase() }
      .values
      .map { duplicates -> duplicates.first() }
      .map { model ->
      GenerationModelSimple(
        code = model.code,
        name = model.name,
        description = model.description,
        version = model.version,
        isDefault = model.isDefault,
        supportedMediaTypes = parseMediaTypes(model.supportedMediaTypes),
      )
    }
  }

  /**
   * 모델 설정 조회 (파라미터, 프리셋 포함)
   */
  fun findModelConfig(providerCode: String, modelCode: String): GenerationModelConfig {
    kLogger.debug { "모델 설정 조회 - providerCode: $providerCode, modelCode: $modelCode" }

    val providers = findActiveProvidersByCode(providerCode)
    val providerOrder = providers.mapIndexed { index, provider -> provider.id to index }.toMap()
    val providerIds = providers.map { it.id }

    val model = modelRepository.findByProviderIdInAndStatus(providerIds, GenerationModelStatus.ACTIVE)
      .filter { it.code.equals(modelCode, ignoreCase = true) }
      .sortedWith(
        compareBy<MultimediaProviderModel>(
          { providerOrder[it.providerId] ?: Int.MAX_VALUE },
          { it.sortOrder },
          { it.id }
        )
      )
      .firstOrNull()
      ?: throw ObjectNotFoundException("모델을 찾을 수 없습니다: $modelCode")

    val provider = providers.firstOrNull { it.id == model.providerId }
      ?: throw ObjectNotFoundException("프로바이더를 찾을 수 없습니다: $providerCode")

    val mediaConfigs = mediaConfigRepository.findByModelId(model.id)
    val presets = presetRepository.findByModelId(model.id)

    // 프리셋을 타입별로 그룹핑
    val presetsByType = presets
      .filter { it.status == com.jongmin.jspring.data.entity.StatusType.ACTIVE }
      .groupBy { it.presetType }
      .mapValues { (_, list) -> list.map { toPresetDto(it) } }

    return GenerationModelConfig(
      code = model.code,
      name = model.name,
      description = model.description,
      version = model.version,
      providerCode = provider.code,
      providerName = provider.name,
      supportedMediaTypes = parseMediaTypes(model.supportedMediaTypes),
      mediaConfigs = mediaConfigs.map { toMediaConfigDto(it) },
      presets = presetsByType,
    )
  }

  private fun findActiveProvidersByCode(providerCode: String): List<MultimediaProvider> {
    val providers = providerRepository.findByCodeAndStatusOrderBySortOrderAscIdAsc(
      providerCode,
      GenerationProviderStatus.ACTIVE
    )
    if (providers.isNotEmpty()) {
      return providers
    }

    throw ObjectNotFoundException("활성 프로바이더를 찾을 수 없습니다: $providerCode")
  }

  private fun providerOrderComparator() = compareBy<MultimediaProvider>({ it.sortOrder }, { it.id })

  // ========== Helper Methods ==========

  private fun toMediaConfigDto(entity: MultimediaModelMediaConfig) = GenerationMediaConfigDto(
    mediaType = entity.mediaType,
    defaultParams = parseJson(entity.defaultParams),
    availableParams = parseJson(entity.availableParams),
    promptFormat = entity.promptFormat,
    maxPromptLength = entity.maxPromptLength,
    supportsNegativePrompt = entity.supportsNegativePrompt,
    minWidth = entity.minWidth,
    maxWidth = entity.maxWidth,
    minHeight = entity.minHeight,
    maxHeight = entity.maxHeight,
    supportedAspectRatios = entity.supportedAspectRatios?.let { parseJsonList(it) },
    minDurationSec = entity.minDurationSec,
    maxDurationSec = entity.maxDurationSec,
    costPerUnit = entity.costPerUnit,
    costUnitType = entity.costUnitType,
    costCurrency = entity.costCurrency,
  )

  private fun toPresetDto(entity: MultimediaModelPreset) = GenerationPresetDto(
    code = entity.code,
    name = entity.name,
    description = entity.description,
    mediaType = entity.mediaType,
    presetType = entity.presetType,
    params = parseJson(entity.params),
    isDefault = entity.isDefault,
  )

  @Suppress("UNCHECKED_CAST")
  private fun parseJson(json: String): Map<String, Any>? {
    return try {
      objectMapper.readValue(json, Map::class.java) as? Map<String, Any>
    } catch (e: Exception) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseJsonList(json: String): List<String>? {
    return try {
      objectMapper.readValue(json, List::class.java) as? List<String>
    } catch (e: Exception) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseMediaTypes(json: String): List<GenerationMediaType> {
    return try {
      val list = objectMapper.readValue(json, List::class.java) as List<String>
      list.mapNotNull { runCatching { GenerationMediaType.valueOf(it) }.getOrNull() }
    } catch (e: Exception) {
      emptyList()
    }
  }
}

