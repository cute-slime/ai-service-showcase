package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

/**
 * (백오피스) AI 미디어 생성 프로바이더 관련 Request DTO
 *
 * @author Claude Code
 * @since 2026.01.10
 */

// ========== Provider ==========

/**
 * 프로바이더 생성 요청 DTO
 */
data class CreateGenerationProvider(
  @field:NotBlank(message = "code는 필수입니다")
  @field:Size(max = 50, message = "code는 50자를 초과할 수 없습니다")
  val code: String,

  @field:NotBlank(message = "name은 필수입니다")
  @field:Size(max = 100, message = "name은 100자를 초과할 수 없습니다")
  val name: String,

  val description: String? = null,

  val status: GenerationProviderStatus = GenerationProviderStatus.ACTIVE,

  val logoUrl: String? = null,

  val websiteUrl: String? = null,

  val sortOrder: Int = 0,

  /** API 설정 (선택) */
  val apiConfig: CreateGenerationProviderApiConfig? = null,
)

/**
 * 프로바이더 수정 요청 DTO
 */
data class PatchGenerationProvider(
  var id: Long? = null,

  var name: String? = null,

  var description: String? = null,

  var status: GenerationProviderStatus? = null,

  var logoUrl: String? = null,

  var websiteUrl: String? = null,

  var sortOrder: Int? = null,
)

// ========== Provider API Config ==========

/**
 * API 설정 생성 요청 DTO
 */
data class CreateGenerationProviderApiConfig(
  @field:NotNull(message = "authType은 필수입니다")
  val authType: GenerationAuthType,

  val authHeaderName: String = "Authorization",

  val authValuePrefix: String? = null,

  @field:NotBlank(message = "baseUrl은 필수입니다")
  val baseUrl: String,

  val rateLimitPerMinute: Int? = null,

  val rateLimitPerDay: Int? = null,

  val concurrentLimit: Int? = null,

  val connectTimeoutMs: Int = 5000,

  val readTimeoutMs: Int = 60000,

  /** 프로바이더별 확장 설정 JSON */
  val configJson: String? = null,
)

/**
 * API 설정 수정 요청 DTO
 */
data class PatchGenerationProviderApiConfig(
  var id: Long? = null,

  var authType: GenerationAuthType? = null,

  var authHeaderName: String? = null,

  var authValuePrefix: String? = null,

  var baseUrl: String? = null,

  var rateLimitPerMinute: Int? = null,

  var rateLimitPerDay: Int? = null,

  var concurrentLimit: Int? = null,

  var connectTimeoutMs: Int? = null,

  var readTimeoutMs: Int? = null,

  /** 프로바이더별 확장 설정 JSON */
  var configJson: String? = null,
)

// ========== Provider Model ==========

/**
 * 모델 생성 요청 DTO
 */
data class CreateGenerationProviderModel(
  @field:NotBlank(message = "code는 필수입니다")
  @field:Size(max = 50, message = "code는 50자를 초과할 수 없습니다")
  val code: String,

  @field:NotBlank(message = "name은 필수입니다")
  @field:Size(max = 100, message = "name은 100자를 초과할 수 없습니다")
  val name: String,

  val description: String? = null,

  val version: String? = null,

  val status: GenerationModelStatus = GenerationModelStatus.ACTIVE,

  val isDefault: Boolean = false,

  /** 지원 미디어 타입 (예: ["IMAGE", "VIDEO"]) */
  val supportedMediaTypes: List<GenerationMediaType> = listOf(GenerationMediaType.IMAGE),

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
  val configJson: String? = null,

  val releaseDate: LocalDate? = null,

  val deprecationDate: LocalDate? = null,

  val sortOrder: Int = 0,

  /** API 규격 (선택) */
  val apiSpecs: List<CreateGenerationModelApiSpec>? = null,

  /** 미디어 설정 (선택) */
  val mediaConfigs: List<CreateGenerationModelMediaConfig>? = null,

  /** 프리셋 (선택) */
  val presets: List<CreateGenerationModelPreset>? = null,
)

/**
 * 모델 수정 요청 DTO
 */
data class PatchGenerationProviderModel(
  var id: Long? = null,

  var name: String? = null,

  var description: String? = null,

  var version: String? = null,

  var status: GenerationModelStatus? = null,

  var isDefault: Boolean? = null,

  var supportedMediaTypes: List<GenerationMediaType>? = null,

  /** 워크플로우/API 설정 JSON */
  var configJson: String? = null,

  var releaseDate: LocalDate? = null,

  var deprecationDate: LocalDate? = null,

  var sortOrder: Int? = null,
)

// ========== Model API Spec ==========

/**
 * API 규격 생성 요청 DTO
 */
data class CreateGenerationModelApiSpec(
  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  @field:NotBlank(message = "endpointPath는 필수입니다")
  val endpointPath: String,

  val httpMethod: String = "POST",

  val contentType: String = "application/json",

  /** API 요청 템플릿 (JSON) */
  val requestTemplate: Map<String, Any> = emptyMap(),

  /** 파라미터 매핑 (JSON) */
  val paramMapping: Map<String, Any> = emptyMap(),

  val responseType: GenerationResponseType = GenerationResponseType.SYNC,

  /** 응답 매핑 (JSON) */
  val responseMapping: Map<String, Any>? = null,

  val pollingEndpoint: String? = null,

  val pollingIntervalMs: Int = 1000,

  val statusFieldPath: String? = null,

  val resultFieldPath: String? = null,
)

/**
 * API 규격 수정 요청 DTO
 */
data class PatchGenerationModelApiSpec(
  var id: Long? = null,

  var endpointPath: String? = null,

  var httpMethod: String? = null,

  var contentType: String? = null,

  var requestTemplate: Map<String, Any>? = null,

  var paramMapping: Map<String, Any>? = null,

  var responseType: GenerationResponseType? = null,

  var responseMapping: Map<String, Any>? = null,

  var pollingEndpoint: String? = null,

  var pollingIntervalMs: Int? = null,

  var statusFieldPath: String? = null,

  var resultFieldPath: String? = null,
)

// ========== Model Media Config ==========

/**
 * 미디어 설정 생성 요청 DTO
 */
data class CreateGenerationModelMediaConfig(
  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  /** 기본 파라미터 (JSON) */
  val defaultParams: Map<String, Any> = emptyMap(),

  /** 선택 가능 파라미터 (JSON) */
  val availableParams: Map<String, Any> = emptyMap(),

  val promptFormat: GenerationPromptFormat = GenerationPromptFormat.NATURAL,

  val maxPromptLength: Int = 2000,

  val supportsNegativePrompt: Boolean = true,

  val promptTemplate: String? = null,

  val negativePromptTemplate: String? = null,

  val costPerUnit: BigDecimal? = null,

  val costUnitType: GenerationCostUnitType? = null,

  val costCurrency: String = "USD",

  val minWidth: Int? = null,

  val maxWidth: Int? = null,

  val minHeight: Int? = null,

  val maxHeight: Int? = null,

  /** 지원 종횡비 (예: ["1:1", "16:9"]) */
  val supportedAspectRatios: List<String>? = null,

  val minDurationSec: Int? = null,

  val maxDurationSec: Int? = null,
)

/**
 * 미디어 설정 수정 요청 DTO
 */
data class PatchGenerationModelMediaConfig(
  var id: Long? = null,

  var defaultParams: Map<String, Any>? = null,

  var availableParams: Map<String, Any>? = null,

  var promptFormat: GenerationPromptFormat? = null,

  var maxPromptLength: Int? = null,

  var supportsNegativePrompt: Boolean? = null,

  var promptTemplate: String? = null,

  var negativePromptTemplate: String? = null,

  var costPerUnit: BigDecimal? = null,

  var costUnitType: GenerationCostUnitType? = null,

  var costCurrency: String? = null,

  var minWidth: Int? = null,

  var maxWidth: Int? = null,

  var minHeight: Int? = null,

  var maxHeight: Int? = null,

  var supportedAspectRatios: List<String>? = null,

  var minDurationSec: Int? = null,

  var maxDurationSec: Int? = null,
)

// ========== Model Preset ==========

/**
 * 프리셋 생성 요청 DTO
 */
data class CreateGenerationModelPreset(
  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  @field:NotNull(message = "presetType은 필수입니다")
  val presetType: GenerationPresetType,

  @field:NotBlank(message = "code는 필수입니다")
  @field:Size(max = 50, message = "code는 50자를 초과할 수 없습니다")
  val code: String,

  @field:NotBlank(message = "name은 필수입니다")
  @field:Size(max = 100, message = "name은 100자를 초과할 수 없습니다")
  val name: String,

  val description: String? = null,

  /** 프리셋 파라미터 (JSON) */
  val params: Map<String, Any> = emptyMap(),

  val isDefault: Boolean = false,

  val sortOrder: Int = 0,
)

/**
 * 프리셋 수정 요청 DTO
 */
data class PatchGenerationModelPreset(
  var id: Long? = null,

  var name: String? = null,

  var description: String? = null,

  var params: Map<String, Any>? = null,

  var isDefault: Boolean? = null,

  var sortOrder: Int? = null,
)
