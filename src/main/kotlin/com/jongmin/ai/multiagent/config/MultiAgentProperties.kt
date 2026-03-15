package com.jongmin.ai.multiagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 멀티 에이전트 시스템 설정
 *
 * application.yaml에서 app.multi-agent 프리픽스로 설정
 */
@ConfigurationProperties(prefix = "app.multi-agent")
data class MultiAgentProperties(
  /**
   * 기본 오케스트레이터 LLM 설정
   */
  val orchestratorLlm: OrchestratorLlmProperties = OrchestratorLlmProperties(),

  /**
   * 기본 실행 설정
   */
  val defaults: MultiAgentDefaults = MultiAgentDefaults(),

  /**
   * 비용 추적 설정
   */
  val costTracking: CostTrackingProperties = CostTrackingProperties(),
)

/**
 * 오케스트레이터 LLM 설정
 */
data class OrchestratorLlmProperties(
  /**
   * LLM 프로바이더 (anthropic, openai, ollama 등)
   */
  val provider: String = "anthropic",

  /**
   * 모델 이름
   */
  val model: String = "claude-sonnet-4-20250514",

  /**
   * Temperature (창의성 조절, 0.0~1.0)
   */
  val temperature: Double = 0.3,

  /**
   * 최대 응답 토큰 수
   */
  val maxTokens: Int = 4096,
)

/**
 * 기본 실행 설정
 */
data class MultiAgentDefaults(
  /**
   * 최대 실행 사이클 (null이면 에이전트 수 × multiplier로 동적 계산)
   */
  val maxExecutionCycles: Int? = null,

  /**
   * 실행 사이클 배수 (동적 계산 시 사용)
   */
  val executionCyclesMultiplier: Double = 3.0,

  /**
   * 에이전트 간 최대 대화 턴 수
   */
  val maxConversationTurns: Int = 10,

  /**
   * 에이전트별 최대 재시도 횟수
   */
  val maxRetryPerAgent: Int = 3,

  /**
   * Self-Evaluation 통과 임계값 (0.0~1.0)
   */
  val evaluationPassThreshold: Double = 0.7,
)

/**
 * 비용 추적 설정
 */
data class CostTrackingProperties(
  /**
   * 비용 추적 활성화 여부
   */
  val enabled: Boolean = true,

  /**
   * 기본 예산 (null이면 무제한)
   */
  val defaultBudget: Double? = null,

  /**
   * 에이전트별 비용 제한 (null이면 무제한)
   */
  val perAgentCostLimit: Double? = null,
)
