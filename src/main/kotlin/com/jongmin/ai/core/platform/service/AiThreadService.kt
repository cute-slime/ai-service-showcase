package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.toOrderSpecifiers
import com.jongmin.ai.core.AiMessageRepository
import com.jongmin.ai.core.AiThreadRepository
import com.jongmin.ai.core.platform.dto.request.CreateAiThread
import com.jongmin.ai.core.platform.dto.response.AiThreadItem
import com.jongmin.ai.core.platform.entity.AiThread
import com.jongmin.ai.core.platform.entity.QAiThread.aiThread
import com.jongmin.ai.core.platform.entity.QAiThreadStarredJoinTable.aiThreadStarredJoinTable
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
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
import jakarta.annotation.PostConstruct
import kotlin.math.min


@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiThreadService(
  private val objectMapper: ObjectMapper,
  private val aiThreadRepository: AiThreadRepository,
  private val aiMessageRepository: AiMessageRepository,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  lateinit var aiThreadService: AiThreadService

  @PostConstruct
  fun init() {
    aiThreadService = this
  }

  @Transactional
  fun create(session: JSession, dto: CreateAiThread): AiThreadItem {
    kLogger.info { "AI 스레드 생성 - title: ${dto.title}" }
    dto.accountId = session.accountId
    dto.status = StatusType.ACTIVE
    val entity = objectMapper.convert(dto, AiThread::class.java)
    return objectMapper.convert(aiThreadRepository.save(entity), AiThreadItem::class.java)
  }

  fun findAll(
    session: JSession, statuses: Set<StatusType>?, starred: Boolean?, q: String?, pageable: Pageable
  ): Page<AiThreadItem> {
    kLogger.debug { "AI 스레드 목록 조회 - starred: $starred" }

    val predicate = BooleanBuilder(aiThread.accountId.eq(session.accountId).and(aiThread.status.ne(StatusType.DELETED)))

    val having = starred?.let {
      if (it) aiThreadStarredJoinTable.aiThreadId.count().gt(0)
      else aiThreadStarredJoinTable.aiThreadId.count().eq(0)
    }

    val countQuery = queryFactory
      .select(aiThread.countDistinct())
      .from(aiThread)
      .leftJoin(aiThreadStarredJoinTable)
      .on(aiThread.id.eq(aiThreadStarredJoinTable.aiThreadId))
      .where(
        predicate.and(
          starred?.let {
            if (it) aiThreadStarredJoinTable.aiThreadId.isNotNull
            else aiThreadStarredJoinTable.aiThreadId.isNull
          }
        ))
    val count = countQuery.fetchOne() ?: 0

    var listQuery = selectAiThreadItem()
      .from(aiThread)
      .leftJoin(aiThreadStarredJoinTable)
      .on(aiThread.id.eq(aiThreadStarredJoinTable.aiThreadId))
      .where(predicate)
      .groupBy(aiThread)
    listQuery = having?.let { listQuery.having(it) } ?: listQuery
    listQuery = listQuery
      .orderBy(*pageable.sort.toOrderSpecifiers())
      .offset(pageable.offset)
      .limit(min(pageable.pageSize.toLong(), 20))
    val items: List<AiThreadItem> = listQuery.fetch()

    return PageImpl(items, pageable, count)
  }

  fun findOne(session: JSession, id: Long): AiThreadItem {
    kLogger.info { "AI 스레드 조회 - id: $id" }
    return selectAiThreadItem()
      .from(aiThread)
      .leftJoin(aiThreadStarredJoinTable)
      .on(aiThread.id.eq(aiThreadStarredJoinTable.aiThreadId).and(aiThreadStarredJoinTable.accountId.eq(session.accountId)))
      .where(aiThread.id.eq(id).and(aiThread.accountId.eq(session.accountId)).and(aiThread.status.ne(StatusType.DELETED)))
      .groupBy(aiThread)
      .fetchOne() ?: throw ObjectNotFoundException("스레드를 찾을 수 없습니다.")
  }

  @Transactional
  fun patch(session: JSession, data: MutableMap<String, Any>): Map<String, Any?> {
    kLogger.info { "AI 스레드 패치 - id: ${data["id"]}" }
    val target = aiThreadRepository.findById(data["id"] as Long).orElseThrow { ObjectNotFoundException("스레드를 찾을 수 없습니다.") }
    return merge(data, target, "id", "createdAt", "updatedAt", "accountId")
  }

  @Transactional
  fun delete(session: JSession, id: Long) {
    kLogger.info { "AI 스레드 삭제 - id: $id" }
    val target = aiThreadRepository
      .findOne(aiThread.id.eq(id).and(aiThread.accountId.eq(session.accountId)).and(aiThread.status.ne(StatusType.DELETED)))
      .orElseThrow { ObjectNotFoundException("스레드를 찾을 수 없습니다.") }
    target.status = StatusType.DELETED
  }

  @Transactional
  fun deleteAll(session: JSession, ids: Array<Long>) {
    kLogger.info { "AI 스레드 묶음 삭제 - ids: ${ids.toList()}" }
    aiThreadRepository.deleteAllById(ids.toList())
  }

  // :-----------------------------: Utility :-----------------------------:
  @Transactional
  fun reset(session: JSession, aiThreadId: Long): AiThreadItem {
    kLogger.info { "AI 스레드 리셋 - id: $aiThreadId" }
    val target = aiThreadRepository
      .findOne(aiThread.id.eq(aiThreadId).and(aiThread.accountId.eq(session.accountId)).and(aiThread.status.ne(StatusType.DELETED)))
      .orElseThrow { ObjectNotFoundException("스레드를 찾을 수 없습니다.") }

    val lastMessageId = aiMessageRepository.findFirstByAiThreadIdOrderByIdDesc(aiThreadId)?.id
    if (lastMessageId != null) target.startMessageId = lastMessageId

    return findOne(session, aiThreadId)
  }

  // :-----------------------------: Query :-----------------------------:

  private fun selectAiThreadItem(full: Boolean = false): JPAQuery<AiThreadItem> {
    return queryFactory
      .select(
        Projections.bean(
          AiThreadItem::class.java,
          aiThread.id.`as`("id"),
          aiThread.title.`as`("title"),
          aiThread.lastUsedAt.`as`("lastUsedAt"),
          aiThread.createdAt.`as`("createdAt"),
          Expressions.cases()
            .`when`(aiThreadStarredJoinTable.count().gt(0)).then(true)
            .otherwise(false)
            .`as`("starred")
        )
      )
  }
}
