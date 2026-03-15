package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.entity.QAiApiKey.aiApiKey
import com.jongmin.ai.core.platform.entity.QAiAssistant.aiAssistant
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jasypt.util.text.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiAssistantService(
  private val queryFactory: JPAQueryFactory,
  private val textEncryptor: TextEncryptor,
) {
  private val kLogger = KotlinLogging.logger {}

  private fun getRunnableAiAssistantQuery(): JPAQuery<RunnableAiAssistant> {
    return queryFactory
      .select(
        Projections.constructor(
          RunnableAiAssistant::class.java,
          aiAssistant.id.`as`("id"),
          aiAssistant.name.`as`("name"),
          aiAssistant.description.`as`("description"),
          aiProvider.name.`as`("provider"),
          aiProvider.baseUrl.`as`("baseUrl"),
          aiModel.name.`as`("model"),
          aiModel.supportsReasoning.`as`("supportsReasoning"),
          aiModel.reasoningEffort.`as`("reasoningEffort"),
          aiModel.noThinkTrigger.`as`("noThinkTrigger"),
          aiApiKey.encryptedKey.`as`("apiKey"),
          aiAssistant.instructions.`as`("instructions"),
          aiAssistant.temperature.`as`("temperature"),
          aiAssistant.topP.`as`("topP"),
          aiAssistant.topK.`as`("topK"),
          aiAssistant.frequencyPenalty.`as`("frequencyPenalty"),
          aiAssistant.presencePenalty.`as`("presencePenalty"),
          aiAssistant.responseFormat.`as`("responseFormat"),
          aiAssistant.maxTokens.`as`("maxTokens"),
          aiAssistant.status.`as`("status"),
          aiAssistant.type.`as`("type"),
        )
      )
      .from(aiAssistant)
      .join(aiModel).on(aiModel.id.eq(aiAssistant.modelId))
      .join(aiProvider).on(aiProvider.id.eq(aiModel.aiProviderId))
      .join(aiApiKey).on(aiApiKey.id.eq(aiAssistant.apiKeyId))
      .where(aiAssistant.status.eq(StatusType.ACTIVE).and(aiModel.status.eq(StatusType.ACTIVE)))
      .orderBy(aiAssistant.updatedAt.desc())
  }

  fun findById(id: Long): RunnableAiAssistant {
    kLogger.debug { "Find by id assistant - id: $id" }
    val raa = getRunnableAiAssistantQuery()
      .where(aiAssistant.id.eq(id))
      .fetchFirst() ?: throw BadRequestException("Not found assistant by id: $id")
    raa.apiKey = textEncryptor.decrypt(raa.apiKey)
    return raa
  }

  /**
   * 타입과 선택적 카테고리들로 어시스턴트를 조회합니다.
   *
   * @param type 어시스턴트 타입
   * @param categories 카테고리들 (예: "expand", "summarize", "formal" 등). 비어있으면 타입만으로 검색, 여러 개면 IN 조건
   * @return 실행 가능한 AI 어시스턴트
   * @throws BadRequestException 어시스턴트를 찾을 수 없는 경우
   */
  fun findFirst(type: AiAssistantType, vararg categories: String): RunnableAiAssistant {
    kLogger.debug { "Find first agent assistant by type: $type, categories: ${categories.toList()}" }

    // categories가 있으면 IN 조건 추가
    val predicate = aiAssistant.type.eq(type).let { base ->
      if (categories.isNotEmpty()) {
        base.and(aiAssistant.category.`in`(*categories))
      } else {
        base
      }
    }

    val raa = getRunnableAiAssistantQuery()
      .where(predicate)
      .fetchFirst()
      ?: throw BadRequestException("Not found agent assistant by type: $type${if (categories.isNotEmpty()) ", categories: ${categories.toList()}" else ""}")

    raa.apiKey = textEncryptor.decrypt(raa.apiKey)
    return raa
  }

  fun findByName(aiAssistantName: String): RunnableAiAssistant {
    kLogger.debug { "Find by assistant - name: $aiAssistantName" }
    val raa = getRunnableAiAssistantQuery()
      .where(aiAssistant.name.eq(aiAssistantName))
      .fetchFirst() ?: throw BadRequestException("Not found assistant by name: $aiAssistantName")
    raa.apiKey = textEncryptor.decrypt(raa.apiKey)
    return raa
  }
}
