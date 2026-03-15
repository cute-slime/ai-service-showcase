package com.jongmin.ai.core.platform.dao

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.common.repository.rds.PostgresDao
import com.jongmin.jspring.core.util.JTimeUtils
import com.jongmin.ai.core.platform.dto.request.CreateAiMessage
import com.jongmin.ai.core.platform.dto.response.AiMessageItem
import org.springframework.context.MessageSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

@Component
class AiMessageDao(override val jdbcTemplate: JdbcTemplate, override val messageSource: MessageSource) : PostgresDao() {
  fun insert(session: JSession, dto: CreateAiMessage): AiMessageItem {
    val entity = AiMessageItem(
      id = dto.id,
      aiThreadId = dto.aiThreadId,
      content = dto.content,
      role = dto.role,
      type = dto.type,
      status = StatusType.ACTIVE,
      createdAt = JTimeUtils.now(),
      updatedAt = JTimeUtils.now()
    )
    jdbcTemplate.update(
      "INSERT INTO ai_message(id, account_id, ai_thread_id, content, role, type, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);",
      entity.id,
      session.accountId,
      entity.aiThreadId,
      entity.content,
      entity.role?.value(),
      entity.type?.value(),
      entity.status?.value(),
      Timestamp(entity.createdAt?.toInstant()!!.toEpochMilli()),
      Timestamp(entity.updatedAt?.toInstant()!!.toEpochMilli())
    )
    return entity
  }
}
