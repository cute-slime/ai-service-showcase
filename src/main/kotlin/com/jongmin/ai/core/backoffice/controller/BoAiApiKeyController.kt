package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.backoffice.dto.response.BoAiApiKeyItem
import com.jongmin.ai.core.backoffice.service.BoAiApiKeyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoAiApiKeyController(
  private val boAiApiKeyService: BoAiApiKeyService
) : JController() {

  @Operation(
    summary = "(백오피스) 전달된 AI 제공사에 API Key 목록을 반환한다.",
    description = """
    권한: ai("admin")
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai-models/{id}/api-keys")
  fun findAll(@PathVariable id: Long): List<BoAiApiKeyItem> {
    return boAiApiKeyService.findAllByModelId(id)
  }
}
