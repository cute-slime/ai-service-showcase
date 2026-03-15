package com.jongmin.ai.role_playing.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateAiCharacter
import com.jongmin.ai.role_playing.backoffice.dto.request.PatchAiCharacter
import com.jongmin.ai.role_playing.backoffice.dto.response.BoAiCharacterItem
import com.jongmin.ai.role_playing.backoffice.service.BoAiCharacterService
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
class BoAiCharacterController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val boAiCharacterService: BoAiCharacterService
) : JController() {

  @PostMapping("/bo/ai-characters")
  fun create(@Valid @RequestBody dto: CreateAiCharacter): BoAiCharacterItem {
    dto.accountId = session!!.accountId
    dto.ownerId = -1
    dto.currentVersion = 1.0f
    dto.version = mapOf()
    return boAiCharacterService.create(session!!, dto)
  }

  @GetMapping("/bo/ai-characters/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoAiCharacterItem {
    return boAiCharacterService.findById(id)
  }

  @GetMapping("/bo/ai-characters")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoAiCharacterItem> {
    return boAiCharacterService.findAll(statuses, q, jPageable(pageable))
  }

  @GetMapping("/bo/ai-characters/available")
  fun findAll(): List<BoAiCharacterItem> {
    return boAiCharacterService.findAll()
  }

  @PatchMapping("/bo/ai-characters")
  fun patch(@Valid @RequestBody dto: PatchAiCharacter): Map<String, Any?> {
    val datum = objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {})
    dto.type?.let { datum["type"] = it }
    return boAiCharacterService.patch(session!!, datum)
  }

  @DeleteMapping("/bo/ai-characters/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boAiCharacterService.delete(session!!, id))
  }
}

