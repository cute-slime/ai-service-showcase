package com.jongmin.ai.core.platform.dto.response

import java.time.ZonedDateTime

data class AiGenCreativeContent(
  var id: Long? = null,
  var emoji: String? = null,
  var title: String? = null,
  var category: String? = null,
  var subCategory: String? = null,
  var exposureDueDateTime: ZonedDateTime? = null,
  var createdAt: ZonedDateTime? = null,
)
