package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiAssistantType
import java.time.ZonedDateTime

data class BoAiAssistantItem(
  var id: Long? = null,
  var name: String? = null,
  var type: AiAssistantType? = null,
  var description: String? = null,
  var provider: String? = null,
  var modelId: Long? = null,
  var model: String? = null,
  var apiKeyId: Long? = null,
  var apiKey: String? = null,
  var primaryModelId: Long? = null,
  var primaryModel: String? = null,
  var primaryApiKeyId: Long? = null,
  var primaryApiKey: String? = null,
  var restoreModelId: Long? = null,
  var restoreModel: String? = null,
  var restoreApiKeyId: Long? = null,
  var restoreApiKey: String? = null,
  var instructions: String? = null,
  var temperature: Double? = null,
  var topP: Double? = null,
  var responseFormat: String? = null,
  var maxTokens: Int? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)
