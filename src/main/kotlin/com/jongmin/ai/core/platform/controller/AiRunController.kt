package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.backoffice.dto.response.ExecuteWorkflowResponse
import com.jongmin.ai.core.platform.dto.request.CreateAiRun
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.ai.dte.component.handler.ChatAgentTaskHandler
import com.jongmin.jspring.dte.dto.request.ChatAgentRequest
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.dte.entity.JobPriority
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * AI Run 컨트롤러
 *
 * AI 추론 실행을 DTE(Distributed Task Executor) 기반으로 처리합니다.
 * Job ID를 반환하여 클라이언트가 SSE 엔드포인트에 구독하도록 합니다.
 */
@Validated
@RequestMapping("/v1.0")
@RestController
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
class AiRunController(
  private val objectMapper: ObjectMapper,
  private val distributedTaskQueue: DistributedTaskQueue,
  private val cancellationManager: com.jongmin.ai.core.platform.component.AIInferenceCancellationManager,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * AI 추론 실행 요청
   *
   * DTE 기반으로 CHAT_AGENT Job을 등록하고 Job ID를 반환합니다.
   * ChatAgentTaskHandler에서 실제 추론 로직을 처리합니다.
   * 클라이언트는 반환된 executionId로 SSE 엔드포인트에 구독합니다.
   *
   * @param dto AI Run 생성 요청 DTO
   * @return executionId (DTE Job ID) - SSE 구독용
   */
  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/ai-runs")
  fun create(@RequestBody @Valid dto: CreateAiRun): ExecuteWorkflowResponse {
    val session = session!!

    kLogger.info {
      "AI Run 요청 - accountId: ${session.accountId}, aiMessageId: ${dto.aiMessageId}, canvasId: ${dto.canvasId}"
    }

    // ChatAgentRequest로 변환
    val chatAgentRequest = ChatAgentRequest(
      aiMessageId = dto.aiMessageId!!,
      canvasId = dto.canvasId,
      researchMode = dto.researchMode,
      aiAgentId = dto.aiAgentId
    )

    // payload를 Map으로 변환
    val payload: Map<String, Any> = objectMapper.convertValue(
      chatAgentRequest,
      object : TypeReference<Map<String, Any>>() {}
    )

    // DTE Job 생성
    val job = DistributedJob.create(
      type = ChatAgentTaskHandler.TASK_TYPE,
      payload = payload,
      priority = JobPriority.NORMAL,
      requesterId = session.accountId
    )

    // DTE 큐에 등록
    val enqueuedJob = distributedTaskQueue.enqueue(ChatAgentTaskHandler.TASK_TYPE, job)

    kLogger.info {
      "CHAT_AGENT Job 등록 완료 - jobId: ${enqueuedJob.id}, accountId: ${session.accountId}"
    }

    return ExecuteWorkflowResponse(executionId = enqueuedJob.id)
  }

  /**
   * 실행 중인 AI 추론을 중단합니다.
   *
   * Redis를 통해 취소 요청을 등록하여 분산 환경의 모든 서버에서
   * 해당 추론이 취소되었음을 확인할 수 있도록 합니다.
   *
   * @param id AI Run ID
   * @return 취소 성공 여부를 포함한 응답
   */
  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @DeleteMapping("/ai-runs/{id}")
  // 소유권 체크 작업 필요
  fun stop(@PathVariable id: Long): Map<String, Any> {
    // Run ID를 문자열로 변환하여 취소 키로 사용
    val cancellationKey = id.toString()

    // 취소 요청 등록
    val success = cancellationManager.requestCancellation(cancellationKey)

    return mapOf(
      "aiRunId" to id,
      "cancelled" to success,
      "message" to if (success) "AI 추론 취소 요청이 등록되었습니다." else "AI 추론 취소 요청 등록에 실패했습니다."
    )
  }
}
