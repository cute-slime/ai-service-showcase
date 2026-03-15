package com.jongmin.ai.role_playing.platform.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class RolePlayingCommand(
  @field:NotBlank
  var key: String? = null, // enum?
  @field:NotNull
  var value: Any? = null
)

data class Enter(
  var placeId: Long?,
  var mainCharacterId: Long?,
)
