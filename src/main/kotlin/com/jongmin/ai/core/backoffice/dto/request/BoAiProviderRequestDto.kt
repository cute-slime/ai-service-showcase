package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.data.entity.StatusType
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateAiProvider(
  @field:Schema(description = "ID", type = "Long", requiredMode = RequiredMode.AUTO, hidden = true)
  @field:Null
  var id: Long? = null,

  @field:Schema(description = "제공사명", type = "String", requiredMode = RequiredMode.REQUIRED, example = "OpenAI", maxLength = 40)
  @field:NotBlank
  @field:Size(max = 40)
  var name: String? = null,

  @field:Schema(description = "제공사 설명", type = "String", requiredMode = RequiredMode.NOT_REQUIRED, example = "OpenAI", maxLength = 500)
  @field:Size(max = 500)
  var description: String? = null,

  @field:Schema(
    description = "Endpoint(Base URL)",
    type = "String",
    requiredMode = RequiredMode.NOT_REQUIRED,
    example = "https://api.openai.com/v1",
    maxLength = 240
  )
  @field:Size(max = 240)
  var baseUrl: String? = null,

  @field:Schema(description = "제공사 상태", type = "StatusType", requiredMode = RequiredMode.REQUIRED, example = "ACTIVE")
  @field:NotNull
  var status: StatusType? = null,

  @field:NotNull
  @field:Size(min = 1, max = 10)
  var apiKeys: Set<CommonDto.LongKeyStringKey>? = null,
)

data class PatchAiProvider(
  @field:Schema(description = "ID", type = "Long", requiredMode = RequiredMode.REQUIRED)
  @field:NotNull
  var id: Long? = null,

  @field:Schema(description = "제공사 설명", type = "String", requiredMode = RequiredMode.NOT_REQUIRED, example = "OpenAI", maxLength = 500)
  @field:Size(max = 500)
  var description: String? = null,

  @field:Schema(
    description = "Endpoint(Base URL)",
    type = "String",
    requiredMode = RequiredMode.NOT_REQUIRED,
    example = "https://api.openai.com/v1",
    maxLength = 240
  )
  @field:Size(max = 240)
  var baseUrl: String? = null,

  @field:Schema(description = "제공사 상태", type = "StatusType", requiredMode = RequiredMode.NOT_REQUIRED, example = "ACTIVE")
  var status: StatusType? = null,

  @field:Size(min = 1, max = 10)
  var apiKeys: Set<CommonDto.LongKeyStringKey>? = null,
)
