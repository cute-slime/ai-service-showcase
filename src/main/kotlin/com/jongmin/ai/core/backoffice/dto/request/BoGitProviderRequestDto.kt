package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateGitProvider(
  @field:Null
  var id: Long? = null,
  @field:Null
  var accountId: Long? = null,

  @field:NotBlank
  @field:Size(max = 80)
  val name: String? = null,
  @field:NotBlank
  @field:Size(max = 400)
  val token: String? = null,

  @field:NotNull
  var status: StatusType? = null,
)

data class PatchGitProvider(
  @field:NotNull
  var id: Long? = null,

  @field:Size(max = 80)
  val name: String? = null,
  @field:Size(max = 400)
  val token: String? = null,

  var status: StatusType? = null,
)
