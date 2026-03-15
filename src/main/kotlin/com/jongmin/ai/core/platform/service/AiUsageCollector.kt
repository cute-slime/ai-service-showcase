package com.jongmin.ai.core.platform.service

import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.AiRunStepRepository
import com.jongmin.ai.core.CachingType
import com.jongmin.ai.core.platform.callback.AiUsageCallback
import com.jongmin.ai.core.platform.component.monitor.UnexpectedCachingDetector
import com.jongmin.ai.core.platform.dto.CacheUsageInfo
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.jongmin.ai.core.platform.parser.CacheUsageParserFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * AI 사용량 수집기
 *
 * AI 호출 완료 시 토큰 사용량과 캐시 정보를 수집하여 DB에 저장합니다.
 * 비동기로 처리하여 AI 호출 성능에 영향을 주지 않습니다.
 *
 * 핵심 원칙 (방어적 저장):
 * - CachingType이 NONE이더라도 캐싱 데이터가 있으면 저장
 * - 예상치 못한 캐싱 감지 시 로깅 (향후 알림 연동)
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Service
class AiUsageCollector(
  private val aiRunStepRepository: AiRunStepRepository,
  private val aiModelRepository: AiModelRepository,
  private val aiProviderRepository: AiProviderRepository,
  private val parserFactory: CacheUsageParserFactory,
  private val unexpectedCachingDetector: UnexpectedCachingDetector
) : AiUsageCallback {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val TOKENS_PER_UNIT = 1000.0  // 가격 계산 단위 (1000 토큰)
  }

  /**
   * 원시 API 응답에서 사용량 수집
   *
   * @param runStepId AiRunStep ID
   * @param rawUsage API 응답의 usage 객체
   * @param provider 프로바이더명
   * @param modelName 모델명
   */
  @Async
  fun collect(runStepId: Long, rawUsage: Map<String, Any>, provider: String, modelName: String) {
    try {
      // 프로바이더에 맞는 파서로 캐시 정보 추출
      val usage = parserFactory.parse(rawUsage, provider, modelName)

      // 콜백 호출 (내부적으로 DB 저장)
      onUsageCollected(usage, runStepId, rawUsage)

    } catch (e: Exception) {
      kLogger.error(e) { "[사용량 수집] 실패 - runStepId: $runStepId, provider: $provider, model: $modelName" }
      onUsageCollectionFailed(provider, modelName, e, runStepId)
    }
  }

  override fun onUsageCollected(
    usage: CacheUsageInfo,
    runStepId: Long?,
    rawUsage: Map<String, Any>?
  ) {
    try {
      // runStepId가 없으면 저장 불가
      if (runStepId == null) {
        kLogger.debug { "[사용량 수집] runStepId 없음 - 통계만 로깅" }
        logUsageStats(usage)
        return
      }

      val runStep = aiRunStepRepository.findById(runStepId).orElse(null)
      if (runStep == null) {
        kLogger.warn { "[사용량 수집] RunStep 찾을 수 없음 - runStepId: $runStepId" }
        return
      }

      // 모델 조회 (가격 계산용)
      val model = aiModelRepository.findAll(aiModel.name.equalsIgnoreCase(usage.model)).firstOrNull()

      // 프로바이더 조회 (캐싱 정책 확인용)
      val provider = model?.let {
        aiProviderRepository.findAll(aiProvider.id.eq(it.aiProviderId)).firstOrNull()
      }

      // ========== 방어적 저장 로직 ==========
      // CachingType과 무관하게 캐싱 데이터가 있으면 저장
      val expectedCachingType = provider?.cachingType ?: CachingType.UNKNOWN

      // 예상치 못한 캐싱 감지 및 DB 기록
      unexpectedCachingDetector.detectAndRecord(usage, expectedCachingType, rawUsage)

      // 토큰 정보 업데이트
      runStep.totalInputToken = usage.inputTokens
      runStep.totalOutputToken = usage.outputTokens
      runStep.cachedInputTokens = usage.cachedTokens
      runStep.cacheCreationTokens = usage.cacheCreationTokens

      // 비용 계산 (모델 가격 정보가 있는 경우)
      if (model != null) {
        // 일반 입력 토큰 비용 (캐시되지 않은 토큰)
        val uncachedInputTokens = usage.inputTokens - usage.cachedTokens
        runStep.totalInputTokenSpend = calculateCost(uncachedInputTokens, model.inputTokenPrice)

        // 출력 토큰 비용
        runStep.totalOutputTokenSpend = calculateCost(usage.outputTokens, model.outputTokenPrice)

        // 캐시 토큰 비용 (할인된 가격)
        runStep.cachedInputTokenSpend = calculateCost(usage.cachedTokens, model.cachedInputTokenPrice)
      }

      aiRunStepRepository.save(runStep)

      // 상세 로깅
      if (usage.hasCacheHit()) {
        kLogger.info {
          "[캐시 모니터링] 캐시 히트! provider: ${usage.provider}, model: ${usage.model}, " +
              "cached: ${usage.cachedTokens}/${usage.inputTokens} (${usage.cacheHitRatePercent()}), " +
              "runStepId: $runStepId"
        }
      } else {
        kLogger.debug {
          "[사용량 수집] 완료 - provider: ${usage.provider}, model: ${usage.model}, " +
              "input: ${usage.inputTokens}, output: ${usage.outputTokens}, " +
              "runStepId: $runStepId"
        }
      }

    } catch (e: Exception) {
      kLogger.error(e) {
        "[사용량 수집] DB 저장 실패 - runStepId: $runStepId, provider: ${usage.provider}, model: ${usage.model}"
      }
    }
  }

  override fun onUsageCollectionFailed(
    provider: String,
    model: String,
    error: Throwable,
    runStepId: Long?
  ) {
    kLogger.error(error) {
      "[사용량 수집] 실패 - provider: $provider, model: $model, runStepId: $runStepId"
    }
    // 향후: 실패 알림 시스템 연동
  }

  /**
   * 비용 계산 (1000 토큰당 가격 기준)
   */
  private fun calculateCost(tokens: Long, pricePerThousand: BigDecimal): Double {
    if (tokens <= 0) return 0.0
    return (tokens.toDouble() / TOKENS_PER_UNIT) * pricePerThousand.toDouble()
  }

  /**
   * 통계 로깅 (runStepId 없을 때)
   */
  private fun logUsageStats(usage: CacheUsageInfo) {
    if (usage.hasCacheActivity()) {
      kLogger.info {
        "[캐시 통계] provider: ${usage.provider}, model: ${usage.model}, " +
            "input: ${usage.inputTokens}, output: ${usage.outputTokens}, " +
            "cached: ${usage.cachedTokens} (${usage.cacheHitRatePercent()}), " +
            "cacheCreation: ${usage.cacheCreationTokens}"
      }
    }
  }
}
