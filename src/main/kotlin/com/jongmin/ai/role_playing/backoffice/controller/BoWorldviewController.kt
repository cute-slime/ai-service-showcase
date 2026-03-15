package com.jongmin.ai.role_playing.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateWorldview
import com.jongmin.ai.role_playing.backoffice.dto.request.PatchWorldview
import com.jongmin.ai.role_playing.backoffice.dto.response.BoWorldviewItem
import com.jongmin.ai.role_playing.backoffice.service.BoWorldviewService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
class BoWorldviewController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val boWorldviewService: BoWorldviewService
) : JController() {

  @PostMapping("/bo/worldviews")
  fun create(@Valid @RequestBody dto: CreateWorldview): BoWorldviewItem {
    dto.accountId = session!!.accountId
    dto.ownerId = -1
    dto.currentVersion = 1.0f
    dto.version = mapOf()
    return boWorldviewService.create(session!!, dto)
  }

  @GetMapping("/bo/worldviews/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoWorldviewItem {
    return boWorldviewService.findById(id)
  }

  @GetMapping("/bo/worldviews")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoWorldviewItem> {
    return boWorldviewService.findAll(statuses, q, jPageable(pageable))
  }

  @PatchMapping("/bo/worldviews")
  fun patch(@Valid @RequestBody dto: PatchWorldview): Map<String, Any?> {
    val datum = objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {})
    dto.type?.let { datum["type"] = it }
    return boWorldviewService.patch(session!!, datum)
  }

  @DeleteMapping("/bo/worldviews/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boWorldviewService.delete(session!!, id))
  }
}

