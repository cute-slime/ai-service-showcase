package com.jongmin.ai.core.platform.dto

import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 통계 조회 시간 단위
 */
enum class Granularity {
  MINUTE_5,    // 5분 단위
  MINUTE_15,   // 15분 단위
  MINUTE_30,   // 30분 단위
  HOUR,        // 1시간 단위
  DAY,         // 일 단위
  MONTH        // 월 단위
}

/**
 * AI 사용량 통계 조회 요청 DTO
 *
 * @property startTime 조회 시작 시간
 * @property endTime 조회 종료 시간
 * @property granularity 집계 단위
 * @property providerId 프로바이더 ID 필터 (선택)
 * @property modelId 모델 ID 필터 (선택)
 * @property assistantId 어시스턴트 ID 필터 (선택)
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class AiUsageStatsRequest(
  val startTime: ZonedDateTime,
  val endTime: ZonedDateTime,
  val granularity: Granularity = Granularity.HOUR,
  val providerId: Long? = null,
  val modelId: Long? = null,
  val assistantId: Long? = null
)

/**
 * AI 사용량 통계 응답 DTO
 *
 * @property period 집계 기간 문자열 (예: "2025-12-25 14:00")
 * @property providerName 프로바이더명 (집계된 경우)
 * @property modelName 모델명 (집계된 경우)
 * @property totalInputTokens 총 입력 토큰 수
 * @property totalOutputTokens 총 출력 토큰 수
 * @property totalCachedTokens 총 캐시 히트 토큰 수
 * @property totalCacheCreationTokens 총 캐시 생성 토큰 수
 * @property totalCost 총 비용 (USD)
 * @property cachedCost 캐시 토큰 비용 (USD)
 * @property savedCost 캐시로 절감한 비용 (USD)
 * @property cacheHitRate 캐시 히트율 (0.0 ~ 1.0)
 * @property requestCount 요청 수
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class AiUsageStatsResponse(
  val period: String,
  val providerName: String?,
  val modelName: String?,
  val totalInputTokens: Long,
  val totalOutputTokens: Long,
  val totalCachedTokens: Long,
  val totalCacheCreationTokens: Long,
  val totalCost: BigDecimal,
  val cachedCost: BigDecimal,
  val savedCost: BigDecimal,
  val cacheHitRate: Double,
  val requestCount: Long
)

/**
 * 프로바이더별 캐시 효율 비교 응답 DTO
 *
 * @property providerId 프로바이더 ID
 * @property providerName 프로바이더명
 * @property cachingType 캐싱 방식
 * @property totalRequests 총 요청 수
 * @property totalInputTokens 총 입력 토큰
 * @property totalCachedTokens 총 캐시 토큰
 * @property cacheHitRate 캐시 히트율
 * @property discountRate 할인율
 * @property totalCost 총 비용
 * @property savedCost 절감 비용
 * @property efficiencyScore 효율 점수 (0~100)
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class ProviderCacheComparisonResponse(
  val providerId: Long,
  val providerName: String,
  val cachingType: String,
  val totalRequests: Long,
  val totalInputTokens: Long,
  val totalCachedTokens: Long,
  val cacheHitRate: Double,
  val discountRate: Double,
  val totalCost: BigDecimal,
  val savedCost: BigDecimal,
  val efficiencyScore: Int
)

/**
 * 대시보드 요약 정보 응답 DTO
 *
 * @property todayCacheHitRate 오늘 캐시 히트율
 * @property todaySavedCost 오늘 절감 비용 (USD)
 * @property todayTotalRequests 오늘 총 요청 수
 * @property todayTotalCost 오늘 총 비용 (USD)
 * @property weeklyTrend 주간 캐시 히트율 추이
 * @property topProviders 상위 프로바이더별 통계
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class CacheDashboardSummaryResponse(
  val todayCacheHitRate: Double,
  val todaySavedCost: BigDecimal,
  val todayTotalRequests: Long,
  val todayTotalCost: BigDecimal,
  val weeklyTrend: List<DailyTrendItem>,
  val topProviders: List<ProviderSummaryItem>
)

/**
 * 일별 추이 아이템
 */
data class DailyTrendItem(
  val date: String,
  val cacheHitRate: Double,
  val savedCost: BigDecimal,
  val requestCount: Long
)

/**
 * 프로바이더 요약 아이템
 */
data class ProviderSummaryItem(
  val providerName: String,
  val cacheHitRate: Double,
  val totalCost: BigDecimal,
  val requestCount: Long
)
