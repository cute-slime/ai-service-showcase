package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.backoffice.dto.request.CreateGenerationCostRule
import com.jongmin.ai.core.backoffice.dto.request.PatchGenerationCostRule
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationCostRuleDetail
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationCostRuleItem
import com.jongmin.ai.core.backoffice.dto.response.CreateGenerationCostRuleResult
import com.jongmin.ai.core.backoffice.service.BoGenerationCostRuleService
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

/**
 * (백오피스) AI 미디어 생성 비용 규칙 관리 컨트롤러
 *
 * @author Claude Code
 * @since 2026.01.12
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoGenerationCostRuleController(
  private val objectMapper: ObjectMapper,
  private val boGenerationCostRuleService: BoGenerationCostRuleService,
) : JController() {

  @Operation(
    summary = "(백오피스) 생성 비용 규칙을 생성한다.",
    description = """
    권한: ai("admin")
    새로운 미디어 생성 비용 규칙을 등록한다.
    모델/프로바이더별, 해상도/품질/길이 조건별로 세분화된 비용 규칙을 설정할 수 있다.

    ### 비용 규칙 적용 우선순위
    1. priority 값이 낮을수록 우선 적용
    2. 조건이 더 구체적인 규칙 우선 (예: 해상도+품질 > 해상도만)
    3. modelId가 지정된 규칙이 providerId만 지정된 규칙보다 우선
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/generation-cost-rules")
  fun create(
    @Validated @RequestBody dto: CreateGenerationCostRule
  ): CreateGenerationCostRuleResult {
    return boGenerationCostRuleService.create(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) 생성 비용 규칙 상세를 조회한다.",
    description = """
    권한: ai("admin")
    비용 규칙 상세 정보를 조회한다.
    연관된 모델/프로바이더/프리셋 이름도 함께 조회된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-cost-rules/{id}")
  fun findById(
    @PathVariable(required = true) id: Long
  ): BoGenerationCostRuleDetail {
    return boGenerationCostRuleService.findById(id)
  }

  @Operation(
    summary = "(백오피스) 생성 비용 규칙 목록을 조회한다.",
    description = """
    권한: ai("admin")
    비용 규칙 목록을 페이징하여 조회한다.
    상태, 미디어 타입, 모델, 프로바이더로 필터링할 수 있다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-cost-rules")
  fun findAll(
    @Parameter(name = "statuses", description = "상태 필터 (ACTIVE, INACTIVE, DELETED)")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "mediaTypes", description = "미디어 타입 필터 (IMAGE, VIDEO, BGM)")
    @RequestParam(required = false) mediaTypes: Set<GenerationMediaType>? = null,

    @Parameter(name = "modelId", description = "모델 ID 필터")
    @RequestParam(required = false) modelId: Long? = null,

    @Parameter(name = "providerId", description = "프로바이더 ID 필터")
    @RequestParam(required = false) providerId: Long? = null,

    @Parameter(name = "q", description = "검색어 (이름, 설명, 조건코드)")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["priority"], direction = Sort.Direction.ASC, size = 20)
    pageable: Pageable
  ): Page<BoGenerationCostRuleItem> {
    return boGenerationCostRuleService.findAll(statuses, mediaTypes, modelId, providerId, q, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) 생성 비용 규칙을 수정한다.",
    description = """
    권한: ai("admin")
    비용 규칙 정보를 수정한다.
    적용 대상(modelId, providerId, mediaType)도 변경할 수 있으나 주의가 필요하다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/generation-cost-rules/{id}")
  fun patch(
    @PathVariable(required = true) id: Long,
    @Validated @RequestBody dto: PatchGenerationCostRule
  ): Map<String, Any?> {
    dto.id = id
    return boGenerationCostRuleService.patch(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  @Operation(
    summary = "(백오피스) 생성 비용 규칙을 삭제한다.",
    description = """
    권한: ai("admin")
    비용 규칙을 삭제한다 (Soft Delete).
    삭제된 규칙은 비용 계산에서 제외된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/generation-cost-rules/{id}")
  fun delete(
    @PathVariable(required = true) id: Long
  ): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boGenerationCostRuleService.delete(session!!, id))
  }
}
