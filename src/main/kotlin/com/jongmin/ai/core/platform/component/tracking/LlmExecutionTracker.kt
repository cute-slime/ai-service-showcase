package com.jongmin.ai.core.platform.component.tracking

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiRunRepository
import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.AiRunStepRepository
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.entity.AiRun
import com.jongmin.ai.core.platform.entity.AiRunStep
import com.jongmin.ai.core.platform.service.AiUsageCollector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 실행 추적 컴포넌트
 *
 * 모든 LLM 호출에 대해 AiRun/AiRunStep을 생성하고
 * 토큰 사용량 및 비용을 추적합니다.
 *
 * 주요 기능:
 * - 구동 시작 시 AiRun/AiRunStep 자동 생성
 * - 실행 완료 시 상태 업데이트 및 AiUsageCollector 호출
 * - 게임, 채팅, 롤플레잉 등 다양한 컨텍스트 지원
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Component
class LlmExecutionTracker(
  private val aiRunRepository: AiRunRepository,
  private val aiRunStepRepository: AiRunStepRepository,
  private val aiUsageCollector: AiUsageCollector
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * LLM 구동 시작 - AiRun과 AiRunStep 생성
   *
   * @param assistant 실행할 AI 어시스턴트
   * @param contextId 컨텍스트 ID (aiMessageId, gameSessionId 등)
   * @param createAiRun AiRun 생성 여부 (기존 AiRun이 있으면 false)
   * @param existingAiRunId 기존 AiRun ID (createAiRun=false일 때 필수)
   * @return LlmExecutionContext
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun startExecution(
    assistant: RunnableAiAssistant,
    contextId: Long? = null,
    createAiRun: Boolean = true,
    existingAiRunId: Long? = null
  ): LlmExecutionContext {
    kLogger.debug {
      "[LLM 추적] 구동 시작 - provider: ${assistant.provider}, model: ${assistant.model}, " +
          "contextId: $contextId, createAiRun: $createAiRun"
    }

    // AiRun 생성 또는 기존 사용
    val aiRunId = if (createAiRun) {
      createNewAiRun(contextId)
    } else {
      requireNotNull(existingAiRunId) { "existingAiRunId는 createAiRun=false일 때 필수입니다." }
      existingAiRunId
    }

    // AiRunStep 생성
    val aiRunStep = createNewAiRunStep(aiRunId, assistant.id)

    val context = LlmExecutionContext(
      aiRunId = aiRunId,
      aiRunStepId = aiRunStep.id,
      assistantId = assistant.id,
      provider = assistant.provider,
      model = assistant.model,
      contextId = contextId
    )

    kLogger.info {
      "[LLM 추적] 컨텍스트 생성 완료 - aiRunId: $aiRunId, aiRunStepId: ${aiRunStep.id}"
    }

    return context
  }

  /**
   * LLM 구동 시작 (간소화 버전) - AiRunStep만 생성
   *
   * 기존 AiRun 없이 독립적으로 추적이 필요한 경우 사용합니다.
   * (예: 게임 채팅, 단발성 LLM 호출)
   *
   * @param assistant 실행할 AI 어시스턴트
   * @param contextId 컨텍스트 ID
   * @return LlmExecutionContext
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun startSimpleExecution(
    assistant: RunnableAiAssistant,
    contextId: Long? = null
  ): LlmExecutionContext {
    // 임시 AiRun 생성 (추적 목적)
    val aiRunId = createNewAiRun(contextId)
    val aiRunStep = createNewAiRunStep(aiRunId, assistant.id)

    val context = LlmExecutionContext(
      aiRunId = aiRunId,
      aiRunStepId = aiRunStep.id,
      assistantId = assistant.id,
      provider = assistant.provider,
      model = assistant.model,
      contextId = contextId
    )

    kLogger.debug {
      "[LLM 추적] 간소화 컨텍스트 생성 - aiRunStepId: ${aiRunStep.id}"
    }

    return context
  }

  /**
   * LLM 실행 완료 - 상태 업데이트 및 사용량 수집
   *
   * @param context 실행 컨텍스트
   * @param rawUsage API 응답의 usage 객체
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun completeExecution(
    context: LlmExecutionContext,
    rawUsage: Map<String, Any>
  ) {
    kLogger.debug {
      "[LLM 추적] 실행 완료 - aiRunStepId: ${context.aiRunStepId}, " +
          "elapsed: ${context.elapsedMillis()}ms"
    }

    // AiRunStep 상태 업데이트
    aiRunStepRepository.findById(context.aiRunStepId).ifPresent { step ->
      step.status = StatusType.ENDED
      step.completedAt = now()
      aiRunStepRepository.save(step)
    }

    // AiRun 상태 업데이트
    aiRunRepository.findById(context.aiRunId).ifPresent { run ->
      run.status = AiRunStatus.ENDED
      aiRunRepository.save(run)
    }

    // AiUsageCollector로 토큰/비용 수집
    try {
      aiUsageCollector.collect(
        runStepId = context.aiRunStepId,
        rawUsage = rawUsage,
        provider = context.provider,
        modelName = context.model
      )
    } catch (e: Exception) {
      kLogger.error(e) {
        "[LLM 추적] 사용량 수집 실패 - aiRunStepId: ${context.aiRunStepId}"
      }
    }

    kLogger.info {
      "[LLM 추적] 실행 완료 처리됨 - aiRunStepId: ${context.aiRunStepId}, " +
          "provider: ${context.provider}, model: ${context.model}"
    }
  }

  /**
   * LLM 실행 실패 - 상태만 업데이트
   *
   * @param context 실행 컨텍스트
   * @param error 발생한 오류
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun failExecution(
    context: LlmExecutionContext,
    error: Throwable
  ) {
    kLogger.warn(error) {
      "[LLM 추적] 실행 실패 - aiRunStepId: ${context.aiRunStepId}"
    }

    // AiRunStep 상태 업데이트 (FAILED)
    aiRunStepRepository.findById(context.aiRunStepId).ifPresent { step ->
      step.status = StatusType.FAILED
      step.completedAt = now()
      aiRunStepRepository.save(step)
    }

    // AiRun 상태 업데이트
    aiRunRepository.findById(context.aiRunId).ifPresent { run ->
      run.status = AiRunStatus.ENDED
      aiRunRepository.save(run)
    }
  }

  // ==================== Private Methods ====================

  /**
   * 새 AiRun 생성
   */
  private fun createNewAiRun(contextId: Long?): Long {
    val aiRun = AiRun(
      aiMessageId = contextId ?: 0L,  // contextId 없으면 0
      status = AiRunStatus.STARTED
    )
    return aiRunRepository.save(aiRun).id
  }

  /**
   * 새 AiRunStep 생성
   */
  private fun createNewAiRunStep(aiRunId: Long, assistantId: Long?): AiRunStep {
    val aiRunStep = AiRunStep(
      aiRunId = aiRunId,
      aiAssistantId = assistantId,
      status = StatusType.RUNNING,
      logs = null
    )
    return aiRunStepRepository.save(aiRunStep)
  }
}
