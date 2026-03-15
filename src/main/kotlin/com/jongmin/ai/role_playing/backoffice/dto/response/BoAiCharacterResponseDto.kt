package com.jongmin.ai.role_playing.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.role_playing.CharacterType
import java.time.ZonedDateTime

data class BoAiCharacterItem(
  var id: Long? = null,
  var type: CharacterType? = null,
  var tiny: String? = null,
  var small: String? = null,
  var medium: String? = null,
  var large: String? = null,
  var fullText: String? = null,
  var name: String? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
