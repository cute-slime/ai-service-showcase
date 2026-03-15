package com.jongmin.ai.multiagent.executor

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.platform.service.AiAgentService
import com.jongmin.ai.multiagent.model.*
import com.jongmin.ai.multiagent.skill.SkillExecutionContext
import com.jongmin.ai.multiagent.skill.SkillManager
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 에이전트 노드 실행기
 * 개별 에이전트를 실행하고, Thinking + Self-Evaluation + Skills 처리
 */
@Component
class AgentNodeExecutor(
  private val objectMapper: ObjectMapper,
  private val aiAgentService: AiAgentService,
  private val thinkingProcessor: ThinkingProcessor,
  private val selfEvaluationProcessor: SelfEvaluationProcessor,
  private val skillManager: SkillManager,
) {

  /**
   * 에이전트 노드 실행
   */
  fun execute(
    session: JSession,
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): AgentExecutionResult {
    kLogger.info { "에이전트 실행 시작 - nodeId: ${agentNode.id}, agentId: ${agentNode.agentId}" }

    val autonomyConfig = agentNode.autonomyConfig

    // 1. Thinking 단계 (활성화 시)
    if (autonomyConfig?.thinkingEnabled == true) {
      val thinkingResult = thinkingProcessor.performThinking(agentNode, input, context)
      if (thinkingResult.shouldSkip) {
        kLogger.info { "에이전트 스킵 결정 - ${thinkingResult.skipReason}" }
        return createSkipResult(thinkingResult.skipReason)
      }
    }

    // 2. PRE_PROCESS 스킬 실행
    val preProcessedInput = executePreProcessSkills(agentNode, input, context)

    // 3. 기존 AiAgent 조회
    val aiAgent = aiAgentService.findById(agentNode.agentId)

    // 4. 힌트 적용 (재시도 시)
    val enrichedInput = applyHints(preProcessedInput, context.getHints(agentNode.id))

    // 5. 기존 AiAgent 워크플로우 실행
    // TODO: Phase 5에서 실제 실행 로직 구현
    //       현재는 DTE 기반 비동기 실행이므로, 동기 실행을 위한 별도 방법 필요
    //       - SingleWorkflowExecutor 직접 사용
    //       - 또는 AiAgentExecutor에 동기 실행 메서드 추가
    var rawResult: Any = executeAgentWorkflow(session, aiAgent, enrichedInput)

    // 6. ON_DEMAND 스킬 실행 (에이전트 결과 기반 트리거)
    rawResult = executeOnDemandSkills(agentNode, rawResult, context)

    // 7. POST_PROCESS 스킬 실행
    rawResult = executePostProcessSkills(agentNode, rawResult, context)

    // 8. 평가 지표 생성
    val evaluationCriteria = selfEvaluationProcessor.generateEvaluationCriteria(
      agentNode, rawResult
    )

    // 9. Self-Evaluation 수행
    val selfEvaluation = selfEvaluationProcessor.performSelfEvaluation(
      rawResult, evaluationCriteria, agentNode
    )

    // 10. 결과 조립
    val result = AgentExecutionResult(
      output = rawResult,
      evaluationCriteria = evaluationCriteria,
      selfEvaluation = selfEvaluation
    )

    kLogger.info {
      "에이전트 실행 완료 - nodeId: ${agentNode.id}, " +
        "score: ${selfEvaluation?.overallScore ?: "N/A"}, " +
        "skillsExecuted: ${context.getSkillResults(agentNode.id).size}"
    }

    return result
  }

  /**
   * AiAgent 워크플로우 실행
   * TODO: Phase 5에서 실제 구현 완성
   */
  private fun executeAgentWorkflow(
    session: JSession,
    aiAgent: com.jongmin.ai.core.platform.entity.AiAgent,
    input: Any,
  ): Any {
    kLogger.debug { "AiAgent 워크플로우 실행 - agentId: ${aiAgent.id}" }

    // TODO: 실제 워크플로우 실행 구현
    //       현재 AiAgentExecutor는 DTE 기반 비동기 실행
    //       멀티 에이전트에서는 동기 실행이 필요하므로:
    //       1. SingleWorkflowExecutor 직접 사용 또는
    //       2. AiAgentExecutor에 동기 실행 메서드 추가

    // 임시로 입력을 그대로 반환
    return mapOf(
      "agentId" to aiAgent.id,
      "input" to input,
      "status" to "EXECUTED",
      "_note" to "Phase 5에서 실제 워크플로우 실행 구현 예정"
    )
  }

  /**
   * PRE_PROCESS 스킬 실행
   */
  private fun executePreProcessSkills(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): Any {
    val preProcessSkills = skillManager.findSkillsByTriggerMode(
      agentNode, SkillTriggerMode.PRE_PROCESS
    )

    if (preProcessSkills.isEmpty()) return input

    kLogger.debug { "PRE_PROCESS 스킬 실행 - count: ${preProcessSkills.size}" }

    var processedInput = input
    for (skill in preProcessSkills) {
      val skillContext = SkillExecutionContext(
        agentId = agentNode.id,
        input = processedInput,
        previousOutput = null,
        executionContext = context,
        invocationReason = SkillInvocationReason.PRE_PROCESS
      )

      val result = skillManager.executeSkill(skill, skillContext)
      context.storeSkillResult(agentNode.id, result)

      if (result.success && result.output != null) {
        processedInput = result.output
      }
    }

    return processedInput
  }

  /**
   * ON_DEMAND 스킬 실행 (조건 기반 자동 트리거)
   */
  private fun executeOnDemandSkills(
    agentNode: MultiAgentNode,
    output: Any,
    context: MultiAgentExecutionContext,
  ): Any {
    val triggeredSkills = skillManager.findTriggeredSkills(agentNode, output, context)

    if (triggeredSkills.isEmpty()) return output

    kLogger.debug { "ON_DEMAND 스킬 트리거 - count: ${triggeredSkills.size}" }

    var processedOutput = output
    for (skill in triggeredSkills) {
      val skillContext = SkillExecutionContext(
        agentId = agentNode.id,
        input = context.prepareInputForAgent(agentNode.id),
        previousOutput = processedOutput,
        executionContext = context,
        invocationReason = SkillInvocationReason.AUTO_TRIGGERED
      )

      val result = skillManager.executeSkill(skill, skillContext)
      context.storeSkillResult(agentNode.id, result)

      if (result.success && result.output != null) {
        // 스킬 결과를 출력에 병합
        processedOutput = mergeSkillOutput(processedOutput, result.output, skill.id)
      }
    }

    return processedOutput
  }

  /**
   * POST_PROCESS 스킬 실행
   */
  private fun executePostProcessSkills(
    agentNode: MultiAgentNode,
    output: Any,
    context: MultiAgentExecutionContext,
  ): Any {
    val postProcessSkills = skillManager.findSkillsByTriggerMode(
      agentNode, SkillTriggerMode.POST_PROCESS
    )

    if (postProcessSkills.isEmpty()) return output

    kLogger.debug { "POST_PROCESS 스킬 실행 - count: ${postProcessSkills.size}" }

    var processedOutput = output
    for (skill in postProcessSkills) {
      val skillContext = SkillExecutionContext(
        agentId = agentNode.id,
        input = context.prepareInputForAgent(agentNode.id),
        previousOutput = processedOutput,
        executionContext = context,
        invocationReason = SkillInvocationReason.POST_PROCESS
      )

      val result = skillManager.executeSkill(skill, skillContext)
      context.storeSkillResult(agentNode.id, result)

      if (result.success && result.output != null) {
        processedOutput = result.output
      }
    }

    return processedOutput
  }

  /**
   * 스킬 출력 병합
   */
  private fun mergeSkillOutput(original: Any, skillOutput: Any, skillId: String): Any {
    return when {
      original is Map<*, *> && skillOutput is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        val merged = (original as Map<String, Any>).toMutableMap()
        merged["_skill_${skillId}"] = skillOutput
        merged
      }
      else -> mapOf(
        "original" to original,
        "_skill_${skillId}" to skillOutput
      )
    }
  }

  private fun createSkipResult(reason: String?): AgentExecutionResult {
    return AgentExecutionResult(
      output = mapOf("skipped" to true, "reason" to reason),
      evaluationCriteria = EvaluationCriteria(emptyList()),
      selfEvaluation = null
    )
  }

  private fun applyHints(input: Any, hints: List<String>?): Any {
    if (hints.isNullOrEmpty()) return input

    return when (input) {
      is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        (input as Map<String, Any>).toMutableMap().apply {
          put("_hints", hints)
        }
      }
      else -> mapOf("input" to input, "_hints" to hints)
    }
  }

  // =========================================================================
  // Phase 6: Enriched Input 지원 (재시도 가이던스 포함)
  // =========================================================================

  /**
   * EnrichedAgentInput을 처리하는 확장 메서드
   * 재시도 가이던스를 포함한 입력 처리
   */
  fun executeWithEnrichedInput(
    session: JSession,
    agentNode: MultiAgentNode,
    enrichedInput: EnrichedAgentInput,
    context: MultiAgentExecutionContext,
  ): AgentExecutionResult {
    kLogger.info {
      "에이전트 실행 (Enriched) - nodeId: ${agentNode.id}, " +
        "isRetry: ${enrichedInput.isRetry}, " +
        "attempt: ${enrichedInput.retryGuidance?.attemptNumber ?: 1}"
    }

    // 1. 가이던스를 포함하여 입력 준비
    val processedInput = prepareInputWithGuidance(enrichedInput)

    // 2. 기존 execute 메서드 호출
    return execute(session, agentNode, processedInput, context)
  }

  /**
   * 가이던스를 포함한 입력 준비
   */
  private fun prepareInputWithGuidance(enrichedInput: EnrichedAgentInput): Any {
    if (!enrichedInput.isRetry || enrichedInput.retryGuidance == null) {
      return enrichedInput.originalInput
    }

    val guidance = enrichedInput.retryGuidance

    // Map 형태로 변환하여 가이던스 정보 포함
    val inputMap = when (val original = enrichedInput.originalInput) {
      is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        (original as Map<String, Any>).toMutableMap()
      }
      else -> mutableMapOf("input" to original)
    }

    // 가이던스 정보 추가
    inputMap["_retryGuidance"] = mapOf(
      "attemptNumber" to guidance.attemptNumber,
      "previousScore" to guidance.previousScore,
      "targetScore" to guidance.targetScore,
      "issues" to guidance.issues.map { issue ->
        mapOf(
          "criterion" to issue.criterionName,
          "currentScore" to issue.currentScore,
          "targetScore" to issue.targetScore,
          "problem" to issue.description,
          "solution" to issue.improvement
        )
      },
      "suggestions" to guidance.suggestions,
      "priority" to guidance.priority.name
    )

    // 추가 컨텍스트 병합
    guidance.contextEnrichment?.let { inputMap.putAll(it) }

    // 컨텍스트 힌트 추가
    if (enrichedInput.contextHints.isNotEmpty()) {
      inputMap["_contextHints"] = enrichedInput.contextHints
    }

    return inputMap
  }
}
