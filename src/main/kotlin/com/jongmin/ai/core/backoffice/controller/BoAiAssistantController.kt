package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.ExecuteAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.ExecuteModel
import com.jongmin.ai.core.backoffice.dto.request.PatchAiAssistant
import com.jongmin.ai.core.backoffice.dto.PortableAiAssistantData
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.response.BoAiAssistantItem
import com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult
import com.jongmin.ai.core.backoffice.dto.response.ExecuteWorkflowResponse
import com.jongmin.ai.core.backoffice.service.BoAiPortabilityService
import com.jongmin.ai.core.backoffice.service.BoAiAssistantService
import com.jongmin.ai.core.platform.component.adaptive.SimpleAgent
import com.jongmin.ai.dte.component.handler.BoAiAssistantExecuteTaskHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.dte.entity.JobPriority
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoAiAssistantController(
  private val objectMapper: ObjectMapper,
  private val distributedTaskQueue: DistributedTaskQueue,
  private val simpleAgent: SimpleAgent,
  private val boAiAssistantService: BoAiAssistantService,
  private val boAiPortabilityService: BoAiPortabilityService,
) : JController() {

  @PostMapping("/bo/ai-assistants")
  fun create(@Validated @RequestBody dto: CreateAiAssistant): BoAiAssistantItem {
    return boAiAssistantService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) AI 어시스턴트 항목을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 어시스턴트 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-assistants/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiAssistantItem {
    return boAiAssistantService.findById(id)
  }

  @GetMapping("/bo/ai-assistants/{id}/export")
  fun export(@PathVariable id: Long): PortableAiAssistantData {
    return boAiPortabilityService.exportAssistant(id)
  }

  @GetMapping("/bo/ai-assistants")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiAssistantItem> {
    return boAiAssistantService.findAll(statuses, q, jPageable(pageable))
  }

  @GetMapping("/bo/ai-assistants/available")
  fun findAllForPlayground(): List<BoAiAssistantItem> = boAiAssistantService.findAllForPlayground()

  @PatchMapping("/bo/ai-assistants")
  fun patch(@Validated @RequestBody dto: PatchAiAssistant): Map<String, Any?> {
    return boAiAssistantService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
  }

  @PostMapping("/bo/ai-assistants/import")
  fun import(@Validated @RequestBody dto: PortableAiAssistantData): PortableImportResponse {
    return boAiPortabilityService.importAssistant(session!!, dto)
  }

  @PostMapping("/bo/ai-assistants/import/batch")
  fun importBatch(@Validated @RequestBody dtos: List<PortableAiAssistantData>): PortableBatchImportResponse {
    return boAiPortabilityService.importAssistants(session!!, dtos)
  }

  @DeleteMapping("/bo/ai-assistants/{aiAssistantId}")
  fun delete(@PathVariable aiAssistantId: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiAssistantService.delete(session!!, aiAssistantId))
  }

  @Operation(
    summary = "AI 어시스턴트 일괄 삭제",
    description = """
    여러 AI 어시스턴트를 한 번에 삭제합니다.

    - 최대 100개까지 동시 삭제 가능
    - Partial Success: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
    - Soft Delete 방식 (status = DELETED)
    """
  )
  @DeleteMapping("/bo/ai-assistants/bulk")
  fun bulkDelete(
    @Parameter(description = "삭제할 AI 어시스턴트 ID (쉼표로 구분)", required = true, example = "1,2,3,4,5")
    @RequestParam ids: String
  ): BulkDeleteResult {
    val idList = ids.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .mapNotNull { it.toLongOrNull() }
    return boAiAssistantService.bulkDelete(session!!, idList)
  }

  // :-------------------------------------------------------------------------------------------------: //
  @PostMapping("/bo/ai-assistants/executes")
  fun execute(@Validated @RequestBody dto: ExecuteModel): ExecuteWorkflowResponse {
    val accountId = session!!.accountId
    val payloadSource: Map<String, Any?> = objectMapper.convertValue(
      dto,
      object : TypeReference<Map<String, Any?>>() {}
    )
    val payload: Map<String, Any> = payloadSource
      .filterValues { it != null }
      .mapValues { it.value!! }

    val job = DistributedJob.create(
      type = BoAiAssistantExecuteTaskHandler.TASK_TYPE,
      payload = payload,
      priority = JobPriority.NORMAL,
      requesterId = accountId,
      correlationId = dto.canvasId
    )
    val enqueuedJob = distributedTaskQueue.enqueue(BoAiAssistantExecuteTaskHandler.TASK_TYPE, job)

    return ExecuteWorkflowResponse(executionId = enqueuedJob.id)
  }

  @PostMapping("/bo/ai-assistants/{aiAssistantId}/executes")
  fun executeAiAssistant(
    @PathVariable aiAssistantId: Long,
    @Validated @RequestBody dto: ExecuteAiAssistant
  ): CommonDto.JApiResponse<String> {
    return CommonDto.JApiResponse.of(simpleAgent.callAiAssistant(aiAssistantId, dto))
  }
}
