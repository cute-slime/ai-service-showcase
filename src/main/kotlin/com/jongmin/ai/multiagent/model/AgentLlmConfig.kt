package com.jongmin.ai.multiagent.model

/**
 * 에이전트 LLM 설정
 */
data class AgentLlmConfig(
  val provider: String = "openai",   // "openai", "anthropic", "ollama"
  val model: String = "gpt-4o",      // "gpt-4o", "claude-sonnet-4-20250514"
  val temperature: Double = 0.7,
  val maxTokens: Int? = null,
  val systemPrompt: String? = null,
)
