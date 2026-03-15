package com.jongmin.ai.role_playing.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.role_playing.RolePlayingRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateRolePlaying
import com.jongmin.ai.role_playing.backoffice.dto.response.BoRolePlayingItem
import com.jongmin.ai.role_playing.platform.entity.QRolePlaying.rolePlaying
import com.jongmin.ai.role_playing.platform.entity.RolePlaying
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
 * 롤플레잉 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoRolePlayingService(
  private val objectMapper: ObjectMapper,
  repository: RolePlayingRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<RolePlaying, BoRolePlayingItem, RolePlayingRepository>(repository, queryFactory
) {
  override val entityName: String = "RolePlaying"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreateRolePlaying): BoRolePlayingItem {
    kLogger.info { "(BO) RolePlaying 생성 - admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, RolePlaying::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoRolePlayingItem {
    kLogger.debug { "(BO) RolePlaying 조회 - id: $id" }
    return selectBoRolePlayingItem(true)
      .from(rolePlaying)
      .where(rolePlaying.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("rolePlaying not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoRolePlayingItem> {
    kLogger.debug { "(BO) RolePlaying 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(rolePlaying.status, StatusType.DELETED, statuses, q, rolePlaying.subject)
    val countQuery = queryFactory.select(rolePlaying.count()).from(rolePlaying).where(predicate)

    return selectBoRolePlayingItem()
      .from(rolePlaying)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoRolePlayingItem(full: Boolean = false): JPAQuery<BoRolePlayingItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoRolePlayingItem::class.java,
            rolePlaying.id.`as`("id"),
            rolePlaying.subject.`as`("subject"),
            rolePlaying.type.`as`("type"),
            rolePlaying.workflow.`as`("workflow"),
            rolePlaying.description.`as`("description"),
            rolePlaying.lastUsedAt.`as`("lastUsedAt"),
            rolePlaying.status.`as`("status"),
            rolePlaying.createdAt.`as`("createdAt"),
            rolePlaying.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoRolePlayingItem::class.java,
            rolePlaying.id.`as`("id"),
            rolePlaying.subject.`as`("subject"),
            rolePlaying.type.`as`("type"),
            rolePlaying.lastUsedAt.`as`("lastUsedAt"),
            rolePlaying.status.`as`("status"),
            rolePlaying.createdAt.`as`("createdAt"),
          )
        )
  }
}

