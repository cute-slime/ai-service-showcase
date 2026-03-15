package com.jongmin.ai.generation.provider

import com.jongmin.ai.core.CostCalculationService
import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.dto.GenerationPhase
import com.jongmin.ai.generation.dto.GenerationResult
import com.jongmin.ai.generation.dto.ProgressEvent
import com.jongmin.ai.generation.event.JobEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * 에셋 생성 프로바이더 추상 클래스
 *
 * 공통 로직(로깅, 진행 상황 발행, 에러 처리)을 추출하고
 * 템플릿 메서드 패턴으로 실제 생성 로직만 구현하도록 한다.
 *
 * ### 상속 구조:
 * ```
 * AssetGenerationProvider (인터페이스)
 *        ↑
 * AbstractAssetGenerationProvider (추상 클래스 - 공통 로직)
 *        ↑
 *  ┌─────┴─────┐
 *  │           │
 * ComfyUI   NovelAI (구현체들)
 * ```
 *
 * ### 이벤트 발행 경로:
 * ```
 * emitProgress() → JobEventPublisher.emitData()
 *                         ↓
 *                  Redis Pub/Sub
 *                         ↓
 *                  SSE (클라이언트)
 * ```
 *
 * ### 구현 시 오버라이드할 메서드:
 * - [getProviderCode]: 프로바이더 코드 반환
 * - [getSupportedMediaTypes]: 지원 미디어 타입 반환
 * - [doGenerate]: 실제 생성 로직 구현
 *
 * ### 선택적 오버라이드:
 * - [getDefaultTotalSteps]: 기본 단계 수 변경
 * - [isAvailable]: 프로바이더 상태 확인 로직 추가
 *
 * @author Claude Code
 * @since 2026.01.21
 */
abstract class AbstractAssetGenerationProvider : AssetGenerationProvider {

  protected val kLogger = KotlinLogging.logger {}

  /**
   * 작업 이벤트 발행기 - Redis Pub/Sub로 진행 상황을 SSE로 발행
   */
  @Autowired
  private lateinit var eventPublisher: JobEventPublisher

  /**
   * JSON 직렬화용 ObjectMapper
   */
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  /**
   * 비용 계산 서비스 - ai-service 내부 서비스 직접 사용
   */
  @Autowired
  private lateinit var costCalculationService: CostCalculationService

  /**
   * jobId → 진행 상황 Sink 매핑 (로컬 스트리밍용)
   * 진행 상황 이벤트를 발행하기 위한 Sink 저장소
   */
  private val progressSinks = mutableMapOf<String, Sinks.Many<ProgressEvent>>()

  /**
   * 기본 총 단계 수
   * 하위 클래스에서 오버라이드하여 변경 가능
   */
  protected open fun getDefaultTotalSteps(): Int = 5

  /**
   * 에셋 생성 실행 (템플릿 메서드)
   *
   * 공통 로직(로깅, 진행 상황, 에러 처리)을 수행하고
   * 실제 생성은 [doGenerate]에 위임한다.
   */
  override fun generate(context: GenerationContext): GenerationResult {
    val startTime = System.currentTimeMillis()
    val providerCode = getProviderCode()
    val itemIndex = context.itemIndex
    val totalItems = context.totalItems

    kLogger.info {
      "[${providerCode}] 생성 시작 - jobId: ${context.jobId}, " +
          "mediaType: ${context.mediaType}, itemId: ${context.itemId}, " +
          "item: ${itemIndex + 1}/$totalItems"
    }

    // 비용 계산 (생성 전 예상 비용)
    val costResult = calculateCost(context)

    // 초기화 이벤트 발행 (아이템 정보 포함)
    emitProgress(
      ProgressEvent.initializing(
        jobId = context.jobId,
        providerCode = providerCode,
        totalSteps = getDefaultTotalSteps()
      ).copy(
        itemIndex = itemIndex,
        totalItems = totalItems,
        overallProgress = calculateOverallProgress(itemIndex, totalItems, 0)
      )
    )

    return try {
      val result = doGenerate(context)
      val durationMs = System.currentTimeMillis() - startTime

      if (result.success) {
        kLogger.info {
          "[${providerCode}] 생성 완료 - jobId: ${context.jobId}, " +
              "duration: ${durationMs}ms, outputUrl: ${result.outputUrl}, " +
              "item: ${itemIndex + 1}/$totalItems"
        }

        // 아이템 완료 이벤트 발행 (itemCompleted 사용)
        emitProgress(
          ProgressEvent.itemCompleted(
            jobId = context.jobId,
            providerCode = providerCode,
            totalSteps = getDefaultTotalSteps(),
            itemIndex = itemIndex,
            totalItems = totalItems,
            outputUrl = result.outputUrl,
            durationMs = durationMs,
            additionalMetadata = result.metadata
          )
        )
      } else {
        kLogger.warn {
          "[${providerCode}] 생성 실패 - jobId: ${context.jobId}, " +
              "errorCode: ${result.errorCode}, errorMessage: ${result.errorMessage}, " +
              "item: ${itemIndex + 1}/$totalItems"
        }

        // 실패 이벤트 발행
        emitProgress(
          ProgressEvent.failed(
            jobId = context.jobId,
            providerCode = providerCode,
            phase = GenerationPhase.GENERATING,
            step = getDefaultTotalSteps(),
            totalSteps = getDefaultTotalSteps(),
            errorCode = result.errorCode,
            errorMessage = result.errorMessage ?: "Unknown error"
          ).copy(
            itemIndex = itemIndex,
            totalItems = totalItems
          )
        )
      }

      // durationMs 및 비용 정보 추가하여 반환
      result.copy(
        durationMs = durationMs,
        estimatedCost = costResult.cost,
        costCurrency = costResult.currency,
        costUnitType = costResult.unitType,
        appliedCostRuleId = costResult.appliedRuleId
      )

    } catch (e: Exception) {
      val durationMs = System.currentTimeMillis() - startTime
      kLogger.error(e) {
        "[${providerCode}] 생성 예외 발생 - jobId: ${context.jobId}, " +
            "duration: ${durationMs}ms, item: ${itemIndex + 1}/$totalItems"
      }

      // 실패 이벤트 발행
      emitProgress(
        ProgressEvent.failed(
          jobId = context.jobId,
          providerCode = providerCode,
          phase = GenerationPhase.GENERATING,
          step = 1,
          totalSteps = getDefaultTotalSteps(),
          errorCode = "EXCEPTION",
          errorMessage = e.message ?: "Unexpected error"
        ).copy(
          itemIndex = itemIndex,
          totalItems = totalItems
        )
      )

      GenerationResult.failure(
        errorMessage = e.message ?: "Unexpected error during generation",
        errorCode = "EXCEPTION"
      ).copy(
        durationMs = durationMs,
        estimatedCost = costResult.cost,
        costCurrency = costResult.currency,
        costUnitType = costResult.unitType,
        appliedCostRuleId = costResult.appliedRuleId
      )
    } finally {
      // Sink 정리
      cleanupProgressSink(context.jobId)
    }
  }

  /**
   * 전체 진행률 계산
   *
   * 현재 아이템의 진행률을 기반으로 전체 Job의 진행률을 계산합니다.
   *
   * @param itemIndex 현재 아이템 인덱스 (0-based)
   * @param totalItems 전체 아이템 수
   * @param itemProgress 현재 아이템 진행률 (0~100)
   * @return 전체 진행률 (0~100)
   */
  protected fun calculateOverallProgress(itemIndex: Int, totalItems: Int, itemProgress: Int): Int {
    if (totalItems <= 0) return 0
    // 각 아이템이 100/totalItems %를 담당
    val itemContribution = 100.0 / totalItems
    // 완료된 아이템 기여분 + 현재 아이템 기여분
    val completedContribution = itemIndex * itemContribution
    val currentContribution = (itemProgress / 100.0) * itemContribution
    return (completedContribution + currentContribution).toInt().coerceIn(0, 100)
  }

  /**
   * 생성 진행 상황 스트리밍 (로컬용)
   *
   * Sink를 생성하고 진행 상황 이벤트를 구독할 수 있는 Flux를 반환한다.
   * [emitProgress]로 발행된 이벤트가 이 Flux를 통해 전달된다.
   *
   * 주의: 이 메서드는 로컬 스트리밍용이며, DTE SSE는 EventBridge를 통해 자동 발행됨.
   */
  override fun streamProgress(jobId: String): Flux<ProgressEvent> {
    val sink = progressSinks.getOrPut(jobId) {
      Sinks.many().multicast().onBackpressureBuffer()
    }

    return sink.asFlux()
      .timeout(Duration.ofMinutes(10))
      .doOnCancel { cleanupProgressSink(jobId) }
      .doOnTerminate { cleanupProgressSink(jobId) }
  }

  /**
   * 실제 생성 로직 - 하위 클래스에서 구현
   *
   * ### 구현 시 주의사항:
   * - 예외 발생 시 적절한 [GenerationResult.failure] 반환
   * - 진행 상황은 [emitProgress]를 호출하여 발행
   * - 타임아웃 처리는 이 메서드 내에서 수행
   *
   * @param context 생성 요청 컨텍스트
   * @return 생성 결과
   */
  protected abstract fun doGenerate(context: GenerationContext): GenerationResult

  /**
   * 진행 상황 이벤트 발행
   *
   * 하위 클래스에서 생성 진행 중 호출하여 진행 상황을 알린다.
   * JobEventPublisher를 통해 SSE로 클라이언트에 실시간 전달된다.
   *
   * 주의: 이 메서드는 아이템 정보 없이 발행됩니다.
   * 아이템 정보를 포함하려면 [emitProgressWithContext]를 사용하세요.
   *
   * @param event 진행 상황 이벤트
   */
  protected fun emitProgress(event: ProgressEvent) {
    // 1. JobEventPublisher를 통해 Redis Pub/Sub로 SSE 발행 (핵심!)
    try {
      val eventJson = objectMapper.writeValueAsString(event)
      eventPublisher.emitData(
        jobId = event.jobId,
        type = event.type,
        data = eventJson
      )
    } catch (e: Exception) {
      kLogger.warn(e) { "[${getProviderCode()}] 이벤트 발행 실패 - jobId: ${event.jobId}" }
    }

    // 2. 로컬 Sink에도 발행 (streamProgress() 구독자용)
    val sink = progressSinks[event.jobId]
    if (sink != null) {
      val result = sink.tryEmitNext(event)
      if (result.isFailure) {
        kLogger.debug { "[${getProviderCode()}] 로컬 Sink 발행 실패 - jobId: ${event.jobId}, result: $result" }
      }
    }

    // 로깅
    kLogger.debug {
      "[${getProviderCode()}] 진행 상황 - jobId: ${event.jobId}, " +
          "phase: ${event.phase}, progress: ${event.progress}%, " +
          "message: ${event.message}"
    }
  }

  /**
   * 진행 상황 이벤트 발행 (context 기반)
   *
   * 하위 클래스의 doGenerate 내에서 호출하여 진행 상황을 알린다.
   * context에서 아이템 정보(itemIndex, totalItems)를 자동으로 추가하고
   * 전체 진행률(overallProgress)을 계산하여 이벤트에 포함한다.
   *
   * @param event 진행 상황 이벤트
   * @param context 생성 요청 컨텍스트 (아이템 정보 포함)
   */
  protected fun emitProgressWithContext(event: ProgressEvent, context: GenerationContext) {
    val enrichedEvent = event.copy(
      itemIndex = context.itemIndex,
      totalItems = context.totalItems,
      overallProgress = calculateOverallProgress(
        context.itemIndex,
        context.totalItems,
        event.progress
      )
    )
    emitProgress(enrichedEvent)
  }

  /**
   * Sink 정리
   */
  private fun cleanupProgressSink(jobId: String) {
    progressSinks.remove(jobId)?.let { sink ->
      sink.tryEmitComplete()
    }
  }

  /**
   * 비용 계산
   *
   * CostCalculationService를 사용하여 생성 비용을 계산한다.
   * context에서 modelId, providerId, 해상도, 품질, 길이 등을 추출하여 요청.
   *
   * @param context 생성 요청 컨텍스트
   * @return 비용 계산 결과
   */
  private fun calculateCost(context: GenerationContext): CostCalculationService.CostCalculationResult {
    return try {
      costCalculationService.calculate(
        CostCalculationService.CostCalculationRequest(
          modelId = context.modelId,
          providerId = context.providerId,
          mediaType = context.mediaType,
          resolutionCode = context.getResolutionCode(),
          qualityCode = context.getQualityCode(),
          styleCode = context.getStyleCode(),
          durationSec = context.getDurationSec(),
          quantity = 1
        )
      )
    } catch (e: Exception) {
      kLogger.warn(e) {
        "[${getProviderCode()}] 비용 계산 실패 - jobId: ${context.jobId}, " +
            "modelId: ${context.modelId}, providerId: ${context.providerId}"
      }
      // 비용 계산 실패 시 기본값 반환 (생성은 계속 진행)
      CostCalculationService.CostCalculationResult.noRuleFound(context.mediaType)
    }
  }

  /**
   * 프로바이더 설명 (기본 구현)
   */
  override fun getDescription(): String {
    return "${getProviderCode()} Provider (${getSupportedMediaTypes().joinToString(", ")})"
  }
}
