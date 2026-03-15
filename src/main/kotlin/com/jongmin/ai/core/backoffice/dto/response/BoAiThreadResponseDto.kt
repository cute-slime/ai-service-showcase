package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime

data class BoAiThreadItem(
  var id: Long? = null,
  var accountId: Long? = null,
  var title: String? = null,
  var totalInputToken: Long? = null,
  var totalOutputToken: Long? = null,
  var totalInputTokenSpend: Double? = null,
  var totalOutputTokenSpend: Double? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null
)
