package com.jongmin.ai.core.platform.dto.request

import com.jongmin.ai.core.AiRunStatus
import com.jongmin.ai.core.platform.dto.HasAiMessageOwnership
import com.jongmin.ai.core.platform.dto.IsAiThreadIdle
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null

data class CreateAiRun(
  @field:Null
  var id: Long? = null,
  var canvasId: String? = null,
  @field:NotNull
  @field:HasAiMessageOwnership
  @field:IsAiThreadIdle
  var aiMessageId: Long? = null,
  var researchMode: Boolean? = null,
  // @field:NotNull
  // @field:HasAiAgentPermission
  var aiAgentId: Long? = null,
  @field:Null
  var status: AiRunStatus? = null,
  @field:Null
  var responseFormat: String? = null,
)
