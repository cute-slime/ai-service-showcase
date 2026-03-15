package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * (백오피스) AI 미디어 생성 비용 규칙 관련 Response DTO
 *
 * @author Claude Code
 * @since 2026.01.12
 */

/**
 * 비용 규칙 목록 항목 DTO
 */
data class BoGenerationCostRuleItem(
  val id: Long,
  val name: String?,
  val description: String?,
  val mediaType: GenerationMediaType,

  // 적용 대상
  val modelId: Long?,
  val modelName: String?,
  val providerId: Long?,
  val providerName: String?,

  // 적용 조건
  val resolutionCode: String?,
  val qualityCode: String?,
  val styleCode: String?,
  val durationSecFrom: Int?,
  val durationSecTo: Int?,
  val presetId: Long?,

  // 비용 정보
  val costPerUnit: BigDecimal,
  val costUnitType: GenerationCostUnitType,
  val costCurrency: String,

  val priority: Int,
  val status: StatusType,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

/**
 * 비용 규칙 상세 DTO
 */
data class BoGenerationCostRuleDetail(
  val id: Long,
  val name: String?,
  val description: String?,
  val mediaType: GenerationMediaType,

  // 적용 대상 (연관 엔티티 정보 포함)
  val modelId: Long?,
  val modelName: String?,
  val modelCode: String?,
  val providerId: Long?,
  val providerName: String?,
  val providerCode: String?,

  // 적용 조건
  val resolutionCode: String?,
  val qualityCode: String?,
  val styleCode: String?,
  val durationSecFrom: Int?,
  val durationSecTo: Int?,
  val presetId: Long?,
  val presetName: String?,

  // 비용 정보
  val costPerUnit: BigDecimal,
  val costUnitType: GenerationCostUnitType,
  val costCurrency: String,

  val priority: Int,
  val status: StatusType,

  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

/**
 * 비용 규칙 생성 결과 DTO
 */
data class CreateGenerationCostRuleResult(
  val success: Boolean,
  val id: Long?,
  val message: String,
)
