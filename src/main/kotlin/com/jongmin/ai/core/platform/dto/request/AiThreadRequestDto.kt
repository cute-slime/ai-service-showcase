package com.jongmin.ai.core.platform.dto.request

import com.jongmin.jspring.data.entity.StatusType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateAiThread(
  @field:Null
  var id: Long? = null,

  @field:Null
  var accountId: Long? = null,

  @field:Null
  var title: String? = null,

  @field:Null
  var status: StatusType? = null,

  @field:NotBlank
  @field:Size(max = 1000)
  var question: String?
)

data class PatchAiThread(
  @field:NotNull
  var id: Long? = null,

  @field:Size(max = 80)
  var title: String? = null,
)
