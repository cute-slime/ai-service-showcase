package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.OpenHandsSnippetRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateOpenHandsSnippet
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsSnippetItem
import com.jongmin.ai.core.platform.entity.OpenHandsSnippet
import com.jongmin.ai.core.platform.entity.QOpenHandsSnippet.openHandsSnippet
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * OpenHands Snippet 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoOpenHandsSnippetService(
  private val objectMapper: ObjectMapper,
  repository: OpenHandsSnippetRepository,
  queryFactory: JPAQueryFactory
) : BaseCrudService<OpenHandsSnippet, BoOpenHandsSnippetItem, OpenHandsSnippetRepository>(repository, queryFactory
) {
  override val entityName: String = "OpenHandsSnippet"

  @Transactional
  fun create(session: JSession, dto: CreateOpenHandsSnippet): BoOpenHandsSnippetItem {
    kLogger.info { "(BO) OpenHandsSnippet 생성 - title: ${dto.title}, admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, OpenHandsSnippet::class.java)
    return findOne(session, repository.save(entity).id)
  }

  override fun findById(id: Long): BoOpenHandsSnippetItem {
    kLogger.debug { "(BO) OpenHandsSnippet 단건 - id: $id" }

    return queryFactory
      .select(BoOpenHandsSnippetItem.buildProjection())
      .from(openHandsSnippet)
      .where(openHandsSnippet.id.eq(id).and(openHandsSnippet.status.ne(StatusType.DELETED)))
      .fetchOne() ?: throw ObjectNotFoundException("OpenHandsSnippet not found.")
  }

  // 기존 API 호환성 유지
  fun findOne(session: JSession, id: Long): BoOpenHandsSnippetItem = findById(id)

  fun findAll(
    session: JSession,
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoOpenHandsSnippetItem> {
    kLogger.debug { "(BO) OpenHandsSnippet 목록 조회, q: $q, page: $pageable" }

    val predicate = buildSearchPredicate(openHandsSnippet.status, StatusType.DELETED, statuses, q, openHandsSnippet.title)

    val countQuery = queryFactory
      .select(openHandsSnippet.count())
      .from(openHandsSnippet)
      .where(predicate)

    return queryFactory
      .select(BoOpenHandsSnippetItem.buildProjection())
      .from(openHandsSnippet)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속
}

