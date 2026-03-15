package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime

data class BoAiMessageItem(
  var id: Long? = null,
  var name: String? = null,
  var description: String? = null,
  var modelId: Long? = null,
  var model: String? = null,
  var instructions: String? = null,
  var temperature: Double? = null,
  var topP: Double? = null,
  var maxTokens: Int? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
