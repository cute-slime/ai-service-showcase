package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.backoffice.dto.request.BoCreateAiRun
import com.jongmin.ai.core.backoffice.dto.response.BoAiRunItem
import com.jongmin.ai.core.backoffice.service.BoAiRunService
import com.jongmin.ai.core.platform.component.adaptive.SimpleAgent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoAiRunController(
  private val boAiRunService: BoAiRunService,
  private val simpleAgent: SimpleAgent,
) : JController() {

  @Operation(
    summary = "(백오피스) AI Run을 생성한다.",
    description = """
        권한: ai("admin")
        백오피스에서 새로운 Run을 생성한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai-runs")
  fun create(@Validated @RequestBody dto: BoCreateAiRun): BoAiRunItem {
    return boAiRunService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) AI Run 항목을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 Run 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-runs/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiRunItem {
    return boAiRunService.findById(id)
  }

  @Operation(
    summary = "(백오피스) AI Run 목록을 조회한다.",
    description = """
        권한: ai("admin")
        백오피스에서 Run 정보를 확인하기 위한 용도로 사용된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-runs")
  fun findAll(
    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiRunItem> {
    return boAiRunService.findAll(jPageable(pageable))
  }
}
