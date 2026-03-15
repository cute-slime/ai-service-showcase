package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiModelType
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.core.backoffice.dto.request.validator.ExistAiProviderId
import jakarta.validation.constraints.*
import java.math.BigDecimal

data class CreateAiModel(
  @field:Null
  var id: Long?,

  @field:Null
  var accountId: Long?,

  @field:NotBlank
  @field:Size(max = 40)
  var name: String?,

  @field:Size(max = 500)
  var description: String?,

  @field:NotNull
  @field:ExistAiProviderId
  var aiProviderId: Long?,

  @field:NotNull
  var status: StatusType?,

  var supportsReasoning: Boolean? = false,

  var reasoningEffort: ReasoningEffort? = null,

  @field:NotNull
  var type: AiModelType?,

  @field:Max(4194304)
  @field:Min(0)
  var maxTokens: Int?,

  @field:NotNull
  @field:Positive
  @field:DecimalMax("10.0")
  @field:DecimalMin("0.00001")
  var inputTokenPrice: BigDecimal?,

  @field:NotNull
  @field:Positive
  @field:DecimalMax("10.0")
  @field:DecimalMin("0.00001")
  var outputTokenPrice: BigDecimal?,

  @field:NotNull
  @field:Positive
  @field:DecimalMax("13.0")
  @field:DecimalMin("0.000013")
  var inputTokenPriceInService: BigDecimal?,

  @field:NotNull
  @field:Positive
  @field:DecimalMax("13.0")
  @field:DecimalMin("0.000013")
  var outputTokenPriceInService: BigDecimal?
)

data class PatchAiModel(
  @field:NotNull
  var id: Long?,

  @field:Size(max = 100)
  var name: String?,

  @field:Size(max = 500)
  var description: String?,

  var status: StatusType?,

  var supportsReasoning: Boolean?,

  var reasoningEffort: ReasoningEffort?,

  @field:Max(4194304)
  @field:Min(0)
  var maxTokens: Int?,

  @field:Positive
  @field:DecimalMax("10.0")
  @field:DecimalMin("0.00001")
  var inputTokenPrice: BigDecimal?,

  @field:Positive
  @field:DecimalMax("10.0")
  @field:DecimalMin("0.00001")
  var outputTokenPrice: BigDecimal?,

  @field:Positive
  @field:DecimalMax("13.0")
  @field:DecimalMin("0.000013")
  var inputTokenPriceInService: BigDecimal?,

  @field:Positive
  @field:DecimalMax("13.0")
  @field:DecimalMin("0.000013")
  var outputTokenPriceInService: BigDecimal?
)
