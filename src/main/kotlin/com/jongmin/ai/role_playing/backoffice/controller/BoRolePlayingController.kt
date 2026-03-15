package com.jongmin.ai.role_playing.backoffice.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.platform.service.RolePlayingService
import com.jongmin.ai.role_playing.RolePlayingType
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateRolePlaying
import com.jongmin.ai.role_playing.backoffice.dto.request.PatchRolePlaying
import com.jongmin.ai.role_playing.backoffice.dto.response.BoRolePlayingItem
import com.jongmin.ai.role_playing.platform.component.RolePlayingExecutor
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
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
class BoRolePlayingController(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val rolePlayingService: RolePlayingService,
  private val rolePlayingExecutor: RolePlayingExecutor,
  private val boRolePlayingService: com.jongmin.ai.role_playing.backoffice.service.BoRolePlayingService
) : JController() {

  @PostMapping("/bo/role-playing")
  fun create(@Valid @RequestBody dto: CreateRolePlaying): BoRolePlayingItem {
    dto.accountId = session!!.accountId
    dto.ownerId = -1
    dto.type = RolePlayingType.DRAMA // 현재는 극만 지원된다.
    dto.workflow = mapOf()
    return boRolePlayingService.create(session!!, dto)
  }

  @GetMapping("/bo/role-playing/{id}")
  fun findOne(@PathVariable(required = true) id: Long): BoRolePlayingItem {
    return boRolePlayingService.findById(id)
  }

  @GetMapping("/bo/role-playing")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoRolePlayingItem> {
    return boRolePlayingService.findAll(statuses, q, jPageable(pageable))
  }

  @PatchMapping("/bo/role-playing")
  fun patch(@Valid @RequestBody dto: PatchRolePlaying): Map<String, Any?> {
    return boRolePlayingService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
  }

  @DeleteMapping("/bo/role-playing/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = boRolePlayingService.delete(session!!, id))
  }

  //
  @PostMapping("/bo/role-playing/{rolePlayingId}/executes", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun execute(
    @PathVariable rolePlayingId: Long,
    @Valid @RequestBody workflow: Map<String, Any>
  ): Flux<String> {
    return try {
      val session = session!!.deepCopy()
      Flux.create {
        Thread.startVirtualThread {
          try {
            val rolePlaying = rolePlayingService.findById(session, rolePlayingId)
            rolePlaying.workflow = workflow
            rolePlayingExecutor.execute(session, rolePlaying, it)
          } catch (e: Exception) {
            it.error(e)
          }
        }
      }
    } catch (e: Exception) {
      Flux.error(BadRequestException(e.message ?: "실행 중 오류가 발생했습니다."))
    }
  }
}

