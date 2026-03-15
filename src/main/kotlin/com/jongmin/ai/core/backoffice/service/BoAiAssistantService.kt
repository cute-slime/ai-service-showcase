package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiAssistantRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAssistant
import com.jongmin.ai.core.backoffice.dto.response.BoAiAssistantItem
import com.jongmin.ai.core.platform.entity.AiAssistant
import com.jongmin.ai.core.platform.entity.QAiApiKey.aiApiKey
import com.jongmin.ai.core.platform.entity.QAiAssistant.aiAssistant
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
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
 * AI 어시스턴트 백오피스 서비스
 *
 * BaseCrudService 상속: delete 공통 처리
 * patch는 특별한 검증 로직이 있어 override
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiAssistantService(
  private val objectMapper: ObjectMapper,
  private val dependencyGuardService: BoAiDependencyGuardService,
  repository: AiAssistantRepository,
  queryFactory: JPAQueryFactory,
) : BaseCrudService<AiAssistant, BoAiAssistantItem, AiAssistantRepository>(repository, queryFactory
) {
  override val entityName: String = "AiAssistant"

  override val patchExcludes: Array<String>
    get() = arrayOf("id", "createdAt", "updatedAt", "accountId", "ownerId", "lastUsedAt")

  fun verifyModelAndKey(modelId: Long, apiKeyId: Long) {
    val exists = queryFactory.selectOne()
      .from(aiProvider)
      .join(aiModel).on(aiModel.aiProviderId.eq(aiProvider.id))
      .join(aiApiKey).on(aiApiKey.aiProviderId.eq(aiProvider.id))
      .where(
        aiModel.id.eq(modelId).and(aiApiKey.id.eq(apiKeyId))
          .and(
            aiModel.status.ne(StatusType.DELETED)
              .and(aiApiKey.status.ne(StatusType.DELETED).and(aiProvider.status.ne(StatusType.DELETED)))
          )
      )
      .fetchFirst() != null

    if (!exists) {
      throw ObjectNotFoundException("Model or API Key not found.")
    }
  }

  @Transactional
  fun create(session: JSession, dto: CreateAiAssistant): BoAiAssistantItem {
    kLogger.info { "(BO) AI 어시스턴트 생성 - admin: ${session.username}(${session.accountId})" }

    verifyModelAndKey(dto.primaryModelId!!, dto.primaryApiKeyId!!)
    dto.modelId = dto.primaryModelId
    dto.apiKeyId = dto.primaryApiKeyId
    dto.accountId = -1
    dto.ownerId = -1
    val entity = objectMapper.convert(dto, AiAssistant::class.java)
    entity.responseFormat = if (entity.responseFormat == "") "TEXT" else entity.responseFormat
    repository.save(entity)
    return findById(entity.id)
  }

  override fun findById(id: Long): BoAiAssistantItem {
    kLogger.debug { "(BO) AI 어시스턴트 조회 - id: $id" }
    return selectBoAiAssistantItem(true)
      .from(aiAssistant)
      .join(aiModel).on(aiModel.id.eq(aiAssistant.modelId))
      .join(aiProvider).on(aiProvider.id.eq(aiModel.aiProviderId))
      .where(aiAssistant.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Assistant not found.")
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiAssistantItem> {
    kLogger.debug { "(BO) AI 어시스턴트 목록 조회 - statuses: $statuses, q: $q, pageable: $pageable" }

    val predicate = buildSearchPredicate(
      aiAssistant.status, StatusType.DELETED, statuses, q,
      aiAssistant.name, aiAssistant.description, aiAssistant.type.stringValue()
    )

    val countQuery = queryFactory
      .select(aiAssistant.count())
      .from(aiAssistant)
      .join(aiModel).on(aiModel.id.eq(aiAssistant.modelId))
      .where(predicate)

    return selectBoAiAssistantItem()
      .from(aiAssistant)
      .join(aiModel).on(aiModel.id.eq(aiAssistant.modelId))
      .join(aiProvider).on(aiProvider.id.eq(aiModel.aiProviderId))
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  fun findAllForPlayground(): List<BoAiAssistantItem> {
    val items: List<BoAiAssistantItem> = selectBoAiAssistantItem()
      .from(aiAssistant)
      .join(aiModel).on(aiModel.id.eq(aiAssistant.modelId))
      .join(aiProvider).on(aiProvider.id.eq(aiModel.aiProviderId))
      .where(aiAssistant.status.ne(StatusType.DELETED))
      .orderBy(aiAssistant.name.asc())
      .fetch()

    return items.filter { it.status == StatusType.ACTIVE }
  }

  @Transactional
  override fun patch(session: JSession, data: Map<String, Any?>): Map<String, Any?> {
    kLogger.info { "(BO) AI 어시스턴트 패치 - data: $data, admin: ${session.username}(${session.accountId})" }

    val target = repository.findById(data["id"] as Long).orElseThrow { ObjectNotFoundException("Assistant not found.") }

    // Map을 mutable로 변환하여 수정 가능하게 함
    val mutableData = data.toMutableMap()

    if (data.containsKey("primaryModelId") || data.containsKey("primaryApiKeyId")) {
      val newPrimaryModelId = data["primaryModelId"] as? Long ?: target.primaryModelId
      val newPrimaryApiKeyId = data["primaryApiKeyId"] as? Long ?: target.primaryApiKeyId
      verifyModelAndKey(newPrimaryModelId, newPrimaryApiKeyId)
      mutableData["modelId"] = newPrimaryModelId
      mutableData["apiKeyId"] = newPrimaryApiKeyId
    }

    return merge(mutableData, target, "id", "createdAt", "updatedAt", "accountId", "ownerId", "lastUsedAt")
  }


  @Transactional
  override fun delete(session: JSession, id: Long): Boolean {
    dependencyGuardService.assertAssistantDeletable(id)
    return super.delete(session, id)
  }

  /**
   * AI 어시스턴트 일괄 삭제 (Soft Delete)
   *
   * Partial Success 방식: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
   *
   * @param session 관리자 세션
   * @param ids 삭제할 AI 어시스턴트 ID 목록
   * @return 삭제 결과 (삭제 개수, 성공/실패 ID 목록)
   */
  @Transactional
  fun bulkDelete(session: JSession, ids: List<Long>): com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult {
    kLogger.info { "(BO) AI 어시스턴트 일괄 삭제 - ids: $ids, admin: ${session.username}(${session.accountId})" }

    if (ids.isEmpty()) {
      throw IllegalArgumentException("삭제할 ID가 제공되지 않았습니다.")
    }
    if (ids.size > 100) {
      throw IllegalArgumentException("한 번에 최대 100개까지만 삭제할 수 있습니다. 요청 개수: ${ids.size}")
    }

    // 존재하면서 삭제되지 않은 어시스턴트만 조회
    val existingAssistants = repository.findAllById(ids).filter { it.status != StatusType.DELETED }
    val existingIds = existingAssistants.map { it.id }
    val failedIds = ids - existingIds.toSet()

    if (failedIds.isNotEmpty()) {
      kLogger.warn { "(BO) AI 어시스턴트 일괄 삭제 - 존재하지 않거나 이미 삭제된 ID 무시: $failedIds" }
    }

    existingIds.forEach { assistantId ->
      dependencyGuardService.assertAssistantDeletable(assistantId)
    }

    // Soft Delete 처리
    existingAssistants.forEach { it.status = StatusType.DELETED }

    kLogger.info { "(BO) AI 어시스턴트 일괄 삭제 완료 - 삭제: ${existingIds.size}개, 실패: ${failedIds.size}개" }
    return com.jongmin.ai.core.backoffice.dto.response.BulkDeleteResult(
      deletedCount = existingIds.size,
      deletedIds = existingIds,
      failedIds = failedIds
    )
  }

  // :--------------------- Utility Methods ---------------------:

  private fun selectBoAiAssistantItem(full: Boolean = false): JPAQuery<BoAiAssistantItem> {
    return if (full)
      queryFactory
        .select(
          Projections.bean(
            BoAiAssistantItem::class.java,
            aiAssistant.id.`as`("id"),
            aiAssistant.name.`as`("name"),
            aiAssistant.type.`as`("type"),
            aiAssistant.description.`as`("description"),
            aiProvider.name.`as`("provider"),
            aiAssistant.modelId.`as`("modelId"),
            aiAssistant.apiKeyId.`as`("apiKeyId"),
            aiAssistant.primaryModelId.`as`("primaryModelId"),
            aiAssistant.primaryApiKeyId.`as`("primaryApiKeyId"),
            aiAssistant.restoreModelId.`as`("restoreModelId"),
            aiAssistant.restoreApiKeyId.`as`("restoreApiKeyId"),
            aiAssistant.instructions.`as`("instructions"),
            aiAssistant.temperature.`as`("temperature"),
            aiAssistant.topP.`as`("topP"),
            aiAssistant.responseFormat.`as`("responseFormat"),
            aiAssistant.maxTokens.`as`("maxTokens"),
            aiAssistant.lastUsedAt.`as`("lastUsedAt"),
            aiAssistant.status.`as`("status"),
            aiAssistant.createdAt.`as`("createdAt"),
            aiAssistant.updatedAt.`as`("updatedAt"),
          )
        )
    else
      queryFactory
        .select(
          Projections.bean(
            BoAiAssistantItem::class.java,
            aiAssistant.id.`as`("id"),
            aiAssistant.name.`as`("name"),
            aiAssistant.type.`as`("type"),
            aiProvider.name.`as`("provider"),
            aiAssistant.modelId.`as`("modelId"),
            aiModel.name.`as`("model"),
            aiAssistant.temperature.`as`("temperature"),
            aiAssistant.topP.`as`("topP"),
            aiAssistant.responseFormat.`as`("responseFormat"),
            aiAssistant.maxTokens.`as`("maxTokens"),
            aiAssistant.lastUsedAt.`as`("lastUsedAt"),
            aiAssistant.status.`as`("status"),
            aiAssistant.createdAt.`as`("createdAt"),
          )
        )
  }
}


