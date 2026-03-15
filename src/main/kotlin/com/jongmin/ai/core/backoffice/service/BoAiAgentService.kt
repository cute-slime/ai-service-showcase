package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiAgentRepository
import com.jongmin.ai.core.AiAgentType
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAgent
import com.jongmin.ai.core.backoffice.dto.response.BoAiAgentItem
import com.jongmin.ai.core.platform.entity.AiAgent
import com.jongmin.ai.core.platform.entity.QAiAgent.aiAgent
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
 * AI 에이전트 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiAgentService(
  private val objectMapper: ObjectMapper,
  aiAgentRepository: AiAgentRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<AiAgent, BoAiAgentItem, AiAgentRepository>(aiAgentRepository, queryFactory
) {
  override val entityName: String = "AiAgent"

  // 패치 시 수정 불가능한 필드들
  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId", "lastUsedAt")

  @Transactional
  fun create(session: JSession, dto: CreateAiAgent): BoAiAgentItem {
    kLogger.info { "(BO) AI 에이전트 생성 - admin: ${session.username}(${session.accountId})" }
    dto.accountId = -1
    dto.ownerId = -1
    // XXX 정상 처리 필요
    dto.type = AiAgentType.CUSTOM
    val entity = objectMapper.convert(dto, AiAgent::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoAiAgentItem {
    kLogger.debug { "(BO) AI 에이전트 조회 - id: $id" }

    return selectBoAiAgentItem(true)
      .from(aiAgent)
      .where(aiAgent.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Agent not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiAgentItem> {
    kLogger.debug { "(BO) AI 에이전트 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(aiAgent.status, StatusType.DELETED, statuses)
    val countQuery = queryFactory.select(aiAgent.count()).from(aiAgent).where(predicate)

    return selectBoAiAgentItem()
      .from(aiAgent)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  /**
   * AI 에이전트 일괄 삭제 (Soft Delete)
   *
   * Partial Success 방식: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
   *
   * @param session 관리자 세션
   * @param ids 삭제할 AI 에이전트 ID 목록
   * @return 삭제 결과 (삭제 개수, 성공/실패 ID 목록)
   */
  @Transactional
  fun bulkDelete(session: JSession, ids: List<Long>): com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult {
    kLogger.info { "(BO) AI 에이전트 일괄 삭제 - ids: $ids, admin: ${session.username}(${session.accountId})" }

    if (ids.isEmpty()) {
      throw IllegalArgumentException("삭제할 ID가 제공되지 않았습니다.")
    }
    if (ids.size > 100) {
      throw IllegalArgumentException("한 번에 최대 100개까지만 삭제할 수 있습니다. 요청 개수: ${ids.size}")
    }

    // 존재하면서 삭제되지 않은 에이전트만 조회
    val existingAgents = repository.findAllById(ids).filter { it.status != StatusType.DELETED }
    val existingIds = existingAgents.map { it.id }
    val failedIds = ids - existingIds.toSet()

    if (failedIds.isNotEmpty()) {
      kLogger.warn { "(BO) AI 에이전트 일괄 삭제 - 존재하지 않거나 이미 삭제된 ID 무시: $failedIds" }
    }

    // Soft Delete 처리
    existingAgents.forEach { it.status = StatusType.DELETED }

    kLogger.info { "(BO) AI 에이전트 일괄 삭제 완료 - 삭제: ${existingIds.size}개, 실패: ${failedIds.size}개" }
    return com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult(
      deletedCount = existingIds.size,
      deletedIds = existingIds,
      failedIds = failedIds
    )
  }

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoAiAgentItem(full: Boolean = false): JPAQuery<BoAiAgentItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoAiAgentItem::class.java,
            aiAgent.id.`as`("id"),
            aiAgent.name.`as`("name"),
            aiAgent.type.`as`("type"),
            aiAgent.workflow.`as`("workflow"),
            aiAgent.description.`as`("description"),
            aiAgent.lastUsedAt.`as`("lastUsedAt"),
            aiAgent.status.`as`("status"),
            aiAgent.createdAt.`as`("createdAt"),
            aiAgent.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoAiAgentItem::class.java,
            aiAgent.id.`as`("id"),
            aiAgent.name.`as`("name"),
            aiAgent.type.`as`("type"),
            aiAgent.lastUsedAt.`as`("lastUsedAt"),
            aiAgent.status.`as`("status"),
            aiAgent.createdAt.`as`("createdAt"),
          )
        )
  }
}


