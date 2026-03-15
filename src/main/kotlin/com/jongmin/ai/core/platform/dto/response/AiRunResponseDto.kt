package com.jongmin.ai.core.platform.dto.response

import com.jongmin.ai.core.AiRunStatus
import java.time.ZonedDateTime

data class AiRunItem(
  var id: Long? = null,
  var aiMessageId: String? = null,
  var status: AiRunStatus? = null,
  var createdAt: ZonedDateTime? = null
)
