package com.jongmin.ai.core.platform.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateAiCreativeContent(
  @field:NotBlank
  @field:Size(max = 32)
  var canvasId: String? = null,

  @field:NotBlank
  @field:Size(max = 80)
  var title: String? = null,

  var userRequest: String? = null, // enum

  var userRequestDetail: String? = null,

  var contentType: String? = null, // enum

  var contentLength: String? = null, // enum

  var toneAndManner: String? = null, // enum

  var researchLevel: String? = null, // enum
)
