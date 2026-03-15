package com.jongmin.ai.multiagent.executor

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.multiagent.entity.MultiAgentWorkflow
import com.jongmin.ai.multiagent.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.UUID

private val kLogger = KotlinLogging.logger {}

/**
 * 멀티 에이전트 워크플로우 실행 엔진
 * OrchestratorAgent를 통해 전체 워크플로우를 조율
 */
@Component
class MultiAgentEngine(
  private val orchestratorAgent: OrchestratorAgent,
) {

  // 오케스트레이터 기본 LLM 설정
  private val defaultOrchestratorLlmConfig = AgentLlmConfig(
    provider = "anthropic",
    model = "claude-sonnet-4-20250514",
    temperature = 0.3,  // 판단용이므로 낮은 temperature
  )

  /**
   * 워크플로우 실행 컨텍스트 생성
   */
  fun createExecutionContext(
    workflow: MultiAgentWorkflow,
    initialInput: Any? = null
  ): MultiAgentExecutionContext {
    val executionId = UUID.randomUUID().toString()

    kLogger.info { "멀티 에이전트 워크플로우 컨텍스트 생성 - workflowId: ${workflow.id}, executionId: $executionId" }

    return MultiAgentExecutionContext(
      workflowId = workflow.id,
      executionId = executionId,
      agents = workflow.agents,
      edges = workflow.edges,
      orchestratorConfig = workflow.orchestratorConfig,
    ).apply {
      this.initialInput = initialInput
    }
  }

  /**
   * 최대 실행 사이클 계산
   */
  fun calculateMaxCycles(context: MultiAgentExecutionContext): Int {
    return context.orchestratorConfig.getEffectiveMaxExecutionCycles(context.agents.size)
  }

  /**
   * 워크플로우 실행
   * OrchestratorAgent를 통해 전체 워크플로우 조율
   */
  fun execute(
    session: JSession,
    workflow: MultiAgentWorkflow,
    input: Any,
    orchestratorLlmConfig: AgentLlmConfig? = null,
  ): AgentExecutionResult {
    val context = createExecutionContext(workflow, input)
    val maxCycles = calculateMaxCycles(context)

    kLogger.info {
      "멀티 에이전트 워크플로우 실행 시작 - " +
        "workflowId: ${workflow.id}, agents: ${context.agents.size}, maxCycles: $maxCycles"
    }

    // OrchestratorAgent로 워크플로우 실행 위임
    val effectiveConfig = orchestratorLlmConfig ?: defaultOrchestratorLlmConfig
    val result = orchestratorAgent.execute(session, input, context, effectiveConfig)

    kLogger.info {
      "멀티 에이전트 워크플로우 실행 완료 - " +
        "workflowId: ${workflow.id}, executedAgents: ${context.getAllAgentResults().size}"
    }

    return result
  }

  /**
   * 워크플로우 실행 (비동기 버전)
   * DTE 통합 시 사용
   */
  fun executeAsync(
    session: JSession,
    workflow: MultiAgentWorkflow,
    input: Any,
    orchestratorLlmConfig: AgentLlmConfig? = null,
    callback: (AgentExecutionResult) -> Unit,
  ) {
    // TODO: Phase 6에서 비동기 실행 및 이벤트 스트리밍 구현
    val result = execute(session, workflow, input, orchestratorLlmConfig)
    callback(result)
  }
}
