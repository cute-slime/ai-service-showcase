package com.jongmin.ai.core.platform.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime

data class AiRunStepItem(
  var id: Long? = null,
  var aiRunId: String? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null
)
