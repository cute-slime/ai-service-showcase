package com.jongmin.ai.core.platform.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiMessageContentType
import com.jongmin.ai.core.AiMessageRole
import java.time.ZonedDateTime

data class AiMessageItem(
  var id: Long? = null,
  var aiThreadId: Long? = null,
  var content: String? = null,
  var type: AiMessageContentType? = null,
  var role: AiMessageRole? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
