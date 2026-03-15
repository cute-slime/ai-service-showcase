package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.request.CreateAiMessage
import com.jongmin.ai.core.backoffice.dto.request.PatchAiMessage
import com.jongmin.ai.core.backoffice.dto.response.BoAiMessageItem
import com.jongmin.ai.core.backoffice.service.BoAiMessageService
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
@Tag(name = "900-3. BackOffice - AI Chat")
@RestController
@RequestMapping("/v1.0")
class BoAiMessageController(
  private val objectMapper: ObjectMapper,
  private val boAiMessageService: BoAiMessageService
) : JController() {

  @Operation(
    summary = "(백오피스) AI 채팅을 생성한다.",
    description = """
        권한: ai("admin")
        백오피스에서 새로운 채팅을 생성한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai-chats")
  fun create(@Validated @RequestBody dto: CreateAiMessage): BoAiMessageItem {
    return boAiMessageService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) AI 메시지 항목을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 채팅 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-chats/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiMessageItem {
    return boAiMessageService.findById(id)
  }

  @Operation(
    summary = "(백오피스) AI 메시지 목록을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 채팅 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-chats")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiMessageItem> {
    return boAiMessageService.findAll(statuses, q, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) AI 채팅을 수정한다.",
    description = """
        권한: ai("admin")
        백오피스에서 기존 채팅을 수정한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/ai-chats")
  fun patch(@Validated @RequestBody dto: PatchAiMessage): Map<String, Any?> {
    return boAiMessageService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
  }

  @Operation(
    summary = "(백오피스) AI 채팅을 삭제한다.",
    description = """
        권한: ai("admin")
        설정된 대기기간 없이 즉시 채팅이 삭제된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai-chats/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiMessageService.delete(session!!, id))
  }
}
