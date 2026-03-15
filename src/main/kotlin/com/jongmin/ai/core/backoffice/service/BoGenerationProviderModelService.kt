package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.core.*
import com.jongmin.ai.core.backoffice.dto.request.*
import com.jongmin.ai.core.backoffice.dto.response.*
import com.jongmin.ai.core.platform.entity.multimedia.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * (백오피스) AI 미디어 생성 프로바이더 모델 관리 서비스
 *
 * Model + ApiSpec + MediaConfig CRUD 및 자식 엔티티 일괄 관리
 *
 * @author Claude Code
 * @since 2026.02.19 (BoGenerationProviderService에서 분리)
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGenerationProviderModelService(
  private val objectMapper: ObjectMapper,
  private val providerRepository: GenerationProviderRepository,
  private val modelRepository: GenerationProviderModelRepository,
  private val apiSpecRepository: GenerationModelApiSpecRepository,
  private val mediaConfigRepository: GenerationModelMediaConfigRepository,
  private val presetRepository: GenerationModelPresetRepository,
  private val costRuleRepository: GenerationCostRuleRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  // ========== Model CRUD ==========

  @Transactional
  fun createModel(
    session: JSession,
    providerId: Long,
    dto: CreateGenerationProviderModel
  ): CreateGenerationProviderModelResult {
    kLogger.info { "(BO) MultimediaProviderModel 생성 - providerId: $providerId, code: ${dto.code}" }

    if (!providerRepository.existsById(providerId)) {
      return CreateGenerationProviderModelResult(false, null, "프로바이더를 찾을 수 없습니다: $providerId")
    }

    if (modelRepository.existsByProviderIdAndCode(providerId, dto.code)) {
      return CreateGenerationProviderModelResult(false, null, "이미 존재하는 모델 코드입니다: ${dto.code}")
    }

    val model = MultimediaProviderModel(
      providerId = providerId,
      code = dto.code,
      name = dto.name,
      description = dto.description,
      version = dto.version,
      status = dto.status,
      isDefault = dto.isDefault,
      supportedMediaTypes = objectMapper.writeValueAsString(dto.supportedMediaTypes),
      configJson = dto.configJson,
      releaseDate = dto.releaseDate,
      deprecationDate = dto.deprecationDate,
      sortOrder = dto.sortOrder,
    )
    val saved = modelRepository.save(model)

    dto.apiSpecs?.forEach { spec -> saveApiSpec(saved.id, spec) }
    dto.mediaConfigs?.forEach { config -> saveMediaConfig(saved.id, config) }
    dto.presets?.forEach { preset -> savePreset(saved.id, preset) }

    return CreateGenerationProviderModelResult(true, saved.id, "모델이 생성되었습니다.")
  }

  fun findModelById(id: Long): BoGenerationProviderModelDetail {
    kLogger.debug { "(BO) MultimediaProviderModel 상세 조회 - id: $id" }

    val model = modelRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("모델을 찾을 수 없습니다: $id") }

    val provider = providerRepository.findById(model.providerId).orElse(null)
    val apiSpecs = apiSpecRepository.findByModelId(id)
    val mediaConfigs = mediaConfigRepository.findByModelId(id)
    val presets = presetRepository.findByModelId(id)

    return BoGenerationProviderModelDetail(
      id = model.id,
      providerId = model.providerId,
      providerCode = provider?.code,
      providerName = provider?.name,
      code = model.code,
      name = model.name,
      description = model.description,
      version = model.version,
      status = model.status,
      isDefault = model.isDefault,
      supportedMediaTypes = parseMediaTypes(model.supportedMediaTypes),
      configJson = model.configJson,
      releaseDate = model.releaseDate,
      deprecationDate = model.deprecationDate,
      sortOrder = model.sortOrder,
      apiSpecs = apiSpecs.map { toApiSpecDto(it) },
      mediaConfigs = mediaConfigs.map { toMediaConfigDto(it) },
      presets = presets.map { toPresetDto(it) },
      createdAt = model.createdAt,
      updatedAt = model.updatedAt,
    )
  }

  fun findModelsByProviderId(providerId: Long): List<BoGenerationProviderModelItem> {
    val provider = providerRepository.findById(providerId).orElse(null)
    val models = modelRepository.findByProviderId(providerId)
    return models.map { toModelItem(it, provider?.code, provider?.name) }
  }

  @Transactional
  fun patchModel(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "(BO) MultimediaProviderModel 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val model = modelRepository.findById(data["id"] as Long)
      .orElseThrow { ObjectNotFoundException("모델을 찾을 수 없습니다: ${data["id"]}") }

    val newIsDefault = data["isDefault"] as? Boolean

    // isDefault=true로 변경 시 같은 providerId의 기존 기본 모델 해제
    if (newIsDefault == true && !model.isDefault) {
      modelRepository.findByProviderIdAndIsDefaultTrue(model.providerId)?.let { existing ->
        if (existing.id != model.id) {
          kLogger.info { "(BO) 기존 기본 모델 해제 - id: ${existing.id}, code: ${existing.code}" }
          existing.isDefault = false
        }
      }
    }

    val mutableData = data.toMutableMap()
    @Suppress("UNCHECKED_CAST")
    (data["supportedMediaTypes"] as? List<String>)?.let {
      mutableData["supportedMediaTypes"] = objectMapper.writeValueAsString(it)
    }

    // configJson 빈 문자열 처리 (JSON 컬럼은 빈 문자열 불허)
    val clearConfigJson = data.containsKey("configJson") &&
        (data["configJson"] as? String).isNullOrBlank()
    if (clearConfigJson) {
      mutableData.remove("configJson")
    }

    val result = merge(mutableData, model, "id", "providerId", "code", "createdAt", "updatedAt")

    if (clearConfigJson) {
      model.configJson = null
      result["configJson"] = null
    }

    // merge 후 isDefault 명시적 설정 (Boolean 타입이 merge에서 누락될 수 있음)
    newIsDefault?.let {
      model.isDefault = it
      if (!result.containsKey("isDefault")) {
        result["isDefault"] = it
      }
    }

    return result
  }

  @Transactional
  fun deleteModel(session: JSession, id: Long) {
    kLogger.info { "(BO) MultimediaProviderModel 삭제 - id: $id, admin: ${session.username}" }

    val model = modelRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("모델을 찾을 수 없습니다: $id") }

    deleteModelChildren(id)
    modelRepository.delete(model)
  }

  // ========== Provider 서비스에서 호출하는 위임 메서드 ==========

  /**
   * 프로바이더 상세 조회 시 모델 목록 반환
   */
  fun findModelItemsForProvider(
    providerId: Long,
    providerCode: String?,
    providerName: String?,
    includePresets: Boolean
  ): List<BoGenerationProviderModelItem> {
    val models = modelRepository.findByProviderId(providerId)

    val presetsByModelId: Map<Long, List<BoGenerationModelPresetDto>> = if (includePresets && models.isNotEmpty()) {
      val modelIds = models.map { it.id }
      val allPresets = presetRepository.findByModelIdIn(modelIds)
      allPresets.groupBy { it.modelId }.mapValues { (_, presets) ->
        presets.map { toPresetDto(it) }
      }
    } else {
      emptyMap()
    }

    return if (includePresets) {
      models.map { model ->
        toModelItemWithPresets(model, providerCode, providerName, presetsByModelId[model.id])
      }
    } else {
      models.map { toModelItem(it, providerCode, providerName) }
    }
  }

  /**
   * 프로바이더 목록 조회 시 모델 데이터 일괄 반환 (N+1 방지)
   */
  fun findModelItemsForProviders(
    providerIds: List<Long>,
    providerMap: Map<Long, MultimediaProvider>,
    includePresets: Boolean
  ): Map<Long, List<BoGenerationProviderModelItem>> {
    val allModels = modelRepository.findByProviderIdIn(providerIds)
    if (allModels.isEmpty()) return emptyMap()

    val modelsByProviderId = allModels.groupBy { it.providerId }

    val presetsByModelId: Map<Long, List<BoGenerationModelPresetDto>> = if (includePresets) {
      val modelIds = allModels.map { it.id }
      val allPresets = presetRepository.findByModelIdIn(modelIds)
      allPresets.groupBy { it.modelId }.mapValues { (_, presets) ->
        presets.map { toPresetDto(it) }
      }
    } else {
      emptyMap()
    }

    return modelsByProviderId.mapValues { (providerId, models) ->
      val provider = providerMap[providerId]
      models.map { model ->
        toModelItemWithPresets(
          entity = model,
          providerCode = provider?.code,
          providerName = provider?.name,
          presets = if (includePresets) presetsByModelId[model.id] else null
        )
      }
    }
  }

  /**
   * 프로바이더 삭제 시 해당 프로바이더의 모든 모델과 자식 엔티티 삭제
   */
  @Transactional
  fun deleteAllByProviderId(providerId: Long) {
    val models = modelRepository.findByProviderId(providerId)
    models.forEach { deleteModelChildren(it.id) }
    costRuleRepository.deleteByProviderId(providerId)
    modelRepository.deleteAll(models)
  }

  // ========== Private Helper ==========

  private fun deleteModelChildren(modelId: Long) {
    costRuleRepository.deleteByModelId(modelId)
    apiSpecRepository.deleteByModelId(modelId)
    mediaConfigRepository.deleteByModelId(modelId)
    presetRepository.deleteByModelId(modelId)
  }

  private fun saveApiSpec(modelId: Long, dto: CreateGenerationModelApiSpec): MultimediaModelApiSpec {
    val spec = MultimediaModelApiSpec(
      modelId = modelId,
      mediaType = dto.mediaType,
      endpointPath = dto.endpointPath,
      httpMethod = dto.httpMethod,
      contentType = dto.contentType,
      requestTemplate = objectMapper.writeValueAsString(dto.requestTemplate),
      paramMapping = objectMapper.writeValueAsString(dto.paramMapping),
      responseType = dto.responseType,
      responseMapping = dto.responseMapping?.let { objectMapper.writeValueAsString(it) },
      pollingEndpoint = dto.pollingEndpoint,
      pollingIntervalMs = dto.pollingIntervalMs,
      statusFieldPath = dto.statusFieldPath,
      resultFieldPath = dto.resultFieldPath,
    )
    return apiSpecRepository.save(spec)
  }

  private fun saveMediaConfig(modelId: Long, dto: CreateGenerationModelMediaConfig): MultimediaModelMediaConfig {
    val config = MultimediaModelMediaConfig(
      modelId = modelId,
      mediaType = dto.mediaType,
      defaultParams = objectMapper.writeValueAsString(dto.defaultParams),
      availableParams = objectMapper.writeValueAsString(dto.availableParams),
      promptFormat = dto.promptFormat,
      maxPromptLength = dto.maxPromptLength,
      supportsNegativePrompt = dto.supportsNegativePrompt,
      promptTemplate = dto.promptTemplate,
      negativePromptTemplate = dto.negativePromptTemplate,
      costPerUnit = dto.costPerUnit,
      costUnitType = dto.costUnitType,
      costCurrency = dto.costCurrency,
      minWidth = dto.minWidth,
      maxWidth = dto.maxWidth,
      minHeight = dto.minHeight,
      maxHeight = dto.maxHeight,
      supportedAspectRatios = dto.supportedAspectRatios?.let { objectMapper.writeValueAsString(it) },
      minDurationSec = dto.minDurationSec,
      maxDurationSec = dto.maxDurationSec,
    )
    return mediaConfigRepository.save(config)
  }

  private fun savePreset(modelId: Long, dto: CreateGenerationModelPreset): MultimediaModelPreset {
    val preset = MultimediaModelPreset(
      modelId = modelId,
      mediaType = dto.mediaType,
      presetType = dto.presetType,
      code = dto.code,
      name = dto.name,
      description = dto.description,
      params = objectMapper.writeValueAsString(dto.params),
      isDefault = dto.isDefault,
      sortOrder = dto.sortOrder,
    )
    return presetRepository.save(preset)
  }

  fun toModelItem(
    entity: MultimediaProviderModel,
    providerCode: String?,
    providerName: String?
  ): BoGenerationProviderModelItem {
    val presetCount = presetRepository.findByModelId(entity.id).size
    return BoGenerationProviderModelItem(
      id = entity.id,
      providerId = entity.providerId,
      providerCode = providerCode,
      providerName = providerName,
      code = entity.code,
      name = entity.name,
      description = entity.description,
      version = entity.version,
      status = entity.status,
      isDefault = entity.isDefault,
      supportedMediaTypes = parseMediaTypes(entity.supportedMediaTypes),
      hasConfigJson = !entity.configJson.isNullOrBlank(),
      releaseDate = entity.releaseDate,
      deprecationDate = entity.deprecationDate,
      sortOrder = entity.sortOrder,
      presetCount = presetCount,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }

  private fun toModelItemWithPresets(
    entity: MultimediaProviderModel,
    providerCode: String?,
    providerName: String?,
    presets: List<BoGenerationModelPresetDto>?
  ): BoGenerationProviderModelItem {
    return BoGenerationProviderModelItem(
      id = entity.id,
      providerId = entity.providerId,
      providerCode = providerCode,
      providerName = providerName,
      code = entity.code,
      name = entity.name,
      description = entity.description,
      version = entity.version,
      status = entity.status,
      isDefault = entity.isDefault,
      supportedMediaTypes = parseMediaTypes(entity.supportedMediaTypes),
      hasConfigJson = !entity.configJson.isNullOrBlank(),
      releaseDate = entity.releaseDate,
      deprecationDate = entity.deprecationDate,
      sortOrder = entity.sortOrder,
      presetCount = presets?.size ?: 0,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
      presets = presets,
    )
  }

  private fun toApiSpecDto(entity: MultimediaModelApiSpec) = BoGenerationModelApiSpecDto(
    id = entity.id,
    modelId = entity.modelId,
    mediaType = entity.mediaType,
    endpointPath = entity.endpointPath,
    httpMethod = entity.httpMethod,
    contentType = entity.contentType,
    requestTemplate = parseJson(entity.requestTemplate),
    paramMapping = parseJson(entity.paramMapping),
    responseType = entity.responseType,
    responseMapping = entity.responseMapping?.let { parseJson(it) },
    pollingEndpoint = entity.pollingEndpoint,
    pollingIntervalMs = entity.pollingIntervalMs,
    statusFieldPath = entity.statusFieldPath,
    resultFieldPath = entity.resultFieldPath,
    createdAt = entity.createdAt,
    updatedAt = entity.updatedAt,
  )

  private fun toMediaConfigDto(entity: MultimediaModelMediaConfig) = BoGenerationModelMediaConfigDto(
    id = entity.id,
    modelId = entity.modelId,
    mediaType = entity.mediaType,
    defaultParams = parseJson(entity.defaultParams),
    availableParams = parseJson(entity.availableParams),
    promptFormat = entity.promptFormat,
    maxPromptLength = entity.maxPromptLength,
    supportsNegativePrompt = entity.supportsNegativePrompt,
    promptTemplate = entity.promptTemplate,
    negativePromptTemplate = entity.negativePromptTemplate,
    costPerUnit = entity.costPerUnit,
    costUnitType = entity.costUnitType,
    costCurrency = entity.costCurrency,
    minWidth = entity.minWidth,
    maxWidth = entity.maxWidth,
    minHeight = entity.minHeight,
    maxHeight = entity.maxHeight,
    supportedAspectRatios = entity.supportedAspectRatios?.let { parseJsonList(it) },
    minDurationSec = entity.minDurationSec,
    maxDurationSec = entity.maxDurationSec,
    createdAt = entity.createdAt,
    updatedAt = entity.updatedAt,
  )

  private fun toPresetDto(entity: MultimediaModelPreset) = BoGenerationModelPresetDto(
    id = entity.id,
    modelId = entity.modelId,
    mediaType = entity.mediaType,
    presetType = entity.presetType,
    code = entity.code,
    name = entity.name,
    description = entity.description,
    params = parseJson(entity.params),
    isDefault = entity.isDefault,
    status = entity.status,
    sortOrder = entity.sortOrder,
    createdAt = entity.createdAt,
    updatedAt = entity.updatedAt,
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

  private fun parseMediaTypes(json: String): List<GenerationMediaType> {
    return try {
      @Suppress("UNCHECKED_CAST")
      val list = objectMapper.readValue(json, List::class.java) as List<String>
      list.mapNotNull { runCatching { GenerationMediaType.valueOf(it) }.getOrNull() }
    } catch (e: Exception) {
      emptyList()
    }
  }
}

