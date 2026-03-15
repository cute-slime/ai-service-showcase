package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.dto.CommonDto.JApiResponse
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.ai.core.platform.component.adaptive.SimpleAgent
import com.jongmin.ai.core.platform.dao.AiThreadDao
import com.jongmin.ai.core.platform.dto.MyAiThreads
import com.jongmin.ai.core.platform.dto.request.CreateAiThread
import com.jongmin.ai.core.platform.dto.request.PatchAiThread
import com.jongmin.ai.core.platform.dto.response.AiThreadItem
import com.jongmin.ai.core.platform.service.AiThreadService
import com.jongmin.ai.core.platform.service.FavoriteAiThreadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Validated
@RequestMapping("/v1.0")
@RestController
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
class AiThreadController(
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val eventSender: EventSender,
  private val redisNodeRepository: RedisNodeRepository,
  private val aiThreadService: AiThreadService,
  private val simpleAgent: SimpleAgent,
  private val favoriteAiThreadService: FavoriteAiThreadService,
  private val aiThreadDao: AiThreadDao
) : JController() {

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/ai-threads")
  fun create(@RequestBody @Valid dto: CreateAiThread): AiThreadItem {
    val session = session!!
    // XXX AI 스레드 리밋 체크 (부정 사용자 차단)
    dto.title = "\uD83D\uDCAC New Chat"
    val aiThreadItem = aiThreadService.create(session, dto)
    val memoizedSession = session.deepCopy()
    simpleAgent.executeThreadTitleGenerator(session.accountId, dto.question!!) {
      aiThreadDao.update(aiThreadItem.id!!, it)
      aiThreadItem.title = it
      eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_UPDATED, aiThreadItem, memoizedSession.accountId)
    }
    return aiThreadItem
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AnyMatches)
  @GetMapping("/ai-threads")
  fun findAll(
    @Parameter(name = "statuses", description = "상태")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "starred", description = "별표시 유무")
    @RequestParam(required = false) starred: Boolean? = null,

    @Parameter(name = "q", description = "검색어")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["aiThread.id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<AiThreadItem> {
    return aiThreadService.findAll(session!!, statuses, starred, q, jPageable(pageable))
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AnyMatches)
  @GetMapping("/ai-threads/{id}")
  fun findOne(@PathVariable id: Long): AiThreadItem {
    return aiThreadService.findOne(session!!, id)
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PatchMapping("/ai-threads")
  fun patch(@RequestBody @Valid dto: PatchAiThread): Map<String, Any?> {
    val changes = aiThreadService.patch(session!!, objectMapper.convertValue(dto, object : TypeReference<MutableMap<String, Any>>() {}))
    val aiThreadItem = aiThreadService.findOne(session!!, dto.id!!)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_UPDATED, aiThreadItem, session!!.accountId)
    return changes
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @DeleteMapping("/ai-threads/{id}")
  fun delete(@PathVariable id: Long): JApiResponse<Boolean> {
    aiThreadService.delete(session!!, id)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_DELETED, mapOf("id" to id), session!!.accountId)
    return JApiResponse.TRUE
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @DeleteMapping("/ai-threads")
  fun deleteAll(@MyAiThreads ids: Array<Long>): JApiResponse<Boolean> {
    aiThreadService.deleteAll(session!!, ids)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_DELETED, mapOf("ids" to ids), session!!.accountId)
    return JApiResponse.TRUE
  }

  // :-----------------------------: Utility :-----------------------------:
  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/ai-threads/{id}/reset")
  fun reset(
    @PathVariable id: Long,
    @RequestHeader(value = HttpHeaders.FROM, required = false) suid: String?
  ): AiThreadItem {
    val aiThreadItem = aiThreadService.reset(session!!, id)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_RESET, aiThreadItem, session!!.accountId)
    return aiThreadItem
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/ai-threads/{id}/starred")
  fun addFavorites(
    @PathVariable id: Long,
    @RequestHeader(value = HttpHeaders.FROM, required = false) suid: String?
  ): AiThreadItem {
    favoriteAiThreadService.add(session!!, id)
    val aiThreadItem = aiThreadService.findOne(session!!, id)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_UPDATED, aiThreadItem, session!!.accountId)
    return aiThreadItem
  }

  @Operation
  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @DeleteMapping("/ai-threads/{id}/starred")
  fun removeFavorites(
    @PathVariable id: Long,
    @RequestHeader(value = HttpHeaders.FROM, required = false) suid: String?
  ): AiThreadItem {
    favoriteAiThreadService.remove(session!!, id)
    val aiThreadItem = aiThreadService.findOne(session!!, id)
    eventSender.sendEventToAccount(chatTopic, MessageType.AI_THREAD_UPDATED, aiThreadItem, session!!.accountId)
    return aiThreadItem
  }
}

