package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiExecutionType
import com.jongmin.ai.core.AiRunRepository
import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.AiRunStepRepository
import com.jongmin.ai.core.platform.entity.AiRun
import com.jongmin.ai.core.platform.entity.AiRunStep
import com.jongmin.ai.core.platform.service.AiUsageCollector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 AI 실행 추적기
 *
 * 모든 AI 호출 유형(LLM, VLM, 이미지, 비디오, 오디오, TTS, STT, 임베딩 등)에 대해
 * AiRun/AiRunStep을 생성하고 메트릭을 추적하는 공통 서비스.
 *
 * 모든 타입별 게이트웨이(LlmGateway, ImageGenerationGateway 등)가 이 서비스를 사용.
 *
 * 주요 기능:
 * - 실행 시작 시 AiRun/AiRunStep 자동 생성
 * - 실행 완료 시 상태 업데이트 및 메트릭 저장
 * - 실행 실패 시 에러 기록
 * - 요청/응답 원본 데이터 저장
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class UnifiedAiExecutionTracker(
  private val aiRunRepository: AiRunRepository,
  private val aiRunStepRepository: AiRunStepRepository,
  private val aiUsageCollector: AiUsageCollector
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * AI 실행 시작 - AiRun과 AiRunStep 생성
   *
   * @param executionType AI 실행 유형
   * @param provider AI 프로바이더명
   * @param modelName 사용할 모델명
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID (aiMessageId, gameSessionId 등)
   * @param assistantId AI 어시스턴트 ID (선택)
   * @param requestPayload 요청 데이터 (프롬프트, 설정값 등)
   * @param createAiRun AiRun 생성 여부 (기존 AiRun이 있으면 false)
   * @param existingAiRunId 기존 AiRun ID (createAiRun=false일 때 필수)
   * @return AiExecutionContext
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun startExecution(
    executionType: AiExecutionType,
    provider: String,
    modelName: String,
    callerComponent: String,
    contextId: Long? = null,
    assistantId: Long? = null,
    requestPayload: Map<String, Any>? = null,
    createAiRun: Boolean = true,
    existingAiRunId: Long? = null
  ): AiExecutionContext {
    kLogger.debug {
      "[AI 추적] 실행 시작 - type: $executionType, provider: $provider, model: $modelName, " +
          "caller: $callerComponent, contextId: $contextId"
    }

    // AiRun 생성 또는 기존 사용
    val aiRunId = if (createAiRun) {
      createNewAiRun(contextId, callerComponent)
    } else {
      requireNotNull(existingAiRunId) { "existingAiRunId는 createAiRun=false일 때 필수입니다." }
      existingAiRunId
    }

    // AiRunStep 생성 (확장 필드 포함)
    val aiRunStep = createNewAiRunStep(
      aiRunId = aiRunId,
      assistantId = assistantId,
      executionType = executionType,
      provider = provider,
      modelName = modelName,
      requestPayload = requestPayload
    )

    val context = AiExecutionContext(
      aiRunId = aiRunId,
      aiRunStepId = aiRunStep.id,
      executionType = executionType,
      provider = provider,
      modelName = modelName,
      callerComponent = callerComponent,
      assistantId = assistantId,
      contextId = contextId,
      requestPayload = requestPayload
    )

    kLogger.info {
      "[AI 추적] 컨텍스트 생성 완료 - aiRunId: $aiRunId, aiRunStepId: ${aiRunStep.id}, " +
          "type: $executionType, caller: $callerComponent"
    }

    return context
  }

  /**
   * AI 실행 완료 - 상태 업데이트 및 메트릭 저장
   *
   * @param context 실행 컨텍스트
   * @param metrics 실행 메트릭
   * @param responsePayload 응답 데이터 (프로바이더 원본)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun completeExecution(
    context: AiExecutionContext,
    metrics: AiExecutionMetrics,
    responsePayload: Map<String, Any>? = null
  ) {
    kLogger.debug {
      "[AI 추적] 실행 완료 - aiRunStepId: ${context.aiRunStepId}, " +
          "type: ${context.executionType}, elapsed: ${context.elapsedMillis()}ms"
    }

    // AiRunStep 상태 및 메트릭 업데이트
    aiRunStepRepository.findById(context.aiRunStepId).ifPresent { step ->
      step.status = StatusType.ENDED
      step.completedAt = now()
      step.totalCostUsd = metrics.totalCost
      step.executionMetrics = metrics.toMap()
      step.responsePayload = responsePayload

      // LLM/VLM 토큰 메트릭인 경우 개별 필드도 업데이트 (SQL 집계용)
      if (metrics is AiExecutionMetrics.TokenBased) {
        step.totalInputToken = metrics.inputTokens
        step.totalOutputToken = metrics.outputTokens
        step.cachedInputTokens = metrics.cachedTokens
        step.cacheCreationTokens = metrics.cacheCreationTokens
        step.totalInputTokenSpend = metrics.inputCost
        step.totalOutputTokenSpend = metrics.outputCost
        step.cachedInputTokenSpend = metrics.cachedCost
      }

      aiRunStepRepository.save(step)
    }

    // AiRun 상태 업데이트
    aiRunRepository.findById(context.aiRunId).ifPresent { run ->
      run.status = AiRunStatus.ENDED
      aiRunRepository.save(run)
    }

    kLogger.info {
      "[AI 추적] 실행 완료 처리됨 - aiRunStepId: ${context.aiRunStepId}, " +
          "type: ${context.executionType}, provider: ${context.provider}, " +
          "cost: \$${metrics.totalCost}"
    }
  }

  /**
   * LLM 실행 완료 - 원시 usage 데이터 기반 처리
   *
   * 기존 LlmExecutionTracker와의 호환성을 위한 메서드.
   * AiUsageCollector를 통해 토큰/비용 계산을 위임.
   *
   * @param context 실행 컨텍스트
   * @param rawUsage API 응답의 usage 객체
   * @param responsePayload 응답 데이터 (프로바이더 원본)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun completeLlmExecution(
    context: AiExecutionContext,
    rawUsage: Map<String, Any>,
    responsePayload: Map<String, Any>? = null
  ) {
    kLogger.debug {
      "[AI 추적] LLM 실행 완료 - aiRunStepId: ${context.aiRunStepId}, " +
          "elapsed: ${context.elapsedMillis()}ms"
    }

    // AiRunStep 상태 업데이트 (기본 정보)
    aiRunStepRepository.findById(context.aiRunStepId).ifPresent { step ->
      step.status = StatusType.ENDED
      step.completedAt = now()
      step.responsePayload = responsePayload
      aiRunStepRepository.save(step)
    }

    // AiRun 상태 업데이트
    aiRunRepository.findById(context.aiRunId).ifPresent { run ->
      run.status = AiRunStatus.ENDED
      aiRunRepository.save(run)
    }

    // AiUsageCollector로 토큰/비용 수집 위임 (비동기)
    try {
      aiUsageCollector.collect(
        runStepId = context.aiRunStepId,
        rawUsage = rawUsage,
        provider = context.provider,
        modelName = context.modelName
      )
    } catch (e: Exception) {
      kLogger.error(e) {
        "[AI 추적] 사용량 수집 실패 - aiRunStepId: ${context.aiRunStepId}"
      }
    }

    kLogger.info {
      "[AI 추적] LLM 실행 완료 처리됨 - aiRunStepId: ${context.aiRunStepId}, " +
          "provider: ${context.provider}, model: ${context.modelName}"
    }
  }

  /**
   * AI 실행 실패 - 상태 및 에러 정보 저장
   *
   * @param context 실행 컨텍스트
   * @param error 발생한 오류
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun failExecution(
    context: AiExecutionContext,
    error: Throwable
  ) {
    kLogger.warn(error) {
      "[AI 추적] 실행 실패 - aiRunStepId: ${context.aiRunStepId}, " +
          "type: ${context.executionType}, provider: ${context.provider}"
    }

    // AiRunStep 상태 업데이트 (FAILED)
    aiRunStepRepository.findById(context.aiRunStepId).ifPresent { step ->
      step.status = StatusType.FAILED
      step.completedAt = now()
      step.lastError = mapOf(
        "message" to (error.message ?: "Unknown error"),
        "type" to error.javaClass.simpleName,
        "timestamp" to now().toString()
      )
      aiRunStepRepository.save(step)
    }

    // AiRun 상태 업데이트
    aiRunRepository.findById(context.aiRunId).ifPresent { run ->
      run.status = AiRunStatus.ENDED
      aiRunRepository.save(run)
    }

    kLogger.info {
      "[AI 추적] 실행 실패 기록됨 - aiRunStepId: ${context.aiRunStepId}, " +
          "error: ${error.message}"
    }
  }

  /**
   * 래핑된 실행 - 자동 추적 (동기)
   *
   * 실행 블록을 감싸서 시작/완료/실패를 자동으로 추적.
   *
   * @param executionType AI 실행 유형
   * @param provider AI 프로바이더명
   * @param modelName 사용할 모델명
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param assistantId AI 어시스턴트 ID
   * @param requestPayload 요청 데이터
   * @param metricsExtractor 실행 결과에서 메트릭 추출 함수
   * @param responseExtractor 실행 결과에서 응답 데이터 추출 함수
   * @param execution 실행할 블록
   * @return 실행 결과
   */
  fun <T> trackExecution(
    executionType: AiExecutionType,
    provider: String,
    modelName: String,
    callerComponent: String,
    contextId: Long? = null,
    assistantId: Long? = null,
    requestPayload: Map<String, Any>? = null,
    metricsExtractor: (T) -> AiExecutionMetrics,
    responseExtractor: ((T) -> Map<String, Any>?)? = null,
    execution: () -> T
  ): T {
    val context = startExecution(
      executionType = executionType,
      provider = provider,
      modelName = modelName,
      callerComponent = callerComponent,
      contextId = contextId,
      assistantId = assistantId,
      requestPayload = requestPayload
    )

    return try {
      val result = execution()
      val metrics = metricsExtractor(result)
      val responsePayload = responseExtractor?.invoke(result)
      completeExecution(context, metrics, responsePayload)
      result
    } catch (e: Exception) {
      failExecution(context, e)
      throw e
    }
  }

  // ==================== Private Methods ====================

  /**
   * 새 AiRun 생성
   */
  private fun createNewAiRun(contextId: Long?, callerComponent: String): Long {
    val aiRun = AiRun(
      aiMessageId = contextId ?: 0L,
      status = AiRunStatus.STARTED,
      callerComponent = callerComponent
    )
    return aiRunRepository.save(aiRun).id
  }

  /**
   * 새 AiRunStep 생성 (확장 필드 포함)
   */
  private fun createNewAiRunStep(
    aiRunId: Long,
    assistantId: Long?,
    executionType: AiExecutionType,
    provider: String,
    modelName: String,
    requestPayload: Map<String, Any>?
  ): AiRunStep {
    val aiRunStep = AiRunStep(
      aiRunId = aiRunId,
      aiAssistantId = assistantId,
      status = StatusType.RUNNING,
      executionType = executionType,
      provider = provider,
      modelName = modelName,
      requestPayload = requestPayload,
      logs = null
    )
    return aiRunStepRepository.save(aiRunStep)
  }
}
