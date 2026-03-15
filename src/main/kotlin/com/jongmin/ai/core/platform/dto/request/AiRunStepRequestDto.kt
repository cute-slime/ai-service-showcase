package com.jongmin.ai.core.platform.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.platform.dto.HasAiRunOwnership
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null

data class CreateAiRunStep(
  @field:Null
  var id: Long? = null,
  var aiAssistantId: Long? = null,
  @field:NotNull
  @field:HasAiRunOwnership
  var aiRunId: Long? = null,
  @field:Null
  var status: StatusType? = null,
)
