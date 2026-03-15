package com.jongmin.ai.core

/**
 * LLM Provider 정책 규칙
 */
object LlmProviderRules {
  private val BASE_URL_REQUIRED_PROVIDERS = setOf(
    "zai",
    "z.ai",
    "xai",
    "x.ai",
    "deepseek",
    "cerebras",
    "openrouter",
    "kluster",
    "vllm",
    "lm studio",
    "lmstudio"
  )

  fun requiresBaseUrl(providerName: String?): Boolean {
    return providerName.normalizeProviderName() in BASE_URL_REQUIRED_PROVIDERS
  }
}

fun String?.normalizeProviderName(): String = this?.trim()?.lowercase().orEmpty()
