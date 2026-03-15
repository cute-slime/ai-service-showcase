package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiAgentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateAiAgent(
  @field:Null
  var id: Long?,
  @field:Null
  var accountId: Long?,
  @field:Null
  var ownerId: Long?,

  @field:NotBlank
  @field:Size(max = 40)
  var name: String?,

  var type: AiAgentType?,

  @field:Size(max = 500)
  var description: String?,

  var status: StatusType?,

  @field:Null
  var workflow: Map<String, Any>? = null
)

data class PatchAiAgent(
  @field:NotNull
  var id: Long?,

  @field:Size(max = 40)
  var name: String?,

  var type: AiAgentType?,

  @field:Size(max = 500)
  var description: String?,

  @field:Size(max = 50000)
  var workflow: Map<String, Any>?,

  var status: StatusType?
)
