package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.service.BaseCrudService
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiModelItem
import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateAiModel
import com.jongmin.ai.core.backoffice.dto.response.BoAiModelResponseDto
import com.jongmin.ai.core.platform.entity.AiModel
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * AI 모델 백오피스 서비스
 *
 * BaseCrudService 상속: patch, delete 공통 처리
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiModelService(
  private val objectMapper: ObjectMapper,
  private val dependencyGuardService: BoAiDependencyGuardService,
  repository: AiModelRepository,
  queryFactory: JPAQueryFactory
) : BaseCrudService<AiModel, BoAiModelResponseDto, AiModelRepository>(repository, queryFactory
) {
  override val entityName: String = "AiModel"

  @Transactional
  fun create(session: JSession, dto: CreateAiModel): BoAiModelResponseDto {
    kLogger.info { "(BO) AIModel 생성 - name: ${dto.name}, admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, AiModel::class.java)
    return findById(repository.save(entity).id)
  }

  override fun findById(id: Long): BoAiModelResponseDto {
    kLogger.debug { "(BO) AIModel 단건 - id: $id" }

    return queryFactory
      .select(selectBoAiModelResponseDto())
      .from(aiModel)
      .join(aiProvider).on(aiModel.aiProviderId.eq(aiProvider.id))
      .where(aiModel.id.eq(id).and(aiModel.status.ne(StatusType.DELETED)).and(aiProvider.status.ne(StatusType.DELETED)))
      .fetchOne() ?: throw ObjectNotFoundException("Model not found.")
  }

  fun findAll(): List<AiModelItem> {
    return queryFactory
      .select(
        Projections.constructor(
          AiModelItem::class.java,
          aiModel.id.`as`("id"),
          aiModel.name.`as`("name"),
          aiModel.supportsReasoning.`as`("supportsReasoning"),
          aiModel.reasoningEffort.`as`("reasoningEffort")
        )
      )
      .from(aiModel)
      .orderBy(aiModel.name.asc())
      .fetch()
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiModelResponseDto> {
    kLogger.debug { "(BO) AIModel 목록 조회, q: $q, page: $pageable" }

    val predicate = buildSearchPredicate(aiModel.status, StatusType.DELETED, statuses, q, aiModel.name)
      .and(aiProvider.status.ne(StatusType.DELETED))

    val countQuery = queryFactory
      .select(aiModel.count())
      .from(aiModel)
      .join(aiProvider).on(aiModel.aiProviderId.eq(aiProvider.id))
      .where(predicate)

    return queryFactory
      .select(selectBoAiModelResponseDto())
      .from(aiModel)
      .join(aiProvider).on(aiModel.aiProviderId.eq(aiProvider.id))
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  @Transactional
  override fun patch(session: JSession, data: Map<String, Any?>): Map<String, Any?> {
    val id = data["id"] as Long
    val target = repository.findById(id).orElseThrow { ObjectNotFoundException("Model not found.") }

    val requestedName = (data["name"] as? String)?.trim()
    if (requestedName != null && requestedName.isBlank()) {
      throw BadRequestException("AI 모델명은 비어 있을 수 없습니다.")
    }

    val nextName = requestedName ?: target.name
    val nextReasoningEffort = (data["reasoningEffort"] as? ReasoningEffort) ?: target.reasoningEffort
    validateDuplicateModelName(
      targetId = target.id,
      aiProviderId = target.aiProviderId,
      name = nextName,
      reasoningEffort = nextReasoningEffort
    )

    val mutableData = data.toMutableMap()
    if (requestedName == null) {
      mutableData.remove("name")
    } else {
      mutableData["name"] = requestedName
    }

    return super.patch(session, mutableData)
  }

  @Transactional
  override fun delete(session: JSession, id: Long): Boolean {
    dependencyGuardService.assertModelDeletable(id)
    return super.delete(session, id)
  }

  // :--------Utility Functions--------:

  private fun validateDuplicateModelName(
    targetId: Long,
    aiProviderId: Long,
    name: String,
    reasoningEffort: ReasoningEffort?
  ) {
    val reasoningPredicate = reasoningEffort?.let { aiModel.reasoningEffort.eq(it) } ?: aiModel.reasoningEffort.isNull

    val duplicated = queryFactory
      .selectOne()
      .from(aiModel)
      .where(
        aiModel.id.ne(targetId)
          .and(aiModel.aiProviderId.eq(aiProviderId))
          .and(aiModel.name.eq(name))
          .and(reasoningPredicate)
      )
      .fetchFirst() != null

    if (duplicated) {
      throw BadRequestException("이미 존재하는 AI 모델명입니다: $name")
    }
  }

  private fun selectBoAiModelResponseDto() = Projections.bean(
    BoAiModelResponseDto::class.java,
    aiModel.id.`as`("id"),
    aiModel.name.`as`("name"),
    aiModel.aiProviderId.`as`("aiProviderId"),
    aiProvider.name.`as`("aiProvider"),
    aiModel.supportsReasoning.`as`("supportsReasoning"),
    aiModel.reasoningEffort.`as`("reasoningEffort"),
    aiModel.type.`as`("type"),
    aiModel.description.`as`("description"),
    aiModel.maxTokens.`as`("maxTokens"),
    aiModel.inputTokenPrice.`as`("inputTokenPrice"),
    aiModel.outputTokenPrice.`as`("outputTokenPrice"),
    aiModel.inputTokenPriceInService.`as`("inputTokenPriceInService"),
    aiModel.outputTokenPriceInService.`as`("outputTokenPriceInService"),
    aiModel.lastUsedAt.`as`("lastUsedAt"),
    aiModel.status.`as`("status"),
    aiModel.createdAt.`as`("createdAt"),
    aiModel.updatedAt.`as`("updatedAt"),
  )
}


