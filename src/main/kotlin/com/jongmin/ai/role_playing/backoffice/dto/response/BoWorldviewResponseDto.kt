package com.jongmin.ai.role_playing.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.role_playing.WorldviewType
import java.time.ZonedDateTime

data class BoWorldviewItem(
  var id: Long? = null,
  var type: WorldviewType? = null,
  var tiny: String? = null,
  var small: String? = null,
  var medium: String? = null,
  var large: String? = null,
  var fullText: String? = null,
  var subject: String? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
