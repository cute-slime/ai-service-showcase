package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.core.*
import com.jongmin.ai.core.backoffice.dto.request.*
import com.jongmin.ai.core.backoffice.dto.response.*
import com.jongmin.ai.core.platform.entity.multimedia.*
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

/**
 * (백오피스) AI 미디어 생성 프로바이더 관리 서비스
 *
 * Provider + ApiConfig CRUD 및 목록/상세 조회 오케스트레이션
 *
 * @author Claude Code
 * @since 2026.01.10
 * @modified 2026.02.19 Model/Preset CRUD 분리 (SRP 개선)
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGenerationProviderService(
  private val providerRepository: GenerationProviderRepository,
  private val apiConfigRepository: GenerationProviderApiConfigRepository,
  private val queryFactory: JPAQueryFactory,
  private val boGenerationProviderModelService: BoGenerationProviderModelService,
) {
  private val kLogger = KotlinLogging.logger {}

  // ========== Provider CRUD ==========

  @Transactional
  fun createProvider(session: JSession, dto: CreateGenerationProvider): CreateGenerationProviderResult {
    kLogger.info { "(BO) MultimediaProvider 생성 요청 - code: ${dto.code}, admin: ${session.username}" }

    val provider = MultimediaProvider(
      code = dto.code,
      name = dto.name,
      description = dto.description,
      status = dto.status,
      logoUrl = dto.logoUrl,
      websiteUrl = dto.websiteUrl,
      sortOrder = dto.sortOrder,
    )
    val saved = providerRepository.save(provider)

    dto.apiConfig?.let { config ->
      val apiConfig = MultimediaProviderApiConfig(
        providerId = saved.id,
        authType = config.authType,
        authHeaderName = config.authHeaderName,
        authValuePrefix = config.authValuePrefix,
        baseUrl = config.baseUrl,
        rateLimitPerMinute = config.rateLimitPerMinute,
        rateLimitPerDay = config.rateLimitPerDay,
        concurrentLimit = config.concurrentLimit,
        connectTimeoutMs = config.connectTimeoutMs,
        readTimeoutMs = config.readTimeoutMs,
        configJson = config.configJson,
      )
      apiConfigRepository.save(apiConfig)
    }

    kLogger.info { "(BO) MultimediaProvider 생성 완료 - id: ${saved.id}, code: ${dto.code}, admin: ${session.username}" }
    return CreateGenerationProviderResult(true, saved.id, "프로바이더가 생성되었습니다.")
  }

  fun findProviderById(id: Long, includePresets: Boolean = false): BoGenerationProviderDetail {
    kLogger.debug { "(BO) MultimediaProvider 상세 조회 - id: $id, includePresets: $includePresets" }

    val provider = providerRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프로바이더를 찾을 수 없습니다: $id") }

    val apiConfig = apiConfigRepository.findByProviderId(id)
    val modelItems = boGenerationProviderModelService.findModelItemsForProvider(
      id, provider.code, provider.name, includePresets
    )

    return BoGenerationProviderDetail(
      id = provider.id,
      code = provider.code,
      name = provider.name,
      description = provider.description,
      status = provider.status,
      logoUrl = provider.logoUrl,
      websiteUrl = provider.websiteUrl,
      sortOrder = provider.sortOrder,
      apiConfig = apiConfig?.let { toApiConfigDto(it) },
      models = modelItems,
      createdAt = provider.createdAt,
      updatedAt = provider.updatedAt,
    )
  }

  fun findAllProviders(
    statuses: Set<GenerationProviderStatus>?,
    q: String?,
    includeModels: Boolean = false,
    includePresets: Boolean = false,
    pageable: Pageable
  ): Page<BoGenerationProviderItem> {
    kLogger.debug { "(BO) MultimediaProvider 목록 조회 - statuses: $statuses, q: $q, includeModels: $includeModels, includePresets: $includePresets" }

    val where = BooleanBuilder()
    statuses?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaProvider.status.`in`(it)) }
    q?.takeIf { it.isNotBlank() }?.let {
      where.and(
        multimediaProvider.code.containsIgnoreCase(it)
          .or(multimediaProvider.name.containsIgnoreCase(it))
      )
    }

    val query = queryFactory
      .select(
        Projections.constructor(
          BoGenerationProviderItem::class.java,
          multimediaProvider.id,
          multimediaProvider.code,
          multimediaProvider.name,
          multimediaProvider.description,
          multimediaProvider.status,
          multimediaProvider.logoUrl,
          multimediaProvider.websiteUrl,
          multimediaProvider.sortOrder,
          multimediaProviderModel.id.countDistinct().intValue(),
          multimediaProvider.createdAt,
          multimediaProvider.updatedAt,
        )
      )
      .from(multimediaProvider)
      .leftJoin(multimediaProviderModel).on(multimediaProviderModel.providerId.eq(multimediaProvider.id))
      .where(where)
      .groupBy(multimediaProvider.id)

    val total = queryFactory
      .select(multimediaProvider.id.countDistinct())
      .from(multimediaProvider)
      .where(where)
      .fetchOne() ?: 0L

    val items = query
      .orderBy(*buildProviderOrderSpecifiers(pageable.sort))
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()

    if (!includeModels) {
      return PageImpl(items, pageable, total)
    }

    // includeModels=true 시 모델 서비스에 위임
    val providerIds = items.map { it.id }
    if (providerIds.isEmpty()) {
      return PageImpl(items, pageable, total)
    }

    val providerMap = providerRepository.findAllById(providerIds).associateBy { it.id }
    val modelsByProviderId = boGenerationProviderModelService.findModelItemsForProviders(
      providerIds, providerMap, includePresets
    )

    val itemsWithModels = items.map { item ->
      item.copy(models = modelsByProviderId[item.id])
    }

    return PageImpl(itemsWithModels, pageable, total)
  }

  @Transactional
  fun patchProvider(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "(BO) MultimediaProvider 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val provider = providerRepository.findById(data["id"] as Long)
      .orElseThrow { ObjectNotFoundException("프로바이더를 찾을 수 없습니다: ${data["id"]}") }

    return merge(data, provider, "id", "code", "createdAt", "updatedAt")
  }

  @Transactional
  fun deleteProvider(session: JSession, id: Long) {
    kLogger.info { "(BO) MultimediaProvider 삭제 - id: $id, admin: ${session.username}" }

    val provider = providerRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프로바이더를 찾을 수 없습니다: $id") }

    // 자식 엔티티 삭제를 모델 서비스에 위임
    boGenerationProviderModelService.deleteAllByProviderId(id)
    apiConfigRepository.deleteByProviderId(id)
    providerRepository.delete(provider)
  }

  // ========== Provider API Config ==========

  @Transactional
  fun createOrUpdateApiConfig(
    session: JSession,
    providerId: Long,
    dto: CreateGenerationProviderApiConfig
  ): BoGenerationProviderApiConfigDto {
    kLogger.info { "(BO) MultimediaProviderApiConfig 생성/수정 - providerId: $providerId" }

    if (!providerRepository.existsById(providerId)) {
      throw ObjectNotFoundException("프로바이더를 찾을 수 없습니다: $providerId")
    }

    val existing = apiConfigRepository.findByProviderId(providerId)
    val config = existing ?: MultimediaProviderApiConfig(providerId = providerId, baseUrl = dto.baseUrl)

    config.authType = dto.authType
    config.authHeaderName = dto.authHeaderName
    config.authValuePrefix = dto.authValuePrefix
    config.baseUrl = dto.baseUrl
    config.rateLimitPerMinute = dto.rateLimitPerMinute
    config.rateLimitPerDay = dto.rateLimitPerDay
    config.concurrentLimit = dto.concurrentLimit
    config.connectTimeoutMs = dto.connectTimeoutMs
    config.readTimeoutMs = dto.readTimeoutMs
    config.configJson = dto.configJson

    return toApiConfigDto(apiConfigRepository.save(config))
  }

  // ========== Private Helper ==========

  @Suppress("UNCHECKED_CAST")
  private fun buildProviderOrderSpecifiers(sort: Sort): Array<OrderSpecifier<*>> {
    if (sort.isEmpty) {
      return arrayOf(multimediaProvider.sortOrder.asc(), multimediaProvider.id.desc())
    }

    return sort.map { sortOrder ->
      val path = when (sortOrder.property) {
        "id" -> multimediaProvider.id
        "code" -> multimediaProvider.code
        "name" -> multimediaProvider.name
        "status" -> multimediaProvider.status
        "sortOrder" -> multimediaProvider.sortOrder
        "createdAt" -> multimediaProvider.createdAt
        "updatedAt" -> multimediaProvider.updatedAt
        else -> multimediaProvider.id
      }
      if (sortOrder.isAscending) {
        OrderSpecifier(Order.ASC, path as com.querydsl.core.types.Expression<Comparable<*>>)
      } else {
        OrderSpecifier(Order.DESC, path as com.querydsl.core.types.Expression<Comparable<*>>)
      }
    }.toList().toTypedArray()
  }

  private fun toApiConfigDto(entity: MultimediaProviderApiConfig) = BoGenerationProviderApiConfigDto(
    id = entity.id,
    providerId = entity.providerId,
    authType = entity.authType,
    authHeaderName = entity.authHeaderName,
    authValuePrefix = entity.authValuePrefix,
    baseUrl = entity.baseUrl,
    rateLimitPerMinute = entity.rateLimitPerMinute,
    rateLimitPerDay = entity.rateLimitPerDay,
    concurrentLimit = entity.concurrentLimit,
    connectTimeoutMs = entity.connectTimeoutMs,
    readTimeoutMs = entity.readTimeoutMs,
    configJson = entity.configJson,
    createdAt = entity.createdAt,
    updatedAt = entity.updatedAt,
  )
}

