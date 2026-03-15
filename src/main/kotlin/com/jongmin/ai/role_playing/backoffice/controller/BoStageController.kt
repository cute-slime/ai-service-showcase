package com.jongmin.ai.role_playing.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateStage
import com.jongmin.ai.role_playing.backoffice.dto.request.PatchStage
import com.jongmin.ai.role_playing.backoffice.dto.response.BoStageItem
import com.jongmin.ai.role_playing.backoffice.service.BoStageService
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
class BoStageController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val boStageService: BoStageService
) : JController() {

  @PostMapping("/bo/stages")
  fun create(@Valid @RequestBody dto: CreateStage): BoStageItem {
    dto.accountId = session!!.accountId
    dto.ownerId = -1
    dto.currentVersion = 1.0f
    dto.version = mapOf()
    return boStageService.create(session!!, dto)
  }

  @GetMapping("/bo/stages/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoStageItem {
    return boStageService.findById(id)
  }

  @GetMapping("/bo/stages")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoStageItem> {
    return boStageService.findAll(statuses, q, jPageable(pageable))
  }

  @GetMapping("/bo/stages/available")
  fun findAll(): List<BoStageItem> {
    return boStageService.findAll()
  }

  @PatchMapping("/bo/stages")
  fun patch(@Valid @RequestBody dto: PatchStage): Map<String, Any?> {
    val datum = objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {})
    dto.type?.let { datum["type"] = it }
    return boStageService.patch(session!!, datum)
  }

  @DeleteMapping("/bo/stages/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boStageService.delete(session!!, id))
  }
}

