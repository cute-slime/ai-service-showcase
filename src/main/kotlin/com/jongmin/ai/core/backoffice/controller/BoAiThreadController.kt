package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.request.BoCreateThread
import com.jongmin.ai.core.backoffice.dto.request.BoPatchThread
import com.jongmin.ai.core.backoffice.dto.response.BoAiThreadItem
import com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult
import com.jongmin.ai.core.backoffice.service.BoAiThreadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
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
class BoAiThreadController(
  private val objectMapper: ObjectMapper,
  private val boAiThreadService: BoAiThreadService
) : JController() {

  @Operation(
    summary = "(백오피스) AI 스레드를 생성한다.",
    description = """
        권한: ai("admin")
        백오피스에서 새로운 스레드를 생성한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai-threads")
  fun create(@Validated @RequestBody dto: BoCreateThread): BoAiThreadItem {
    return boAiThreadService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) AI 스레드 항목을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 스레드 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-threads/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiThreadItem {
    return boAiThreadService.findById(id)
  }

  @Operation(
    summary = "(백오피스) AI 스레드 목록을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 스레드 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-threads")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiThreadItem> {
    return boAiThreadService.findAll(statuses, q, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) AI 스레드를 수정한다.",
    description = """
        권한: ai("admin")
        백오피스에서 기존 스레드를 수정한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/ai-threads")
  fun patch(@Validated @RequestBody dto: BoPatchThread): Map<String, Any?> {
    return boAiThreadService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
  }

  @Operation(
    summary = "(백오피스) AI 스레드를 삭제한다.",
    description = """
        권한: ai("admin")
        설정된 대기기간 없이 즉시 스레드가 삭제된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai-threads/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiThreadService.delete(session!!, id))
  }

  @Operation(
    summary = "AI 스레드 일괄 삭제",
    description = """
    여러 AI 스레드를 한 번에 삭제합니다.

    - 최대 100개까지 동시 삭제 가능
    - Partial Success: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
    - Soft Delete 방식 (status = DELETED)
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai-threads/bulk")
  fun bulkDelete(
    @Parameter(description = "삭제할 AI 스레드 ID (쉼표로 구분)", required = true, example = "1,2,3,4,5")
    @RequestParam ids: String
  ): BulkDeleteResult {
    val idList = ids.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .mapNotNull { it.toLongOrNull() }
    return boAiThreadService.bulkDelete(session!!, idList)
  }
}
