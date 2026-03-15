package com.jongmin.ai.core.platform.service

import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.AiRunStepRepository
import com.jongmin.ai.core.platform.dto.*
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiRunStep.aiRunStep
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * AI 사용량 통계 서비스
 *
 * 캐시 사용량 및 비용 통계를 조회합니다.
 * 프로바이더별/모델별/시간별 집계를 지원합니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Service
@Transactional(readOnly = true)
class AiUsageStatsService(
  private val queryFactory: JPAQueryFactory,
  private val aiRunStepRepository: AiRunStepRepository,
  private val aiModelRepository: AiModelRepository,
  private val aiProviderRepository: AiProviderRepository
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
  }

  /**
   * 사용량 통계 조회
   */
  fun getUsageStats(request: AiUsageStatsRequest): List<AiUsageStatsResponse> {
    kLogger.debug {
      "[통계 조회] ${request.startTime} ~ ${request.endTime}, granularity: ${request.granularity}"
    }

    // 기본 조건
    val conditions = buildConditions(
      startTime = request.startTime,
      endTime = request.endTime,
      assistantId = request.assistantId
    )

    // Tuple 방식으로 집계 쿼리 실행
    val raw = fetchUsageStatsRaw(conditions)

    // 결과 변환
    return listOf(
      convertToResponse(
        raw = raw,
        period = "${request.startTime.format(DATETIME_FORMATTER)} ~ ${request.endTime.format(DATETIME_FORMATTER)}",
        providerName = null,
        modelName = null
      )
    )
  }

  /**
   * 프로바이더별 캐시 효율 비교
   */
  fun getProviderComparison(
    startTime: ZonedDateTime,
    endTime: ZonedDateTime
  ): List<ProviderCacheComparisonResponse> {
    kLogger.debug { "[프로바이더 비교] ${startTime} ~ ${endTime}" }

    // 모든 프로바이더 조회
    val providers = aiProviderRepository.findAll()

    return providers.map { provider ->
      // 해당 프로바이더의 모델들 조회
      val modelIds = aiModelRepository.findAll(aiModel.aiProviderId.eq(provider.id))
        .map { it.id }

      if (modelIds.isEmpty()) {
        return@map ProviderCacheComparisonResponse(
          providerId = provider.id,
          providerName = provider.name,
          cachingType = provider.cachingType.name,
          totalRequests = 0,
          totalInputTokens = 0,
          totalCachedTokens = 0,
          cacheHitRate = 0.0,
          discountRate = provider.defaultCacheDiscountRate.toDouble(),
          totalCost = BigDecimal.ZERO,
          savedCost = BigDecimal.ZERO,
          efficiencyScore = 0
        )
      }

      // 해당 프로바이더의 RunStep 통계
      // 주의: 현재 AiRunStep에는 modelId가 없으므로 assistantId를 통해 연결해야 함
      // 임시로 전체 통계 반환 (향후 개선 필요)
      val conditions = aiRunStep.createdAt.between(startTime, endTime)
      val stats = fetchUsageStatsRaw(conditions)

      val cacheHitRate = if (stats.totalInputTokens > 0)
        stats.totalCachedTokens.toDouble() / stats.totalInputTokens.toDouble()
      else 0.0

      val totalCost = BigDecimal.valueOf(stats.totalInputSpend + stats.totalOutputSpend + stats.cachedSpend)
      val savedCost = calculateSavedCost(stats.totalCachedTokens, provider.defaultCacheDiscountRate)
      val efficiencyScore = calculateEfficiencyScore(cacheHitRate, provider.defaultCacheDiscountRate.toDouble())

      ProviderCacheComparisonResponse(
        providerId = provider.id,
        providerName = provider.name,
        cachingType = provider.cachingType.name,
        totalRequests = stats.requestCount,
        totalInputTokens = stats.totalInputTokens,
        totalCachedTokens = stats.totalCachedTokens,
        cacheHitRate = cacheHitRate,
        discountRate = provider.defaultCacheDiscountRate.toDouble(),
        totalCost = totalCost.setScale(4, RoundingMode.HALF_UP),
        savedCost = savedCost,
        efficiencyScore = efficiencyScore
      )
    }
  }

  /**
   * 대시보드 요약 정보 조회
   */
  fun getDashboardSummary(): CacheDashboardSummaryResponse {
    val today = LocalDate.now()
    val startOfToday = today.atStartOfDay(ZonedDateTime.now().zone)
    val endOfToday = today.plusDays(1).atStartOfDay(ZonedDateTime.now().zone)

    // 오늘 통계
    val todayStats = getTodayStats(startOfToday, endOfToday)

    // 주간 추이
    val weeklyTrend = getWeeklyTrend(today)

    // 상위 프로바이더
    val topProviders = getTopProviders(startOfToday, endOfToday)

    return CacheDashboardSummaryResponse(
      todayCacheHitRate = todayStats.cacheHitRate,
      todaySavedCost = todayStats.savedCost,
      todayTotalRequests = todayStats.requestCount,
      todayTotalCost = todayStats.totalCost,
      weeklyTrend = weeklyTrend,
      topProviders = topProviders
    )
  }

  // ========== Private Methods ==========

  /**
   * 집계 통계 조회 (QueryDSL 7.x 타입 명시 메서드 사용)
   */
  private fun fetchUsageStatsRaw(conditions: BooleanExpression): UsageStatsRaw {
    // QueryDSL 7.x에서는 sumLong(), sumDouble() 등 타입을 명시하는 메서드 사용
    val inputTokenSum = aiRunStep.totalInputToken.sumLong()
    val outputTokenSum = aiRunStep.totalOutputToken.sumLong()
    val cachedTokensSum = aiRunStep.cachedInputTokens.sumLong()
    val cacheCreationSum = aiRunStep.cacheCreationTokens.sumLong()
    val inputSpendSum = aiRunStep.totalInputTokenSpend.sumDouble()
    val outputSpendSum = aiRunStep.totalOutputTokenSpend.sumDouble()
    val cachedSpendSum = aiRunStep.cachedInputTokenSpend.sumDouble()
    val countExpr = aiRunStep.id.count()

    val tuple = queryFactory
      .select(
        inputTokenSum,
        outputTokenSum,
        cachedTokensSum,
        cacheCreationSum,
        inputSpendSum,
        outputSpendSum,
        cachedSpendSum,
        countExpr
      )
      .from(aiRunStep)
      .where(conditions)
      .fetchOne()

    return if (tuple != null) {
      UsageStatsRaw(
        totalInputTokens = tuple.get(inputTokenSum) ?: 0L,
        totalOutputTokens = tuple.get(outputTokenSum) ?: 0L,
        totalCachedTokens = tuple.get(cachedTokensSum) ?: 0L,
        totalCacheCreationTokens = tuple.get(cacheCreationSum) ?: 0L,
        totalInputSpend = tuple.get(inputSpendSum) ?: 0.0,
        totalOutputSpend = tuple.get(outputSpendSum) ?: 0.0,
        cachedSpend = tuple.get(cachedSpendSum) ?: 0.0,
        requestCount = tuple.get(countExpr) ?: 0L
      )
    } else {
      UsageStatsRaw.empty()
    }
  }

  private fun buildConditions(
    startTime: ZonedDateTime,
    endTime: ZonedDateTime,
    assistantId: Long? = null
  ): BooleanExpression {
    var conditions: BooleanExpression = aiRunStep.createdAt.between(startTime, endTime)

    if (assistantId != null) {
      conditions = conditions.and(aiRunStep.aiAssistantId.eq(assistantId))
    }

    return conditions
  }

  private fun convertToResponse(
    raw: UsageStatsRaw,
    period: String,
    providerName: String?,
    modelName: String?
  ): AiUsageStatsResponse {
    val cacheHitRate = if (raw.totalInputTokens > 0)
      raw.totalCachedTokens.toDouble() / raw.totalInputTokens.toDouble()
    else 0.0

    val totalCost = BigDecimal.valueOf(raw.totalInputSpend + raw.totalOutputSpend + raw.cachedSpend)

    // 절감 비용 = 캐시 토큰 * (일반가 - 캐시가) 근사 계산
    // 정확한 계산은 모델별 가격 정보가 필요
    val savedCost = BigDecimal.valueOf(raw.cachedSpend * 0.5)  // 임시: 50% 절감 가정

    return AiUsageStatsResponse(
      period = period,
      providerName = providerName,
      modelName = modelName,
      totalInputTokens = raw.totalInputTokens,
      totalOutputTokens = raw.totalOutputTokens,
      totalCachedTokens = raw.totalCachedTokens,
      totalCacheCreationTokens = raw.totalCacheCreationTokens,
      totalCost = totalCost.setScale(4, RoundingMode.HALF_UP),
      cachedCost = BigDecimal.valueOf(raw.cachedSpend).setScale(4, RoundingMode.HALF_UP),
      savedCost = savedCost.setScale(4, RoundingMode.HALF_UP),
      cacheHitRate = cacheHitRate,
      requestCount = raw.requestCount
    )
  }

  private fun getTodayStats(startOfToday: ZonedDateTime, endOfToday: ZonedDateTime): TodayStats {
    val conditions = aiRunStep.createdAt.between(startOfToday, endOfToday)
    val raw = fetchUsageStatsRaw(conditions)

    val cacheHitRate = if (raw.totalInputTokens > 0)
      raw.totalCachedTokens.toDouble() / raw.totalInputTokens.toDouble()
    else 0.0

    return TodayStats(
      cacheHitRate = cacheHitRate,
      savedCost = BigDecimal.valueOf(raw.cachedSpend * 0.5),
      requestCount = raw.requestCount,
      totalCost = BigDecimal.valueOf(raw.totalInputSpend + raw.totalOutputSpend + raw.cachedSpend)
    )
  }

  private fun getWeeklyTrend(today: LocalDate): List<DailyTrendItem> {
    return (6 downTo 0).map { daysAgo ->
      val date = today.minusDays(daysAgo.toLong())
      val startOfDay = date.atStartOfDay(ZonedDateTime.now().zone)
      val endOfDay = date.plusDays(1).atStartOfDay(ZonedDateTime.now().zone)

      val conditions = aiRunStep.createdAt.between(startOfDay, endOfDay)
      val raw = fetchUsageStatsRaw(conditions)

      val cacheHitRate = if (raw.totalInputTokens > 0)
        raw.totalCachedTokens.toDouble() / raw.totalInputTokens.toDouble()
      else 0.0

      DailyTrendItem(
        date = date.format(DATE_FORMATTER),
        cacheHitRate = cacheHitRate,
        savedCost = BigDecimal.valueOf(raw.cachedSpend * 0.5).setScale(2, RoundingMode.HALF_UP),
        requestCount = raw.requestCount
      )
    }
  }

  private fun getTopProviders(startOfToday: ZonedDateTime, endOfToday: ZonedDateTime): List<ProviderSummaryItem> {
    // 임시 구현: 전체 프로바이더 반환
    val providers = aiProviderRepository.findAll().take(5)

    return providers.map { provider ->
      ProviderSummaryItem(
        providerName = provider.name,
        cacheHitRate = 0.0,  // 향후 개선
        totalCost = BigDecimal.ZERO,
        requestCount = 0
      )
    }
  }

  private fun calculateSavedCost(cachedTokens: Long, discountRate: BigDecimal): BigDecimal {
    // 절감 비용 = 캐시 토큰 * 일반 가격 * 할인율
    // 임시: 평균 입력 가격 $0.001/1K 토큰 가정
    val avgInputPrice = 0.001
    val saved = (cachedTokens.toDouble() / 1000) * avgInputPrice * discountRate.toDouble()
    return BigDecimal.valueOf(saved).setScale(4, RoundingMode.HALF_UP)
  }

  private fun calculateEfficiencyScore(cacheHitRate: Double, discountRate: Double): Int {
    // 효율 점수 = 캐시 히트율 * 할인율 * 100
    return ((cacheHitRate * discountRate) * 100).toInt().coerceIn(0, 100)
  }

  // ========== Data Classes ==========

  data class UsageStatsRaw(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalCachedTokens: Long,
    val totalCacheCreationTokens: Long,
    val totalInputSpend: Double,
    val totalOutputSpend: Double,
    val cachedSpend: Double,
    val requestCount: Long
  ) {
    companion object {
      fun empty() = UsageStatsRaw(0, 0, 0, 0, 0.0, 0.0, 0.0, 0)
    }
  }

  data class TodayStats(
    val cacheHitRate: Double,
    val savedCost: BigDecimal,
    val requestCount: Long,
    val totalCost: BigDecimal
  )
}
