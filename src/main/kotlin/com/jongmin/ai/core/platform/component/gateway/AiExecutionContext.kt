package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiExecutionType
import java.time.Duration
import java.time.ZonedDateTime

/**
 * AI 실행 컨텍스트
 *
 * 모든 AI 호출(LLM, VLM, 이미지 생성, 비디오 생성 등)의 추적 정보를 담는 데이터 클래스.
 * UnifiedAiExecutionTracker가 생성하며, 게이트웨이에서 실행 완료 시 업데이트에 사용됨.
 *
 * @property aiRunId AiRun 엔티티 ID
 * @property aiRunStepId AiRunStep 엔티티 ID
 * @property executionType AI 실행 유형 (LLM, VLM, IMAGE_GENERATION 등)
 * @property provider AI 프로바이더명 (openai, anthropic, comfyui 등)
 * @property modelName 사용된 모델명
 * @property callerComponent 호출자 컴포넌트 식별자
 * @property assistantId 사용된 AiAssistant ID (선택)
 * @property contextId 호출 컨텍스트 ID (aiMessageId, gameSessionId 등)
 * @property requestPayload 요청 데이터 (프롬프트, 설정값 등)
 * @property startedAt 구동 시작 시간
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
data class AiExecutionContext(
  val aiRunId: Long,
  val aiRunStepId: Long,
  val executionType: AiExecutionType,
  val provider: String,
  val modelName: String,
  val callerComponent: String,
  val assistantId: Long? = null,
  val contextId: Long? = null,
  val requestPayload: Map<String, Any>? = null,
  val startedAt: ZonedDateTime = now()
) {
  /**
   * 실행 시간 (밀리초)
   */
  fun elapsedMillis(): Long {
    return Duration.between(startedAt, now()).toMillis()
  }

  /**
   * 디버그용 문자열
   */
  override fun toString(): String {
    return "AiExecutionContext(aiRunId=$aiRunId, aiRunStepId=$aiRunStepId, " +
        "type=$executionType, provider=$provider, model=$modelName, caller=$callerComponent)"
  }
}
