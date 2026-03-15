package com.jongmin.ai.role_playing.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.role_playing.StageType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateStage(
  @field:Null
  var id: Long?,
  @field:Null
  var accountId: Long?,
  @field:Null
  var ownerId: Long?,
  var status: StatusType?,
  @field:Null
  var currentVersion: Float?,
  @field:Null
  var version: Map<String, Any>?,

  @field:Size(max = 200)
  var tiny: String? = null,
  @field:Size(max = 1000)
  var small: String? = null, // 1000
  @field:Size(max = 5000)
  var medium: String? = null, // 5000
  @field:Size(max = 10000)
  var large: String? = null,
  @field:Size(max = 50000)
  var fullText: String? = null,

  @field:NotNull
  var type: StageType?,

  @field:NotBlank
  @field:Size(max = 80)
  var name: String?,
)

data class PatchStage(
  @field:NotNull
  var id: Long?,

  var status: StatusType?,

  @field:Size(max = 200)
  var tiny: String? = null,
  @field:Size(max = 1000)
  var small: String? = null, // 1000
  @field:Size(max = 5000)
  var medium: String? = null, // 5000
  @field:Size(max = 10000)
  var large: String? = null,
  @field:Size(max = 50000)
  var fullText: String? = null,

  var type: StageType?,

  @field:Size(max = 80)
  var name: String?,
)
