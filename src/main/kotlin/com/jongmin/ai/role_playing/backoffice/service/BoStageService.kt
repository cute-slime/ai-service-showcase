package com.jongmin.ai.role_playing.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.role_playing.StageRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateStage
import com.jongmin.ai.role_playing.backoffice.dto.response.BoStageItem
import com.jongmin.ai.role_playing.platform.entity.QStage.stage
import com.jongmin.ai.role_playing.platform.entity.Stage
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
 * 스테이지 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoStageService(
  private val objectMapper: ObjectMapper,
  repository: StageRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<Stage, BoStageItem, StageRepository>(repository, queryFactory
) {
  override val entityName: String = "Stage"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreateStage): BoStageItem {
    kLogger.info { "(BO) Stage 생성 - admin: ${session.username}(${session.accountId})" }

    val entity = objectMapper.convert(dto, Stage::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoStageItem {
    kLogger.debug { "(BO) Stage 조회 - id: $id" }
    return selectBoStageItem(true)
      .from(stage)
      .where(stage.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Stage not found.")
  }

  fun findAll(): List<BoStageItem> {
    kLogger.debug { "(BO) Stage 전체 목록 조회" }
    return selectBoStageItem()
      .from(stage)
      .where(stage.status.ne(StatusType.DELETED))
      .fetch()
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoStageItem> {
    kLogger.debug { "(BO) Stage 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(stage.status, StatusType.DELETED, statuses, q, stage.name)
    val countQuery = queryFactory.select(stage.count()).from(stage).where(predicate)

    return selectBoStageItem()
      .from(stage)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoStageItem(full: Boolean = false): JPAQuery<BoStageItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoStageItem::class.java,
            stage.id.`as`("id"),
            stage.type.`as`("type"),
            stage.status.`as`("status"),
            stage.tiny.`as`("tiny"),
            stage.small.`as`("small"),
            stage.medium.`as`("medium"),
            stage.large.`as`("large"),
            stage.fullText.`as`("fullText"),
            stage.name.`as`("name"),
            stage.createdAt.`as`("createdAt"),
            stage.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoStageItem::class.java,
            stage.id.`as`("id"),
            stage.status.`as`("status"),
            stage.tiny.`as`("tiny"),
            stage.type.`as`("type"),
            stage.name.`as`("name"),
            stage.createdAt.`as`("createdAt"),
          )
        )
  }
}

