package com.jongmin.ai.multiagent.skill.trigger

import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.ConditionType
import com.jongmin.ai.multiagent.model.SkillCondition
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 규칙 기반 트리거 평가기
 * 키워드, 패턴, 조건 매칭으로 트리거 여부 판단
 *
 * 기존 SkillManager.checkTriggerConditions() 로직 추출
 */
@Component
class RuleBasedTriggerEvaluator : SkillTriggerEvaluator {

  override val strategy = TriggerStrategy.RULE_BASED

  override fun shouldTrigger(
    skill: AgentSkill,
    output: Any,
    context: MultiAgentExecutionContext,
  ): TriggerEvaluationResult {
    val startTime = System.currentTimeMillis()
    val triggerConfig = skill.triggerConfig
    val outputStr = output.toString().lowercase()

    // 1. 키워드 매칭
    for (keyword in triggerConfig.triggerKeywords) {
      if (outputStr.contains(keyword.lowercase())) {
        val duration = System.currentTimeMillis() - startTime
        kLogger.debug { "키워드 매칭 - skill: ${skill.id}, keyword: $keyword" }
        return TriggerEvaluationResult.triggered(
          reason = "키워드 매칭: $keyword",
          matchedRule = "keyword:$keyword",
          evaluationTimeMs = duration,
        )
      }
    }

    // 2. 패턴 매칭
    triggerConfig.triggerPatterns?.forEach { pattern ->
      try {
        if (Regex(pattern).containsMatchIn(output.toString())) {
          val duration = System.currentTimeMillis() - startTime
          kLogger.debug { "패턴 매칭 - skill: ${skill.id}, pattern: $pattern" }
          return TriggerEvaluationResult.triggered(
            reason = "패턴 매칭: $pattern",
            matchedRule = "pattern:$pattern",
            evaluationTimeMs = duration,
          )
        }
      } catch (e: Exception) {
        kLogger.warn { "잘못된 정규식 패턴 - skill: ${skill.id}, pattern: $pattern, error: ${e.message}" }
      }
    }

    // 3. 조건 평가
    triggerConfig.conditions?.forEach { condition ->
      if (evaluateCondition(condition, output, context)) {
        val duration = System.currentTimeMillis() - startTime
        kLogger.debug { "조건 매칭 - skill: ${skill.id}, condition: ${condition.type}" }
        return TriggerEvaluationResult.triggered(
          reason = "조건 충족: ${condition.type}",
          matchedRule = "condition:${condition.type}",
          evaluationTimeMs = duration,
        )
      }
    }

    val duration = System.currentTimeMillis() - startTime
    return TriggerEvaluationResult.notTriggered(
      reason = "매칭되는 규칙 없음",
      evaluationTimeMs = duration,
    )
  }

  /**
   * 개별 조건 평가
   */
  private fun evaluateCondition(
    condition: SkillCondition,
    output: Any,
    context: MultiAgentExecutionContext,
  ): Boolean {
    return when (condition.type) {
      ConditionType.INPUT_CONTAINS -> {
        output.toString().contains(condition.value.toString())
      }

      ConditionType.SCORE_BELOW -> {
        val threshold = when (val value = condition.value) {
          is Double -> value
          is Number -> value.toDouble()
          else -> 0.7
        }
        val lastScore = context.getLastAgentScore()
        lastScore != null && lastScore < threshold
      }

      ConditionType.RETRY_COUNT -> {
        val agentId = condition.field ?: return false
        val retryCount = context.getRetryCount(agentId)
        val targetValue = when (val value = condition.value) {
          is Int -> value
          is Number -> value.toInt()
          else -> 0
        }
        when (condition.operator) {
          "gt" -> retryCount > targetValue
          "gte" -> retryCount >= targetValue
          "lt" -> retryCount < targetValue
          "lte" -> retryCount <= targetValue
          "eq" -> retryCount == targetValue
          else -> false
        }
      }

      ConditionType.CONTEXT_HAS -> {
        context.hasContextKey(condition.value.toString())
      }

      ConditionType.PREVIOUS_AGENT_FAILED -> {
        context.hasPreviousAgentFailed()
      }

      else -> false
    }
  }
}
