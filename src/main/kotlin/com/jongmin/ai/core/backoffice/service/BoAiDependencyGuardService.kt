package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.platform.entity.QAiAgent.aiAgent
import com.jongmin.ai.core.platform.entity.QAiAssistant.aiAssistant
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.data.entity.StatusType
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiDependencyGuardService(
  private val queryFactory: JPAQueryFactory
) {
  fun assertProviderDeletable(providerId: Long) {
    val activeModelCount = queryFactory
      .select(aiModel.count())
      .from(aiModel)
      .where(
        aiModel.aiProviderId.eq(providerId)
          .and(aiModel.status.ne(StatusType.DELETED))
      )
      .fetchOne() ?: 0L

    val activeAgentWorkflowCount = countActiveAgentWorkflowReferences("aiProviderId", providerId)

    if (activeModelCount > 0 || activeAgentWorkflowCount > 0) {
      throw BadRequestException(
        buildProviderDeleteBlockedMessage(activeModelCount, activeAgentWorkflowCount)
      )
    }
  }

  fun assertModelDeletable(modelId: Long) {
    val activeAssistantCount = queryFactory
      .select(aiAssistant.count())
      .from(aiAssistant)
      .where(
        aiAssistant.status.ne(StatusType.DELETED)
          .and(
            aiAssistant.modelId.eq(modelId)
              .or(aiAssistant.primaryModelId.eq(modelId))
              .or(aiAssistant.restoreModelId.eq(modelId))
          )
      )
      .fetchOne() ?: 0L

    val activeAgentWorkflowCount = countActiveAgentWorkflowReferences("aiModelId", modelId)

    if (activeAssistantCount > 0 || activeAgentWorkflowCount > 0) {
      throw BadRequestException(
        buildModelDeleteBlockedMessage(activeAssistantCount, activeAgentWorkflowCount)
      )
    }
  }

  fun assertAssistantDeletable(assistantId: Long) {
    val activeAgentWorkflowCount = countActiveAgentWorkflowReferences("aiAssistantId", assistantId)

    if (activeAgentWorkflowCount > 0) {
      throw BadRequestException(
        "사용 중인 AI 어시스턴트는 삭제할 수 없습니다. 연결된 AI 에이전트 ${activeAgentWorkflowCount}개를 먼저 정리해주세요."
      )
    }
  }

  private fun countActiveAgentWorkflowReferences(referenceKey: String, targetId: Long): Long {
    // workflow는 JSONB라서 우선 활성 에이전트 정의를 메모리에서 순회한다.
    return queryFactory
      .selectFrom(aiAgent)
      .where(aiAgent.status.ne(StatusType.DELETED))
      .fetch()
      .count { containsWorkflowReference(it.workflow, referenceKey, targetId) }
      .toLong()
  }

  private fun containsWorkflowReference(
    value: Any?,
    referenceKey: String,
    targetId: Long
  ): Boolean {
    return when (value) {
      is Map<*, *> -> value.entries.any { (key, nestedValue) ->
        (key?.toString() == referenceKey && matchesTargetId(nestedValue, targetId)) ||
            containsWorkflowReference(nestedValue, referenceKey, targetId)
      }

      is List<*> -> value.any { nestedValue ->
        containsWorkflowReference(nestedValue, referenceKey, targetId)
      }

      else -> false
    }
  }

  private fun matchesTargetId(value: Any?, targetId: Long): Boolean {
    return when (value) {
      is Number -> value.toLong() == targetId
      is String -> value.toLongOrNull() == targetId
      else -> false
    }
  }

  private fun buildProviderDeleteBlockedMessage(
    activeModelCount: Long,
    activeAgentWorkflowCount: Long
  ): String {
    return when {
      activeModelCount > 0 && activeAgentWorkflowCount > 0 ->
        "사용 중인 AI 제공사는 삭제할 수 없습니다. 연결된 AI 모델 ${activeModelCount}개와 AI 에이전트 ${activeAgentWorkflowCount}개를 먼저 정리해주세요."

      activeModelCount > 0 ->
        "사용 중인 AI 제공사는 삭제할 수 없습니다. 연결된 AI 모델 ${activeModelCount}개를 먼저 정리해주세요."

      else ->
        "사용 중인 AI 제공사는 삭제할 수 없습니다. 연결된 AI 에이전트 ${activeAgentWorkflowCount}개를 먼저 정리해주세요."
    }
  }

  private fun buildModelDeleteBlockedMessage(
    activeAssistantCount: Long,
    activeAgentWorkflowCount: Long
  ): String {
    return when {
      activeAssistantCount > 0 && activeAgentWorkflowCount > 0 ->
        "사용 중인 AI 모델은 삭제할 수 없습니다. 연결된 AI 어시스턴트 ${activeAssistantCount}개와 AI 에이전트 ${activeAgentWorkflowCount}개를 먼저 정리해주세요."

      activeAssistantCount > 0 ->
        "사용 중인 AI 모델은 삭제할 수 없습니다. 연결된 AI 어시스턴트 ${activeAssistantCount}개를 먼저 정리해주세요."

      else ->
        "사용 중인 AI 모델은 삭제할 수 없습니다. 연결된 AI 에이전트 ${activeAgentWorkflowCount}개를 먼저 정리해주세요."
    }
  }
}
