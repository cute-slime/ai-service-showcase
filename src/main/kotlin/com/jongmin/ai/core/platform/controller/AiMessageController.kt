package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository.Companion.BLOCK_BY_ACCOUNT_ID
import com.jongmin.ai.core.AiMessageRole
import com.jongmin.ai.core.platform.dto.HasAiThreadOwnership
import com.jongmin.ai.core.platform.dto.request.CreateAiMessage
import com.jongmin.ai.core.platform.dto.response.AiMessageItem
import com.jongmin.ai.core.platform.service.AiMessageService
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Validated
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
@RestController
@RequestMapping("/v1.0")
class AiMessageController(
  private val redisNodeRepository: RedisNodeRepository,
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val eventSender: EventSender,
  private val aiMessageService: AiMessageService
) : JController() {

  @PostMapping("/ai-threads/{aiThreadId}/ai-messages")
  fun create(
    @HasAiThreadOwnership @PathVariable aiThreadId: Long, // TODO TEST
    @Valid @RequestBody dto: CreateAiMessage
  ): AiMessageItem {
    val session = session!!
    val key = "${BLOCK_BY_ACCOUNT_ID}_${session.accountId}"
    if (!redisNodeRepository.lockIfAbsent(key, 5)) // #block ai message using thread id
      throw BadRequestException("추론 중입니다. 잠시 후 다시 시도해주세요.")
    val aiMessages = aiMessageService.findAll(session, aiThreadId, 1)
    val lastMessage = aiMessages.lastOrNull()
    if (lastMessage?.role == AiMessageRole.USER) throw BadRequestException("연속으로 사용자 메시지를 쌓을 수 없습니다.")
    dto.aiThreadId = aiThreadId
    val messageItem = aiMessageService.create(session, dto)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_MESSAGE_CREATED, messageItem, dto.accountId!!, useFcm = false)
    redisNodeRepository.unlock(key)
    return messageItem
  }

  @GetMapping("/ai-messages/{id}")
  fun findOne(@PathVariable(required = true) id: Long): AiMessageItem = aiMessageService.findById(session!!, id)

  @GetMapping("/ai-threads/{aiThreadId}/ai-messages")
  fun findAll(
    @PathVariable aiThreadId: Long,

    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<AiMessageItem> = aiMessageService.findAll(session!!, aiThreadId, statuses, q, jPageable(pageable))


  @GetMapping("/ai-threads/{aiThreadId}/ai-messages/feeds")
  fun feeds(
    @PathVariable aiThreadId: Long,
    lastId: Long?
  ): CommonDto.Feed<AiMessageItem> = CommonDto.Feed(20, aiMessageService.findAllForFeed(session!!, aiThreadId, lastId, 20))

  @DeleteMapping("/ai-messages/{id}")
  fun delete(@PathVariable(required = true) id: Long): CommonDto.JApiResponse<Boolean> =
    CommonDto.JApiResponse(data = aiMessageService.delete(session!!, id))

  // 좋아요 등등의 기능 구현 추가
}

