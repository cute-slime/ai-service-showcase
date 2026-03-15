package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.backoffice.dto.response.ExecuteWorkflowResponse
import com.jongmin.ai.core.platform.component.agent.executor.AiAgentExecutor
import com.jongmin.ai.core.platform.service.AiAgentService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@Validated
@RequestMapping("/v1.0")
@RestController
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
class AiAgentController(
  private val aiAgentExecutor: AiAgentExecutor,
  private val aiAgentService: AiAgentService,
) : JController() {

  /**
   * AI 에이전트 워크플로우 실행
   *
   * DTE 기반으로 워크플로우를 실행하고 Job ID를 반환합니다.
   * 클라이언트는 반환된 executionId로 SSE 엔드포인트에 구독합니다.
   *
   * @param id AI 에이전트 ID
   * @return executionId (DTE Job ID) - SSE 구독용
   */
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/ai-agents/{id}/executes")
  fun execute(@PathVariable id: Long): ExecuteWorkflowResponse {
    val session = session!!.deepCopy()
    val aiAgent = aiAgentService.findById(id)
    val executionId = aiAgentExecutor.execute(session, null, aiAgent.workflow)
    return ExecuteWorkflowResponse(executionId = executionId)
  }
}
