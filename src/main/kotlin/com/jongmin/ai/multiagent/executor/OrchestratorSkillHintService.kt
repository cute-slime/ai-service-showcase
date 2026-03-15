package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.*
import com.jongmin.ai.multiagent.skill.SkillRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 오케스트레이터의 스킬 힌트/역량 점수 계산 전담 서비스
 *
 * OrchestratorAgent에서 스킬 기반 라우팅 로직을 분리하여
 * 오케스트레이션 흐름과 점수 계산 책임을 분리한다.
 */
@Component
class OrchestratorSkillHintService(
  private val skillRegistry: SkillRegistry,
) {
  /**
   * 에이전트에 대한 스킬 힌트 사전 준비
   */
  fun prepareSkillHintsForAgent(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ) {
    val hints = suggestSkillsForAgent(agentNode, input, context)
    if (hints.isNotEmpty()) {
      context.storeSkillHints(agentNode.id, hints)
      kLogger.info {
        "스킬 힌트 준비 - agent: ${agentNode.id}, hints: ${hints.map { it.skillId }}"
      }
    }
  }

  /**
   * 에이전트에게 추천할 스킬 목록 생성
   */
  fun suggestSkillsForAgent(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): List<SkillHint> {
    val inventory = agentNode.skillInventory ?: return emptyList()
    val hints = mutableListOf<SkillHint>()
    val inputStr = input.toString().lowercase()

    inventory.skills
      .filter { it.enabled }
      .sortedByDescending { it.priority }
      .forEach { slot ->
        val skill = skillRegistry.getSkill(slot.skillId) ?: return@forEach
        val triggerConfig = slot.overrideConfig ?: skill.triggerConfig
        val relevanceScore = calculateSkillRelevance(skill, triggerConfig, inputStr, context)

        if (relevanceScore > 0.3) {
          hints.add(
            SkillHint(
              skillId = skill.id,
              skillName = skill.name,
              relevanceScore = relevanceScore,
              triggerReason = buildTriggerReason(skill, triggerConfig, inputStr),
              suggestedTiming = determineSuggestedTiming(triggerConfig),
              contextClues = extractContextClues(inputStr, skill),
            )
          )
        }
      }

    return hints
      .sortedByDescending { it.relevanceScore }
      .take(inventory.maxConcurrentSkills)
  }

  /**
   * 역량 점수 계산 (스킬 매칭 포함)
   */
  fun calculateCapabilityScoreWithSkills(
    input: Any,
    agentNode: MultiAgentNode,
    context: MultiAgentExecutionContext,
  ): Double {
    var score = calculateCapabilityScore(input, agentNode.capability)
    val skillBonus = calculateSkillMatchBonus(agentNode, input, context)
    score += skillBonus * 0.3
    return score.coerceIn(0.0, 1.0)
  }

  /**
   * 역량 점수 계산 (기본)
   */
  private fun calculateCapabilityScore(input: Any, capability: AgentCapability?): Double {
    if (capability == null) return 0.5

    val inputStr = input.toString().lowercase()
    var score = 0.0

    val keywordMatches = capability.triggerKeywords.count { keyword ->
      inputStr.contains(keyword.lowercase())
    }
    score += keywordMatches * 0.2

    capability.triggerPatterns?.forEach { pattern ->
      if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(inputStr)) {
        score += 0.3
      }
    }

    return score.coerceIn(0.0, 1.0)
  }

  /**
   * 스킬 매칭 보너스 계산
   */
  private fun calculateSkillMatchBonus(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): Double {
    val inventory = agentNode.skillInventory ?: return 0.0
    val inputStr = input.toString().lowercase()

    var bonus = 0.0
    var skillCount = 0

    inventory.skills
      .filter { it.enabled }
      .forEach { slot ->
        val skill = skillRegistry.getSkill(slot.skillId) ?: return@forEach
        val relevance = calculateSkillRelevance(skill, skill.triggerConfig, inputStr, context)
        if (relevance > 0.3) {
          bonus += relevance * slot.priority
          skillCount++
        }
      }

    return if (skillCount > 0) bonus / skillCount else 0.0
  }

  /**
   * 스킬 관련성 점수 계산
   */
  private fun calculateSkillRelevance(
    skill: AgentSkill,
    triggerConfig: SkillTriggerConfig,
    inputStr: String,
    context: MultiAgentExecutionContext,
  ): Double {
    var score = 0.0

    val keywordMatches = triggerConfig.triggerKeywords.count { keyword ->
      inputStr.contains(keyword.lowercase())
    }
    score += keywordMatches * 0.2

    triggerConfig.triggerPatterns?.forEach { pattern ->
      if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(inputStr)) {
        score += 0.3
      }
    }

    triggerConfig.conditions?.forEach { condition ->
      if (evaluateCondition(condition, inputStr, context)) {
        score += 0.25
      }
    }

    if (triggerConfig.triggerMode == SkillTriggerMode.ALWAYS) {
      score = maxOf(score, 0.5)
    }

    return score.coerceIn(0.0, 1.0)
  }

  /**
   * 조건 평가
   */
  private fun evaluateCondition(
    condition: SkillCondition,
    inputStr: String,
    context: MultiAgentExecutionContext,
  ): Boolean {
    return when (condition.type) {
      ConditionType.INPUT_CONTAINS -> {
        val value = condition.value.toString().lowercase()
        inputStr.contains(value)
      }

      ConditionType.CONTEXT_HAS -> {
        val key = condition.value.toString()
        context.hasContextKey(key)
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

      ConditionType.PREVIOUS_AGENT_FAILED -> {
        context.hasPreviousAgentFailed()
      }

      else -> false
    }
  }

  /**
   * 트리거 이유 생성
   */
  private fun buildTriggerReason(
    skill: AgentSkill,
    triggerConfig: SkillTriggerConfig,
    inputStr: String,
  ): String {
    val reasons = mutableListOf<String>()

    val matchedKeywords = triggerConfig.triggerKeywords.filter { keyword ->
      inputStr.contains(keyword.lowercase())
    }
    if (matchedKeywords.isNotEmpty()) {
      reasons.add("키워드 매칭: ${matchedKeywords.joinToString()}")
    }

    triggerConfig.triggerPatterns?.forEach { pattern ->
      if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(inputStr)) {
        reasons.add("패턴 매칭: $pattern")
      }
    }

    when (triggerConfig.triggerMode) {
      SkillTriggerMode.ALWAYS -> reasons.add("항상 실행 모드")
      SkillTriggerMode.PRE_PROCESS -> reasons.add("전처리 스킬")
      SkillTriggerMode.POST_PROCESS -> reasons.add("후처리 스킬")
      else -> {}
    }

    return reasons.joinToString("; ").ifEmpty { "관련성 분석 기반 추천" }
  }

  /**
   * 추천 실행 시점 결정
   */
  private fun determineSuggestedTiming(triggerConfig: SkillTriggerConfig): SkillTriggerMode {
    return triggerConfig.triggerMode
  }

  /**
   * 컨텍스트 단서 추출
   */
  private fun extractContextClues(inputStr: String, skill: AgentSkill): List<String> {
    val clues = mutableListOf<String>()

    skill.triggerConfig.triggerKeywords.forEach { keyword ->
      val index = inputStr.indexOf(keyword.lowercase())
      if (index >= 0) {
        val start = maxOf(0, index - 20)
        val end = minOf(inputStr.length, index + keyword.length + 20)
        clues.add(inputStr.substring(start, end).trim())
      }
    }

    return clues.take(3)
  }
}
