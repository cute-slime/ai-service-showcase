package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.platform.dto.HasAiMessageOwnership
import com.jongmin.ai.core.platform.dto.IsAiThreadIdle
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null

data class BoCreateAiRun(
  @field:Null
  var id: Long? = null,
  @field:NotNull
  @field:HasAiMessageOwnership
  @field:IsAiThreadIdle
  var aiMessageId: Long? = null,
  @field:NotNull
//  @field:HasAiAgentPermission
  var aiAgentId: Long? = null,
  // 아래 값들은 AiAssistant의 프로퍼티를 덮어쓴다.
  var model: String? = null,
  var instructions: String? = null,
  var additionalInstructions: String? = null,
  var additionalMessages: Map<String, Any>? = null,
  var temperature: Double? = null,
  var topP: Double? = null,
  var maxPromptTokens: Int? = null,
  var maxCompletionTokens: Int? = null,
  var parallelToolCalls: Boolean? = null,
  var responseFormat: String? = null
)
