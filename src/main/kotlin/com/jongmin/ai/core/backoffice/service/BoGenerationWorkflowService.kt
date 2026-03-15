package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowFormat
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.core.GenerationWorkflowRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.request.PatchGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationWorkflow
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationWorkflowListItem
import com.jongmin.ai.core.backoffice.dto.response.BoGenerationWorkflowVariable
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaProvider
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaWorkflow
import com.jongmin.ai.core.platform.entity.multimedia.QMultimediaProvider.multimediaProvider
import com.jongmin.ai.core.platform.entity.multimedia.QMultimediaWorkflow.multimediaWorkflow
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.entity.JSession
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * (백오피스) 멀티미디어 워크플로우 관리 서비스
 *
 * providerId를 기준으로 multimedia_provider와 의존 관계를 유지하며,
 * 공급사별 워크플로우 JSON payload를 CRUD한다.
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoGenerationWorkflowService(
  private val objectMapper: ObjectMapper,
  private val workflowRepository: GenerationWorkflowRepository,
  private val providerRepository: GenerationProviderRepository,
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(session: JSession, dto: CreateGenerationWorkflow): BoGenerationWorkflow {
    kLogger.info {
      "(BO) MultimediaWorkflow 생성 - providerId: ${dto.providerId}, mediaType: ${dto.mediaType}, admin: ${session.username}"
    }

    val provider = findProvider(dto.providerId)
    val trimmedName = dto.name.trim()

    if (
      workflowRepository.existsByProviderIdAndMediaTypeAndNameAndPipelineAndVersion(
        dto.providerId,
        dto.mediaType,
        trimmedName,
        dto.pipeline,
        dto.version
      )
    ) {
      throw IllegalArgumentException("동일 provider/mediaType/pipeline/name/version 조합의 워크플로우가 이미 존재합니다.")
    }

    if (dto.isDefault) {
      clearDefaultWorkflow(dto.providerId, dto.mediaType, dto.pipeline, null)
    }

    val entity = MultimediaWorkflow(
      providerId = dto.providerId,
      mediaType = dto.mediaType,
      name = trimmedName,
      pipeline = dto.pipeline,
      description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
      format = dto.format,
      payload = objectMapper.writeValueAsString(dto.payload),
      variables = objectMapper.writeValueAsString(dto.variables),
      version = dto.version,
      isDefault = dto.isDefault,
      status = dto.status,
    )

    return toDetail(workflowRepository.save(entity), provider)
  }

  fun findById(id: Long): BoGenerationWorkflow {
    val entity = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("워크플로우를 찾을 수 없습니다: $id") }
    val provider = findProvider(entity.providerId)
    return toDetail(entity, provider)
  }

  fun findAll(
    statuses: Set<GenerationWorkflowStatus>?,
    providerCodes: Set<String>?,
    mediaTypes: Set<GenerationMediaType>?,
    pipelines: Set<GenerationWorkflowPipeline>?,
    formats: Set<GenerationWorkflowFormat>?,
    q: String?,
    pageable: Pageable
  ): Page<BoGenerationWorkflowListItem> {
    val where = BooleanBuilder()

    statuses?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaWorkflow.status.`in`(it)) }
    mediaTypes?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaWorkflow.mediaType.`in`(it)) }
    pipelines?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaWorkflow.pipeline.`in`(it)) }
    formats?.takeIf { it.isNotEmpty() }?.let { where.and(multimediaWorkflow.format.`in`(it)) }
    providerCodes?.takeIf { it.isNotEmpty() }?.let {
      val normalizedCodes = it.map { code -> code.trim().uppercase() }.filter { code -> code.isNotBlank() }
      if (normalizedCodes.isNotEmpty()) {
        where.and(multimediaProvider.code.upper().`in`(normalizedCodes))
      }
    }
    q?.takeIf { it.isNotBlank() }?.let {
      where.and(
        multimediaWorkflow.name.containsIgnoreCase(it)
          .or(multimediaWorkflow.description.containsIgnoreCase(it))
          .or(multimediaProvider.code.containsIgnoreCase(it))
          .or(multimediaProvider.name.containsIgnoreCase(it))
      )
    }

    val total = queryFactory
      .select(multimediaWorkflow.id.count())
      .from(multimediaWorkflow)
      .leftJoin(multimediaProvider).on(multimediaProvider.id.eq(multimediaWorkflow.providerId))
      .where(where)
      .fetchOne() ?: 0L

    val items = queryFactory
      .select(multimediaWorkflow)
      .from(multimediaWorkflow)
      .leftJoin(multimediaProvider).on(multimediaProvider.id.eq(multimediaWorkflow.providerId))
      .where(where)
      .orderBy(*buildOrderSpecifiers(pageable.sort))
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()

    val providersById = providerRepository.findAllById(items.map { entity -> entity.providerId }.distinct())
      .associateBy { provider -> provider.id }

    val responseItems = items.map { entity ->
      val provider = providersById[entity.providerId]
      val variableCount = parseVariables(entity.variables).size
      BoGenerationWorkflowListItem(
        id = entity.id,
        providerId = entity.providerId,
        providerCode = provider?.code ?: "UNKNOWN",
        providerName = provider?.name ?: "Unknown Provider",
        mediaType = entity.mediaType,
        name = entity.name,
        pipeline = entity.pipeline,
        description = entity.description,
        format = entity.format,
        version = entity.version,
        isDefault = entity.isDefault,
        status = entity.status,
        variableCount = variableCount,
        payloadSize = entity.payload.length,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
    }

    return PageImpl(responseItems, pageable, total)
  }

  @Transactional
  fun patch(session: JSession, id: Long, dto: PatchGenerationWorkflow): BoGenerationWorkflow {
    kLogger.info { "(BO) MultimediaWorkflow 수정 - id: $id, admin: ${session.username}" }

    val entity = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("워크플로우를 찾을 수 없습니다: $id") }

    dto.providerId?.let {
      findProvider(it)
      entity.providerId = it
    }
    dto.mediaType?.let { entity.mediaType = it }
    dto.name?.let { entity.name = it.trim() }
    dto.pipeline?.let { entity.pipeline = it }
    dto.description?.let { description ->
      entity.description = description.trim().takeIf { value -> value.isNotEmpty() }
    }
    dto.format?.let { entity.format = it }
    dto.payload?.let { entity.payload = objectMapper.writeValueAsString(it) }
    dto.variables?.let { entity.variables = objectMapper.writeValueAsString(it) }
    dto.version?.let { entity.version = it }
    dto.status?.let { entity.status = it }
    dto.isDefault?.let { entity.isDefault = it }

    if (entity.isDefault) {
      clearDefaultWorkflow(entity.providerId, entity.mediaType, entity.pipeline, entity.id)
    }

    val saved = workflowRepository.save(entity)
    val provider = findProvider(saved.providerId)
    return toDetail(saved, provider)
  }

  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    kLogger.info { "(BO) MultimediaWorkflow 삭제 - id: $id, admin: ${session.username}" }

    val entity = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("워크플로우를 찾을 수 없습니다: $id") }
    workflowRepository.delete(entity)
    return true
  }

  private fun findProvider(providerId: Long): MultimediaProvider {
    return providerRepository.findById(providerId)
      .orElseThrow { ObjectNotFoundException("프로바이더를 찾을 수 없습니다: $providerId") }
  }

  private fun toDetail(entity: MultimediaWorkflow, provider: MultimediaProvider): BoGenerationWorkflow {
    return BoGenerationWorkflow(
      id = entity.id,
      providerId = entity.providerId,
      providerCode = provider.code,
      providerName = provider.name,
      mediaType = entity.mediaType,
      name = entity.name,
      pipeline = entity.pipeline,
      description = entity.description,
      format = entity.format,
      payload = parsePayload(entity.payload),
      variables = parseVariables(entity.variables),
      version = entity.version,
      isDefault = entity.isDefault,
      status = entity.status,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }

  @Transactional
  private fun clearDefaultWorkflow(
    providerId: Long,
    mediaType: GenerationMediaType,
    pipeline: GenerationWorkflowPipeline,
    excludeId: Long?
  ) {
    val existing = workflowRepository.findByProviderIdAndMediaTypeAndIsDefaultTrue(
      providerId,
      mediaType,
      pipeline
    )
    if (existing != null && existing.id != excludeId) {
      existing.isDefault = false
    }
  }

  private fun parsePayload(json: String): Map<String, Any> {
    return try {
      @Suppress("UNCHECKED_CAST")
      objectMapper.readValue(json, Map::class.java) as? Map<String, Any> ?: emptyMap()
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun parseVariables(json: String): List<BoGenerationWorkflowVariable> {
    return try {
      val rawList = objectMapper.readValue(json, object : TypeReference<List<Map<String, Any?>>>() {})
      rawList.map { row ->
        BoGenerationWorkflowVariable(
          key = row["key"]?.toString().orEmpty(),
          type = runCatching {
            com.jongmin.ai.core.GenerationWorkflowVariableType.valueOf(row["type"]?.toString().orEmpty())
          }.getOrDefault(com.jongmin.ai.core.GenerationWorkflowVariableType.STRING),
          required = row["required"] as? Boolean ?: false,
          description = row["description"]?.toString(),
          defaultValue = row["defaultValue"],
        )
      }.filter { variable -> variable.key.isNotBlank() }
    } catch (_: Exception) {
      emptyList()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun buildOrderSpecifiers(sort: Sort): Array<OrderSpecifier<*>> {
    if (sort.isEmpty) {
      return arrayOf(multimediaWorkflow.updatedAt.desc(), multimediaWorkflow.id.desc())
    }

    return sort.toList().map { sortOrder ->
      val path: Expression<out Comparable<*>> = when (sortOrder.property) {
        "id" -> multimediaWorkflow.id
        "name" -> multimediaWorkflow.name
        "providerCode" -> multimediaProvider.code
        "providerName" -> multimediaProvider.name
        "mediaType" -> multimediaWorkflow.mediaType
        "pipeline" -> multimediaWorkflow.pipeline
        "format" -> multimediaWorkflow.format
        "version" -> multimediaWorkflow.version
        "status" -> multimediaWorkflow.status
        "isDefault" -> multimediaWorkflow.isDefault
        "createdAt" -> multimediaWorkflow.createdAt
        "updatedAt" -> multimediaWorkflow.updatedAt
        else -> multimediaWorkflow.id
      }

      if (sortOrder.isAscending) {
        OrderSpecifier(Order.ASC, path as Expression<Comparable<*>>)
      } else {
        OrderSpecifier(Order.DESC, path as Expression<Comparable<*>>)
      }
    }.toTypedArray()
  }
}
