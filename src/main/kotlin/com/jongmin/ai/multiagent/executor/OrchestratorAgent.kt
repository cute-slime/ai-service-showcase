package com.jongmin.ai.multiagent.executor

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.multiagent.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 오케스트레이터 에이전트
 * 멀티 에이전트 워크플로우의 지휘자 (진입점 + 반출점 + 에러 핸들링 + 동적 라우팅 + 멘토링 패턴)
 */
@Component
class OrchestratorAgent(
  private val agentNodeExecutor: AgentNodeExecutor,
  private val skillHintService: OrchestratorSkillHintService,
  private val failureDecisionService: OrchestratorFailureDecisionService,
  private val retryGuidanceGenerator: RetryGuidanceGenerator,
) {

  /**
   * 🔑 워크플로우 진입점 - 외부에서 호출되는 메인 메서드
   */
  fun execute(
    session: JSession,
    input: Any,
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): AgentExecutionResult {
    kLogger.info { "멀티 에이전트 워크플로우 시작 - executionId: ${context.executionId}" }

    val config = context.orchestratorConfig
    val maxCycles = config.getEffectiveMaxExecutionCycles(context.agents.size)
    var currentCycle = 0

    try {
      // 1. 시작 에이전트 결정
      val startAgent = determineStartAgent(input, context, orchestratorLlmConfig)
      kLogger.info { "시작 에이전트 결정: ${startAgent.id}" }

      // 1-1. 시작 에이전트에 대한 스킬 힌트 사전 준비
      skillHintService.prepareSkillHintsForAgent(startAgent, input, context)

      // 2. 에이전트 실행 루프
      var currentAgent: MultiAgentNode? = startAgent
      var currentInput: Any = input

      while (currentAgent != null && currentCycle < maxCycles) {
        // 로컬 변수로 캐시 (스마트 캐스트 활성화)
        val agent = currentAgent!!
        currentCycle++
        kLogger.info { "실행 사이클 $currentCycle/$maxCycles - 에이전트: ${agent.id}" }

        // 2-1. 에이전트 실행
        val result = executeAgentWithRetry(session, agent, currentInput, context, orchestratorLlmConfig)

        // 2-2. 결과 검증
        val validation = validateResult(result, config)
        if (validation == ValidationResult.FAIL) {
          val decision = failureDecisionService.decide(agent, result, context, orchestratorLlmConfig)
          when (decision.action) {
            OrchestratorAction.RETRY -> continue  // 재시도는 executeAgentWithRetry에서 처리됨
            OrchestratorAction.DELEGATE -> {
              currentAgent = context.agents.find { it.id == decision.targetAgentId }
              continue
            }
            OrchestratorAction.SKIP -> {
              currentAgent = determineNextAgent(agent, result, context, orchestratorLlmConfig)
              currentInput = result.output
              continue
            }
            OrchestratorAction.ABORT -> {
              kLogger.warn { "워크플로우 중단 결정" }
              return createAbortResult(decision.message)
            }
            OrchestratorAction.REQUEST_INTERVENTION -> {
              kLogger.info { "사용자 개입 요청" }
              return createInterventionResult(agent, result, decision.message)
            }
          }
        }

        // 2-3. 완료 조건 판단
        if (isWorkflowComplete(result, context)) {
          kLogger.info { "워크플로우 완료 조건 충족" }
          break
        }

        // 2-4. 다음 에이전트 결정
        currentAgent = determineNextAgent(agent, result, context, orchestratorLlmConfig)
        currentInput = result.output

        // 2-5. 다음 에이전트에 대한 스킬 힌트 사전 준비
        currentAgent?.let { nextAgent ->
          skillHintService.prepareSkillHintsForAgent(nextAgent, currentInput, context)
        }
      }

      // 3. 최종 결과 반환 (반출점)
      return collectFinalResult(context, orchestratorLlmConfig)

    } catch (e: Exception) {
      kLogger.error(e) { "워크플로우 실행 실패" }
      return handleWorkflowError(e, context)
    }
  }

  /**
   * 에이전트 실행 (가이던스 기반 재시도 포함)
   * Phase 6: 멘토링 패턴 - 고지능 모델이 저지능 에이전트에게 개선 지침 제공
   */
  private fun executeAgentWithRetry(
    session: JSession,
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): AgentExecutionResult {
    val config = context.orchestratorConfig
    val passThreshold = config.evaluationPassThreshold

    // 1. 첫 실행 (EnrichedAgentInput으로 래핑)
    var enrichedInput = EnrichedAgentInput.forFirstAttempt(input)
    var result = agentNodeExecutor.executeWithEnrichedInput(session, agentNode, enrichedInput, context)
    context.storeAgentResult(agentNode.id, result)

    // 2. 재시도 루프 (가이던스 기반)
    while (context.getRetryCount(agentNode.id) < config.maxRetryPerAgent) {
      // 2-1. 결과 검증
      val validation = validateResult(result, config)
      if (validation != ValidationResult.FAIL) {
        kLogger.info { "에이전트 통과 - ${agentNode.id}, score: ${result.selfEvaluation?.overallScore}" }
        return result
      }

      // 2-2. 재시도 횟수 증가
      context.incrementRetryCount(agentNode.id)
      val currentRetryCount = context.getRetryCount(agentNode.id)
      kLogger.info {
        "에이전트 ${agentNode.id} 재시도 ($currentRetryCount/${config.maxRetryPerAgent}) - " +
          "현재 점수: ${result.selfEvaluation?.overallScore}, 목표 점수: $passThreshold"
      }

      // 2-3. 가이던스 생성 (활성화 시)
      enrichedInput = if (config.retryWithGuidance) {
        val guidance = retryGuidanceGenerator.generateGuidance(
          agentNode = agentNode,
          result = result,
          attemptNumber = currentRetryCount,
          orchestratorLlmConfig = orchestratorLlmConfig,
          passThreshold = passThreshold
        )
        kLogger.info {
          "가이던스 생성 완료 - agent: ${agentNode.id}, " +
            "issues: ${guidance.issues.size}, suggestions: ${guidance.suggestions.size}"
        }
        EnrichedAgentInput.forRetry(input, guidance, result)
      } else {
        // 가이던스 없이 단순 재시도
        EnrichedAgentInput.forFirstAttempt(input)
      }

      // 2-4. 재시도 실행
      result = agentNodeExecutor.executeWithEnrichedInput(session, agentNode, enrichedInput, context)
      context.storeAgentResult(agentNode.id, result)
    }

    // 3. 최대 재시도 초과 - 마지막 결과 반환
    kLogger.warn {
      "에이전트 재시도 횟수 초과 - ${agentNode.id}, " +
        "최종 점수: ${result.selfEvaluation?.overallScore}"
    }
    return result
  }

  /**
   * 시작 에이전트 결정
   */
  fun determineStartAgent(
    input: Any,
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): MultiAgentNode {
    val config = context.orchestratorConfig

    if (config.routingMode == RoutingMode.STATIC) {
      // STATIC: 첫 번째 에이전트 (또는 Edge 기반)
      return context.agents.first()
    }

    // DYNAMIC: 역량 매칭
    val scoredAgents = context.agents.map { agent ->
      val score = skillHintService.calculateCapabilityScoreWithSkills(input, agent, context)
      agent to score
    }

    return scoredAgents
      .maxByOrNull { it.second }?.first
      ?: context.agents.first()
  }

  /**
   * 다음 에이전트 결정
   */
  fun determineNextAgent(
    currentAgent: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): MultiAgentNode? {
    val candidates = context.getNextAgentCandidates(currentAgent.id)
    if (candidates.isEmpty()) return null

    val config = context.orchestratorConfig

    if (config.routingMode == RoutingMode.STATIC) {
      return candidates.first()
    }

    // DYNAMIC: 결과 분석하여 최적 에이전트 선택
    val scoredCandidates = candidates.map { agent ->
      val score = skillHintService.calculateCapabilityScoreWithSkills(result.output, agent, context)
      agent to score
    }

    return scoredCandidates
      .filter { it.second >= config.dynamicRoutingConfig.skipThreshold }
      .maxByOrNull { it.second }?.first
  }

  /**
   * 결과 검증
   */
  private fun validateResult(
    result: AgentExecutionResult,
    config: OrchestratorConfig,
  ): ValidationResult {
    val selfEval = result.selfEvaluation ?: return ValidationResult.PASS
    val threshold = result.evaluationCriteria.passThreshold

    return when {
      selfEval.overallScore >= threshold -> ValidationResult.PASS
      selfEval.overallScore >= threshold * 0.7 -> ValidationResult.WARN
      else -> ValidationResult.FAIL
    }
  }

  /**
   * 완료 조건 판단
   */
  fun isWorkflowComplete(
    lastResult: AgentExecutionResult,
    context: MultiAgentExecutionContext,
  ): Boolean {
    val hasNext = context.hasUnexecutedAgents()
    val requiredDone = context.allRequiredAgentsExecuted()
    val scoreOk = (lastResult.selfEvaluation?.overallScore ?: 0.0) >= 0.8

    return !hasNext || (requiredDone && scoreOk)
  }

  /**
   * 🔑 최종 결과 수집 (반출점)
   */
  fun collectFinalResult(
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): AgentExecutionResult {
    val allResults = context.getAllAgentResults()

    kLogger.info { "최종 결과 수집 - 실행된 에이전트: ${allResults.keys.joinToString()}" }

    // 결과 조합
    val finalOutput = combineResults(allResults)

    // 전체 워크플로우에 대한 평가
    val finalEvaluation = evaluateOverallWorkflow(allResults, orchestratorLlmConfig)

    return AgentExecutionResult(
      output = finalOutput,
      evaluationCriteria = finalEvaluation.criteria,
      selfEvaluation = finalEvaluation.evaluation
    )
  }

  private fun combineResults(results: Map<String, AgentExecutionResult>): Map<String, Any> {
    return results.mapValues { it.value.output }
  }

  private fun evaluateOverallWorkflow(
    results: Map<String, AgentExecutionResult>,
    orchestratorLlmConfig: AgentLlmConfig,
  ): FinalEvaluation {
    // 간단 버전: 개별 점수 평균
    val scores = results.values.mapNotNull { it.selfEvaluation?.overallScore }
    val avgScore = if (scores.isNotEmpty()) scores.average() else 0.5

    return FinalEvaluation(
      criteria = EvaluationCriteria(
        criteria = listOf(Criterion("전체 완성도", "모든 에이전트 결과의 종합 평가")),
        passThreshold = 0.7
      ),
      evaluation = SelfEvaluation(
        overallScore = avgScore,
        criteriaScores = mapOf("전체 완성도" to avgScore),
        reasoning = "개별 에이전트 점수 평균: $avgScore",
        confidence = 0.8
      )
    )
  }

  private fun createAbortResult(message: String?): AgentExecutionResult {
    return AgentExecutionResult(
      output = mapOf("status" to "ABORTED", "message" to message),
      evaluationCriteria = EvaluationCriteria(emptyList()),
      selfEvaluation = null
    )
  }

  private fun createInterventionResult(
    agent: MultiAgentNode,
    result: AgentExecutionResult,
    message: String?,
  ): AgentExecutionResult {
    return AgentExecutionResult(
      output = mapOf(
        "status" to "INTERVENTION_REQUIRED",
        "agentId" to agent.id,
        "lastResult" to result.output,
        "message" to message
      ),
      evaluationCriteria = EvaluationCriteria(emptyList()),
      selfEvaluation = null
    )
  }

  private fun handleWorkflowError(e: Exception, context: MultiAgentExecutionContext): AgentExecutionResult {
    return AgentExecutionResult(
      output = mapOf(
        "status" to "ERROR",
        "error" to e.message,
        "executedAgents" to context.getAllAgentResults().keys.toList()
      ),
      evaluationCriteria = EvaluationCriteria(emptyList()),
      selfEvaluation = null
    )
  }

  /**
   * 에이전트에게 추천할 스킬 목록 생성
   *
   * 외부 호출 호환을 위해 유지하고, 실제 로직은 OrchestratorSkillHintService에 위임한다.
   */
  fun suggestSkillsForAgent(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): List<SkillHint> {
    return skillHintService.suggestSkillsForAgent(agentNode, input, context)
  }

}

/**
 * 오케스트레이터 결정
 */
data class OrchestratorDecision(
  val action: OrchestratorAction,
  val targetAgentId: String? = null,
  val message: String? = null,
)

/**
 * 오케스트레이터 액션
 */
enum class OrchestratorAction {
  RETRY,
  DELEGATE,
  SKIP,
  ABORT,
  REQUEST_INTERVENTION,
}

/**
 * 최종 평가 결과
 */
data class FinalEvaluation(
  val criteria: EvaluationCriteria,
  val evaluation: SelfEvaluation?,
)
