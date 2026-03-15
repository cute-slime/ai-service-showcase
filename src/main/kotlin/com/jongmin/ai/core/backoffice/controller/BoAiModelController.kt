package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiModelItem
import com.jongmin.ai.core.backoffice.dto.PortableAiModelData
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.request.CreateAiModel
import com.jongmin.ai.core.backoffice.dto.request.PatchAiModel
import com.jongmin.ai.core.backoffice.dto.response.BoAiModelResponseDto
import com.jongmin.ai.core.backoffice.service.BoAiPortabilityService
import com.jongmin.ai.core.backoffice.service.BoAiModelService
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
class BoAiModelController(
  private val objectMapper: ObjectMapper,
  private val boAiModelService: BoAiModelService,
  private val boAiPortabilityService: BoAiPortabilityService,
) : JController() {

  @Operation(
    summary = "(백오피스) AI 모델을 생성한다.",
    description = """
    권한: ai("admin")
    백오피스에서 새로운 AI 모델을 생성한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai-models")
  fun create(@Validated @RequestBody dto: CreateAiModel): BoAiModelResponseDto {
    dto.accountId = session!!.accountId
    return boAiModelService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) AI 모델 항목을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 모델 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-models/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiModelResponseDto {
    return boAiModelService.findById(id)
  }

  @GetMapping("/bo/ai-models/{id}/export")
  fun export(@PathVariable id: Long): PortableAiModelData {
    return boAiPortabilityService.exportModel(id)
  }

  @Operation(
    summary = "(백오피스) AI 모델 목록을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 모델 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/all-ai-models")
  fun findAll(): List<AiModelItem> {
    return boAiModelService.findAll()
  }

  @Operation(
    summary = "(백오피스) AI 모델 목록을 조회한다.",
    description = """
    권한: ai("admin")
    백오피스에서 AI 모델 정보를 확인하기 위한 용도로 사용된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-models")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiModelResponseDto> {
    return boAiModelService.findAll(statuses, q, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) AI 모델을 수정한다.",
    description = """
    권한: ai("admin")
    백오피스에서 기존 AI 모델을 수정한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/ai-models")
  fun patch(@Validated @RequestBody dto: PatchAiModel): Map<String, Any?> {
    return boAiModelService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {}))
  }

  @PostMapping("/bo/ai-models/import")
  fun import(@Validated @RequestBody dto: PortableAiModelData): PortableImportResponse {
    return boAiPortabilityService.importModel(session!!, dto)
  }

  @PostMapping("/bo/ai-models/import/batch")
  fun importBatch(@Validated @RequestBody dtos: List<PortableAiModelData>): PortableBatchImportResponse {
    return boAiPortabilityService.importModels(session!!, dtos)
  }

  @Operation(
    summary = "(백오피스) AI 모델을 삭제한다.",
    description = """
    권한: ai("admin")
    설정된 대기기간 없이 즉시 AI 모델이 삭제된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai-models/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiModelService.delete(session!!, id))
  }
}
