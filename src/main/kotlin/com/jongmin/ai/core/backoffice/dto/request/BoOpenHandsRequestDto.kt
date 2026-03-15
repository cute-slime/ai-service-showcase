package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsTaskItem
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsCreateConversation(
  val repository: String? = null,
  val gitProvider: String? = null,
  val selectedBranch: String? = "dev",
  val suggestedTask: BoOpenHandsTaskItem? = null,
)
