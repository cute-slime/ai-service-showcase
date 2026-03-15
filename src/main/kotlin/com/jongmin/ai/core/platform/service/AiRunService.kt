package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.convert
import com.jongmin.ai.core.AiRunRepository
import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.platform.dto.request.CreateAiRun
import com.jongmin.ai.core.platform.dto.response.AiRunItem
import com.jongmin.ai.core.platform.entity.AiRun
import com.jongmin.ai.core.platform.entity.QAiMessage.aiMessage
import com.jongmin.ai.core.platform.entity.QAiRun.aiRun
import com.jongmin.ai.core.platform.entity.QAiThread.aiThread
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class AiRunService(
  private val objectMapper: ObjectMapper,
  private val aiRunRepository: AiRunRepository,
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(session: JSession, dto: CreateAiRun): AiRunItem {
    kLogger.info { "AI Run 생성" }
    // 현재는 복잡한 기능은 구현하지 않고 메시지에 런을 구동하는 로직만 작성함
    dto.status = AiRunStatus.READY
    dto.responseFormat = "TEXT"
    val entity = objectMapper.convert(dto, AiRun::class.java)
    if (isNotIdle(session, dto.aiMessageId!!)) throw BadRequestException("스레드가 이미 실행상태입니다. 완료 또는 중지 후 다시 시도해주세요.")
    return objectMapper.convert(aiRunRepository.save(entity), AiRunItem::class.java)
  }

  @Transactional
  fun stop(id: Long) {
    kLogger.info { "AI Run 중지" }
    aiRunRepository.deleteById(id)
  }

  fun isIdle(session: JSession, aiMessageId: Long): Boolean {
    val count: Long = queryFactory
      .select(aiRun.count())
      .from(aiThread)
      .join(aiMessage).on(aiMessage.aiThreadId.eq(aiThread.id))
      .join(aiRun).on(aiRun.aiMessageId.eq(aiMessage.id))
      .where(aiMessage.id.eq(aiMessageId).and(aiMessage.accountId.eq(session.accountId)).and(aiRun.status.eq(AiRunStatus.ENDED)))
      .fetchOne() ?: 0
    return count == 0L
  }

  fun isNotIdle(session: JSession, aiMessageId: Long): Boolean {
    return !isIdle(session, aiMessageId)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun patchStatus(aiRunId: Long, newStatus: AiRunStatus) {
    aiRunRepository.findById(aiRunId).ifPresent {
      it.status = newStatus
    }
  }
}
