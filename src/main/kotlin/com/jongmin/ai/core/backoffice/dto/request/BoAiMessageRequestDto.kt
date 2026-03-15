package com.jongmin.ai.core.backoffice.dto.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null

data class CreateAiMessage(
  @field:Null
  var id: Long?,
)

data class PatchAiMessage(
  @field:NotNull
  var id: Long?,
)
