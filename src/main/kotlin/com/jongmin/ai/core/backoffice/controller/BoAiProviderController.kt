package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.request.CreateAiProvider
import com.jongmin.ai.core.backoffice.dto.request.PatchAiProvider
import com.jongmin.ai.core.backoffice.dto.PortableAiProviderData
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.response.BoAiProviderItem
import com.jongmin.ai.core.backoffice.service.BoAiPortabilityService
import com.jongmin.ai.core.backoffice.service.BoAiProviderService
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
class BoAiProviderController(
  private val objectMapper: ObjectMapper,
  private val boAiProviderService: BoAiProviderService,
  private val boAiPortabilityService: BoAiPortabilityService,
) : JController() {

  @Operation(
    summary = "(백오피스) AI 제공사를 생성한다.",
    description = """
    권한: ai("admin")
    백오피스에서 새로운 AI 제공사를 생성한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai-providers")
  fun create(@Validated @RequestBody dto: CreateAiProvider): BoAiProviderItem {
    return boAiProviderService.create(session!!, dto, dto.apiKeys!!)
  }

  @Operation(
    summary = "(백오피스) AI 제공사 항목을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 제공사 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-providers/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiProviderItem {
    return boAiProviderService.findById(id)
  }

  @GetMapping("/bo/ai-providers/{id}/export")
  fun export(@PathVariable id: Long): PortableAiProviderData {
    return boAiPortabilityService.exportProvider(id)
  }

  @Operation(
    summary = "(백오피스) AI 제공사 목록을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 제공사 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/all-ai-providers")
  fun findAll(): List<CommonDto.Profile> {
    return boAiProviderService.findAll()
  }

  @Operation(
    summary = "(백오피스) AI 제공사 목록을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 제공사 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-providers")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiProviderItem> {
    return boAiProviderService.findAll(statuses, q, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) AI 제공사를 수정한다.",
    description = """
    권한: ai("admin")
    백오피스에서 기존 AI 제공사를 수정한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/ai-providers")
  fun patch(@Validated @RequestBody dto: PatchAiProvider): Map<String, Any?> {
    return boAiProviderService.patch(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {}),
      dto.apiKeys
    )
  }

  @PostMapping("/bo/ai-providers/import")
  fun import(@Validated @RequestBody dto: PortableAiProviderData): PortableImportResponse {
    return boAiPortabilityService.importProvider(session!!, dto)
  }

  @PostMapping("/bo/ai-providers/import/batch")
  fun importBatch(@Validated @RequestBody dtos: List<PortableAiProviderData>): PortableBatchImportResponse {
    return boAiPortabilityService.importProviders(session!!, dtos)
  }

  @Operation(
    summary = "(백오피스) AI 제공사를 삭제한다.",
    description = """
    권한: ai("admin")
    설정된 대기기간 없이 즉시 AI 제공사가 삭제된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai-providers/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiProviderService.delete(session!!, id))
  }
}
