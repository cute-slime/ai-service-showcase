package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant

/**
 * 에이전트 스킬 인벤토리
 * 에이전트가 사용할 수 있는 스킬 목록
 */
data class AgentSkillInventory(
  // 장착된 스킬 슬롯
  val skills: List<SkillSlot> = emptyList(),

  // 동시 실행 가능 스킬 수 제한
  val maxConcurrentSkills: Int = 3,

  // 스킬 실행 횟수 제한 (null이면 무제한)
  val skillExecutionBudget: Int? = null,

  // 스킬 실행 비용 제한 (null이면 무제한)
  val skillCostBudget: Double? = null,
)

/**
 * 스킬 슬롯 (에이전트에 장착된 스킬)
 */
data class SkillSlot(
  val skillId: String = "",                     // 참조할 스킬 ID
  val priority: Int = 0,                        // 높을수록 먼저 검토
  val enabled: Boolean = true,                  // 활성화 여부
  val overrideConfig: SkillTriggerConfig? = null,  // 에이전트별 설정 오버라이드
  val aliases: List<String>? = null,            // 이 에이전트에서 사용할 별칭
)

/**
 * 스킬 실행 결과
 */
data class SkillExecutionResult(
  val skillId: String = "",
  val success: Boolean = false,
  val output: Any? = null,
  val error: String? = null,
  val metadata: SkillExecutionMetadata? = null,
)

/**
 * 스킬 실행 메타데이터
 */
data class SkillExecutionMetadata(
  val executedAt: Instant = Instant.now(),
  val durationMs: Long? = null,
  val costIncurred: Double? = null,
  val invocationReason: SkillInvocationReason = SkillInvocationReason.AUTO_TRIGGERED,
  val inputSummary: String? = null,
  val outputSummary: String? = null,
)

/**
 * 스킬 호출 사유
 */
enum class SkillInvocationReason(private val typeCode: Int) {
  AUTO_TRIGGERED(1),      // 조건 자동 충족
  AGENT_REQUESTED(2),     // 에이전트가 명시적 요청
  ORCHESTRATOR_HINT(3),   // 오케스트레이터 힌트
  RETRY_ENHANCEMENT(4),   // 재시도 시 보강
  PRE_PROCESS(5),         // 전처리
  POST_PROCESS(6),        // 후처리
  ;

  companion object {
    private val map = entries.associateBy(SkillInvocationReason::typeCode)

    @JsonCreator
    fun getType(value: Int): SkillInvocationReason = map[value] ?: AUTO_TRIGGERED
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
