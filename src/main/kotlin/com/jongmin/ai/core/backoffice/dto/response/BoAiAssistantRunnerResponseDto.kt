package com.jongmin.ai.core.backoffice.dto.response

data class TranslationItem(
  val name: String? = null,
  val description: String? = null,
)

data class SubjectTranslationItem(
  val title: String? = null,
  val shortDescription: String? = null,
  val description: String? = null,
)

data class QuestionBankTranslationItem(
  val question: String? = null,
  val body: String? = null,
  val explanations: String? = null,
  val source: String? = null,
)
