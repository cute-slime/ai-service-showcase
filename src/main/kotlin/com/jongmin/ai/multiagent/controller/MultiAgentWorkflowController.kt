package com.jongmin.ai.multiagent.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.multiagent.dto.*
import com.jongmin.ai.multiagent.model.MultiAgentProgressEvent
import com.jongmin.ai.multiagent.service.MultiAgentWorkflowService
import com.jongmin.ai.multiagent.service.MultiAgentProgressManager
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux

/**
 * 멀티 에이전트 워크플로우 Platform API
 *
 * 워크플로우 실행, 진행 상황 스트리밍, Human Review 응답 등을 제공
 */
@RestController
@RequestMapping("/v1.0/multi-agent/workflows")
class MultiAgentWorkflowController(
  private val workflowService: MultiAgentWorkflowService,
  private val progressManager: MultiAgentProgressManager,
) : JController() {

  /**
   * 워크플로우 비동기 실행
   *
   * DTE 큐에 작업을 등록하고 즉시 응답
   * 실행 결과는 SSE 스트리밍 또는 상태 조회로 확인
   */
  @PostMapping("/{workflowId}/execute")
  fun execute(
    @PathVariable workflowId: Long,
    @Valid @RequestBody request: ExecuteWorkflowRequest,
  ): ExecuteWorkflowResponse {
    return workflowService.executeAsync(session!!, workflowId, request)
  }

  /**
   * 실행 진행 상황 SSE 스트리밍
   *
   * 실시간으로 에이전트 실행 상황을 스트리밍
   */
  @GetMapping(
    "/executions/{jobId}/progress",
    produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
  )
  fun streamProgress(@PathVariable jobId: String): Flux<MultiAgentProgressEvent> {
    return progressManager.subscribe(jobId)
  }

  /**
   * 실행 상태 조회
   */
  @GetMapping("/executions/{jobId}/status")
  fun getExecutionStatus(@PathVariable jobId: String): ExecutionStatusResponse {
    return workflowService.getExecutionStatus(jobId)
  }

  /**
   * Human Review 응답 제출
   */
  @PostMapping("/reviews/{reviewId}/respond")
  fun respondToReview(
    @PathVariable reviewId: String,
    @Valid @RequestBody request: ReviewResponseRequest,
  ): CommonDto.JApiResponse<Boolean> {
    workflowService.submitReviewResponse(session!!, reviewId, request)
    return CommonDto.JApiResponse(data = true)
  }

  /**
   * 대기 중인 Human Review 목록 조회
   */
  @GetMapping("/{workflowId}/reviews/pending")
  fun getPendingReviews(@PathVariable workflowId: Long): List<PendingReviewResponse> {
    return workflowService.getPendingReviews(workflowId)
  }
}
