package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime

data class BoAiRunItem(
  var id: Long? = null,
  var assistantId: Long? = null,
  var threadId: Long? = null,
  var status: StatusType? = null,
  var instructions: String? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null
)
