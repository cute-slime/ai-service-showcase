package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiMessageRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateAiMessage
import com.jongmin.ai.core.backoffice.dto.response.BoAiMessageItem
import com.jongmin.ai.core.platform.entity.AiMessage
import com.jongmin.ai.core.platform.entity.QAiMessage.aiMessage
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * AI 메시지 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiMessageService(
  private val objectMapper: ObjectMapper,
  repository: AiMessageRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<AiMessage, BoAiMessageItem, AiMessageRepository>(repository, queryFactory
) {
  override val entityName: String = "AiMessage"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreateAiMessage): BoAiMessageItem {
    kLogger.info { "(BO) AI 메시지 생성 - admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, AiMessage::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoAiMessageItem {
    kLogger.debug { "(BO) AI 메시지 조회 - id: $id" }
    return selectBoAiMessageItem(true)
      .from(aiMessage)
      .where(aiMessage.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Chat not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiMessageItem> {
    kLogger.debug { "(BO) AI 메시지 목록 조회" }

    val predicate = buildSearchPredicate(aiMessage.status, StatusType.DELETED, statuses)
    val countQuery = queryFactory.select(aiMessage.count()).from(aiMessage).where(predicate)

    return selectBoAiMessageItem()
      .from(aiMessage)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  private fun selectBoAiMessageItem(full: Boolean = false): JPAQuery<BoAiMessageItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoAiMessageItem::class.java,
            aiMessage.id.`as`("id"),
            aiMessage.status.`as`("status"),
            aiMessage.createdAt.`as`("createdAt"),
            aiMessage.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoAiMessageItem::class.java,
            aiMessage.id.`as`("id"),
            aiMessage.status.`as`("status"),
            aiMessage.createdAt.`as`("createdAt"),
          )
        )
  }
}


