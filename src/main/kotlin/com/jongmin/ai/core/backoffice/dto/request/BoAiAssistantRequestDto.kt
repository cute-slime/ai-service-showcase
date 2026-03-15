package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.backoffice.dto.request.validator.ExistAiModelId
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.*

data class CreateAiAssistant(
  @field:Null
  var id: Long?,
  @field:Null
  var accountId: Long?,
  @field:Null
  var ownerId: Long?,

  @field:NotBlank
  @field:Size(max = 40)
  var name: String?,

  @field:NotNull
  var type: AiAssistantType?,

  @field:Size(max = 500)
  var description: String?,

  @field:NotNull
  @field:ExistAiModelId
  var primaryModelId: Long?,
  @field:NotNull
  var primaryApiKeyId: Long?,
  @field:Null
  var modelId: Long?,
  @field:Null
  var apiKeyId: Long?,

  @field:Size(max = 100_000)
  var instructions: String?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("2.0")
  var temperature: Double?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("1.0")
  var topP: Double?,

  @field:Size(max = 100)
  var responseFormat: String?,

  var maxTokens: Int?,

  var status: StatusType?
)


data class PatchAiAssistant(
  @field:NotNull
  var id: Long?,

  @field:Size(max = 40)
  var name: String?,

  var type: AiAssistantType?,

  @field:Size(max = 500)
  var description: String?,

  @field:ExistAiModelId
  var modelId: Long?,
  var apiKeyId: Long?,

  @field:ExistAiModelId
  var primaryModelId: Long?,
  var primaryApiKeyId: Long?,

  @field:Size(max = 100_000)
  var instructions: String?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("2.0")
  var temperature: Double?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("1.0")
  var topP: Double?,

  @field:Size(max = 100)
  var responseFormat: String?,

  var maxTokens: Int?,

  var status: StatusType?
)

// :----------------------------------------------------------------------------------------------------------------
data class ExecuteModel(
  @field:NotBlank
  var canvasId: String?,

  @field:Positive
  var aiAssistantId: Long? = null,

  @field:ExistAiModelId
  var modelId: Long?,
  var apiKeyId: Long?,

  @field:Size(max = 100_000)
  var instructions: String?,

  @field:NotBlank
  @field:Size(max = 100_000)
  var question: String?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("2.0")
  var temperature: Double?,

  @field:DecimalMin("0.0")
  @field:DecimalMax("1.0")
  var topP: Double?,

  @field:Size(max = 100)
  var responseFormat: String?,

  var maxTokens: Int?,
) {
  @AssertTrue(message = "aiAssistantId 또는 (modelId, apiKeyId, instructions) 조합이 필요합니다.")
  fun hasValidExecutionTarget(): Boolean {
    if (aiAssistantId != null) {
      return true
    }

    return modelId != null && apiKeyId != null && !instructions.isNullOrBlank()
  }
}

data class ExecuteAiAssistant(
  @field:NotBlank
  @field:Size(max = 400_000)
  var question: String? = null
)
