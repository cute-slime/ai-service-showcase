package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * (백오피스) AI 미디어 생성 프로바이더 관련 Response DTO
 *
 * @author Claude Code
 * @since 2026.01.10
 */

// ========== Provider ==========

/**
 * 프로바이더 목록 항목 DTO
 *
 * includeModels=true 파라미터 사용 시 models 필드 포함
 */
data class BoGenerationProviderItem(
  val id: Long,
  val code: String,
  val name: String,
  val description: String?,
  val status: GenerationProviderStatus,
  val logoUrl: String?,
  val websiteUrl: String?,
  val sortOrder: Int,

  /** 모델 수 */
  val modelCount: Int = 0,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,

  /**
   * 모델 목록 (includeModels=true 시 포함)
   *
   * QueryDSL Projection 호환을 위해 기본값 null
   */
  val models: List<BoGenerationProviderModelItem>? = null,
) {
  /**
   * QueryDSL Projections.constructor() 호환용 보조 생성자
   *
   * models 필드 없이 11개 파라미터로 호출 시 사용
   */
  constructor(
    id: Long,
    code: String,
    name: String,
    description: String?,
    status: GenerationProviderStatus,
    logoUrl: String?,
    websiteUrl: String?,
    sortOrder: Int,
    modelCount: Int,
    createdAt: ZonedDateTime?,
    updatedAt: ZonedDateTime?,
  ) : this(
    id = id,
    code = code,
    name = name,
    description = description,
    status = status,
    logoUrl = logoUrl,
    websiteUrl = websiteUrl,
    sortOrder = sortOrder,
    modelCount = modelCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    models = null,
  )
}

/**
 * 프로바이더 상세 DTO
 */
data class BoGenerationProviderDetail(
  val id: Long,
  val code: String,
  val name: String,
  val description: String?,
  val status: GenerationProviderStatus,
  val logoUrl: String?,
  val websiteUrl: String?,
  val sortOrder: Int,

  /** API 설정 */
  val apiConfig: BoGenerationProviderApiConfigDto?,

  /** 모델 목록 */
  val models: List<BoGenerationProviderModelItem>?,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== Provider API Config ==========

/**
 * API 설정 DTO
 */
data class BoGenerationProviderApiConfigDto(
  val id: Long,
  val providerId: Long,
  val authType: GenerationAuthType,
  val authHeaderName: String,
  val authValuePrefix: String?,
  val baseUrl: String,
  val rateLimitPerMinute: Int?,
  val rateLimitPerDay: Int?,
  val concurrentLimit: Int?,
  val connectTimeoutMs: Int,
  val readTimeoutMs: Int,
  val configJson: String?,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== Provider Model ==========

/**
 * 모델 목록 항목 DTO
 *
 * includePresets=true 파라미터 사용 시 presets 필드 포함
 */
data class BoGenerationProviderModelItem(
  val id: Long,
  val providerId: Long,
  val providerCode: String?,
  val providerName: String?,
  val code: String,
  val name: String,
  val description: String?,
  val version: String?,
  val status: GenerationModelStatus,
  val isDefault: Boolean,
  val supportedMediaTypes: List<GenerationMediaType>,

  /** 워크플로우/API 설정 JSON 존재 여부 (목록에서는 내용 대신 boolean) */
  val hasConfigJson: Boolean = false,

  val releaseDate: LocalDate?,
  val deprecationDate: LocalDate?,
  val sortOrder: Int,

  /** 프리셋 수 */
  val presetCount: Int = 0,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,

  /**
   * 프리셋 목록 (includePresets=true 시 포함)
   *
   * 기본값 null로 기존 API 호환성 유지
   */
  val presets: List<BoGenerationModelPresetDto>? = null,
)

/**
 * 모델 상세 DTO
 */
data class BoGenerationProviderModelDetail(
  val id: Long,
  val providerId: Long,
  val providerCode: String?,
  val providerName: String?,
  val code: String,
  val name: String,
  val description: String?,
  val version: String?,
  val status: GenerationModelStatus,
  val isDefault: Boolean,
  val supportedMediaTypes: List<GenerationMediaType>,

  /**
   * 워크플로우/API 설정 JSON
   *
   * 프로바이더별 API 호출에 필요한 설정을 저장합니다.
   * - ComfyUI: 전체 워크플로우 JSON (플레이스홀더 포함)
   * - NovelAI: API 파라미터 설정 {"sampler": "k_euler", ...}
   * - Midjourney: 파라미터 설정 {"version": "6", "style": "raw", ...}
   *
   * 플레이스홀더 예시: {{PROMPT}}, {{NEGATIVE_PROMPT}}, {{SEED}}, {{WIDTH}}, {{HEIGHT}}
   */
  val configJson: String?,

  val releaseDate: LocalDate?,
  val deprecationDate: LocalDate?,
  val sortOrder: Int,

  /** API 규격 목록 */
  val apiSpecs: List<BoGenerationModelApiSpecDto>?,

  /** 미디어 설정 목록 */
  val mediaConfigs: List<BoGenerationModelMediaConfigDto>?,

  /** 프리셋 목록 */
  val presets: List<BoGenerationModelPresetDto>?,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== Model API Spec ==========

/**
 * API 규격 DTO
 */
data class BoGenerationModelApiSpecDto(
  val id: Long,
  val modelId: Long,
  val mediaType: GenerationMediaType,
  val endpointPath: String,
  val httpMethod: String,
  val contentType: String,
  val requestTemplate: Map<String, Any>?,
  val paramMapping: Map<String, Any>?,
  val responseType: GenerationResponseType,
  val responseMapping: Map<String, Any>?,
  val pollingEndpoint: String?,
  val pollingIntervalMs: Int,
  val statusFieldPath: String?,
  val resultFieldPath: String?,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== Model Media Config ==========

/**
 * 미디어 설정 DTO
 */
data class BoGenerationModelMediaConfigDto(
  val id: Long,
  val modelId: Long,
  val mediaType: GenerationMediaType,
  val defaultParams: Map<String, Any>?,
  val availableParams: Map<String, Any>?,
  val promptFormat: GenerationPromptFormat,
  val maxPromptLength: Int,
  val supportsNegativePrompt: Boolean,
  val promptTemplate: String?,
  val negativePromptTemplate: String?,
  val costPerUnit: BigDecimal?,
  val costUnitType: GenerationCostUnitType?,
  val costCurrency: String,
  val minWidth: Int?,
  val maxWidth: Int?,
  val minHeight: Int?,
  val maxHeight: Int?,
  val supportedAspectRatios: List<String>?,
  val minDurationSec: Int?,
  val maxDurationSec: Int?,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== Model Preset ==========

/**
 * 프리셋 DTO
 */
data class BoGenerationModelPresetDto(
  val id: Long,
  val modelId: Long,
  val mediaType: GenerationMediaType,
  val presetType: GenerationPresetType,
  val code: String,
  val name: String,
  val description: String?,
  val params: Map<String, Any>?,
  val isDefault: Boolean,
  val status: StatusType,
  val sortOrder: Int,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

// ========== 작업 결과 DTO ==========

/**
 * 프로바이더 생성 결과
 */
data class CreateGenerationProviderResult(
  val success: Boolean,
  val id: Long?,
  val message: String,
)

/**
 * 모델 생성 결과
 */
data class CreateGenerationProviderModelResult(
  val success: Boolean,
  val id: Long?,
  val message: String,
)

/**
 * 프리셋 생성 결과
 */
data class CreateGenerationModelPresetResult(
  val success: Boolean,
  val id: Long?,
  val message: String,
)
