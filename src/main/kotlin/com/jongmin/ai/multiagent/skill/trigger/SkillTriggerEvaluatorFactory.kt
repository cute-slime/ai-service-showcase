package com.jongmin.ai.multiagent.skill.trigger

import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 트리거 평가기 팩토리
 * 전략에 맞는 평가기 반환
 *
 * Spring이 모든 SkillTriggerEvaluator 구현체를 자동 주입
 */
@Component
class SkillTriggerEvaluatorFactory(
  private val evaluators: List<SkillTriggerEvaluator>,
) {

  /**
   * 전략별 평가기 맵 (lazy 초기화)
   */
  private val evaluatorMap: Map<TriggerStrategy, SkillTriggerEvaluator> by lazy {
    evaluators.associateBy { it.strategy }.also { map ->
      kLogger.info { "트리거 평가기 등록 완료 - strategies: ${map.keys}" }
    }
  }

  /**
   * 기본 평가기 (RULE_BASED)
   */
  private val defaultEvaluator: SkillTriggerEvaluator by lazy {
    evaluatorMap[TriggerStrategy.RULE_BASED]
      ?: throw IllegalStateException("RuleBasedTriggerEvaluator가 등록되지 않았습니다")
  }

  /**
   * 전략에 맞는 평가기 반환
   *
   * @param strategy 트리거 전략
   * @return 해당 전략의 평가기 (없으면 기본값 RULE_BASED)
   */
  fun getEvaluator(strategy: TriggerStrategy): SkillTriggerEvaluator {
    return evaluatorMap[strategy] ?: run {
      kLogger.warn { "전략 $strategy 에 대한 평가기가 없습니다. 기본값(RULE_BASED) 사용" }
      defaultEvaluator
    }
  }

  /**
   * 지원하는 전략 목록
   */
  fun getSupportedStrategies(): Set<TriggerStrategy> {
    return evaluatorMap.keys
  }

  /**
   * 특정 전략 지원 여부
   */
  fun isStrategySupported(strategy: TriggerStrategy): Boolean {
    return evaluatorMap.containsKey(strategy)
  }
}
