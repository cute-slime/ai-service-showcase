package com.jongmin.ai.core.system.controller

import com.jongmin.ai.core.CostCalculationService
import com.jongmin.ai.core.system.dto.SystemCostCalculationRequest
import com.jongmin.ai.core.system.dto.SystemCostCalculationResponse
import com.jongmin.jspring.web.aspect.SystemCall
import com.jongmin.jspring.web.controller.JController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 시스템 비용 계산 컨트롤러
 *
 * 다른 마이크로서비스(game-service 등)에서 미디어 생성 비용을 계산할 때 사용하는 내부 API.
 * @SystemCall 어노테이션으로 시스템 토큰 인증 필수.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Validated
@RestController
@RequestMapping("/v1.0")
@Tag(name = "System - Cost Calculation", description = "비용 계산 시스템 API (내부용)")
class SystemCostCalculationController(
  private val costCalculationService: CostCalculationService,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 비용 계산
   *
   * 미디어 생성(이미지, 영상, BGM)의 예상 비용을 계산한다.
   * GenerationCostRule에서 가장 적합한 규칙을 찾아 비용을 산출한다.
   *
   * @param request 비용 계산 요청
   * @return 비용 계산 결과
   */
  @Operation(
    summary = "비용 계산",
    description = """
      미디어 생성의 예상 비용을 계산합니다.

      ## 비용 계산 규칙 우선순위
      1. 모델 ID로 규칙 조회 (가장 구체적)
      2. 프로바이더 ID로 규칙 조회 (fallback)
      3. 조건(해상도, 품질, 길이) 매칭
      4. 우선순위(priority) 기준 정렬

      ## 요청 예시
      ```json
      {
        "modelId": 1,
        "providerId": 1,
        "mediaType": "IMAGE",
        "resolutionCode": "1024x1024",
        "qualityCode": "hd",
        "quantity": 1
      }
      ```

      ## 응답 예시
      ```json
      {
        "cost": 0.080000,
        "currency": "USD",
        "unitType": "PER_IMAGE",
        "costPerUnit": 0.080000,
        "appliedRuleId": 1,
        "appliedRuleName": "DALL-E 3 HD",
        "success": true
      }
      ```

      ## 실패 시 응답
      ```json
      {
        "cost": 0,
        "currency": "USD",
        "unitType": "PER_REQUEST",
        "costPerUnit": 0,
        "appliedRuleId": null,
        "appliedRuleName": null,
        "success": false,
        "failureReason": "No matching cost rule found for mediaType: IMAGE"
      }
      ```
    """
  )
  @SystemCall
  @PostMapping("/system/generation/cost-calculation")
  fun calculateCost(
    @RequestBody request: SystemCostCalculationRequest
  ): SystemCostCalculationResponse {
    kLogger.debug {
      "System Cost Calculation 요청 - modelId: ${request.modelId}, " +
          "providerId: ${request.providerId}, mediaType: ${request.mediaType}"
    }

    val result = costCalculationService.calculate(request.toServiceRequest())

    kLogger.debug {
      "System Cost Calculation 응답 - success: ${result.success}, " +
          "cost: ${result.cost} ${result.currency}, ruleId: ${result.appliedRuleId}"
    }

    return SystemCostCalculationResponse.from(result)
  }
}
