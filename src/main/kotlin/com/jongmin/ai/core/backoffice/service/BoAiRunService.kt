package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.AiRunRepository
import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.backoffice.dto.request.BoCreateAiRun
import com.jongmin.ai.core.backoffice.dto.response.BoAiRunItem
import com.jongmin.ai.core.platform.entity.AiRun
import com.jongmin.ai.core.platform.entity.QAiRun.aiRun
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAiRunService(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val aiRunRepository: AiRunRepository,
  private val queryFactory: JPAQueryFactory,
) {
  private val kLogger = KotlinLogging.logger {}

  @Transactional
  fun create(session: JSession, dto: BoCreateAiRun): BoAiRunItem {
    kLogger.info { "(BO) AI Run 생성 - admin: ${session.username}(${session.accountId})" }
    val entity = objectMapper.convert(dto, AiRun::class.java)
    aiRunRepository.save(entity)
    return findById(entity.id)
  }

  fun findById(id: Long): BoAiRunItem {
    kLogger.debug { "(BO) AI Run 조회 - id: $id" }
    return selectBoAiRunItem()
      .from(aiRun)
      .where(aiRun.id.eq(id))
      .fetchOne() ?: throw ObjectNotFoundException("Run not found.")
  }

  fun findAll(
    pageable: Pageable
  ): Page<BoAiRunItem> {
    kLogger.debug { "(BO) AI Run 목록 조회" }

    val predicate = BooleanBuilder(aiRun.status.ne(AiRunStatus.ENDED))
    val countQuery = queryFactory.select(aiRun.count()).from(aiRun).where(predicate)

    return selectBoAiRunItem()
      .from(aiRun)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }

  @Transactional
  fun patch(session: JSession, data: MutableMap<String, Any>): Map<String, Any?> {
    kLogger.info { "(BO) AI Run 패치 - data: $data, admin: ${session.username}(${session.accountId})" }

    val target = aiRunRepository.findById(data["id"] as Long).orElseThrow { ObjectNotFoundException("Run not found.") }
    return merge(data, target, "id", "createdAt", "updatedAt")
  }

  private fun selectBoAiRunItem(): JPAQuery<BoAiRunItem> {
    return queryFactory
      .select(
        Projections.bean(
          BoAiRunItem::class.java,
          aiRun.id.`as`("id"),
//          aiRun.assistantId.`as`("assistantId"),
//          aiRun.threadId.`as`("threadId"),
          aiRun.status.`as`("status"),
//          aiRun.instructions.`as`("instructions"),
          aiRun.createdAt.`as`("createdAt"),
        )
      )
  }
}

