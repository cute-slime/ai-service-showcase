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
 * (백오피스) AI 미디어 생성 모델 프리셋 관리 서비스
 *
 * Preset 단독 CRUD (컨트롤러 엔드포인트 대응)
 *
 * @author Claude Code
 * @since 2026.02.19 (BoGenerationProviderService에서 분리)
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGenerationModelPresetService(
  private val objectMapper: ObjectMapper,
  private val modelRepository: GenerationProviderModelRepository,
  private val presetRepository: GenerationModelPresetRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun createPreset(
    session: JSession,
    modelId: Long,
    dto: CreateGenerationModelPreset
  ): CreateGenerationModelPresetResult {
    kLogger.info { "(BO) MultimediaModelPreset 생성 - modelId: $modelId, code: ${dto.code}" }

    if (!modelRepository.existsById(modelId)) {
      return CreateGenerationModelPresetResult(false, null, "모델을 찾을 수 없습니다: $modelId")
    }

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
    val saved = presetRepository.save(preset)
    return CreateGenerationModelPresetResult(true, saved.id, "프리셋이 생성되었습니다.")
  }

  fun findPresetsByModelId(modelId: Long): List<BoGenerationModelPresetDto> {
    return presetRepository.findByModelId(modelId).map { toPresetDto(it) }
  }

  @Transactional
  fun patchPreset(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "(BO) MultimediaModelPreset 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val preset = presetRepository.findById(data["id"] as Long)
      .orElseThrow { ObjectNotFoundException("프리셋을 찾을 수 없습니다: ${data["id"]}") }

    val newIsDefault = data["isDefault"] as? Boolean

    // isDefault=true로 변경 시 같은 modelId+mediaType+presetType의 기존 기본 프리셋 해제
    if (newIsDefault == true && !preset.isDefault) {
      val existingDefaults = presetRepository.findByModelIdAndMediaTypeAndPresetTypeAndIsDefaultTrue(
        preset.modelId,
        preset.mediaType,
        preset.presetType
      )
      existingDefaults.forEach { existing ->
        if (existing.id != preset.id) {
          kLogger.info { "(BO) 기존 기본 프리셋 해제 - id: ${existing.id}, code: ${existing.code}" }
          existing.isDefault = false
        }
      }
    }

    val mutableData = data.toMutableMap()
    @Suppress("UNCHECKED_CAST")
    (data["params"] as? Map<String, Any>)?.let {
      mutableData["params"] = objectMapper.writeValueAsString(it)
    }

    val result = merge(mutableData, preset, "id", "modelId", "mediaType", "presetType", "code", "createdAt", "updatedAt")

    // merge 후 isDefault 명시적 설정 (Boolean 타입이 merge에서 누락될 수 있음)
    newIsDefault?.let {
      preset.isDefault = it
      if (!result.containsKey("isDefault")) {
        result["isDefault"] = it
      }
    }

    return result
  }

  @Transactional
  fun deletePreset(session: JSession, id: Long) {
    kLogger.info { "(BO) MultimediaModelPreset 삭제 - id: $id, admin: ${session.username}" }

    val preset = presetRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프리셋을 찾을 수 없습니다: $id") }

    presetRepository.delete(preset)
  }

  // ========== Private Helper ==========

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
}

