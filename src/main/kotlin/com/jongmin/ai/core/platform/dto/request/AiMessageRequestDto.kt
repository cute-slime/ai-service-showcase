package com.jongmin.ai.core.platform.dto.request

import com.jongmin.ai.core.AiMessageContentType
import com.jongmin.ai.core.AiMessageRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Null
import org.hibernate.validator.constraints.Length

data class CreateAiMessage(
  @field:Null
  var id: Long? = null,

  @field:Null
  var accountId: Long? = null,

  @field:Null
  var aiThreadId: Long? = null,

  @field:NotBlank
  @field:Length(max = 10000)
  var content: String? = null,

  @field:Null
  var role: AiMessageRole? = null,

  @field:Null
  var type: AiMessageContentType? = null,
)
