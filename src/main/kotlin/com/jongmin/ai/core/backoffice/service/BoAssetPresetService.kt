package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AssetPresetType
import com.jongmin.ai.core.AssetGenerationPresetRepository
import com.jongmin.ai.core.GenerationModelPresetRepository
import com.jongmin.ai.core.GenerationProviderModelRepository
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.core.backoffice.dto.response.*
import com.jongmin.ai.core.backoffice.validator.GenerationConfigValidator
import com.jongmin.ai.core.platform.entity.multimedia.AssetMultimediaPreset
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * (백오피스) Asset Generation Preset Service
 *
 * 플랫폼 설정 > AI > 에셋 프리셋 관리
 * 타입(BACKGROUND, CHARACTER)별로 1개의 프리셋만 존재
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAssetPresetService(
  private val jsonMapper: JsonMapper,
  private val presetRepository: AssetGenerationPresetRepository,
  private val generationConfigValidator: GenerationConfigValidator,
  private val generationProviderRepository: GenerationProviderRepository,
  private val generationProviderModelRepository: GenerationProviderModelRepository,
  private val generationModelPresetRepository: GenerationModelPresetRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  // ========== 목록 조회 ==========

  /**
   * 전체 프리셋 목록 조회
   *
   * 모든 프리셋(BACKGROUND, CHARACTER) 반환
   * 타입별 1개씩만 존재하므로 최대 2개
   */
  fun findAll(): AssetGenerationPresetListResponse {
    kLogger.info { "에셋 생성 프리셋 목록 조회" }

    val presets = presetRepository.findAll().map { preset ->
      AssetGenerationPresetListItem(
        id = preset.id,
        type = preset.type,
        name = preset.name,
        totalMediaCount = parseGenerationConfig(preset.generationConfig)?.totalCount() ?: 0,
        updatedAt = preset.updatedAt,
      )
    }

    return AssetGenerationPresetListResponse(
      presets = presets,
      total = presets.size,
    )
  }

  // ========== 상세 조회 ==========

  /**
   * 타입별 프리셋 상세 조회
   *
   * @param type 프리셋 타입 (BACKGROUND, CHARACTER)
   * @return 프리셋 상세 정보 (없으면 기본값으로 생성)
   */
  fun findByType(type: AssetPresetType): AssetGenerationPresetResponse {
    kLogger.info { "에셋 생성 프리셋 조회 - type: $type" }

    // 프리셋이 없으면 기본값으로 자동 생성
    val preset = presetRepository.findByType(type)
      ?: createDefaultPreset(type)

    // 파싱 및 enrich
    val generationConfig = parseGenerationConfig(preset.generationConfig)
      ?.let { enrichGenerationConfig(it) }
      ?: GenerationConfig()

    return AssetGenerationPresetResponse(
      id = preset.id,
      type = preset.type,
      name = preset.name,
      generationConfig = generationConfig,
      totalMediaCount = generationConfig.totalCount(),
      createdAt = preset.createdAt,
      updatedAt = preset.updatedAt,
    )
  }

  // ========== 수정 ==========

  /**
   * 프리셋 수정 (PATCH 패턴)
   *
   * @param session 세션 정보
   * @param data PATCH 요청 데이터 (Map)
   * @return 변경된 필드들
   */
  @Transactional
  fun patch(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    // type은 필수 - PathVariable로 전달됨
    val typeStr = data["type"] as? String
      ?: throw ObjectNotFoundException("프리셋 타입이 필요합니다")
    val type = AssetPresetType.valueOf(typeStr)

    kLogger.info { "에셋 생성 프리셋 수정 - type: $type, admin: ${session.username}" }

    // 프리셋 조회 (없으면 생성)
    var preset = presetRepository.findByType(type)

    if (preset == null) {
      preset = createDefaultPreset(type)
      kLogger.info { "새 프리셋 생성됨 - type: $type, id: ${preset.id}" }
    }

    // generationConfig 검증 및 저장
    @Suppress("UNCHECKED_CAST")
    val configData = data["generationConfig"] as? Map<String, Any?>

    if (configData != null) {
      val generationConfig = convertToGenerationConfig(configData)
      generationConfigValidator.validate(generationConfig)

      // JSON으로 직렬화하여 컬럼에 저장
      val configJson = serializeGenerationConfig(generationConfig)
      preset.generationConfig = configJson
    }

    // name 업데이트
    val name = data["name"] as? String
    if (name != null) {
      preset.name = name
    }

    // updatedAt 갱신
    preset.updatedAt = now()

    // 명시적으로 저장
    presetRepository.save(preset)

    // 변경된 필드들 반환
    val result = mutableMapOf<String, Any?>()
    if (name != null) result["name"] = name
    if (configData != null) result["generationConfig"] = configData

    kLogger.info { "프리셋 수정 완료 - type: $type, changedFields: ${result.keys}" }

    return result
  }

  // ========== Helper Methods ==========

  /**
   * 기본 프리셋 생성
   */
  @Transactional
  fun createDefaultPreset(type: AssetPresetType): AssetMultimediaPreset {
    // 빈 설정으로 생성 (사용자가 직접 설정해야 함)
    val defaultConfig = GenerationConfig()

    val preset = AssetMultimediaPreset(
      type = type,
      name = when (type) {
        AssetPresetType.BACKGROUND -> "배경 에셋 기본 프리셋"
        AssetPresetType.CHARACTER -> "캐릭터 에셋 기본 프리셋"
      },
      generationConfig = serializeGenerationConfig(defaultConfig),
    )

    val saved = presetRepository.save(preset)
    kLogger.info { "기본 프리셋 생성 - type: $type, id: ${saved.id}" }
    return saved
  }

  /**
   * JSON 문자열을 GenerationConfig로 파싱
   */
  private fun parseGenerationConfig(json: String?): GenerationConfig? {
    if (json.isNullOrBlank()) return null
    return try {
      jsonMapper.readValue(json, GenerationConfig::class.java)
    } catch (e: Exception) {
      kLogger.warn { "GenerationConfig 파싱 실패: ${e.message}, json: $json" }
      null
    }
  }

  /**
   * GenerationConfig를 JSON 문자열로 직렬화
   */
  private fun serializeGenerationConfig(config: GenerationConfig): String {
    return jsonMapper.writeValueAsString(config)
  }

  /**
   * 설정에 이름 정보 추가 (응답용)
   * DB에서 Provider/Model/Preset 이름을 조회하여 표시용 필드 채움
   */
  private fun enrichGenerationConfig(config: GenerationConfig): GenerationConfig {
    return GenerationConfig(
      image = config.image?.map { enrichMediaItem(it) },
      video = config.video?.map { enrichMediaItem(it) },
      bgm = config.bgm?.map { enrichMediaItem(it) },
      ost = config.ost?.map { enrichMediaItem(it) },
      sfx = config.sfx?.map { enrichMediaItem(it) },
    )
  }

  /**
   * 개별 MediaGenerationConfigItem에 이름 정보 추가
   */
  private fun enrichMediaItem(item: MediaGenerationConfigItem): MediaGenerationConfigItem {
    val provider = generationProviderRepository.findById(item.providerId).orElse(null)
    val model = generationProviderModelRepository.findById(item.modelId).orElse(null)
    val preset = generationModelPresetRepository.findById(item.presetId).orElse(null)

    return MediaGenerationConfigItem(
      providerId = item.providerId,
      providerName = provider?.name,
      modelId = item.modelId,
      modelName = model?.name,
      presetId = item.presetId,
      presetName = preset?.name,
      count = item.count,
    )
  }

  /**
   * Map 데이터를 GenerationConfig로 변환
   */
  @Suppress("UNCHECKED_CAST")
  private fun convertToGenerationConfig(data: Map<String, Any?>): GenerationConfig {
    return GenerationConfig(
      image = convertMediaItemList(data["image"] as? List<Map<String, Any?>>),
      video = convertMediaItemList(data["video"] as? List<Map<String, Any?>>),
      bgm = convertMediaItemList(data["bgm"] as? List<Map<String, Any?>>),
      ost = convertMediaItemList(data["ost"] as? List<Map<String, Any?>>),
      sfx = convertMediaItemList(data["sfx"] as? List<Map<String, Any?>>),
    )
  }

  /**
   * MediaGenerationConfigItem 리스트 변환
   */
  private fun convertMediaItemList(data: List<Map<String, Any?>>?): List<MediaGenerationConfigItem>? {
    if (data.isNullOrEmpty()) return null
    return data.mapNotNull { itemData ->
      try {
        MediaGenerationConfigItem(
          providerId = (itemData["providerId"] as Number).toLong(),
          providerName = itemData["providerName"] as? String,
          modelId = (itemData["modelId"] as Number).toLong(),
          modelName = itemData["modelName"] as? String,
          presetId = (itemData["presetId"] as Number).toLong(),
          presetName = itemData["presetName"] as? String,
          count = (itemData["count"] as Number).toInt(),
        )
      } catch (e: Exception) {
        kLogger.warn { "MediaGenerationConfigItem 변환 실패: ${e.message}" }
        null
      }
    }.takeIf { it.isNotEmpty() }
  }
}

