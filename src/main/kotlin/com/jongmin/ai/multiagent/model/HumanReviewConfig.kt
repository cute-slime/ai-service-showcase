package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Human Review 설정
 * 체크박스 형태로 다중 선택 가능한 5가지 가드
 */
data class HumanReviewConfig(
  // Guard 1: 에이전트 개입 요청 허용
  val agentInterventionEnabled: Boolean = true,

  // Guard 2: 스마트 HITL (애매한 결과만)
  val smartHitlEnabled: Boolean = true,
  val smartHitlConfig: SmartHitlConfig = SmartHitlConfig(),

  // Guard 3: 체크포인트 (특정 에이전트)
  val checkpointEnabled: Boolean = false,
  val checkpointAgentIds: List<String> = emptyList(),

  // Guard 4: 비용 가드
  val costGuardEnabled: Boolean = true,
  val costGuardConfig: CostGuardConfig = CostGuardConfig(),

  // Guard 5: 풀 HITL (모든 실행)
  val fullHitlEnabled: Boolean = false,

  // 공통 설정
  val reviewTimeoutMinutes: Int = 30,                                         // 검토 대기 시간
  val defaultAction: ReviewDefaultAction = ReviewDefaultAction.CONTINUE,      // 타임아웃 시 기본 동작
) {
  companion object {
    /**
     * 스마트 모드 (기본값)
     * Guard 1, 2, 4 활성화
     */
    fun smart() = HumanReviewConfig(
      agentInterventionEnabled = true,
      smartHitlEnabled = true,
      checkpointEnabled = false,
      costGuardEnabled = true,
      fullHitlEnabled = false
    )

    /**
     * 디버그 모드
     * 모든 가드 활성화
     */
    fun debug() = HumanReviewConfig(
      agentInterventionEnabled = true,
      smartHitlEnabled = true,
      checkpointEnabled = true,
      costGuardEnabled = true,
      fullHitlEnabled = true
    )

    /**
     * 최소 모드
     * 비용 가드만 활성화
     */
    fun minimal() = HumanReviewConfig(
      agentInterventionEnabled = false,
      smartHitlEnabled = false,
      checkpointEnabled = false,
      costGuardEnabled = true,
      fullHitlEnabled = false
    )

    /**
     * 비활성화 (자동 실행)
     */
    fun disabled() = HumanReviewConfig(
      agentInterventionEnabled = false,
      smartHitlEnabled = false,
      checkpointEnabled = false,
      costGuardEnabled = false,
      fullHitlEnabled = false
    )
  }
}

/**
 * 스마트 HITL 설정
 */
data class SmartHitlConfig(
  val ambiguousScoreMin: Double = 0.4,        // 이 점수 이상이면서
  val ambiguousScoreMax: Double = 0.7,        // 이 점수 미만이면 "애매함"
  val lowConfidenceThreshold: Double = 0.5,   // 확신도가 이 이하면 검토
)

/**
 * 비용 가드 설정
 */
data class CostGuardConfig(
  val budgetWarningThreshold: Double = 0.7,   // 예산 70% 도달 시 경고
  val budgetHaltThreshold: Double = 0.9,      // 예산 90% 도달 시 중단
  val estimatedBudget: Double? = null,        // 예상 예산 (null이면 무제한)
  val perAgentCostLimit: Double? = null,      // 에이전트당 비용 제한
)

/**
 * 검토 타임아웃 시 기본 동작
 */
enum class ReviewDefaultAction(private val typeCode: Int) {
  CONTINUE(1),   // 현재 결과로 계속 진행
  ABORT(2),      // 워크플로우 중단
  RETRY(3),      // 재시도
  ;

  companion object {
    private val map = entries.associateBy(ReviewDefaultAction::typeCode)

    @JsonCreator
    fun getType(value: Int): ReviewDefaultAction = map[value] ?: CONTINUE
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
