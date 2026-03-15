package com.jongmin.ai.multiagent.skill

import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.*
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import com.jongmin.ai.multiagent.skill.trigger.SkillTriggerEvaluatorFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 관리자
 * 스킬 트리거 판단, 실행 조율, 결과 수집
 *
 * Phase 4: 트리거 전략 패턴 적용
 * - SkillTriggerEvaluatorFactory를 통해 전략별 평가기 사용
 * - RULE_BASED, LLM_BASED, HYBRID 전략 지원
 */
@Component
class SkillManager(
  private val skillRegistry: SkillRegistry,
  private val skillExecutors: List<SkillExecutor>,
  private val triggerEvaluatorFactory: SkillTriggerEvaluatorFactory,
) {

  /**
   * 트리거 모드별 스킬 조회
   */
  fun findSkillsByTriggerMode(
    agentNode: MultiAgentNode,
    triggerMode: SkillTriggerMode,
  ): List<AgentSkill> {
    val inventory = agentNode.skillInventory ?: return emptyList()

    return inventory.skills
      .filter { it.enabled }
      .sortedByDescending { it.priority }
      .mapNotNull { slot -> skillRegistry.getSkill(slot.skillId) }
      .filter { skill -> skill.triggerConfig.triggerMode == triggerMode }
  }

  /**
   * 조건 충족하는 스킬 찾기 (AUTO, ON_DEMAND)
   *
   * Phase 4: 트리거 전략 패턴 적용
   * - 스킬별 triggerStrategy에 따라 적절한 평가기 사용
   * - 평가 결과와 함께 로깅
   */
  fun findTriggeredSkills(
    agentNode: MultiAgentNode,
    output: Any,
    context: MultiAgentExecutionContext,
  ): List<AgentSkill> {
    val inventory = agentNode.skillInventory ?: return emptyList()

    return inventory.skills
      .filter { it.enabled }
      .sortedByDescending { it.priority }
      .mapNotNull { slot -> skillRegistry.getSkill(slot.skillId) }
      .filter { skill ->
        skill.triggerConfig.triggerMode in listOf(SkillTriggerMode.AUTO, SkillTriggerMode.ON_DEMAND)
      }
      .filter { skill ->
        val strategy = getTriggerStrategy(skill)
        val evaluator = triggerEvaluatorFactory.getEvaluator(strategy)
        val result = evaluator.shouldTrigger(skill, output, context)

        kLogger.debug {
          "트리거 평가 - skill: ${skill.id}, strategy: $strategy, " +
            "triggered: ${result.shouldTrigger}, reason: ${result.reason}"
        }

        result.shouldTrigger
      }
  }

  /**
   * 스킬의 트리거 전략 조회
   * executorConfig에서 triggerStrategy 필드 확인, 없으면 기본값(RULE_BASED)
   */
  private fun getTriggerStrategy(skill: AgentSkill): TriggerStrategy {
    val strategyValue = skill.executorConfig?.get("triggerStrategy")
    return when (strategyValue) {
      is String -> try {
        TriggerStrategy.valueOf(strategyValue.uppercase())
      } catch (e: Exception) {
        TriggerStrategy.RULE_BASED
      }
      is TriggerStrategy -> strategyValue
      else -> TriggerStrategy.RULE_BASED
    }
  }

  /**
   * 스킬 실행
   */
  fun executeSkill(
    skill: AgentSkill,
    context: SkillExecutionContext,
  ): SkillExecutionResult {
    kLogger.debug { "스킬 실행 - skillId: ${skill.id}, agentId: ${context.agentId}" }

    val startTime = System.currentTimeMillis()

    val executor = skillExecutors.find { it.skillType == skill.executorType }
    if (executor == null) {
      kLogger.warn { "스킬 실행기 없음 - type: ${skill.executorType}" }
      return SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = "No executor found for skill type: ${skill.executorType}"
      )
    }

    return try {
      if (!executor.canExecute(skill, context)) {
        return SkillExecutionResult(
          skillId = skill.id,
          success = false,
          output = null,
          error = "Skill cannot be executed in current context"
        )
      }

      val result = executor.execute(skill, context)
      val durationMs = System.currentTimeMillis() - startTime

      result.copy(
        metadata = SkillExecutionMetadata(
          executedAt = Instant.now(),
          durationMs = durationMs,
          costIncurred = result.metadata?.costIncurred,
          invocationReason = context.invocationReason
        )
      )
    } catch (e: Exception) {
      kLogger.error(e) { "스킬 실행 실패 - skillId: ${skill.id}" }
      SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = e.message,
        metadata = SkillExecutionMetadata(
          durationMs = System.currentTimeMillis() - startTime,
          invocationReason = context.invocationReason
        )
      )
    }
  }

}
