package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 오케스트레이터 설정
 */
data class OrchestratorConfig(
  // 라우팅 설정
  val routingMode: RoutingMode = RoutingMode.DYNAMIC,
  val dynamicRoutingConfig: DynamicRoutingConfig = DynamicRoutingConfig(),

  // 실행 사이클 제한 (워크플로우 전체)
  val maxExecutionCycles: Int? = null,         // null이면 동적 계산: 에이전트 수 × multiplier
  val executionCyclesMultiplier: Double = 3.0,

  // 에이전트 간 협업 대화 제한
  val maxConversationTurns: Int = 10,

  // 재시도 설정 (사이클 미소비)
  val maxRetryPerAgent: Int = 3,
  val retryWithGuidance: Boolean = true,       // 멘토링 패턴 활성화

  // Self-Evaluation 설정
  val evaluationPassThreshold: Double = 0.7,
  val autoRetryOnLowScore: Boolean = true,

  // Human Review 설정 (Phase 7)
  val humanReviewConfig: HumanReviewConfig? = HumanReviewConfig.smart(),
) {
  /**
   * 실제 최대 실행 사이클 계산
   */
  fun getEffectiveMaxExecutionCycles(agentCount: Int): Int {
    return maxExecutionCycles ?: (agentCount * executionCyclesMultiplier).toInt()
  }
}

/**
 * 라우팅 모드
 */
enum class RoutingMode(private val typeCode: Int) {
  STATIC(1),    // 정의된 워크플로우 순서대로
  DYNAMIC(2),   // 오케스트레이터가 최적 경로 결정 (기본값)
  ;

  companion object {
    private val map = entries.associateBy(RoutingMode::typeCode)

    @JsonCreator
    fun getType(value: Int): RoutingMode = map[value] ?: DYNAMIC
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 동적 라우팅 설정
 */
data class DynamicRoutingConfig(
  val skipThreshold: Double = 0.8,             // 이 점수 이상이면 다음 에이전트 스킵 고려
  val maxSkipCount: Int = 3,                   // 최대 스킵 횟수
  val alwaysExecute: List<String> = emptyList(), // 항상 실행해야 하는 에이전트 ID
)
