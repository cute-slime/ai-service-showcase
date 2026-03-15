package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiThreadRepository
import com.jongmin.ai.core.backoffice.dto.request.BoCreateThread
import com.jongmin.ai.core.backoffice.dto.response.BoAiThreadItem
import com.jongmin.ai.core.platform.entity.AiThread
import com.jongmin.ai.core.platform.entity.QAiThread.aiThread
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
 * AI 스레드 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiThreadService(
  private val objectMapper: ObjectMapper,
  repository: AiThreadRepository,
  queryFactory: JPAQueryFactory
) : BaseCrudService<AiThread, BoAiThreadItem, AiThreadRepository>(repository, queryFactory
) {
  override val entityName: String = "AiThread"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId")

  @Transactional
  fun create(session: JSession, dto: BoCreateThread): BoAiThreadItem {
    kLogger.info { "(BO) AI 스레드 생성 - admin: ${session.username}(${session.accountId})" }
    dto.accountId = session.accountId
    dto.status = StatusType.ACTIVE
    val entity = objectMapper.convert(dto, AiThread::class.java)
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoAiThreadItem {
    kLogger.debug { "(BO) AI 스레드 조회 - id: $id" }
    return selectBoAiThreadItem(true)
      .from(aiThread)
      .where(aiThread.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Thread not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiThreadItem> {
    kLogger.debug { "(BO) AI 스레드 목록 조회" }

    val predicate = buildSearchPredicate(aiThread.status, StatusType.DELETED, statuses)
    val countQuery = queryFactory.select(aiThread.count()).from(aiThread).where(predicate)

    return selectBoAiThreadItem()
      .from(aiThread)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  // patch(), delete()는 BaseCrudService에서 상속

  /**
   * AI 스레드 일괄 삭제 (Soft Delete)
   *
   * Partial Success 방식: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
   *
   * @param session 관리자 세션
   * @param ids 삭제할 AI 스레드 ID 목록
   * @return 삭제 결과 (삭제 개수, 성공/실패 ID 목록)
   */
  @Transactional
  fun bulkDelete(session: JSession, ids: List<Long>): com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult {
    kLogger.info { "(BO) AI 스레드 일괄 삭제 - ids: $ids, admin: ${session.username}(${session.accountId})" }

    if (ids.isEmpty()) {
      throw IllegalArgumentException("삭제할 ID가 제공되지 않았습니다.")
    }
    if (ids.size > 100) {
      throw IllegalArgumentException("한 번에 최대 100개까지만 삭제할 수 있습니다. 요청 개수: ${ids.size}")
    }

    // 존재하면서 삭제되지 않은 스레드만 조회
    val existingThreads = repository.findAllById(ids).filter { it.status != StatusType.DELETED }
    val existingIds = existingThreads.map { it.id }
    val failedIds = ids - existingIds.toSet()

    if (failedIds.isNotEmpty()) {
      kLogger.warn { "(BO) AI 스레드 일괄 삭제 - 존재하지 않거나 이미 삭제된 ID 무시: $failedIds" }
    }

    // Soft Delete 처리
    existingThreads.forEach { it.status = StatusType.DELETED }

    kLogger.info { "(BO) AI 스레드 일괄 삭제 완료 - 삭제: ${existingIds.size}개, 실패: ${failedIds.size}개" }
    return com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult(
      deletedCount = existingIds.size,
      deletedIds = existingIds,
      failedIds = failedIds
    )
  }

  private fun selectBoAiThreadItem(full: Boolean = false): JPAQuery<BoAiThreadItem> {
    return queryFactory
      .select(
        Projections.bean(
          BoAiThreadItem::class.java,
          aiThread.id.`as`("id"),
          aiThread.accountId.`as`("accountId"),
          aiThread.title.`as`("title"),
          aiThread.totalInputToken.`as`("totalInputToken"),
          aiThread.totalOutputToken.`as`("totalOutputToken"),
          aiThread.totalInputTokenSpend.`as`("totalInputTokenSpend"),
          aiThread.totalOutputTokenSpend.`as`("totalOutputTokenSpend"),
          aiThread.lastUsedAt.`as`("lastUsedAt"),
          aiThread.status.`as`("status"),
          aiThread.createdAt.`as`("createdAt"),
          aiThread.updatedAt.`as`("updatedAt")
        )
      )
  }
}

