package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.jspring.core.util.convert
import com.jongmin.ai.core.AiRunStepRepository
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.dto.request.CreateAiRunStep
import com.jongmin.ai.core.platform.dto.response.AiRunStepItem
import com.jongmin.ai.core.platform.entity.AiRunStep
import com.jongmin.ai.core.platform.entity.QAiRunStep.aiRunStep
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiRunStepService(
  private val objectMapper: ObjectMapper,
  private val aiRunStepRepository: AiRunStepRepository,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  // 정상 타이밍에 시작과 종료를 찍지 않아서 시작 > 시작 > 종료 찍혀서 오류나고 있는 것 수정

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun create(session: JSession, dto: CreateAiRunStep): AiRunStepItem {
    kLogger.info { "AI Run Step 생성 - aiRunId: ${dto.aiRunId}" }
    if (aiRunStepRepository.exists(
        aiRunStep.aiRunId.eq(dto.aiRunId).and(aiRunStep.status.eq(StatusType.RUNNING))
      )
    ) throw BadRequestException("현재 Ai Run의 상태 전환이 불가능합니다.")
    dto.status = StatusType.RUNNING
    val entity = objectMapper.convert(dto, AiRunStep::class.java)
    return objectMapper.convert(aiRunStepRepository.save(entity), AiRunStepItem::class.java)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun patchStatus(aiRunId: Long, runnableAiAssistant: RunnableAiAssistant?, newStatus: StatusType) {
    kLogger.info { "AI Run Step 패치 - id: $aiRunId" }
    val predicate = aiRunStep.aiRunId.eq(aiRunId).and(aiRunStep.status.eq(StatusType.RUNNING))
    runnableAiAssistant?.let { predicate.and(aiRunStep.aiAssistantId.eq(it.id)) }
    aiRunStepRepository.findOne(predicate).ifPresent {
      it.status = newStatus
      if (newStatus == StatusType.ENDED) it.completedAt = now()
    }
  }
}
