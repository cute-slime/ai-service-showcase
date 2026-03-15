package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.PromptEnhancerProfileRepository
import com.jongmin.ai.core.backoffice.dto.request.CreatePromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.request.PatchPromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.request.PromptEnhancerLockedTemplatePayload
import com.jongmin.ai.core.backoffice.dto.response.BoPromptEnhancerLockedTemplate
import com.jongmin.ai.core.backoffice.dto.response.BoPromptEnhancerProfile
import com.jongmin.ai.core.backoffice.dto.response.BoPromptEnhancerProfileListItem
import com.jongmin.ai.core.platform.entity.multimedia.PromptEnhancerProfile
import com.jongmin.ai.core.platform.entity.multimedia.QPromptEnhancerProfile.promptEnhancerProfile
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.data.entity.StatusType
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * (백오피스) 프롬프트 인첸터 프로필 관리 서비스
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoPromptEnhancerProfileService(
  private val objectMapper: ObjectMapper,
  private val profileRepository: PromptEnhancerProfileRepository,
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  private val supportedProviderCodes = setOf("NOVELAI", "COMFYUI")

  @Transactional
  fun create(session: JSession, dto: CreatePromptEnhancerProfile): BoPromptEnhancerProfile {
    val providerCode = normalizeProviderCode(dto.providerCode)
    val name = dto.name.trim()
    val preferredArtistTags = normalizePreferredArtistTags(providerCode, dto.preferredArtistTags)

    kLogger.info {
      "(BO) PromptEnhancerProfile 생성 - provider: $providerCode, name: $name, admin: ${session.username}(${session.accountId})"
    }

    if (dto.isDefault) {
      clearDefault(providerCode, null)
    }

    val entity = PromptEnhancerProfile(
      providerCode = providerCode,
      name = name,
      description = dto.description?.trim()?.takeIf { it.isNotEmpty() },
      targetRule = normalizeTargetRule(dto.targetRule),
      priority = normalizePriority(dto.priority),
      preferredArtistTags = toJsonArray(preferredArtistTags),
      styleKeywords = toJsonArray(cleanKeywords(dto.styleKeywords)),
      vibeKeywords = toJsonArray(cleanKeywords(dto.vibeKeywords)),
      styleBlock = dto.lockedTemplate?.styleBlock?.trim()?.takeIf { it.isNotEmpty() },
      characterBlock = dto.lockedTemplate?.characterBlock?.trim()?.takeIf { it.isNotEmpty() },
      backgroundBlock = dto.lockedTemplate?.backgroundBlock?.trim()?.takeIf { it.isNotEmpty() },
      sampler = dto.lockedTemplate?.sampler?.trim()?.takeIf { it.isNotEmpty() },
      steps = dto.lockedTemplate?.steps,
      cfgScale = dto.lockedTemplate?.cfgScale,
      width = dto.lockedTemplate?.width,
      height = dto.lockedTemplate?.height,
      seed = dto.lockedTemplate?.seed,
      isDefault = dto.isDefault,
    )
    entity.status = dto.status

    return toDetail(profileRepository.save(entity))
  }

  fun findById(id: Long): BoPromptEnhancerProfile {
    val entity = profileRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프롬프트 인첸터 프로필을 찾을 수 없습니다: $id") }

    if (entity.status == StatusType.DELETED) {
      throw ObjectNotFoundException("프롬프트 인첸터 프로필을 찾을 수 없습니다: $id")
    }

    return toDetail(entity)
  }

  fun findAll(
    statuses: Set<StatusType>?,
    providerCodes: Set<String>?,
    q: String?,
    pageable: Pageable
  ): Page<BoPromptEnhancerProfileListItem> {
    val where = BooleanBuilder()
    where.and(promptEnhancerProfile.status.ne(StatusType.DELETED))

    statuses?.takeIf { it.isNotEmpty() }?.let { where.and(promptEnhancerProfile.status.`in`(it)) }
    providerCodes?.takeIf { it.isNotEmpty() }?.let {
      val normalizedCodes = it.map { code -> normalizeProviderCode(code) }
      where.and(promptEnhancerProfile.providerCode.`in`(normalizedCodes))
    }
    q?.takeIf { it.isNotBlank() }?.let {
      where.and(
        promptEnhancerProfile.name.containsIgnoreCase(it)
          .or(promptEnhancerProfile.description.containsIgnoreCase(it))
          .or(promptEnhancerProfile.providerCode.containsIgnoreCase(it))
          .or(promptEnhancerProfile.targetRule.containsIgnoreCase(it))
      )
    }

    val total = queryFactory
      .select(promptEnhancerProfile.id.count())
      .from(promptEnhancerProfile)
      .where(where)
      .fetchOne() ?: 0L

    val items = queryFactory
      .selectFrom(promptEnhancerProfile)
      .where(where)
      .orderBy(*buildOrderSpecifiers(pageable.sort))
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()
      .map { entity ->
        BoPromptEnhancerProfileListItem(
          id = entity.id,
          providerCode = entity.providerCode,
          name = entity.name,
          description = entity.description,
          targetRule = parseJsonNode(entity.targetRule),
          priority = entity.priority,
          preferredArtistTagCount = parseJsonArray(entity.preferredArtistTags).size,
          styleKeywordCount = parseJsonArray(entity.styleKeywords).size,
          vibeKeywordCount = parseJsonArray(entity.vibeKeywords).size,
          isDefault = entity.isDefault,
          status = entity.status,
          createdAt = entity.createdAt,
          updatedAt = entity.updatedAt,
        )
      }

    return PageImpl(items, pageable, total)
  }

  @Transactional
  fun patch(session: JSession, id: Long, dto: PatchPromptEnhancerProfile): BoPromptEnhancerProfile {
    val entity = profileRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프롬프트 인첸터 프로필을 찾을 수 없습니다: $id") }
    if (entity.status == StatusType.DELETED) {
      throw ObjectNotFoundException("프롬프트 인첸터 프로필을 찾을 수 없습니다: $id")
    }

    kLogger.info { "(BO) PromptEnhancerProfile 수정 - id: $id, admin: ${session.username}(${session.accountId})" }

    dto.providerCode?.let { entity.providerCode = normalizeProviderCode(it) }
    dto.name?.let { entity.name = it.trim() }
    if (dto.description != null) {
      entity.description = dto.description.trim().takeIf { value -> value.isNotEmpty() }
    }
    if (dto.targetRule != null) {
      entity.targetRule = normalizeTargetRule(dto.targetRule)
    }
    dto.priority?.let { entity.priority = normalizePriority(it) }

    dto.preferredArtistTags?.let {
      entity.preferredArtistTags = toJsonArray(normalizePreferredArtistTags(entity.providerCode, it))
    }
    dto.styleKeywords?.let { entity.styleKeywords = toJsonArray(cleanKeywords(it)) }
    dto.vibeKeywords?.let { entity.vibeKeywords = toJsonArray(cleanKeywords(it)) }
    applyLockedTemplate(entity, dto.lockedTemplate)
    dto.isDefault?.let { entity.isDefault = it }
    dto.status?.let { entity.status = it }

    if (entity.isDefault) {
      clearDefault(entity.providerCode, entity.id)
    }

    return toDetail(profileRepository.save(entity))
  }

  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    val entity = profileRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("프롬프트 인첸터 프로필을 찾을 수 없습니다: $id") }

    kLogger.info { "(BO) PromptEnhancerProfile 삭제 - id: $id, admin: ${session.username}(${session.accountId})" }

    entity.status = StatusType.DELETED
    entity.isDefault = false
    return true
  }

  @Transactional
  private fun clearDefault(providerCode: String, excludeId: Long?) {
    val existing = profileRepository.findFirstByProviderCodeAndIsDefaultTrueAndStatusNot(
      providerCode,
      StatusType.DELETED
    )

    if (existing != null && existing.id != excludeId) {
      existing.isDefault = false
    }
  }

  private fun toDetail(entity: PromptEnhancerProfile): BoPromptEnhancerProfile {
    return BoPromptEnhancerProfile(
      id = entity.id,
      providerCode = entity.providerCode,
      name = entity.name,
      description = entity.description,
      targetRule = parseJsonNode(entity.targetRule),
      priority = entity.priority,
      preferredArtistTags = parseJsonArray(entity.preferredArtistTags),
      styleKeywords = parseJsonArray(entity.styleKeywords),
      vibeKeywords = parseJsonArray(entity.vibeKeywords),
      lockedTemplate = BoPromptEnhancerLockedTemplate(
        styleBlock = entity.styleBlock,
        characterBlock = entity.characterBlock,
        backgroundBlock = entity.backgroundBlock,
        sampler = entity.sampler,
        steps = entity.steps,
        cfgScale = entity.cfgScale,
        width = entity.width,
        height = entity.height,
        seed = entity.seed
      ),
      isDefault = entity.isDefault,
      status = entity.status,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )
  }

  private fun applyLockedTemplate(entity: PromptEnhancerProfile, locked: PromptEnhancerLockedTemplatePayload?) {
    if (locked == null) return

    entity.styleBlock = locked.styleBlock?.trim()?.takeIf { it.isNotEmpty() }
    entity.characterBlock = locked.characterBlock?.trim()?.takeIf { it.isNotEmpty() }
    entity.backgroundBlock = locked.backgroundBlock?.trim()?.takeIf { it.isNotEmpty() }
    entity.sampler = locked.sampler?.trim()?.takeIf { it.isNotEmpty() }
    entity.steps = locked.steps
    entity.cfgScale = locked.cfgScale
    entity.width = locked.width
    entity.height = locked.height
    entity.seed = locked.seed
  }

  private fun cleanKeywords(values: List<String>): List<String> {
    return values
      .map { value -> value.trim() }
      .filter { value -> value.isNotBlank() }
      .map { value -> if (value.length > 120) value.take(120) else value }
      .distinct()
  }

  private fun normalizePreferredArtistTags(providerCode: String, values: List<String>): List<String> {
    val cleaned = cleanKeywords(values)
    if (providerCode == "NOVELAI" && cleaned.size == 1) {
      throw IllegalArgumentException("preferredArtistTags는 비워두거나 최소 2개 이상 입력해야 합니다.")
    }
    return cleaned
  }

  private fun normalizeProviderCode(providerCode: String): String {
    val normalized = providerCode.trim().uppercase()
    if (normalized.isEmpty()) {
      throw IllegalArgumentException("providerCode는 필수입니다.")
    }
    if (!supportedProviderCodes.contains(normalized)) {
      throw IllegalArgumentException("지원하지 않는 providerCode입니다: $providerCode")
    }
    return normalized
  }

  private fun normalizeTargetRule(targetRule: JsonNode?): String {
    if (targetRule == null || targetRule.isNull) {
      return "{}"
    }
    if (!targetRule.isObject) {
      throw IllegalArgumentException("targetRule는 JSON object 형태여야 합니다.")
    }
    return objectMapper.writeValueAsString(targetRule)
  }

  private fun normalizePriority(priority: Int): Int {
    if (priority < 0 || priority > 100000) {
      throw IllegalArgumentException("priority는 0~100000 범위여야 합니다.")
    }
    return priority
  }

  private fun toJsonArray(values: List<String>): String {
    return objectMapper.writeValueAsString(values)
  }

  private fun parseJsonArray(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        .map { value -> value.trim() }
        .filter { value -> value.isNotBlank() }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun parseJsonNode(json: String?): JsonNode? {
    if (json.isNullOrBlank()) return null
    return try {
      objectMapper.readTree(json)
    } catch (_: Exception) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun buildOrderSpecifiers(sort: Sort): Array<OrderSpecifier<*>> {
    if (sort.isEmpty) {
      return arrayOf(
        promptEnhancerProfile.priority.asc(),
        promptEnhancerProfile.updatedAt.desc(),
        promptEnhancerProfile.id.desc()
      )
    }

    return sort.toList().map { sortOrder ->
      val path: Expression<out Comparable<*>> = when (sortOrder.property) {
        "id" -> promptEnhancerProfile.id
        "providerCode" -> promptEnhancerProfile.providerCode
        "name" -> promptEnhancerProfile.name
        "priority" -> promptEnhancerProfile.priority
        "status" -> promptEnhancerProfile.status
        "isDefault" -> promptEnhancerProfile.isDefault
        "createdAt" -> promptEnhancerProfile.createdAt
        "updatedAt" -> promptEnhancerProfile.updatedAt
        else -> promptEnhancerProfile.id
      }

      if (sortOrder.isAscending) {
        OrderSpecifier(Order.ASC, path as Expression<Comparable<*>>)
      } else {
        OrderSpecifier(Order.DESC, path as Expression<Comparable<*>>)
      }
    }.toTypedArray()
  }
}
