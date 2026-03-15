package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateGitProvider
import com.jongmin.ai.core.backoffice.dto.request.PatchGitProvider
import com.jongmin.ai.core.backoffice.dto.response.BoGitProviderItem
import com.jongmin.ai.core.backoffice.service.BoGitProviderService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoGitProviderController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val gitProviderService: BoGitProviderService,
) : JController() {
  @PostMapping("/bo/git-providers")
  fun create(@Validated @RequestBody dto: CreateGitProvider): BoGitProviderItem {
    return gitProviderService.create(session!!, dto)
  }

  @GetMapping("/bo/git-providers")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,
  ): List<BoGitProviderItem> = gitProviderService.findAll(session!!, statuses)

  @GetMapping("/bo/git-providers/{id}")
  fun findOne(@PathVariable id: Long): BoGitProviderItem = gitProviderService.findOne(session!!, id)

  @PatchMapping("/bo/git-providers")
  fun patch(@Validated @RequestBody dto: PatchGitProvider): Map<String, Any?> {
    val data = objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {}).toMutableMap()
    return gitProviderService.patch(session!!, data)
  }

  @DeleteMapping("/bo/git-providers/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> =
    CommonDto.JApiResponse.of(gitProviderService.delete(session!!, id))
}

