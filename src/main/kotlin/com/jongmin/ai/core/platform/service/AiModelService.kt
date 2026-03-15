package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.AiCoreProperties
import com.jongmin.ai.core.LlmProviderRules
import com.jongmin.ai.core.platform.entity.QAiApiKey.aiApiKey
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.jasypt.util.text.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiModelService(
  private val queryFactory: JPAQueryFactory,
  private val textEncryptor: TextEncryptor,
) {
  fun findAiCoreProperties(apiKeyId: Long, primaryModelId: Long): AiCoreProperties {
    val acp = queryFactory.select(
      Projections.constructor(
        AiCoreProperties::class.java,
        aiProvider.name.`as`("provider"),
        aiProvider.baseUrl.`as`("baseUrl"),
        aiModel.name.`as`("model"),
        aiModel.supportsReasoning.`as`("supportsReasoning"),
        aiModel.reasoningEffort.`as`("reasoningEffort"),
        aiModel.noThinkTrigger.`as`("noThinkTrigger"),
        aiApiKey.encryptedKey.`as`("apiKey"),
      )
    )
      .from(aiModel)
      .join(aiProvider).on(aiProvider.id.eq(aiModel.aiProviderId))
      .join(aiApiKey).on(aiApiKey.aiProviderId.eq(aiProvider.id))
      .where(
        aiModel.id.eq(primaryModelId).and(aiApiKey.id.eq(apiKeyId)).and(aiProvider.status.eq(StatusType.ACTIVE))
          .and(aiModel.status.eq(StatusType.ACTIVE)).and(aiApiKey.status.eq(StatusType.ACTIVE))
      )
      .fetchOne() ?: throw BadRequestException("Not found ai meta by apiKeyId: $apiKeyId, primaryModelId: $primaryModelId")

    if (LlmProviderRules.requiresBaseUrl(acp.provider) && acp.baseUrl.isNullOrBlank()) {
      throw BadRequestException("Provider '${acp.provider}'의 endpoint(baseUrl)가 비어 있습니다. AI Provider 설정에서 endpoint를 입력해주세요.")
    }

    acp.apiKey = textEncryptor.decrypt(acp.apiKey)
    return acp
  }
}
