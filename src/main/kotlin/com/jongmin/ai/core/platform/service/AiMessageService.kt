package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.toOrderSpecifiers
import com.jongmin.ai.core.AiMessageContentType
import com.jongmin.ai.core.AiMessageRepository
import com.jongmin.ai.core.AiMessageRole
import com.jongmin.ai.core.platform.dto.request.CreateAiMessage
import com.jongmin.ai.core.platform.dto.response.AiMessageItem
import com.jongmin.ai.core.platform.entity.AiMessage
import com.jongmin.ai.core.platform.entity.QAiMessage.aiMessage
import com.jongmin.ai.core.platform.entity.QAiThread.aiThread
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiMessageService(
  private val objectMapper: ObjectMapper,
  private val aiMessageRepository: AiMessageRepository,
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(session: JSession, dto: CreateAiMessage): AiMessageItem {
    kLogger.info { "AI 메시지 생성 - id: ${dto.id}" }
    dto.role = dto.role ?: AiMessageRole.USER
    dto.type = dto.type ?: AiMessageContentType.TEXT
    dto.accountId = session.accountId
    val entity = objectMapper.convert(dto, AiMessage::class.java)
    entity.status = StatusType.ACTIVE
    aiMessageRepository.save(entity)
    return findById(session, entity.id)
  }

  fun findById(session: JSession, id: Long): AiMessageItem {
    kLogger.debug { "AI 메시지 조회 - id: $id" }
    return selectAiMessageItem(true)
      .from(aiMessage)
      .where(aiMessage.id.eq(id).and(aiMessage.accountId.eq(session.accountId)).and(aiMessage.status.eq(StatusType.ACTIVE)))
      .fetchOne() ?: throw ObjectNotFoundException("Chat not found.")
  }

  fun findAllForAiMessageThread(session: JSession, aiMessageId: Long, limit: Long = 100): List<AiMessage> {
    kLogger.debug { "AI 메시지 스레드 조회 - aiMessageId: $aiMessageId" }

    val aiThreadId = queryFactory
      .select(aiMessage.aiThreadId)
      .from(aiMessage)
      .where(
        aiMessage.id.eq(aiMessageId)
          .and(aiMessage.accountId.eq(session.accountId))
          .and(aiMessage.status.eq(StatusType.ACTIVE))
      )
      .orderBy(aiMessage.id.asc())
      .fetchOne() ?: throw ObjectNotFoundException("Message not found.")

    return findAll(session, aiThreadId, limit)
  }

  fun findAll(
    session: JSession,
    aiThreadId: Long,
    limit: Long = 100 // 100 이나 되는 이유는 AI 가 대화 문맥 파악을 위함
  ): List<AiMessage> {
    kLogger.debug { "AI 메시지 목록 조회 - all" }
    return queryFactory
      .select(aiMessage)
      .where(
        aiMessage.aiThreadId.eq(aiThreadId)
          .and(aiMessage.accountId.eq(session.accountId))
          .and(aiMessage.status.eq(StatusType.ACTIVE))
          .and(aiMessage.id.gt(aiThread.startMessageId.coalesce(0)))
      )
      .from(aiMessage)
      .join(aiThread).on(aiMessage.aiThreadId.eq(aiThread.id))
      .orderBy(aiMessage.id.desc())
      .limit(limit)
      .fetch()
      .reversed()
  }

  fun findAllForFeed(
    session: JSession,
    aiThreadId: Long,
    lastAiMessageId: Long?,
    limit: Long = 20
  ): List<AiMessageItem> {
    kLogger.debug { "Feed AI 메시지 목록 조회 - aiThreadId: $aiThreadId, lastAiMessageId: $lastAiMessageId" }
    val predicate = BooleanBuilder(
      aiMessage.aiThreadId.eq(aiThreadId)
        .and(aiMessage.accountId.eq(session.accountId))
        .and(aiMessage.status.eq(StatusType.ACTIVE))
        .and(aiMessage.id.gt(aiThread.startMessageId.coalesce(0)))
    )
    return selectAiMessageItem()
      .where(lastAiMessageId?.let { predicate.and(aiMessage.id.lt(it)) })
      .from(aiMessage)
      .join(aiThread).on(aiMessage.aiThreadId.eq(aiThread.id))
      .orderBy(aiMessage.id.desc())
      .limit(limit)
      .fetch()
      .reversed()
  }

  fun findAll(
    session: JSession,
    threadId: Long,
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<AiMessageItem> {
    kLogger.debug { "AI 메시지 목록 조회 - pageable" }

    val predicate = BooleanBuilder(
      aiMessage.aiThreadId.eq(threadId)
        .and(aiMessage.accountId.eq(session.accountId))
        .and(aiMessage.status.eq(StatusType.ACTIVE))
        .and(aiMessage.id.gt(aiThread.startMessageId.coalesce(0)))
    )

    val count: Long = queryFactory
      .select(aiMessage.count())
      .from(aiMessage)
      .join(aiThread).on(aiMessage.aiThreadId.eq(aiThread.id))
      .where(predicate)
      .fetchOne() ?: 0

    val items: List<AiMessageItem> = selectAiMessageItem()
      .from(aiMessage)
      .join(aiThread).on(aiMessage.aiThreadId.eq(aiThread.id))
      .where(predicate)
      .orderBy(*pageable.sort.toOrderSpecifiers())
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()

    return PageImpl(items, pageable, count)
  }

  fun getContent(session: JSession, aiMessageId: Long): String {
    return aiMessageRepository
      .findOne(aiMessage.id.eq(aiMessageId).and(aiMessage.accountId.eq(session.accountId)).and(aiMessage.status.eq(StatusType.ACTIVE)))
      .map { it.content }
      .orElseThrow { ObjectNotFoundException("메시지를 찾을 수 없습니다.") }
  }

  @Transactional
  fun delete(session: JSession, aiMessageId: Long): Boolean {
    kLogger.info { "AI 메시지 삭제 - id: $aiMessageId" }
    val chat = aiMessageRepository
      .findOne(aiMessage.id.eq(aiMessageId).and(aiMessage.accountId.eq(session.accountId)).and(aiMessage.status.ne(StatusType.DELETED)))
      .orElseThrow { ObjectNotFoundException("메시지를 찾을 수 없습니다.") }
    chat.status = StatusType.DELETED
    return true
  }

  private fun selectAiMessageItem(full: Boolean = false): JPAQuery<AiMessageItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            AiMessageItem::class.java,
            aiMessage.id.`as`("id"),
            aiMessage.aiThreadId.`as`("aiThreadId"),
            aiMessage.content.`as`("content"),
            aiMessage.type.`as`("type"),
            aiMessage.role.`as`("role"),
            aiMessage.status.`as`("status"),
            aiMessage.createdAt.`as`("createdAt"),
            aiMessage.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            AiMessageItem::class.java,
            aiMessage.id.`as`("id"),
            aiMessage.aiThreadId.`as`("aiThreadId"),
            aiMessage.content.`as`("content"),
            aiMessage.type.`as`("type"),
            aiMessage.role.`as`("role"),
            aiMessage.status.`as`("status"),
            aiMessage.createdAt.`as`("createdAt"),
          )
        )
  }
}
