package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.backoffice.dto.response.BoAiApiKeyItem
import com.jongmin.ai.core.platform.entity.QAiApiKey.aiApiKey
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jasypt.util.text.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiApiKeyService(
  private val textEncryptor: TextEncryptor,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  fun findAllByModelId(aiModelId: Long): List<BoAiApiKeyItem> {
    kLogger.debug { "(BO) AiApiKey 목록 - aiModelId: $aiModelId" }

    val result = queryFactory
      .select(aiApiKey)
      .from(aiProvider)
      .join(aiModel).on(aiModel.aiProviderId.eq(aiProvider.id))
      .join(aiApiKey).on(aiApiKey.aiProviderId.eq(aiProvider.id))
      .where(
        aiModel.id.eq(aiModelId)
          .and(aiProvider.status.ne(StatusType.DELETED))
          .and(aiModel.status.ne(StatusType.DELETED))
          .and(aiApiKey.status.ne(StatusType.DELETED))
      )
      .fetch()

    return result.map { BoAiApiKeyItem(it.id, textEncryptor.decrypt(it.encryptedKey)) }.toList()
  }
}
