package com.jongmin.ai.role_playing.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.service.RolePlayingService
import com.jongmin.ai.role_playing.platform.component.RolePlayingExecutor
import com.jongmin.ai.role_playing.platform.dto.request.RolePlayingCommand
import dev.langchain4j.data.message.ChatMessageType
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper

@Validated
@RequestMapping("/v1.0")
@RestController
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
class RolePlayingController(
  val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val rolePlayingService: RolePlayingService,
  private val rolePlayingExecutor: RolePlayingExecutor,
) : JController() {

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/role-playing/{rolePlayingId}")
  fun create(
    @PathVariable rolePlayingId: Long,
    @RequestBody @Valid dto: RolePlayingCommand
  ): Flux<String> {
    val session = session!!.deepCopy()
    return Flux.create { emitter ->
      Thread.startVirtualThread {
        // val aiMessages = aiMessageService.findAllForAiMessageThread(session, dto.aiMessageId!!)
        // val lastMessage = aiMessages.last()
        // if (lastMessage.role == AiMessageRole.ASSISTANT) throw BadRequestException("사용자 메시지에 대해서만 추론할 수 있습니다.")

        val input = objectMapper.writeValueAsString(dto)
        // val aiThreadId = lastMessage.aiThreadId
        val lockKey = "${RedisNodeRepository.BLOCK_BY_ROLE_PLAYING_ID}_${rolePlayingId}_${session.accountId}"
        if (!redisNodeRepository.lockIfAbsent(lockKey, 15)) {
          emitter.error(BadRequestException("추론 중입니다. 잠시 후 다시 시도해주세요."))
        } else {
          val rolePlaying = rolePlayingService.findById(session, rolePlayingId)
          rolePlayingExecutor
            .execute(
              session, rolePlaying, emitter, listOf(IAiChatMessage.from(ChatMessageType.TOOL_EXECUTION_RESULT, input))
            ) { output ->
              output as MutableMap<*, *>
              redisNodeRepository.unlock(lockKey)
            }
        }

      }
    }
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @DeleteMapping("/role-playing/{rolePlayingId}")
  // TODO 소유권 체크
  fun stop(@PathVariable rolePlayingId: Long) {
  }
}
