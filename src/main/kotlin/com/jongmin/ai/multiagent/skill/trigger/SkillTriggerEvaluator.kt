package com.jongmin.ai.multiagent.skill.trigger

import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy

/**
 * 스킬 트리거 평가기 인터페이스
 * 전략 패턴으로 트리거 판단 방식 추상화
 *
 * 구현체:
 * - RuleBasedTriggerEvaluator: 키워드/패턴/조건 규칙 기반
 * - LlmBasedTriggerEvaluator: LLM 문맥 이해 기반
 * - HybridTriggerEvaluator: 규칙 우선, 불확실 시 LLM
 */
interface SkillTriggerEvaluator {

  /**
   * 이 평가기가 처리하는 전략 타입
   */
  val strategy: TriggerStrategy

  /**
   * 스킬 트리거 여부 판단
   *
   * @param skill 평가할 스킬
   * @param output 에이전트 출력 (ON_DEMAND 판단용)
   * @param context 실행 컨텍스트
   * @return 트리거 평가 결과
   */
  fun shouldTrigger(
    skill: AgentSkill,
    output: Any,
    context: MultiAgentExecutionContext,
  ): TriggerEvaluationResult
}

/**
 * 트리거 평가 결과
 */
data class TriggerEvaluationResult(
  /**
   * 트리거 여부
   */
  val shouldTrigger: Boolean,

  /**
   * 트리거/미트리거 사유
   * 디버깅 및 로깅용
   */
  val reason: String? = null,

  /**
   * LLM 판단 시 신뢰도 (0.0 ~ 1.0)
   * 규칙 기반에서는 null
   */
  val confidence: Double? = null,

  /**
   * 규칙 기반 시 매칭된 규칙
   * 예: "keyword:검색", "pattern:.*뉴스.*", "condition:SCORE_BELOW"
   */
  val matchedRule: String? = null,

  /**
   * 사용된 전략
   */
  val strategyUsed: TriggerStrategy? = null,

  /**
   * 평가 소요 시간 (밀리초)
   */
  val evaluationTimeMs: Long? = null,
) {
  companion object {
    /**
     * 트리거 결과 생성 (규칙 기반)
     */
    fun triggered(
      reason: String,
      matchedRule: String,
      evaluationTimeMs: Long? = null,
    ) = TriggerEvaluationResult(
      shouldTrigger = true,
      reason = reason,
      matchedRule = matchedRule,
      strategyUsed = TriggerStrategy.RULE_BASED,
      evaluationTimeMs = evaluationTimeMs,
    )

    /**
     * 미트리거 결과 생성 (규칙 기반)
     */
    fun notTriggered(
      reason: String = "매칭되는 규칙 없음",
      evaluationTimeMs: Long? = null,
    ) = TriggerEvaluationResult(
      shouldTrigger = false,
      reason = reason,
      strategyUsed = TriggerStrategy.RULE_BASED,
      evaluationTimeMs = evaluationTimeMs,
    )

    /**
     * 트리거 결과 생성 (LLM 기반)
     */
    fun triggeredByLlm(
      reason: String,
      confidence: Double,
      evaluationTimeMs: Long? = null,
    ) = TriggerEvaluationResult(
      shouldTrigger = true,
      reason = reason,
      confidence = confidence,
      strategyUsed = TriggerStrategy.LLM_BASED,
      evaluationTimeMs = evaluationTimeMs,
    )

    /**
     * 미트리거 결과 생성 (LLM 기반)
     */
    fun notTriggeredByLlm(
      reason: String,
      confidence: Double,
      evaluationTimeMs: Long? = null,
    ) = TriggerEvaluationResult(
      shouldTrigger = false,
      reason = reason,
      confidence = confidence,
      strategyUsed = TriggerStrategy.LLM_BASED,
      evaluationTimeMs = evaluationTimeMs,
    )
  }
}
