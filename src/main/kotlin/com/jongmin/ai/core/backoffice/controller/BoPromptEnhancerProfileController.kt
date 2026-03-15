package com.jongmin.ai.core.backoffice.controller

import com.jongmin.ai.core.backoffice.dto.request.CreatePromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.request.PatchPromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.response.BoPromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.response.BoPromptEnhancerProfileListItem
import com.jongmin.ai.core.backoffice.service.BoPromptEnhancerProfileService
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * (백오피스) 프롬프트 인첸터 프로필 관리 컨트롤러
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-9. BackOffice - Prompt Enhancer Profile", description = "프롬프트 인첸터 프로필 백오피스 API")
@Validated
@RestController
@RequestMapping("/v1.0")
class BoPromptEnhancerProfileController(
  private val boPromptEnhancerProfileService: BoPromptEnhancerProfileService,
) : JController() {

  @Operation(summary = "프롬프트 인첸터 프로필 생성")
  @PostMapping("/bo/prompt-enhancer-profiles")
  fun create(
    @Valid @RequestBody dto: CreatePromptEnhancerProfile
  ): BoPromptEnhancerProfile {
    return boPromptEnhancerProfileService.create(session!!, dto)
  }

  @Operation(summary = "프롬프트 인첸터 프로필 상세 조회")
  @GetMapping("/bo/prompt-enhancer-profiles/{id}")
  fun findOne(
    @Parameter(description = "프로필 ID")
    @PathVariable id: Long
  ): BoPromptEnhancerProfile {
    return boPromptEnhancerProfileService.findById(id)
  }

  @Operation(summary = "프롬프트 인첸터 프로필 목록 조회")
  @GetMapping("/bo/prompt-enhancer-profiles")
  fun findAll(
    @Parameter(description = "상태 필터")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(description = "프로바이더 코드 필터")
    @RequestParam(required = false) providerCodes: Set<String>? = null,

    @Parameter(description = "검색어 (name/description/targetRule)")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["updatedAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoPromptEnhancerProfileListItem> {
    return boPromptEnhancerProfileService.findAll(statuses, providerCodes, q, pageable)
  }

  @Operation(summary = "프롬프트 인첸터 프로필 수정")
  @PatchMapping("/bo/prompt-enhancer-profiles/{id}")
  fun patch(
    @PathVariable id: Long,
    @Valid @RequestBody dto: PatchPromptEnhancerProfile
  ): BoPromptEnhancerProfile {
    return boPromptEnhancerProfileService.patch(session!!, id, dto)
  }

  @Operation(summary = "프롬프트 인첸터 프로필 삭제(소프트 삭제)")
  @DeleteMapping("/bo/prompt-enhancer-profiles/{id}")
  fun delete(
    @PathVariable id: Long
  ): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boPromptEnhancerProfileService.delete(session!!, id))
  }
}
