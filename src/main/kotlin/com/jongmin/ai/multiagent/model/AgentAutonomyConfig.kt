package com.jongmin.ai.multiagent.model

/**
 * 에이전트 자율성 설정
 */
data class AgentAutonomyConfig(
  val llmConfig: AgentLlmConfig? = null,
  val thinkingEnabled: Boolean = true,         // Thinking 기능 활성화
  val conversationEnabled: Boolean = false,    // 다른 에이전트와 대화 가능
)
