package com.jongmin.ai.multiagent.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.multiagent.dto.*
import com.jongmin.ai.multiagent.entity.MultiAgentWorkflow
import com.jongmin.ai.multiagent.executor.HumanReviewManager
import com.jongmin.ai.multiagent.model.HumanReviewResponse
import com.jongmin.ai.multiagent.model.ReviewAction
import com.jongmin.ai.multiagent.repository.MultiAgentWorkflowRepository
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.dte.entity.JobPriority
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 멀티 에이전트 워크플로우 Platform 서비스
 *
 * 워크플로우 실행, 상태 조회, Human Review 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class MultiAgentWorkflowService(
  private val objectMapper: ObjectMapper,
  private val workflowRepository: MultiAgentWorkflowRepository,
  private val taskQueue: DistributedTaskQueue,
  private val humanReviewManager: HumanReviewManager,
  private val progressManager: MultiAgentProgressManager,
) {

  companion object {
    /**
     * DTE 태스크 타입
     */
    const val TASK_TYPE = "MULTI_AGENT_WORKFLOW"
  }

  /**
   * 워크플로우 비동기 실행
   *
   * DTE 큐에 작업을 등록하고 Job ID 반환
   */
  @Transactional
  fun executeAsync(
    session: JSession,
    workflowId: Long,
    request: ExecuteWorkflowRequest,
  ): ExecuteWorkflowResponse {
    kLogger.info { "워크플로우 비동기 실행 요청 - workflowId: $workflowId, account: ${session.accountId}" }

    // 워크플로우 존재 확인
    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { IllegalArgumentException("Workflow not found: $workflowId") }

    // 권한 확인
    validateAccess(session, workflow)

    // DTE Job 생성
    val payload = mapOf<String, Any>(
      "workflowId" to workflowId,
      "input" to request.input,
      "accountId" to session.accountId,
    ).let { basePayload ->
      request.options?.let { basePayload + ("options" to it) } ?: basePayload
    }

    val job = DistributedJob.create(
      type = TASK_TYPE,
      payload = payload,
      priority = JobPriority.DEFAULT,
      requesterId = session.accountId,
      correlationId = "multi-agent-${workflowId}-${System.currentTimeMillis()}",
    )

    // 큐에 등록
    val enqueuedJob = taskQueue.enqueue(TASK_TYPE, job)

    kLogger.info { "워크플로우 큐 등록 완료 - workflowId: $workflowId, jobId: ${enqueuedJob.id}" }

    return ExecuteWorkflowResponse(
      jobId = enqueuedJob.id,
      workflowId = workflowId,
      status = "QUEUED"
    )
  }

  /**
   * 실행 상태 조회
   */
  fun getExecutionStatus(jobId: String): ExecutionStatusResponse {
    val progress = progressManager.getProgress(jobId)

    return ExecutionStatusResponse(
      jobId = jobId,
      status = progress?.status?.name ?: "UNKNOWN",
      progress = progress?.let {
        if (it.totalAgents > 0) it.completedAgents.toDouble() / it.totalAgents else 0.0
      } ?: 0.0,
      currentAgent = progress?.currentAgentId,
      completedAgents = progress?.completedAgents ?: 0,
      totalAgents = progress?.totalAgents ?: 0
    )
  }

  /**
   * Human Review 응답 제출
   */
  fun submitReviewResponse(
    session: JSession,
    reviewId: String,
    request: ReviewResponseRequest,
  ) {
    kLogger.info { "Human Review 응답 - reviewId: $reviewId, action: ${request.action}, by: ${session.username}" }

    val response = HumanReviewResponse(
      requestId = reviewId,
      action = ReviewAction.valueOf(request.action),
      modifiedOutput = request.modifiedOutput,
      hint = request.hint,
      comment = request.comment,
      reviewedBy = session.username
    )

    val submitted = humanReviewManager.submitReviewResponse(response)
    if (!submitted) {
      throw IllegalArgumentException("Review request not found or expired: $reviewId")
    }
  }

  /**
   * 대기 중인 Human Review 목록 조회
   */
  fun getPendingReviews(workflowId: Long): List<PendingReviewResponse> {
    return humanReviewManager.getPendingReviews(workflowId).map { review ->
      PendingReviewResponse(
        reviewId = review.id,
        agentId = review.agentId,
        guardType = review.guardType.name,
        reason = review.reason,
        options = review.options.map { opt ->
          ReviewOptionDto(
            action = opt.action.name,
            label = opt.label,
            description = opt.description,
            isDefault = opt.isDefault
          )
        },
        expiresAt = review.expiresAt.toString()
      )
    }
  }

  /**
   * 워크플로우 접근 권한 확인
   */
  private fun validateAccess(session: JSession, workflow: MultiAgentWorkflow) {
    if (workflow.accountId != session.accountId) {
      throw IllegalArgumentException("Access denied to workflow: ${workflow.id}")
    }
  }
}
