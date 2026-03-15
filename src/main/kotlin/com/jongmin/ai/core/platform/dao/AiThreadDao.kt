package com.jongmin.ai.core.platform.dao

import com.jongmin.ai.common.repository.rds.PostgresDao
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AiThreadDao(override val jdbcTemplate: JdbcTemplate, override val messageSource: MessageSource) : PostgresDao() {
  private val kLogger = KotlinLogging.logger {}

  fun update(id: Long, title: String): Int {
    val finalTitle = if (title.length > 80) title.substring(0, 77) + "..." else title
    val updated = jdbcTemplate.update("UPDATE ai_thread SET title = ? WHERE id = ?", finalTitle, id)
    kLogger.info { "aiThread 제목 수정 - id: $id, title: $finalTitle, updated: $updated" }
    return updated
  }
}
