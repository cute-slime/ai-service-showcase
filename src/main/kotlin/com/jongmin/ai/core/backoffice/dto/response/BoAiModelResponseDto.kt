package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiModelType
import com.jongmin.ai.core.ReasoningEffort
import java.math.BigDecimal
import java.time.ZonedDateTime

data class BoAiModelResponseDto(
  var id: Long? = null,
  var aiProviderId: Long? = null,
  var aiProvider: String? = null,
  var supportsReasoning: Boolean? = null,
  var reasoningEffort: ReasoningEffort? = null,
  var type: AiModelType? = null,
  var name: String? = null,
  var description: String? = null,
  var maxTokens: Int? = null,
  var inputTokenPrice: BigDecimal? = null,
  var outputTokenPrice: BigDecimal? = null,
  var inputTokenPriceInService: BigDecimal? = null,
  var outputTokenPriceInService: BigDecimal? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null
)

