package com.jongmin.ai.role_playing.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.role_playing.RolePlayingType
import java.time.ZonedDateTime

data class BoRolePlayingItem(
  var id: Long? = null,
  var type: RolePlayingType? = null,
  var subject: String? = null,
  var workflow: Map<String, Any>? = null,
  var description: String? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
