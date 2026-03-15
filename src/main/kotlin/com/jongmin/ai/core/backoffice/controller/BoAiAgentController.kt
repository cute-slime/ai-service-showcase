package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAgent
import com.jongmin.ai.core.backoffice.dto.request.ExecuteNodeRequest
import com.jongmin.ai.core.backoffice.dto.request.PatchAiAgent
import com.jongmin.ai.core.backoffice.dto.PortableAiAgentData
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.response.BoAiAgentItem
import com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult
import com.jongmin.ai.core.backoffice.dto.response.ExecuteWorkflowResponse
import com.jongmin.ai.core.backoffice.service.BoAiPortabilityService
import com.jongmin.ai.core.backoffice.service.BoAiAgentService
import com.jongmin.ai.core.backoffice.service.SingleNodeExecutionService
import com.jongmin.ai.core.platform.component.agent.executor.AiAgentExecutor
import com.jongmin.ai.core.platform.component.agent.executor.model.Workflow
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoAiAgentController(
  private val objectMapper: ObjectMapper,
  private val aiAgentExecutor: AiAgentExecutor,
  private val boAiAgentService: BoAiAgentService,
  private val singleNodeExecutionService: SingleNodeExecutionService,
  private val boAiPortabilityService: BoAiPortabilityService,
) : JController() {

  @PostMapping("/bo/ai-agents")
  fun create(@Valid @RequestBody dto: CreateAiAgent): BoAiAgentItem {
    dto.workflow = mapOf()
    return boAiAgentService.create(session!!, dto)
  }

  @GetMapping("/bo/ai-agents/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiAgentItem {
    return boAiAgentService.findById(id)
  }

  @GetMapping("/bo/ai-agents/{id}/export")
  fun export(@PathVariable id: Long): PortableAiAgentData {
    return boAiPortabilityService.exportAgent(id)
  }

  @GetMapping("/bo/ai-agents")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiAgentItem> {
    return boAiAgentService.findAll(statuses, q, jPageable(pageable))
  }

  @PatchMapping("/bo/ai-agents")
  fun patch(@Valid @RequestBody dto: PatchAiAgent): Map<String, Any?> {
    return boAiAgentService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
  }

  @PostMapping("/bo/ai-agents/import")
  fun import(@Valid @RequestBody dto: PortableAiAgentData): PortableImportResponse {
    return boAiPortabilityService.importAgent(session!!, dto)
  }

  @PostMapping("/bo/ai-agents/import/batch")
  fun importBatch(@Valid @RequestBody dtos: List<PortableAiAgentData>): PortableBatchImportResponse {
    return boAiPortabilityService.importAgents(session!!, dtos)
  }

  @DeleteMapping("/bo/ai-agents/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiAgentService.delete(session!!, id))
  }

  @Operation(
    summary = "AI 에이전트 일괄 삭제",
    description = """
    여러 AI 에이전트를 한 번에 삭제합니다.

    - 최대 100개까지 동시 삭제 가능
    - Partial Success: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
    - Soft Delete 방식 (status = DELETED)
    """
  )
  @DeleteMapping("/bo/ai-agents/bulk")
  fun bulkDelete(
    @Parameter(description = "삭제할 AI 에이전트 ID (쉼표로 구분)", required = true, example = "1,2,3,4,5")
    @RequestParam ids: String
  ): BulkDeleteResult {
    val idList = ids.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .mapNotNull { it.toLongOrNull() }
    return boAiAgentService.bulkDelete(session!!, idList)
  }

  /**
   * 워크플로우 실행 요청
   *
   * DTE 기반으로 워크플로우를 실행하고 Job ID를 반환합니다.
   * 클라이언트는 반환된 executionId로 SSE 엔드포인트에 구독하여 실시간 이벤트를 수신합니다.
   *
   * @param workflow 실행할 워크플로우
   * @return executionId (DTE Job ID) - SSE 구독용
   */
  @PostMapping("/bo/ai-agents/executes")
  fun execute(@Valid @RequestBody workflow: Workflow): ExecuteWorkflowResponse {
    val executionId = aiAgentExecutor.execute(session!!, workflow)
    return ExecuteWorkflowResponse(executionId = executionId)
  }

  // ==================== 단일 노드 실행 API ====================

  /**
   * 단일 노드 스트리밍 실행
   *
   * 워크플로우 전체를 실행하지 않고 특정 노드만 독립적으로 실행.
   * LLM 생성 과정을 SSE로 스트리밍하며, 완료 시 최종 결과 전송.
   *
   * SSE 이벤트 형식:
   * - delta 이벤트: {"type": "AI_CHAT_DELTA", "nodeId": "...", "delta": "..."}
   * - 완료 이벤트: {"success": true, "sources": {"concept": "..."}}
   * - 에러 이벤트: {"success": false, "error": {...}}
   *
   * @param request 노드 실행 요청 (nodeType, aiAssistantId, inputs, overrides)
   * @return SSE 스트림 (delta + 최종 결과)
   */
  @Operation(
    summary = "단일 노드 스트리밍 실행",
    description = "워크플로우 전체를 실행하지 않고 특정 노드만 독립적으로 실행합니다. LLM 생성 과정을 SSE로 스트리밍합니다."
  )
  @PostMapping("/bo/ai-agents/execute-node/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun executeNodeStream(@Valid @RequestBody request: ExecuteNodeRequest): Flux<String> {
    val session = session!!
    return Flux.create { sink ->
      Thread.startVirtualThread {
        singleNodeExecutionService.executeNodeStreaming(session, request, sink)
      }
    }
  }
}
