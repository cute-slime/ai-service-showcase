package com.jongmin.ai.core.platform.dto.response

import java.time.ZonedDateTime

data class AiThreadItem(
  var id: Long? = null,
  var title: String? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var createdAt: ZonedDateTime? = null,
  var starred: Boolean? = null,
  var runningStatus: RunningStatus? = null,
)

data class RunningStatus(
  var running: Boolean? = null,
  var status: String? = null,
)
