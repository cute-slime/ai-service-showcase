package com.jongmin.ai.core

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaCostRule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 미디어 생성 비용 계산 서비스
 *
 * GenerationCostRule을 기반으로 미디어 생성 비용을 계산한다.
 * 여러 조건(해상도, 품질, 스타일, 길이)에 따라 가장 적합한 규칙을 찾아 비용을 산출한다.
 *
 * ### 비용 계산 흐름:
 * ```
 * 1. 모델 ID로 규칙 조회 (가장 구체적)
 * 2. 매칭되는 조건 필터링 (resolution, quality, duration 등)
 * 3. 우선순위(priority) 기준 정렬
 * 4. 첫 번째 매칭 규칙의 비용 단위로 계산
 * ```
 *
 * ### 사용 예시:
 * ```kotlin
 * val request = CostCalculationRequest(
 *   modelId = 1L,
 *   providerId = 1L,
 *   mediaType = GenerationMediaType.IMAGE,
 *   resolutionCode = "1024x1024",
 *   qualityCode = "hd"
 * )
 * val result = costCalculationService.calculate(request)
 * // result.cost = 0.080000, result.currency = "USD"
 * ```
 *
 * @author Claude Code
 * @since 2026.01.12
 */
@Service
@Transactional(readOnly = true)
class CostCalculationService(
  private val costRuleRepository: GenerationCostRuleRepository,
) {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 비용 계산 요청
   */
  data class CostCalculationRequest(
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
    val quantity: Int = 1
  )

  /**
   * 비용 계산 결과
   */
  data class CostCalculationResult(
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
    val failureReason: String? = null
  ) {
    companion object {
      /**
       * 규칙을 찾지 못했을 때 0 비용 반환
       */
      fun noRuleFound(mediaType: GenerationMediaType): CostCalculationResult {
        return CostCalculationResult(
          cost = BigDecimal.ZERO,
          currency = "USD",
          unitType = GenerationCostUnitType.PER_REQUEST,
          costPerUnit = BigDecimal.ZERO,
          appliedRuleId = null,
          appliedRuleName = null,
          success = false,
          failureReason = "No matching cost rule found for mediaType: $mediaType"
        )
      }
    }
  }

  /**
   * 비용 계산
   *
   * @param request 비용 계산 요청
   * @return 계산 결과
   */
  fun calculate(request: CostCalculationRequest): CostCalculationResult {
    kLogger.debug {
      "[CostCalculation] 비용 계산 요청 - modelId: ${request.modelId}, " +
          "providerId: ${request.providerId}, mediaType: ${request.mediaType}"
    }

    // 1. 모델 레벨 규칙 조회 시도
    var matchingRule: MultimediaCostRule? = null

    if (request.modelId != null) {
      val modelRules = costRuleRepository.findByModelIdAndMediaTypeAndStatusOrderByPriorityAsc(
        request.modelId,
        request.mediaType,
        StatusType.ACTIVE
      )
      matchingRule = findBestMatchingRule(modelRules, request)
    }

    // 2. 모델 레벨에서 못 찾으면 프로바이더 레벨 규칙 조회
    if (matchingRule == null && request.providerId != null) {
      val providerRules = costRuleRepository.findByProviderIdAndModelIdIsNullAndMediaTypeAndStatusOrderByPriorityAsc(
        request.providerId,
        request.mediaType,
        StatusType.ACTIVE
      )
      matchingRule = findBestMatchingRule(providerRules, request)
    }

    // 3. 규칙을 찾지 못한 경우
    if (matchingRule == null) {
      kLogger.warn {
        "[CostCalculation] 매칭 규칙 없음 - modelId: ${request.modelId}, " +
            "providerId: ${request.providerId}, mediaType: ${request.mediaType}"
      }
      return CostCalculationResult.noRuleFound(request.mediaType)
    }

    // 4. 비용 계산
    val calculatedCost = calculateCostByUnit(matchingRule, request)

    kLogger.info {
      "[CostCalculation] 비용 계산 완료 - ruleId: ${matchingRule.id}, " +
          "ruleName: ${matchingRule.name}, cost: $calculatedCost ${matchingRule.costCurrency}"
    }

    return CostCalculationResult(
      cost = calculatedCost,
      currency = matchingRule.costCurrency,
      unitType = matchingRule.costUnitType,
      costPerUnit = matchingRule.costPerUnit,
      appliedRuleId = matchingRule.id,
      appliedRuleName = matchingRule.name,
      success = true
    )
  }

  /**
   * 조건에 가장 잘 맞는 규칙 찾기
   *
   * 이미 priority 순으로 정렬되어 있으므로, 조건에 맞는 첫 번째 규칙 반환
   */
  private fun findBestMatchingRule(
    rules: List<MultimediaCostRule>,
    request: CostCalculationRequest
  ): MultimediaCostRule? {
    return rules.firstOrNull { rule ->
      matchesConditions(rule, request)
    }
  }

  /**
   * 규칙의 조건이 요청과 매칭되는지 확인
   *
   * 규칙의 조건이 NULL이면 모든 값에 매칭됨 (wildcard)
   */
  private fun matchesConditions(
    rule: MultimediaCostRule,
    request: CostCalculationRequest
  ): Boolean {
    // 해상도 조건 체크
    if (rule.resolutionCode != null && rule.resolutionCode != request.resolutionCode) {
      return false
    }

    // 품질 조건 체크
    if (rule.qualityCode != null && rule.qualityCode != request.qualityCode) {
      return false
    }

    // 스타일 조건 체크
    if (rule.styleCode != null && rule.styleCode != request.styleCode) {
      return false
    }

    // 프리셋 조건 체크
    if (rule.presetId != null && rule.presetId != request.presetId) {
      return false
    }

    // 길이 조건 체크 (범위)
    if (request.durationSec != null) {
      val from = rule.durationSecFrom
      val to = rule.durationSecTo

      // 시작 범위 체크
      if (from != null && request.durationSec < from) {
        return false
      }

      // 종료 범위 체크
      if (to != null && request.durationSec > to) {
        return false
      }
    }

    return true
  }

  /**
   * 단위 타입에 따른 비용 계산
   */
  private fun calculateCostByUnit(
    rule: MultimediaCostRule,
    request: CostCalculationRequest
  ): BigDecimal {
    val baseUnit = rule.costPerUnit
    val quantity = request.quantity.toBigDecimal()

    return when (rule.costUnitType) {
      // 이미지당: 단가 × 수량
      GenerationCostUnitType.PER_IMAGE -> {
        baseUnit.multiply(quantity)
      }

      // 초당: 단가 × 초 × 수량
      GenerationCostUnitType.PER_SECOND -> {
        val seconds = (request.durationSec ?: 1).toBigDecimal()
        baseUnit.multiply(seconds).multiply(quantity)
      }

      // 분당: 단가 × (초/60) × 수량
      GenerationCostUnitType.PER_MINUTE -> {
        val seconds = (request.durationSec ?: 60).toBigDecimal()
        val minutes = seconds.divide(BigDecimal(60), 4, RoundingMode.HALF_UP)
        baseUnit.multiply(minutes).multiply(quantity)
      }

      // 토큰당: 단가 × 토큰 수 (현재 미사용, 향후 확장)
      GenerationCostUnitType.PER_TOKEN -> {
        baseUnit.multiply(quantity)
      }

      // 요청당: 단가 × 수량
      GenerationCostUnitType.PER_REQUEST -> {
        baseUnit.multiply(quantity)
      }
    }.setScale(6, RoundingMode.HALF_UP)
  }

  /**
   * 간편 계산 메서드 - 이미지 생성용
   */
  fun calculateImageCost(
    modelId: Long?,
    providerId: Long?,
    resolutionCode: String? = null,
    qualityCode: String? = null,
    styleCode: String? = null,
    quantity: Int = 1
  ): CostCalculationResult {
    return calculate(
      CostCalculationRequest(
        modelId = modelId,
        providerId = providerId,
        mediaType = GenerationMediaType.IMAGE,
        resolutionCode = resolutionCode,
        qualityCode = qualityCode,
        styleCode = styleCode,
        quantity = quantity
      )
    )
  }

  /**
   * 간편 계산 메서드 - 영상 생성용
   */
  fun calculateVideoCost(
    modelId: Long?,
    providerId: Long?,
    durationSec: Int,
    resolutionCode: String? = null,
    qualityCode: String? = null,
    quantity: Int = 1
  ): CostCalculationResult {
    return calculate(
      CostCalculationRequest(
        modelId = modelId,
        providerId = providerId,
        mediaType = GenerationMediaType.VIDEO,
        durationSec = durationSec,
        resolutionCode = resolutionCode,
        qualityCode = qualityCode,
        quantity = quantity
      )
    )
  }

  /**
   * 간편 계산 메서드 - BGM 생성용
   */
  fun calculateBgmCost(
    modelId: Long?,
    providerId: Long?,
    durationSec: Int,
    qualityCode: String? = null,
    quantity: Int = 1
  ): CostCalculationResult {
    return calculate(
      CostCalculationRequest(
        modelId = modelId,
        providerId = providerId,
        mediaType = GenerationMediaType.BGM,
        durationSec = durationSec,
        qualityCode = qualityCode,
        quantity = quantity
      )
    )
  }
}

