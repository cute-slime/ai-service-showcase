package com.jongmin.ai.role_playing.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.role_playing.RolePlayingType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateRolePlaying(
  @field:Null
  var id: Long?,
  @field:Null
  var accountId: Long?,
  @field:Null
  var ownerId: Long?,

  @field:NotBlank
  @field:Size(max = 40)
  var subject: String?,

  var type: RolePlayingType?,

  @field:Size(max = 500)
  var description: String?,

  var status: StatusType?,

  @field:Null
  var workflow: Map<String, Any>? = null
)

data class PatchRolePlaying(
  @field:NotNull
  var id: Long?,

  @field:Size(max = 40)
  var subject: String?,

  var type: RolePlayingType?,

  @field:Size(max = 500)
  var description: String?,

  @field:Size(max = 50000)
  var workflow: Map<String, Any>?,

  var status: StatusType?
)
