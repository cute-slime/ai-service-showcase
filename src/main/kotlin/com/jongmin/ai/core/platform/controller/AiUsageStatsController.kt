package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.platform.dto.*
import com.jongmin.ai.core.platform.service.AiUsageStatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

/**
 * AI 사용량 통계 API 컨트롤러
 *
 * 캐시 사용량 및 비용 통계를 조회하는 API를 제공합니다.
 * 프로바이더별/모델별/시간별 집계를 지원합니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Tag(name = "AI Usage Stats", description = "AI 사용량 및 캐시 통계 API")
@Validated
@RestController
@RequestMapping("/api/ai/stats")
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["read"]),
  condition = MatchingCondition.AllMatches
)
class AiUsageStatsController(
  private val aiUsageStatsService: AiUsageStatsService
) : JController() {

  /**
   * 사용량 통계 조회
   *
   * 지정된 시간 범위와 조건으로 AI 사용량 통계를 조회합니다.
   */
  @Operation(summary = "사용량 통계 조회", description = "시간 범위별 AI 사용량 및 캐시 통계를 조회합니다")
  @GetMapping("/usage")
  fun getUsageStats(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: ZonedDateTime,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: ZonedDateTime,
    @RequestParam(defaultValue = "HOUR") granularity: Granularity,
    @RequestParam(required = false) providerId: Long?,
    @RequestParam(required = false) modelId: Long?,
    @RequestParam(required = false) assistantId: Long?
  ): List<AiUsageStatsResponse> {
    val request = AiUsageStatsRequest(
      startTime = startTime,
      endTime = endTime,
      granularity = granularity,
      providerId = providerId,
      modelId = modelId,
      assistantId = assistantId
    )

    return aiUsageStatsService.getUsageStats(request)
  }

  /**
   * 프로바이더별 캐시 효율 비교
   *
   * 모든 프로바이더의 캐시 효율을 비교 분석합니다.
   */
  @Operation(summary = "프로바이더별 캐시 효율 비교", description = "프로바이더별 캐시 히트율, 절감 비용 등을 비교합니다")
  @GetMapping("/provider-comparison")
  fun getProviderComparison(
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startTime: ZonedDateTime,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endTime: ZonedDateTime
  ): List<ProviderCacheComparisonResponse> {
    return aiUsageStatsService.getProviderComparison(startTime, endTime)
  }

  /**
   * 대시보드 요약 정보 조회
   *
   * 오늘의 캐시 통계, 주간 추이, 상위 프로바이더 정보를 반환합니다.
   */
  @Operation(summary = "대시보드 요약 정보", description = "오늘 캐시 히트율, 주간 추이, 상위 프로바이더 통계를 조회합니다")
  @GetMapping("/dashboard")
  fun getDashboardSummary(): CacheDashboardSummaryResponse {
    return aiUsageStatsService.getDashboardSummary()
  }
}
