package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val kLogger = KotlinLogging.logger {}

/**
 * Human Review 관리자
 * 5가지 가드 조건 판단 및 검토 요청 생성/처리
 *
 * Guard 1: Agent Intervention - 에이전트 직접 요청
 * Guard 2: Smart HITL - 애매한 결과 검토
 * Guard 3: Checkpoint - 특정 에이전트 후 검토
 * Guard 4: Cost Guard - 비용 임계값 초과
 * Guard 5: Full HITL - 모든 실행 검토
 */
@Component
class HumanReviewManager {

  // 대기 중인 검토 요청
  private val pendingReviews = ConcurrentHashMap<String, HumanReviewRequest>()

  // 검토 응답 (폴링용)
  private val reviewResponses = ConcurrentHashMap<String, HumanReviewResponse>()

  /**
   * 검토 필요 여부 판단 (모든 가드 체크)
   */
  fun checkReviewRequired(
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext,
    interventionRequest: AgentInterventionRequest? = null,
  ): HumanReviewGuardType? {
    val config = context.orchestratorConfig.humanReviewConfig
      ?: return null

    // Guard 5: Full HITL (최우선)
    if (config.fullHitlEnabled) {
      kLogger.debug { "Full HITL 활성화 - 검토 필요" }
      return HumanReviewGuardType.FULL_HITL
    }

    // Guard 1: Agent Intervention
    if (config.agentInterventionEnabled && interventionRequest != null) {
      kLogger.debug { "에이전트 개입 요청 - ${interventionRequest.reason}" }
      return HumanReviewGuardType.AGENT_INTERVENTION
    }

    // Guard 3: Checkpoint
    if (config.checkpointEnabled && agentNode.id in config.checkpointAgentIds) {
      kLogger.debug { "체크포인트 에이전트 - ${agentNode.id}" }
      return HumanReviewGuardType.CHECKPOINT
    }

    // Guard 4: Cost Guard
    if (config.costGuardEnabled && checkCostThreshold(context, config.costGuardConfig)) {
      kLogger.debug { "비용 임계값 초과" }
      return HumanReviewGuardType.COST_GUARD
    }

    // Guard 2: Smart HITL
    if (config.smartHitlEnabled && checkAmbiguousResult(result, config.smartHitlConfig)) {
      kLogger.debug { "애매한 결과 감지 - score: ${result.selfEvaluation?.overallScore}" }
      return HumanReviewGuardType.SMART_HITL
    }

    return null
  }

  /**
   * 비용 임계값 체크 (Guard 4)
   */
  private fun checkCostThreshold(
    context: MultiAgentExecutionContext,
    config: CostGuardConfig
  ): Boolean {
    val currentCost = context.getCurrentCost() ?: return false
    val budget = config.estimatedBudget ?: return false

    val ratio = currentCost / budget
    return ratio >= config.budgetWarningThreshold
  }

  /**
   * 애매한 결과 체크 (Guard 2)
   */
  private fun checkAmbiguousResult(
    result: AgentExecutionResult,
    config: SmartHitlConfig
  ): Boolean {
    val score = result.selfEvaluation?.overallScore ?: return false
    val confidence = result.selfEvaluation.confidence

    // 점수가 애매한 범위에 있거나 확신도가 낮으면 검토 필요
    val isAmbiguousScore = score >= config.ambiguousScoreMin && score < config.ambiguousScoreMax
    val isLowConfidence = confidence < config.lowConfidenceThreshold

    return isAmbiguousScore || isLowConfidence
  }

  /**
   * 검토 요청 생성
   */
  fun createReviewRequest(
    guardType: HumanReviewGuardType,
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext,
    interventionRequest: AgentInterventionRequest? = null,
  ): HumanReviewRequest {
    val config = context.orchestratorConfig.humanReviewConfig ?: HumanReviewConfig.smart()
    val expiresAt = Instant.now().plusSeconds(config.reviewTimeoutMinutes * 60L)

    val request = HumanReviewRequest(
      id = UUID.randomUUID().toString(),
      workflowId = context.workflowId,
      executionId = context.executionId,
      agentId = agentNode.id,
      guardType = guardType,
      reason = buildReviewReason(guardType, agentNode, result, interventionRequest),
      context = buildReviewContext(agentNode, result, context),
      options = buildReviewOptions(guardType),
      expiresAt = expiresAt
    )

    pendingReviews[request.id] = request
    kLogger.info { "검토 요청 생성 - id: ${request.id}, guard: $guardType, agent: ${agentNode.id}" }

    return request
  }

  /**
   * 검토 응답 대기 (블로킹)
   * 실제 운영에서는 비동기 처리 권장
   */
  fun waitForReview(
    request: HumanReviewRequest,
    config: HumanReviewConfig
  ): HumanReviewResponse {
    kLogger.info { "검토 응답 대기 - requestId: ${request.id}" }

    val startTime = Instant.now()
    val timeoutMillis = config.reviewTimeoutMinutes * 60 * 1000L

    while (true) {
      // 응답 확인
      reviewResponses[request.id]?.let { response ->
        pendingReviews.remove(request.id)
        reviewResponses.remove(request.id)
        kLogger.info { "검토 응답 수신 - action: ${response.action}" }
        return response
      }

      // 타임아웃 체크
      if (Instant.now().toEpochMilli() - startTime.toEpochMilli() > timeoutMillis) {
        kLogger.warn { "검토 타임아웃 - 기본 동작 수행: ${config.defaultAction}" }
        pendingReviews.remove(request.id)
        return createDefaultResponse(request, config.defaultAction)
      }

      // 폴링 간격
      Thread.sleep(1000)
    }
  }

  /**
   * 검토 응답 제출 (외부 API에서 호출)
   */
  fun submitReviewResponse(response: HumanReviewResponse): Boolean {
    return if (pendingReviews.containsKey(response.requestId)) {
      reviewResponses[response.requestId] = response
      kLogger.info { "검토 응답 제출됨 - requestId: ${response.requestId}, action: ${response.action}" }
      true
    } else {
      kLogger.warn { "만료되거나 존재하지 않는 검토 요청 - requestId: ${response.requestId}" }
      false
    }
  }

  /**
   * 대기 중인 검토 요청 조회
   */
  fun getPendingReviews(workflowId: Long): List<HumanReviewRequest> {
    return pendingReviews.values.filter { it.workflowId == workflowId }
  }

  /**
   * 특정 검토 요청 조회
   */
  fun getReviewRequest(requestId: String): HumanReviewRequest? {
    return pendingReviews[requestId]
  }

  /**
   * 검토 요청 취소
   */
  fun cancelReviewRequest(requestId: String): Boolean {
    return pendingReviews.remove(requestId) != null
  }

  // ========== Private Helpers ==========

  private fun buildReviewReason(
    guardType: HumanReviewGuardType,
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    interventionRequest: AgentInterventionRequest?
  ): String {
    return when (guardType) {
      HumanReviewGuardType.AGENT_INTERVENTION ->
        "[에이전트 요청] ${interventionRequest?.description ?: "에이전트가 사람 검토를 요청했습니다"}"

      HumanReviewGuardType.SMART_HITL -> {
        val score = result.selfEvaluation?.overallScore ?: 0.0
        val confidence = result.selfEvaluation?.confidence ?: 0.0
        "[애매한 결과] 점수: %.2f, 확신도: %.2f - 검토가 필요합니다".format(score, confidence)
      }

      HumanReviewGuardType.CHECKPOINT ->
        "[체크포인트] ${agentNode.name} 에이전트 실행 후 필수 검토"

      HumanReviewGuardType.COST_GUARD ->
        "[비용 경고] 예산 임계값에 도달했습니다. 계속 진행할지 결정해주세요."

      HumanReviewGuardType.FULL_HITL ->
        "[전체 검토] 모든 에이전트 실행 결과 검토 모드"
    }
  }

  private fun buildReviewContext(
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext
  ): ReviewContext {
    return ReviewContext(
      agentName = agentNode.name,
      agentOutput = result.output,
      selfEvaluation = result.selfEvaluation,
      currentCost = context.getCurrentCost(),
      estimatedTotalCost = null,  // TODO: 비용 추정 로직 추가
      executedAgents = context.getAllAgentResults().keys.toList(),
      remainingAgents = context.availableAgents.map { it.id }
    )
  }

  private fun buildReviewOptions(guardType: HumanReviewGuardType): List<ReviewOption> {
    val baseOptions = listOf(
      ReviewOption(
        action = ReviewAction.APPROVE,
        label = "승인",
        description = "결과를 승인하고 다음 단계로 진행합니다",
        isDefault = true
      ),
      ReviewOption(
        action = ReviewAction.REJECT,
        label = "거부 및 재시도",
        description = "결과를 거부하고 에이전트를 다시 실행합니다"
      ),
      ReviewOption(
        action = ReviewAction.PROVIDE_HINT,
        label = "힌트 제공",
        description = "개선 힌트를 제공하고 재시도합니다"
      ),
      ReviewOption(
        action = ReviewAction.SKIP_AGENT,
        label = "건너뛰기",
        description = "이 에이전트를 건너뛰고 다음으로 진행합니다"
      ),
      ReviewOption(
        action = ReviewAction.ABORT,
        label = "중단",
        description = "워크플로우 실행을 중단합니다"
      )
    )

    // 특정 가드에 따른 추가 옵션
    return when (guardType) {
      HumanReviewGuardType.COST_GUARD -> baseOptions + listOf(
        ReviewOption(
          action = ReviewAction.MODIFY,
          label = "예산 증액",
          description = "예산을 증액하고 계속 진행합니다"
        )
      )
      else -> baseOptions
    }
  }

  private fun createDefaultResponse(
    request: HumanReviewRequest,
    defaultAction: ReviewDefaultAction
  ): HumanReviewResponse {
    val action = when (defaultAction) {
      ReviewDefaultAction.CONTINUE -> ReviewAction.APPROVE
      ReviewDefaultAction.ABORT -> ReviewAction.ABORT
      ReviewDefaultAction.RETRY -> ReviewAction.REJECT
    }

    return HumanReviewResponse(
      requestId = request.id,
      action = action,
      comment = "타임아웃으로 인한 자동 응답",
      reviewedBy = "SYSTEM"
    )
  }
}
