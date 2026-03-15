package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant

/**
 * 사람 검토 요청
 */
data class HumanReviewRequest(
  val id: String,                            // 요청 ID
  val workflowId: Long,                      // 워크플로우 ID
  val executionId: String,                   // 실행 ID
  val agentId: String,                       // 요청 에이전트 ID
  val guardType: HumanReviewGuardType,       // 트리거된 가드
  val reason: String,                        // 검토 요청 사유
  val context: ReviewContext,                // 검토 컨텍스트
  val options: List<ReviewOption>,           // 사용자 선택지
  val createdAt: Instant = Instant.now(),
  val expiresAt: Instant,                    // 만료 시간
)

/**
 * 검토 컨텍스트
 */
data class ReviewContext(
  val agentName: String,                     // 에이전트 이름
  val agentOutput: Any?,                     // 에이전트 출력
  val selfEvaluation: SelfEvaluation?,       // 자체 평가 결과
  val currentCost: Double?,                  // 현재까지 비용
  val estimatedTotalCost: Double?,           // 예상 총 비용
  val executedAgents: List<String>,          // 실행된 에이전트 목록
  val remainingAgents: List<String>,         // 남은 에이전트 목록
)

/**
 * 검토 선택지
 */
data class ReviewOption(
  val action: ReviewAction,
  val label: String,
  val description: String,
  val isDefault: Boolean = false,
)

/**
 * 검토 액션
 */
enum class ReviewAction(private val typeCode: Int) {
  APPROVE(1),        // 승인하고 계속 진행
  REJECT(2),         // 거부하고 재시도
  MODIFY(3),         // 결과 수정 후 진행
  SKIP_AGENT(4),     // 이 에이전트 건너뛰기
  ABORT(5),          // 워크플로우 중단
  PROVIDE_HINT(6),   // 힌트 제공 후 재시도
  ;

  companion object {
    private val map = entries.associateBy(ReviewAction::typeCode)

    @JsonCreator
    fun getType(value: Int): ReviewAction = map[value] ?: APPROVE
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 사람 검토 응답
 */
data class HumanReviewResponse(
  val requestId: String,
  val action: ReviewAction,
  val modifiedOutput: Any? = null,           // MODIFY 시 수정된 결과
  val hint: String? = null,                  // PROVIDE_HINT 시 힌트
  val comment: String? = null,               // 검토자 코멘트
  val reviewedBy: String,                    // 검토자 ID
  val reviewedAt: Instant = Instant.now(),
)

/**
 * 가드 타입
 */
enum class HumanReviewGuardType(private val typeCode: Int) {
  AGENT_INTERVENTION(1),  // Guard 1: 에이전트 요청
  SMART_HITL(2),          // Guard 2: 애매한 결과
  CHECKPOINT(3),          // Guard 3: 체크포인트
  COST_GUARD(4),          // Guard 4: 비용 초과
  FULL_HITL(5),           // Guard 5: 전체 검토
  ;

  companion object {
    private val map = entries.associateBy(HumanReviewGuardType::typeCode)

    @JsonCreator
    fun getType(value: Int): HumanReviewGuardType = map[value] ?: SMART_HITL
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 에이전트 개입 요청
 * 에이전트가 스스로 판단하여 사람 검토 요청 (Guard 1)
 */
data class AgentInterventionRequest(
  val agentId: String,
  val reason: InterventionReason,
  val description: String,
  val severity: InterventionSeverity,
  val suggestedOptions: List<String>? = null,
)

/**
 * 개입 요청 사유
 */
enum class InterventionReason(private val typeCode: Int) {
  AMBIGUOUS_INPUT(1),       // 입력이 애매함
  CONFLICTING_CONTEXT(2),   // 컨텍스트 충돌
  ETHICAL_CONCERN(3),       // 윤리적 우려
  CAPABILITY_LIMIT(4),      // 능력 한계
  HIGH_RISK_DECISION(5),    // 고위험 결정
  COST_CONCERN(6),          // 비용 우려
  CUSTOM(99),               // 기타
  ;

  companion object {
    private val map = entries.associateBy(InterventionReason::typeCode)

    @JsonCreator
    fun getType(value: Int): InterventionReason = map[value] ?: CUSTOM
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 개입 심각도
 */
enum class InterventionSeverity(private val typeCode: Int) {
  INFO(1),       // 정보성 (계속 진행 가능)
  WARNING(2),    // 경고 (검토 권장)
  CRITICAL(3),   // 심각 (반드시 검토 필요)
  ;

  companion object {
    private val map = entries.associateBy(InterventionSeverity::typeCode)

    @JsonCreator
    fun getType(value: Int): InterventionSeverity = map[value] ?: INFO
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
