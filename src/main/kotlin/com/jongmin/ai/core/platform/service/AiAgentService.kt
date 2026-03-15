package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.core.enums.JService
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.serviceTrace
import com.jongmin.ai.core.AiAgentType
import com.jongmin.ai.core.platform.entity.AiAgent
import com.jongmin.ai.core.platform.entity.QAiAgent.aiAgent
import com.jongmin.jspring.data.entity.StatusType
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiAgentService(
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  fun findById(id: Long): AiAgent {
    kLogger.serviceTrace(JService.AI) { "Find by id agent - id: $id" }
    val raa = queryFactory
      .selectFrom(aiAgent)
      .where(aiAgent.id.eq(id).and(aiAgent.status.eq(StatusType.ACTIVE)))
      .fetchFirst() ?: throw BadRequestException("Not found active ai agent by id: $id")
    return raa
  }

  fun findFirstByType(type: AiAgentType): AiAgent {
    kLogger.serviceTrace(JService.AI) { "Find first by type - aiAgentType: $type" }
    val raa = queryFactory
      .selectFrom(aiAgent)
      .where(aiAgent.type.eq(type).and(aiAgent.status.eq(StatusType.ACTIVE)))
      .fetchFirst() ?: throw BadRequestException("Not found ai agent by type: $type")
    return raa
  }
}
