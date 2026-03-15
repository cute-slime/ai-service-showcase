package com.jongmin.ai.core.platform.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class InferenceRequestV2(
  @field:Null
  var key: String? = null,

  @field:NotNull
  var threadId: Long?,

  @field:NotBlank
  @field:Size(max = 200)
  var question: String?
)
