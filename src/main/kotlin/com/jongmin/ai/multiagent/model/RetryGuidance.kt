package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 재시도 가이던스
 * 오케스트레이터가 에이전트에게 제공하는 개선 지침
 */
data class RetryGuidance(
  val agentId: String,                        // 대상 에이전트
  val attemptNumber: Int,                     // 재시도 횟수 (1부터 시작)
  val previousScore: Double,                  // 이전 점수
  val targetScore: Double,                    // 목표 점수
  val issues: List<GuidanceIssue>,            // 발견된 문제점
  val suggestions: List<String>,              // 개선 제안
  val contextEnrichment: Map<String, Any>?,   // 추가 컨텍스트
  val priority: GuidancePriority = GuidancePriority.MEDIUM,
)

/**
 * 가이던스 이슈
 */
data class GuidanceIssue(
  val criterionName: String,    // 관련 평가 기준
  val currentScore: Double,     // 현재 점수
  val targetScore: Double,      // 목표 점수
  val description: String,      // 문제 설명
  val improvement: String,      // 개선 방법
)

/**
 * 가이던스 우선순위
 */
enum class GuidancePriority(private val typeCode: Int) {
  LOW(1),       // 경미한 개선 필요
  MEDIUM(2),    // 중간 수준 개선 필요
  HIGH(3),      // 중요한 개선 필요
  CRITICAL(4),  // 필수 개선 (이전 결과 사용 불가)
  ;

  companion object {
    private val map = entries.associateBy(GuidancePriority::typeCode)

    @JsonCreator
    fun getType(value: Int): GuidancePriority = map[value] ?: MEDIUM
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 강화된 에이전트 입력
 * 원본 입력 + 재시도 가이던스 + 이전 결과
 */
data class EnrichedAgentInput(
  val originalInput: Any,                       // 원본 입력
  val retryGuidance: RetryGuidance?,            // 재시도 가이던스 (첫 시도 시 null)
  val previousResult: AgentExecutionResult?,    // 이전 실행 결과 (첫 시도 시 null)
  val contextHints: List<String>,               // 컨텍스트 힌트
  val isRetry: Boolean = false,                 // 재시도 여부
) {
  companion object {
    /**
     * 첫 실행용 입력 생성
     */
    fun forFirstAttempt(input: Any): EnrichedAgentInput {
      return EnrichedAgentInput(
        originalInput = input,
        retryGuidance = null,
        previousResult = null,
        contextHints = emptyList(),
        isRetry = false
      )
    }

    /**
     * 재시도용 입력 생성
     */
    fun forRetry(
      input: Any,
      guidance: RetryGuidance,
      previousResult: AgentExecutionResult
    ): EnrichedAgentInput {
      return EnrichedAgentInput(
        originalInput = input,
        retryGuidance = guidance,
        previousResult = previousResult,
        contextHints = guidance.suggestions,
        isRetry = true
      )
    }
  }
}
