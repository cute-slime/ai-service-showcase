package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.buildSearchPredicate
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiApiKeyRepository
import com.jongmin.ai.core.LlmProviderRules
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.backoffice.dto.request.CreateAiProvider
import com.jongmin.ai.core.backoffice.dto.response.BoAiApiKeyItem
import com.jongmin.ai.core.backoffice.dto.response.BoAiProviderItem
import com.jongmin.ai.core.platform.entity.AiApiKey
import com.jongmin.ai.core.platform.entity.AiProvider
import com.jongmin.ai.core.platform.entity.QAiApiKey.aiApiKey
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jasypt.util.text.TextEncryptor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiProviderService(
  private val objectMapper: ObjectMapper,
  private val textEncryptor: TextEncryptor,
  private val aiProviderRepository: AiProviderRepository,
  private val aiApiKeyRepository: AiApiKeyRepository,
  private val dependencyGuardService: BoAiDependencyGuardService,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(
    session: JSession, dto: CreateAiProvider,
    apiKeys: Set<CommonDto.LongKeyStringKey>
  ): BoAiProviderItem {
    kLogger.info { "(BO) AiProvider 생성 - name: ${dto.name}, admin: ${session.username}(${session.accountId})" }
    validateBaseUrlRequirement(dto.name.orEmpty(), dto.baseUrl)

    val savedProvider = aiProviderRepository.save(objectMapper.convert(dto, AiProvider::class.java))

    val newKeys = apiKeys.map {
      AiApiKey(
        accountId = -1,
        aiProviderId = savedProvider.id,
        encryptedKey = textEncryptor.encrypt(it.key!!)
      )
    }.toList()
    if (newKeys.isNotEmpty()) {
      kLogger.debug { "(BO) AiProvider API 키 등록 - ${newKeys.size}건" }
      aiApiKeyRepository.saveAll(newKeys)
    }

    return findById(savedProvider.id)
  }

  fun findById(id: Long): BoAiProviderItem {
    kLogger.debug { "(BO) AiProvider 단건 조회 - id: $id" }

    val item = queryFactory
      .select(selectBoAiProviderItem())
      .from(aiProvider)
      .leftJoin(aiModel).on(aiProvider.id.eq(aiModel.aiProviderId).and(aiModel.status.ne(StatusType.DELETED)))
      .where(aiProvider.id.eq(id).and(aiProvider.status.ne(StatusType.DELETED)))
      .groupBy(aiProvider)  // PostgreSQL: count() 집계 함수 사용 시 GROUP BY 필수
      .fetchOne() ?: throw ObjectNotFoundException("Provider not found.")

    item.apiKeys = aiApiKeyRepository
      .findAll(aiApiKey.aiProviderId.eq(id).and(aiApiKey.status.ne(StatusType.DELETED)))
      .map { BoAiApiKeyItem(it.id, textEncryptor.decrypt(it.encryptedKey)) }.toSet()

    return item
  }

  fun findAll(): List<CommonDto.Profile> {
    return queryFactory
      .select(Projections.bean(CommonDto.Profile::class.java, aiProvider.id.`as`("id"), aiProvider.name.`as`("name")))
      .from(aiProvider)
      .where(aiProvider.status.ne(StatusType.DELETED))
      .fetch()
  }

  fun findAll(
    statuses: Set<StatusType>?,
    q: String?,
    pageable: Pageable
  ): Page<BoAiProviderItem> {
    kLogger.debug { "(BO) AiProvider 목록 조회" }

    val predicate = buildSearchPredicate(aiProvider.status, StatusType.DELETED, statuses, q, aiProvider.name)

    val countQuery = queryFactory
      .select(aiProvider.count())
      .from(aiProvider)
      .where(predicate)

    return queryFactory
      .select(selectBoAiProviderItem())
      .from(aiProvider)
      .leftJoin(aiModel).on(aiProvider.id.eq(aiModel.aiProviderId).and(aiModel.status.ne(StatusType.DELETED)))
      .where(predicate)
      .groupBy(aiProvider)
      .fetchPage(countQuery, pageable)
  }

  @Transactional
  fun patch(
    session: JSession, data: Map<String, Any?>,
    apiKeys: Set<CommonDto.LongKeyStringKey>?
  ): Map<String, Any?> {
    val aiProviderId = data["id"] as Long
    kLogger.info { "(BO) AiProvider 패치 - id: ${aiProviderId}, admin: ${session.username}(${session.accountId})" }

    val target = aiProviderRepository.findById(aiProviderId).orElseThrow { ObjectNotFoundException("AI 제공사를 찾을 수 없습니다.") }
    val patchedBaseUrl = if (data.containsKey("baseUrl")) data["baseUrl"] as String? else target.baseUrl
    validateBaseUrlRequirement(target.name, patchedBaseUrl)

    val changes = merge(
      data,
      target,
      "id",
      "createdAt",
      "updatedAt"
    )

    // Convert prev to Map<Long, AiApiKey> for easier lookup
    val prev = aiApiKeyRepository.findAll(aiApiKey.aiProviderId.eq(aiProviderId)).associateBy { it.id }

    // Save new keys and delete missing ones
    val updated = apiKeys?.map {
      AiApiKey(
        id = it.id ?: 0L,
        accountId = -1,
        aiProviderId = aiProviderId,
        encryptedKey = textEncryptor.encrypt(it.key!!)
      )
    }?.toList() ?: emptyList()
    if (updated.isNotEmpty()) {
      kLogger.debug { "(BO) AiProvider API 키 - ${updated.size}건" }
      aiApiKeyRepository.saveAll(updated)
    }

    // Delete keys that are in prev but not in current apiKeys
    if (apiKeys != null) {
      val deletedIds = prev.keys - apiKeys.mapNotNull { it.id }.toSet()
      if (deletedIds.isNotEmpty()) {
        kLogger.debug { "(BO) AiProvider API 키 삭제 - ids: $deletedIds" }
        aiApiKeyRepository.deleteAllById(deletedIds)
      }
    }

    return changes
  }

  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    kLogger.info { "(BO) AiProvider 삭제 - id: $id, admin: ${session.username}(${session.accountId})" }
    dependencyGuardService.assertProviderDeletable(id)
    val provider = aiProviderRepository.findById(id).orElseThrow { ObjectNotFoundException("AI 제공사를 찾을 수 없습니다.") }
    provider.status = StatusType.DELETED
    return true
  }

  // :----------------- Utility -----------------:

  private fun selectBoAiProviderItem(full: Boolean = false) =
    Projections.bean(
      BoAiProviderItem::class.java,
      aiProvider.id.`as`("id"),
      aiProvider.name.`as`("name"),
      aiProvider.description.`as`("description"),
      aiProvider.baseUrl.`as`("baseUrl"),
      aiModel.id.count().intValue().`as`("modelCount"),
      aiProvider.status.`as`("status"),
      aiProvider.createdAt.`as`("createdAt"),
      aiProvider.updatedAt.`as`("updatedAt")
    )

  private fun validateBaseUrlRequirement(providerName: String, baseUrl: String?) {
    if (LlmProviderRules.requiresBaseUrl(providerName) && baseUrl.isNullOrBlank()) {
      throw BadRequestException("Provider '$providerName'는 endpoint(baseUrl) 설정이 필수입니다.")
    }
  }
}
