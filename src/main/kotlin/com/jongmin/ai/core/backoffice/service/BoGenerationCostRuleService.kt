package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.jspring.core.util.convert
import com.jongmin.ai.core.*
import com.jongmin.ai.core.backoffice.dto.request.CreateGenerationCostRule
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationCostRuleDetail
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationCostRuleItem
import com.jongmin.ai.core.backoffice.dto.response.CreateGenerationCostRuleResult
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaCostRule
import com.jongmin.ai.core.platform.entity.multimedia.QMultimediaCostRule.multimediaCostRule
import com.jongmin.ai.core.platform.entity.multimedia.QMultimediaProvider.multimediaProvider
import com.jongmin.ai.core.platform.entity.multimedia.QMultimediaProviderModel.multimediaProviderModel
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * (백오피스) AI 미디어 생성 비용 규칙 관리 서비스
 *
 * @author Claude Code
 * @since 2026.01.12
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGenerationCostRuleService(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val costRuleRepository: GenerationCostRuleRepository,
  private val providerRepository: GenerationProviderRepository,
  private val modelRepository: GenerationProviderModelRepository,
  private val presetRepository: GenerationModelPresetRepository,
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  // ========== 생성 ==========

  @Transactional
  fun create(session: JSession, dto: CreateGenerationCostRule): CreateGenerationCostRuleResult {
    kLogger.info { "(BO) MultimediaCostRule 생성 - mediaType: ${dto.mediaType}, admin: ${session.username}" }
    val entity = objectMapper.convert(dto, MultimediaCostRule::class.java)
    val saved = costRuleRepository.save(entity)

    return CreateGenerationCostRuleResult(true, saved.id, "비용 규칙이 생성되었습니다.")
  }

  // ========== 조회 (단건) ==========

  fun findById(id: Long): BoGenerationCostRuleDetail {
    kLogger.debug { "(BO) MultimediaCostRule 상세 조회 - id: $id" }

    val rule = costRuleRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("비용 규칙을 찾을 수 없습니다: $id") }

    // 연관 엔티티 정보 조회
    val model = rule.modelId?.let { modelRepository.findById(it).orElse(null) }
    val provider = rule.providerId?.let { providerRepository.findById(it).orElse(null) }
      ?: model?.providerId?.let { providerRepository.findById(it).orElse(null) }
    val preset = rule.presetId?.let { presetRepository.findById(it).orElse(null) }

    return BoGenerationCostRuleDetail(
      id = rule.id,
      name = rule.name,
      description = rule.description,
      mediaType = rule.mediaType,
      modelId = rule.modelId,
      modelName = model?.name,
      modelCode = model?.code,
      providerId = rule.providerId,
      providerName = provider?.name,
      providerCode = provider?.code,
      resolutionCode = rule.resolutionCode,
      qualityCode = rule.qualityCode,
      styleCode = rule.styleCode,
      durationSecFrom = rule.durationSecFrom,
      durationSecTo = rule.durationSecTo,
      presetId = rule.presetId,
      presetName = preset?.name,
      costPerUnit = rule.costPerUnit,
      costUnitType = rule.costUnitType,
      costCurrency = rule.costCurrency,
      priority = rule.priority,
      status = rule.status,
      createdAt = rule.createdAt,
      updatedAt = rule.updatedAt,
    )
  }

  // ========== 조회 (목록) ==========

  fun findAll(
    statuses: Set<StatusType>?,
    mediaTypes: Set<GenerationMediaType>?,
    modelId: Long?,
    providerId: Long?,
    q: String?,
    pageable: Pageable
  ): Page<BoGenerationCostRuleItem> {
    kLogger.debug { "(BO) MultimediaCostRule 목록 조회 - statuses: $statuses, mediaTypes: $mediaTypes" }

    val where = BooleanBuilder()

    // 상태 필터
    statuses?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaCostRule.status.`in`(it)) }

    // 미디어 타입 필터
    mediaTypes?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaCostRule.mediaType.`in`(it)) }

    // 모델 필터
    modelId?.let { where.and(multimediaCostRule.modelId.eq(it)) }

    // 프로바이더 필터
    providerId?.let { where.and(multimediaCostRule.providerId.eq(it)) }

    // 검색어 필터
    q?.takeIf { it.isNotBlank() }?.let {
      where.and(
        multimediaCostRule.name.containsIgnoreCase(it)
          .or(multimediaCostRule.description.containsIgnoreCase(it))
          .or(multimediaCostRule.resolutionCode.containsIgnoreCase(it))
          .or(multimediaCostRule.qualityCode.containsIgnoreCase(it))
      )
    }

    // 카운트 쿼리
    val total = queryFactory
      .select(multimediaCostRule.id.count())
      .from(multimediaCostRule)
      .where(where)
      .fetchOne() ?: 0L

    // 목록 쿼리 (모델/프로바이더 조인)
    val items = queryFactory
      .select(
        Projections.constructor(
          BoGenerationCostRuleItem::class.java,
          multimediaCostRule.id,
          multimediaCostRule.name,
          multimediaCostRule.description,
          multimediaCostRule.mediaType,
          multimediaCostRule.modelId,
          multimediaProviderModel.name, // modelName
          multimediaCostRule.providerId,
          multimediaProvider.name, // providerName
          multimediaCostRule.resolutionCode,
          multimediaCostRule.qualityCode,
          multimediaCostRule.styleCode,
          multimediaCostRule.durationSecFrom,
          multimediaCostRule.durationSecTo,
          multimediaCostRule.presetId,
          multimediaCostRule.costPerUnit,
          multimediaCostRule.costUnitType,
          multimediaCostRule.costCurrency,
          multimediaCostRule.priority,
          multimediaCostRule.status,
          multimediaCostRule.createdAt,
          multimediaCostRule.updatedAt,
        )
      )
      .from(multimediaCostRule)
      .leftJoin(multimediaProviderModel).on(multimediaProviderModel.id.eq(multimediaCostRule.modelId))
      .leftJoin(multimediaProvider).on(
        multimediaProvider.id.eq(multimediaCostRule.providerId)
          .or(multimediaProvider.id.eq(multimediaProviderModel.providerId))
      )
      .where(where)
      .orderBy(*buildOrderSpecifiers(pageable.sort))
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()

    return PageImpl(items, pageable, total)
  }

  // ========== 수정 ==========

  @Transactional
  fun patch(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "(BO) MultimediaCostRule 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val rule = costRuleRepository.findById(data["id"] as Long)
      .orElseThrow { ObjectNotFoundException("비용 규칙을 찾을 수 없습니다: ${data["id"]}") }

    return merge(data, rule, "id", "createdAt", "updatedAt")
  }

  // ========== 삭제 ==========

  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    kLogger.info { "(BO) MultimediaCostRule 삭제 - id: $id, admin: ${session.username}" }

    val rule = costRuleRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("비용 규칙을 찾을 수 없습니다: $id") }

    rule.status = StatusType.DELETED
    return true
  }

  // ========== 헬퍼 메서드 ==========

  /**
   * 정렬 조건 생성
   */
  @Suppress("UNCHECKED_CAST")
  private fun buildOrderSpecifiers(sort: Sort): Array<OrderSpecifier<*>> {
    if (sort.isEmpty) {
      return arrayOf(multimediaCostRule.priority.asc(), multimediaCostRule.id.desc())
    }

    return sort.map { sortOrder ->
      val path = when (sortOrder.property) {
        "id" -> multimediaCostRule.id
        "name" -> multimediaCostRule.name
        "mediaType" -> multimediaCostRule.mediaType
        "priority" -> multimediaCostRule.priority
        "costPerUnit" -> multimediaCostRule.costPerUnit
        "status" -> multimediaCostRule.status
        "createdAt" -> multimediaCostRule.createdAt
        "updatedAt" -> multimediaCostRule.updatedAt
        else -> multimediaCostRule.id
      }
      if (sortOrder.isAscending) {
        OrderSpecifier(Order.ASC, path as com.querydsl.core.types.Expression<Comparable<*>>)
      } else {
        OrderSpecifier(Order.DESC, path as com.querydsl.core.types.Expression<Comparable<*>>)
      }
    }.toList().toTypedArray()
  }
}


