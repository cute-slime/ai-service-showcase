package com.jongmin.ai.role_playing.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.role_playing.PlaceRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreatePlace
import com.jongmin.ai.role_playing.backoffice.dto.response.BoPlaceItem
import com.jongmin.ai.role_playing.platform.entity.Place
import com.jongmin.ai.role_playing.platform.entity.QPlace.place
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
 * 장소 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoPlaceService(
  private val objectMapper: ObjectMapper,
  repository: PlaceRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<Place, BoPlaceItem, PlaceRepository>(repository, queryFactory
) {
  override val entityName: String = "Place"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreatePlace): BoPlaceItem {
    kLogger.info { "(BO) Place 생성 - admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, Place::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoPlaceItem {
    kLogger.debug { "(BO) Place 조회 - id: $id" }
    return selectBoPlaceItem(true)
      .from(place)
      .where(place.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Place not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoPlaceItem> {
    kLogger.debug { "(BO) Place 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(place.status, StatusType.DELETED, statuses, q, place.name)
    val countQuery = queryFactory.select(place.count()).from(place).where(predicate)

    return selectBoPlaceItem()
      .from(place)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  fun findAll(): List<BoPlaceItem> {
    kLogger.debug { "(BO) Place 전체 목록 조회" }

    return selectBoPlaceItem()
      .from(place)
      .where(place.status.ne(StatusType.DELETED))
      .fetch()
  }

  // patch(), delete()는 BaseCrudService에서 상속

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoPlaceItem(full: Boolean = false): JPAQuery<BoPlaceItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoPlaceItem::class.java,
            place.id.`as`("id"),
            place.type.`as`("type"),
            place.status.`as`("status"),
            place.tiny.`as`("tiny"),
            place.small.`as`("small"),
            place.medium.`as`("medium"),
            place.large.`as`("large"),
            place.fullText.`as`("fullText"),
            place.name.`as`("name"),
            place.createdAt.`as`("createdAt"),
            place.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoPlaceItem::class.java,
            place.id.`as`("id"),
            place.status.`as`("status"),
            place.tiny.`as`("tiny"),
            place.type.`as`("type"),
            place.name.`as`("name"),
            place.createdAt.`as`("createdAt"),
          )
        )
  }
}

