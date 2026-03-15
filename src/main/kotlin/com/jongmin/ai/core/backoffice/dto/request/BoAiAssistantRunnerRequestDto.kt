package com.jongmin.ai.core.backoffice.dto.request

data class TranslateRequest(
  val requestFields: RequestFields? = null,
  val requestLanguage: List<String>? = null
)

data class RequestFields(
  val title: String? = null,
  val shortDescription: String? = null,
  val description: String? = null,
)
