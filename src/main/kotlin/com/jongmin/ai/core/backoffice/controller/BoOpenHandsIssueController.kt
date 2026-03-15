package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateOpenHandsIssue
import com.jongmin.ai.core.backoffice.dto.request.PatchOpenHandsIssue
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsIssueItem
import com.jongmin.ai.core.backoffice.service.BoOpenHandsIssueService
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
class BoOpenHandsIssueController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val boOpenHandsIssueService: BoOpenHandsIssueService,
) : JController() {
  @PostMapping("/bo/open-hands-issues")
  fun create(@Validated @RequestBody dto: CreateOpenHandsIssue): BoOpenHandsIssueItem {
    return boOpenHandsIssueService.create(session!!, dto)
  }

  @GetMapping("/bo/open-hands-issues")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "타이틀")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC, size = 1000)
    pageable: Pageable
  ): Page<BoOpenHandsIssueItem> = boOpenHandsIssueService.findAll(session!!, statuses, q, jPageable(pageable))

  @GetMapping("/bo/open-hands-issues/{id}")
  fun findOne(@PathVariable id: Long): BoOpenHandsIssueItem = boOpenHandsIssueService.findOne(session!!, id)

  @PatchMapping("/bo/open-hands-issues")
  fun patch(@Validated @RequestBody dto: PatchOpenHandsIssue): Map<String, Any?> {
    val data = (objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})).toMutableMap()
    dto.workflowStatus?.let { data["workflowStatus"] = it } // enum 값은 문자열로 치환되기 때문에 수동 할당해야한다.
    return boOpenHandsIssueService.patch(session!!, data)
  }

  @DeleteMapping("/bo/open-hands-issues/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> =
    CommonDto.JApiResponse.of(boOpenHandsIssueService.delete(session!!, id))
}

