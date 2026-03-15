package com.jongmin.ai.role_playing.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.role_playing.AiCharacterRepository
import com.jongmin.ai.role_playing.backoffice.dto.request.CreateAiCharacter
import com.jongmin.ai.role_playing.backoffice.dto.response.BoAiCharacterItem
import com.jongmin.ai.role_playing.platform.entity.AiCharacter
import com.jongmin.ai.role_playing.platform.entity.QAiCharacter.aiCharacter
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
 * AI 캐릭터 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiCharacterService(
  private val objectMapper: ObjectMapper,
  repository: AiCharacterRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<AiCharacter, BoAiCharacterItem, AiCharacterRepository>(repository, queryFactory
) {
  override val entityName: String = "AiCharacter"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId")

  @Transactional
  fun create(session: JSession, dto: CreateAiCharacter): BoAiCharacterItem {
    kLogger.info { "(BO) AiCharacter 생성 - admin: ${session.username}(${session.accountId})" }

    val entity = objectMapper.convert(dto, AiCharacter::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoAiCharacterItem {
    kLogger.debug { "(BO) AiCharacter 조회 - id: $id" }
    return selectBoAiCharacterItem(true)
      .from(aiCharacter)
      .where(aiCharacter.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("AiCharacter not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiCharacterItem> {
    kLogger.debug { "(BO) AiCharacter 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(aiCharacter.status, StatusType.DELETED, statuses, q, aiCharacter.name)
    val countQuery = queryFactory.select(aiCharacter.count()).from(aiCharacter).where(predicate)

    return selectBoAiCharacterItem()
      .from(aiCharacter)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  fun findAll(): List<BoAiCharacterItem> {
    kLogger.debug { "(BO) AiCharacter 전체 목록 조회" }

    return selectBoAiCharacterItem()
      .from(aiCharacter)
      .where(aiCharacter.status.ne(StatusType.DELETED))
      .fetch()
  }

  // patch(), delete()는 BaseCrudService에서 상속

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoAiCharacterItem(full: Boolean = false): JPAQuery<BoAiCharacterItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoAiCharacterItem::class.java,
            aiCharacter.id.`as`("id"),
            aiCharacter.type.`as`("type"),
            aiCharacter.status.`as`("status"),
            aiCharacter.tiny.`as`("tiny"),
            aiCharacter.small.`as`("small"),
            aiCharacter.medium.`as`("medium"),
            aiCharacter.large.`as`("large"),
            aiCharacter.fullText.`as`("fullText"),
            aiCharacter.name.`as`("name"),
            aiCharacter.createdAt.`as`("createdAt"),
            aiCharacter.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoAiCharacterItem::class.java,
            aiCharacter.id.`as`("id"),
            aiCharacter.status.`as`("status"),
            aiCharacter.tiny.`as`("tiny"),
            aiCharacter.type.`as`("type"),
            aiCharacter.name.`as`("name"),
            aiCharacter.createdAt.`as`("createdAt"),
          )
        )
  }
}

