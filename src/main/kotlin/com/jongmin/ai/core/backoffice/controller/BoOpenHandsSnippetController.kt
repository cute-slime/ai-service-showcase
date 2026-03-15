package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateOpenHandsSnippet
import com.jongmin.ai.core.backoffice.dto.request.PatchOpenHandsSnippet
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsSnippetItem
import com.jongmin.ai.core.backoffice.service.BoOpenHandsSnippetService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
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
class BoOpenHandsSnippetController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val openHandsSnippetService: BoOpenHandsSnippetService,
) : JController() {
  @PostMapping("/bo/open-hands-snippets")
  fun create(@Validated @RequestBody dto: CreateOpenHandsSnippet): BoOpenHandsSnippetItem {
    return openHandsSnippetService.create(session!!, dto)
  }

  @GetMapping("/bo/open-hands-snippets")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "타이틀")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC, size = 1000)
    pageable: Pageable
  ): Page<BoOpenHandsSnippetItem> = openHandsSnippetService.findAll(session!!, statuses, q, jPageable(pageable))

  @GetMapping("/bo/open-hands-snippets/{id}")
  fun findOne(@PathVariable id: Long): BoOpenHandsSnippetItem = openHandsSnippetService.findOne(session!!, id)

  @PatchMapping("/bo/open-hands-snippets")
  fun patch(@Validated @RequestBody dto: PatchOpenHandsSnippet): Map<String, Any?> =
    openHandsSnippetService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {}))

  @DeleteMapping("/bo/open-hands-snippets/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> =
    CommonDto.JApiResponse.of(openHandsSnippetService.delete(session!!, id))
}

