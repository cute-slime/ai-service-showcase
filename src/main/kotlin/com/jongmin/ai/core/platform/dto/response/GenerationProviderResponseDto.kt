package com.jongmin.ai.core.platform.dto.response

import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationPresetType
import com.jongmin.ai.core.GenerationPromptFormat
import java.math.BigDecimal

/**
 * (플랫폼) AI 미디어 생성 프로바이더 관련 Response DTO
 *
 * FE에서 프로바이더/모델 선택 UI에 사용하는 간략화된 DTO
 *
 * @author Claude Code
 * @since 2026.01.10
 */

// ========== Provider ==========

/**
 * 프로바이더 목록 항목 (간략)
 */
data class GenerationProviderSimple(
  val code: String,
  val name: String,
  val description: String?,
  val logoUrl: String?,

  /** 활성 모델 수 */
  val modelCount: Int = 0,
)

// ========== Model ==========

/**
 * 모델 목록 항목 (간략)
 */
data class GenerationModelSimple(
  val code: String,
  val name: String,
  val description: String?,
  val version: String?,
  val isDefault: Boolean,
  val supportedMediaTypes: List<GenerationMediaType>,
)

/**
 * 모델 설정 상세 (파라미터, 프리셋 포함)
 */
data class GenerationModelConfig(
  val code: String,
  val name: String,
  val description: String?,
  val version: String?,
  val providerCode: String,
  val providerName: String,
  val supportedMediaTypes: List<GenerationMediaType>,

  /** 미디어 설정 목록 */
  val mediaConfigs: List<GenerationMediaConfigDto>,

  /** 프리셋 목록 (타입별 그룹핑) */
  val presets: Map<GenerationPresetType, List<GenerationPresetDto>>,
)

// ========== Media Config ==========

/**
 * 미디어 설정 DTO (FE용)
 */
data class GenerationMediaConfigDto(
  val mediaType: GenerationMediaType,

  /** 기본 파라미터 */
  val defaultParams: Map<String, Any>?,

  /** 선택 가능 파라미터 스키마 */
  val availableParams: Map<String, Any>?,

  /** 프롬프트 설정 */
  val promptFormat: GenerationPromptFormat,
  val maxPromptLength: Int,
  val supportsNegativePrompt: Boolean,

  /** 해상도 제한 */
  val minWidth: Int?,
  val maxWidth: Int?,
  val minHeight: Int?,
  val maxHeight: Int?,
  val supportedAspectRatios: List<String>?,

  /** 길이 제한 (VIDEO, BGM용) */
  val minDurationSec: Int?,
  val maxDurationSec: Int?,

  /** 비용 정보 */
  val costPerUnit: BigDecimal?,
  val costUnitType: GenerationCostUnitType?,
  val costCurrency: String?,
)

// ========== Preset ==========

/**
 * 프리셋 DTO (FE용)
 */
data class GenerationPresetDto(
  val code: String,
  val name: String,
  val description: String?,
  val mediaType: GenerationMediaType,
  val presetType: GenerationPresetType,
  val params: Map<String, Any>?,
  val isDefault: Boolean,
)
