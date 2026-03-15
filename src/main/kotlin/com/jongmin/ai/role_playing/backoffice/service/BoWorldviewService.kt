package com.jongmin.ai.role_playing.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.role_playing.WorldviewRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateWorldview
import com.jongmin.ai.role_playing.backoffice.dto.response.BoWorldviewItem
import com.jongmin.ai.role_playing.platform.entity.QWorldview.worldview
import com.jongmin.ai.role_playing.platform.entity.Worldview
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
 * 월드뷰 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoWorldviewService(
  private val objectMapper: ObjectMapper,
  repository: WorldviewRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<Worldview, BoWorldviewItem, WorldviewRepository>(repository, queryFactory
) {
  override val entityName: String = "Worldview"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreateWorldview): BoWorldviewItem {
    kLogger.info { "(BO) Worldview 생성 - admin: ${session.username}(${session.accountId})" }

    val entity = objectMapper.convert(dto, Worldview::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoWorldviewItem {
    kLogger.debug { "(BO) Worldview 조회 - id: $id" }
    return selectBoWorldviewItem(true)
      .from(worldview)
      .where(worldview.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Worldview not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoWorldviewItem> {
    kLogger.debug { "(BO) Worldview 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(worldview.status, StatusType.DELETED, statuses, q, worldview.subject)
    val countQuery = queryFactory.select(worldview.count()).from(worldview).where(predicate)

    return selectBoWorldviewItem()
      .from(worldview)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoWorldviewItem(full: Boolean = false): JPAQuery<BoWorldviewItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoWorldviewItem::class.java,
            worldview.id.`as`("id"),
            worldview.subject.`as`("subject"),
            worldview.type.`as`("type"),
            worldview.status.`as`("status"),
            worldview.tiny.`as`("tiny"),
            worldview.small.`as`("small"),
            worldview.medium.`as`("medium"),
            worldview.large.`as`("large"),
            worldview.fullText.`as`("fullText"),
            worldview.createdAt.`as`("createdAt"),
            worldview.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoWorldviewItem::class.java,
            worldview.id.`as`("id"),
            worldview.status.`as`("status"),
            worldview.tiny.`as`("tiny"),
            worldview.type.`as`("type"),
            worldview.subject.`as`("subject"),
            worldview.createdAt.`as`("createdAt"),
          )
        )
  }
}

