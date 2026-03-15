package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.OpenHandsIssueRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateOpenHandsIssue
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsIssueItem
import com.jongmin.ai.core.platform.entity.OpenHandsIssue
import com.jongmin.ai.core.platform.entity.QOpenHandsIssue.openHandsIssue
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * OpenHands Issue 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoOpenHandsIssueService(
  private val objectMapper: ObjectMapper,
  repository: OpenHandsIssueRepository,
  queryFactory: JPAQueryFactory
) : BaseCrudService<OpenHandsIssue, BoOpenHandsIssueItem, OpenHandsIssueRepository>(repository, queryFactory
) {
  override val entityName: String = "OpenHandsIssue"

  @Transactional
  fun create(session: JSession, dto: CreateOpenHandsIssue): BoOpenHandsIssueItem {
    kLogger.info { "(BO) OpenHandsIssue 생성 - title: ${dto.title}, admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, OpenHandsIssue::class.java)
    return findOne(session, repository.save(entity).id)
  }

  override fun findById(id: Long): BoOpenHandsIssueItem {
    kLogger.debug { "(BO) OpenHandsIssue 단건 - id: $id" }

    return queryFactory
      .select(BoOpenHandsIssueItem.buildProjection())
      .from(openHandsIssue)
      .where(openHandsIssue.id.eq(id).and(openHandsIssue.status.ne(StatusType.DELETED)))
      .fetchOne() ?: throw ObjectNotFoundException("OpenHandsIssue not found.")
  }

  // 기존 API 호환성 유지
  fun findOne(session: JSession, id: Long): BoOpenHandsIssueItem = findById(id)

  fun findAll(
    session: JSession,
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoOpenHandsIssueItem> {
    kLogger.debug { "(BO) OpenHandsIssue 목록 조회, q: $q, page: $pageable" }

    val predicate = buildSearchPredicate(openHandsIssue.status, StatusType.DELETED, statuses, q, openHandsIssue.title)

    val countQuery = queryFactory
      .select(openHandsIssue.count())
      .from(openHandsIssue)
      .where(predicate)

    return queryFactory
      .select(BoOpenHandsIssueItem.buildProjection())
      .from(openHandsIssue)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속
}

