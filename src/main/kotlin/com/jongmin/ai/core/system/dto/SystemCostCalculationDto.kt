package com.jongmin.ai.core.system.dto

import com.jongmin.ai.core.CostCalculationService
import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import java.math.BigDecimal

/**
 * 시스템 비용 계산 API DTO
 *
 * 다른 마이크로서비스(game-service 등)에서 미디어 생성 비용을 계산할 때 사용하는 DTO.
 * ai-service의 CostCalculationService를 호출하고 결과를 반환한다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */

/**
 * 시스템 비용 계산 요청 DTO
 */
data class SystemCostCalculationRequest(
  /** 모델 ID (우선) */
  val modelId: Long? = null,

  /** 프로바이더 ID (모델 없을 때 fallback) */
  val providerId: Long? = null,

  /** 미디어 타입 */
  val mediaType: GenerationMediaType,

  /** 해상도 코드 (예: "1024x1024") */
  val resolutionCode: String? = null,

  /** 품질 코드 (예: "standard", "hd") */
  val qualityCode: String? = null,

  /** 스타일 코드 */
  val styleCode: String? = null,

  /** 길이 (초, 영상/음악용) */
  val durationSec: Int? = null,

  /** 프리셋 ID */
  val presetId: Long? = null,

  /** 수량 (기본 1) */
  val quantity: Int = 1,
) {
  /**
   * 내부 CostCalculationRequest로 변환
   */
  fun toServiceRequest(): CostCalculationService.CostCalculationRequest {
    return CostCalculationService.CostCalculationRequest(
      modelId = modelId,
      providerId = providerId,
      mediaType = mediaType,
      resolutionCode = resolutionCode,
      qualityCode = qualityCode,
      styleCode = styleCode,
      durationSec = durationSec,
      presetId = presetId,
      quantity = quantity,
    )
  }
}

/**
 * 시스템 비용 계산 응답 DTO
 */
data class SystemCostCalculationResponse(
  /** 계산된 비용 */
  val cost: BigDecimal,

  /** 통화 코드 */
  val currency: String,

  /** 적용된 비용 단위 */
  val unitType: GenerationCostUnitType,

  /** 단위당 비용 */
  val costPerUnit: BigDecimal,

  /** 적용된 규칙 ID */
  val appliedRuleId: Long?,

  /** 적용된 규칙 이름 */
  val appliedRuleName: String?,

  /** 계산 성공 여부 */
  val success: Boolean = true,

  /** 실패 메시지 */
  val failureReason: String? = null,
) {
  companion object {
    /**
     * CostCalculationResult를 응답 DTO로 변환
     */
    fun from(result: CostCalculationService.CostCalculationResult): SystemCostCalculationResponse {
      return SystemCostCalculationResponse(
        cost = result.cost,
        currency = result.currency,
        unitType = result.unitType,
        costPerUnit = result.costPerUnit,
        appliedRuleId = result.appliedRuleId,
        appliedRuleName = result.appliedRuleName,
        success = result.success,
        failureReason = result.failureReason,
      )
    }

    /**
     * 규칙을 찾지 못했을 때 0 비용 반환
     */
    fun noRuleFound(mediaType: GenerationMediaType): SystemCostCalculationResponse {
      return SystemCostCalculationResponse(
        cost = BigDecimal.ZERO,
        currency = "USD",
        unitType = GenerationCostUnitType.PER_REQUEST,
        costPerUnit = BigDecimal.ZERO,
        appliedRuleId = null,
        appliedRuleName = null,
        success = false,
        failureReason = "No matching cost rule found for mediaType: $mediaType",
      )
    }
  }
}
