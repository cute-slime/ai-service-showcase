package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 멀티 에이전트 워크플로우 실행 컨텍스트
 * 에이전트 간 데이터 전달 및 상태 추적
 */
class MultiAgentExecutionContext(
  val workflowId: Long,
  val executionId: String,
  val agents: List<MultiAgentNode>,
  val edges: List<AgentEdge>,
  val orchestratorConfig: OrchestratorConfig,
) {
  // 에이전트별 실행 결과 저장
  private val agentResults = ConcurrentHashMap<String, AgentExecutionResult>()

  // 에이전트별 재시도 카운트
  private val retryCount = ConcurrentHashMap<String, Int>()

  // 실행된 에이전트 ID 목록
  private val executedAgentIds = mutableSetOf<String>()

  // 에이전트별 힌트 (재시도 시 활용)
  private val agentHints = ConcurrentHashMap<String, List<String>>()

  // 스킬 실행 결과 저장
  private val skillResults = ConcurrentHashMap<String, MutableList<SkillExecutionResult>>()

  // 스킬 힌트 저장 (오케스트레이터가 에이전트에게 추천)
  private val skillHints = ConcurrentHashMap<String, List<SkillHint>>()

  // 전역 컨텍스트 저장소
  val contextStore = ConcurrentHashMap<String, Any>()

  // 초기 입력 (워크플로우 시작 시 설정)
  var initialInput: Any? = null

  // ========== 에이전트 조회 ==========

  /**
   * 노드 ID로 에이전트 노드 조회
   */
  fun getAgentNode(nodeId: String): MultiAgentNode {
    return agents.find { it.id == nodeId }
      ?: throw IllegalArgumentException("Agent node not found: $nodeId")
  }

  /**
   * 사용 가능한 에이전트 목록 (아직 실행 안 된 것)
   */
  val availableAgents: List<MultiAgentNode>
    get() = agents.filter { it.id !in executedAgentIds }

  // ========== 결과 저장/조회 ==========

  /**
   * 에이전트 실행 결과 저장
   */
  fun storeAgentResult(agentId: String, result: AgentExecutionResult) {
    agentResults[agentId] = result
    executedAgentIds.add(agentId)
  }

  /**
   * 에이전트 실행 결과 조회
   */
  fun getAgentResult(agentId: String): AgentExecutionResult? {
    return agentResults[agentId]
  }

  /**
   * 모든 에이전트 결과 조회
   */
  fun getAllAgentResults(): Map<String, AgentExecutionResult> {
    return agentResults.toMap()
  }

  // ========== 입력 준비 ==========

  /**
   * 에이전트 입력 준비 (이전 에이전트 출력 → 현재 에이전트 입력)
   */
  fun prepareInputForAgent(agentId: String): Any {
    // 이 에이전트로 들어오는 Edge 찾기
    val incomingEdges = edges.filter { it.target == agentId }

    if (incomingEdges.isEmpty()) {
      // 시작 에이전트: 초기 입력 반환
      return initialInput ?: emptyMap<String, Any>()
    }

    // 이전 에이전트들의 출력 수집
    val previousOutputs = incomingEdges.mapNotNull { edge ->
      agentResults[edge.source]?.output
    }

    return when (previousOutputs.size) {
      0 -> emptyMap<String, Any>()
      1 -> previousOutputs.first()
      else -> mapOf("inputs" to previousOutputs)  // 여러 입력 병합
    }
  }

  // ========== 재시도 관리 ==========

  fun getRetryCount(agentId: String): Int = retryCount.getOrDefault(agentId, 0)

  fun incrementRetryCount(agentId: String) {
    retryCount[agentId] = getRetryCount(agentId) + 1
  }

  fun setHints(agentId: String, hints: List<String>) {
    agentHints[agentId] = hints
  }

  fun getHints(agentId: String): List<String>? = agentHints[agentId]

  // ========== 다음 에이전트 결정 ==========

  /**
   * 현재 에이전트의 다음 후보 에이전트들 조회
   */
  fun getNextAgentCandidates(currentAgentId: String): List<MultiAgentNode> {
    val outgoingEdges = edges.filter { it.source == currentAgentId }
    return outgoingEdges.mapNotNull { edge ->
      agents.find { it.id == edge.target }
    }
  }

  /**
   * 실행되지 않은 에이전트가 있는지 확인
   */
  fun hasUnexecutedAgents(): Boolean {
    return executedAgentIds.size < agents.size
  }

  /**
   * 모든 필수 에이전트가 실행되었는지 확인
   */
  fun allRequiredAgentsExecuted(): Boolean {
    val requiredIds = orchestratorConfig.dynamicRoutingConfig.alwaysExecute
    return requiredIds.all { it in executedAgentIds }
  }

  // ========== 상태 조회 ==========

  fun isAgentExecuted(agentId: String): Boolean = agentId in executedAgentIds

  fun getExecutedAgentCount(): Int = executedAgentIds.size

  // ========== 스킬 결과 관리 ==========

  /**
   * 스킬 실행 결과 저장
   */
  fun storeSkillResult(agentId: String, result: SkillExecutionResult) {
    skillResults.getOrPut(agentId) { mutableListOf() }.add(result)
  }

  /**
   * 에이전트의 스킬 실행 결과 조회
   */
  fun getSkillResults(agentId: String): List<SkillExecutionResult> {
    return skillResults[agentId] ?: emptyList()
  }

  /**
   * 모든 스킬 실행 결과 조회
   */
  fun getAllSkillResults(): Map<String, List<SkillExecutionResult>> {
    return skillResults.toMap()
  }

  // ========== 스킬 힌트 관리 (Phase 5 오케스트레이터 연동) ==========

  /**
   * 스킬 힌트 저장
   */
  fun storeSkillHints(agentId: String, hints: List<SkillHint>) {
    skillHints[agentId] = hints
  }

  /**
   * 에이전트의 스킬 힌트 조회
   */
  fun getSkillHints(agentId: String): List<SkillHint> {
    return skillHints[agentId] ?: emptyList()
  }

  // ========== 컨텍스트 저장소 ==========

  /**
   * 컨텍스트 키 존재 여부 확인
   */
  fun hasContextKey(key: String): Boolean {
    return contextStore.containsKey(key)
  }

  /**
   * 마지막 에이전트 점수 조회
   */
  fun getLastAgentScore(): Double? {
    return agentResults.values.lastOrNull()?.selfEvaluation?.overallScore
  }

  /**
   * 이전 에이전트 실패 여부 확인
   */
  fun hasPreviousAgentFailed(): Boolean {
    val lastResult = agentResults.values.lastOrNull() ?: return false
    val threshold = orchestratorConfig.evaluationPassThreshold
    return (lastResult.selfEvaluation?.overallScore ?: 1.0) < threshold
  }

  // ========== 비용 추적 (Phase 7) ==========

  // 누적 비용
  private var totalCost: Double = 0.0

  /**
   * 현재까지 비용 조회
   */
  fun getCurrentCost(): Double? = totalCost.takeIf { it > 0 }

  /**
   * 비용 추가
   */
  fun addCost(cost: Double) {
    totalCost += cost
  }

  /**
   * 총 비용 설정
   */
  fun setTotalCost(cost: Double) {
    totalCost = cost
  }
}

/**
 * 스킬 힌트
 * 오케스트레이터가 에이전트에게 제공하는 스킬 사용 추천 정보
 */
data class SkillHint(
  val skillId: String,                     // 추천 스킬 ID
  val skillName: String,                   // 스킬 이름
  val relevanceScore: Double,              // 관련성 점수 (0.0 ~ 1.0)
  val triggerReason: String,               // 트리거 이유 설명
  val suggestedTiming: SkillTriggerMode,   // 추천 실행 시점
  val contextClues: List<String>,          // 입력에서 추출한 컨텍스트 단서
)
