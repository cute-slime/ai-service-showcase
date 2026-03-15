package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * (백오피스) AI 미디어 생성 비용 규칙 관련 Request DTO
 *
 * @author Claude Code
 * @since 2026.01.12
 */

/**
 * 비용 규칙 생성 요청 DTO
 */
data class CreateGenerationCostRule(
  // 적용 대상
  val modelId: Long? = null,
  val providerId: Long? = null,

  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  // 적용 조건
  @field:Size(max = 20, message = "resolutionCode는 20자를 초과할 수 없습니다")
  val resolutionCode: String? = null,

  @field:Size(max = 30, message = "qualityCode는 30자를 초과할 수 없습니다")
  val qualityCode: String? = null,

  @field:Size(max = 50, message = "styleCode는 50자를 초과할 수 없습니다")
  val styleCode: String? = null,

  val durationSecFrom: Int? = null,
  val durationSecTo: Int? = null,
  val presetId: Long? = null,

  // 비용 정보
  @field:NotNull(message = "costPerUnit은 필수입니다")
  val costPerUnit: BigDecimal,

  @field:NotNull(message = "costUnitType은 필수입니다")
  val costUnitType: GenerationCostUnitType,

  val costCurrency: String = "USD",

  // 메타 정보
  @field:Size(max = 100, message = "name은 100자를 초과할 수 없습니다")
  val name: String? = null,

  val description: String? = null,

  val priority: Int = 100,

  val status: StatusType = StatusType.ACTIVE,

  /** Service에서 할당 */
  var id: Long? = null,
)

/**
 * 비용 규칙 수정 요청 DTO
 */
data class PatchGenerationCostRule(
  var id: Long? = null,

  // 적용 대상 (변경 가능 - 주의 필요)
  var modelId: Long? = null,
  var providerId: Long? = null,
  var mediaType: GenerationMediaType? = null,

  // 적용 조건
  var resolutionCode: String? = null,
  var qualityCode: String? = null,
  var styleCode: String? = null,
  var durationSecFrom: Int? = null,
  var durationSecTo: Int? = null,
  var presetId: Long? = null,

  // 비용 정보
  var costPerUnit: BigDecimal? = null,
  var costUnitType: GenerationCostUnitType? = null,
  var costCurrency: String? = null,

  // 메타 정보
  var name: String? = null,
  var description: String? = null,
  var priority: Int? = null,
  var status: StatusType? = null,
)
