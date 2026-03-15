package com.jongmin.ai.core.platform.component.tracking

import com.jongmin.jspring.core.util.JTimeUtils.now
import java.time.ZonedDateTime

/**
 * LLM 실행 컨텍스트
 *
 * LLM 호출의 추적 정보를 담는 데이터 클래스입니다.
 * AiRun/AiRunStep과 연계하여 토큰 사용량 및 비용을 추적합니다.
 *
 * @property aiRunId AiRun 엔티티 ID
 * @property aiRunStepId AiRunStep 엔티티 ID
 * @property assistantId 사용된 AiAssistant ID (선택)
 * @property provider AI 프로바이더명 (openai, anthropic 등)
 * @property model 사용된 모델명
 * @property contextId 호출 컨텍스트 ID (aiMessageId, gameSessionId 등)
 * @property startedAt 구동 시작 시간
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
data class LlmExecutionContext(
  val aiRunId: Long,
  val aiRunStepId: Long,
  val assistantId: Long?,
  val provider: String,
  val model: String,
  val contextId: Long? = null,
  val startedAt: ZonedDateTime = now()
) {
  /**
   * 실행 시간 (밀리초)
   */
  fun elapsedMillis(): Long {
    return java.time.Duration.between(startedAt, now()).toMillis()
  }

  /**
   * 디버그용 문자열
   */
  override fun toString(): String {
    return "LlmExecutionContext(aiRunId=$aiRunId, aiRunStepId=$aiRunStepId, " +
        "provider=$provider, model=$model, contextId=$contextId)"
  }
}
