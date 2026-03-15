package com.jongmin.ai.core.backoffice.controller

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowFormat
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.backoffice.dto.request.CreateGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.request.PatchGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationWorkflowListItem
import com.jongmin.ai.core.backoffice.service.BoGenerationWorkflowService
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
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

/**
 * (백오피스) 멀티미디어 생성 워크플로우 관리 컨트롤러
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoGenerationWorkflowController(
  private val boGenerationWorkflowService: BoGenerationWorkflowService,
) : JController() {

  @Operation(
    summary = "(백오피스) 생성 워크플로우를 생성한다.",
    description = """
    권한: ai("admin")
    multimedia_provider에 의존하는 멀티미디어 워크플로우를 생성한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/generation-workflows")
  fun create(
    @Validated @RequestBody dto: CreateGenerationWorkflow
  ): BoGenerationWorkflow {
    return boGenerationWorkflowService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) 생성 워크플로우 상세를 조회한다.",
    description = """
    권한: ai("admin")
    워크플로우 상세와 provider 식별 정보를 함께 반환한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-workflows/{id}")
  fun findById(
    @PathVariable(required = true) id: Long
  ): BoGenerationWorkflow {
    return boGenerationWorkflowService.findById(id)
  }

  @Operation(
    summary = "(백오피스) 생성 워크플로우 목록을 조회한다.",
    description = """
    권한: ai("admin")
    상태/프로바이더코드/미디어타입/포맷/검색어 기준으로 필터링한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-workflows")
  fun findAll(
    @Parameter(name = "statuses", description = "상태 필터")
    @RequestParam(required = false) statuses: Set<GenerationWorkflowStatus>? = null,

    @Parameter(name = "providerCodes", description = "프로바이더 코드 필터 (CSV)")
    @RequestParam(required = false) providerCodes: Set<String>? = null,

    @Parameter(name = "mediaTypes", description = "미디어 타입 필터")
    @RequestParam(required = false) mediaTypes: Set<GenerationMediaType>? = null,

    @Parameter(name = "pipelines", description = "동작 파이프라인 필터")
    @RequestParam(required = false) pipelines: Set<GenerationWorkflowPipeline>? = null,

    @Parameter(name = "formats", description = "워크플로우 포맷 필터")
    @RequestParam(required = false) formats: Set<GenerationWorkflowFormat>? = null,

    @Parameter(name = "q", description = "검색어 (name/description/provider)")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["updatedAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoGenerationWorkflowListItem> {
    return boGenerationWorkflowService.findAll(
      statuses = statuses,
      providerCodes = providerCodes,
      mediaTypes = mediaTypes,
      pipelines = pipelines,
      formats = formats,
      q = q,
      pageable = jPageable(pageable)
    )
  }

  @Operation(
    summary = "(백오피스) 생성 워크플로우를 수정한다.",
    description = """
    권한: ai("admin")
    워크플로우 payload/variables/상태/기본여부 등을 수정한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/generation-workflows/{id}")
  fun patch(
    @PathVariable(required = true) id: Long,
    @Validated @RequestBody dto: PatchGenerationWorkflow
  ): BoGenerationWorkflow {
    dto.id = id
    return boGenerationWorkflowService.patch(session!!, id, dto)
  }

  @Operation(
    summary = "(백오피스) 생성 워크플로우를 삭제한다.",
    description = """
    권한: ai("admin")
    워크플로우를 삭제한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/generation-workflows/{id}")
  fun delete(
    @PathVariable(required = true) id: Long
  ): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boGenerationWorkflowService.delete(session!!, id))
  }
}
