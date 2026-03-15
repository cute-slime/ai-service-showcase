package com.jongmin.ai.multiagent.skill.trigger

import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 하이브리드 트리거 평가기
 * 규칙 먼저 체크 → 매칭 안되면 LLM 판단
 *
 * 장점:
 * - 규칙으로 처리 가능한 케이스는 빠르게 처리 (비용 없음)
 * - 복잡한 케이스는 LLM이 문맥 이해하여 판단
 */
@Component
class HybridTriggerEvaluator(
  private val ruleBasedEvaluator: RuleBasedTriggerEvaluator,
  private val llmBasedEvaluator: LlmBasedTriggerEvaluator,
) : SkillTriggerEvaluator {

  override val strategy = TriggerStrategy.HYBRID

  override fun shouldTrigger(
    skill: AgentSkill,
    output: Any,
    context: MultiAgentExecutionContext,
  ): TriggerEvaluationResult {
    val startTime = System.currentTimeMillis()
    kLogger.debug { "하이브리드 트리거 평가 시작 - skill: ${skill.id}" }

    // 1단계: 규칙 기반 평가
    val ruleResult = ruleBasedEvaluator.shouldTrigger(skill, output, context)

    // 규칙에서 명확히 트리거됨 → LLM 호출 없이 바로 반환
    if (ruleResult.shouldTrigger) {
      val duration = System.currentTimeMillis() - startTime
      kLogger.debug {
        "규칙 기반 트리거 확정 - skill: ${skill.id}, rule: ${ruleResult.matchedRule}"
      }
      return ruleResult.copy(
        reason = "[RULE] ${ruleResult.reason}",
        strategyUsed = TriggerStrategy.HYBRID,
        evaluationTimeMs = duration,
      )
    }

    // 2단계: 규칙 미매칭 → LLM 판단 요청
    kLogger.debug { "규칙 미매칭, LLM 판단 요청 - skill: ${skill.id}" }

    return try {
      val llmResult = llmBasedEvaluator.shouldTrigger(skill, output, context)
      val duration = System.currentTimeMillis() - startTime

      llmResult.copy(
        reason = "[LLM] ${llmResult.reason}",
        strategyUsed = TriggerStrategy.HYBRID,
        evaluationTimeMs = duration,
      )

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      kLogger.error(e) { "LLM 평가 실패, 규칙 결과 반환 - skill: ${skill.id}" }

      // LLM 실패 시 규칙 기반 결과 반환 (기본적으로 미트리거)
      ruleResult.copy(
        reason = "[HYBRID] LLM 실패, 규칙 기반 결과: ${ruleResult.reason}",
        strategyUsed = TriggerStrategy.HYBRID,
        evaluationTimeMs = duration,
      )
    }
  }
}
