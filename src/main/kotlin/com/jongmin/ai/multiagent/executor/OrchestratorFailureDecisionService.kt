package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.AgentExecutionResult
import com.jongmin.ai.multiagent.model.AgentLlmConfig
import com.jongmin.ai.multiagent.model.MultiAgentNode
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 오케스트레이터 실패 의사결정 전담 서비스
 *
 * 실패 상황에서 프롬프트 생성/LLM 판단/결정 파싱 책임을 분리한다.
 */
@Component
class OrchestratorFailureDecisionService(
  private val objectMapper: ObjectMapper,
  private val chatModelProvider: ChatModelProvider,
) {
  private val kLogger = KotlinLogging.logger {}

  fun decide(
    failedAgent: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext,
    orchestratorLlmConfig: AgentLlmConfig,
  ): OrchestratorDecision {
    kLogger.warn { "에이전트 실패 처리 - ${failedAgent.id}" }

    val prompt = buildErrorHandlingPrompt(failedAgent, result, context)
    val llm = chatModelProvider.getChatModel(orchestratorLlmConfig)
    val response = llm.chat(UserMessage.from(prompt)).aiMessage()?.text() ?: ""

    return parseDecision(response)
  }

  private fun buildErrorHandlingPrompt(
    failedAgent: MultiAgentNode,
    result: AgentExecutionResult,
    context: MultiAgentExecutionContext,
  ): String {
    return """
      에이전트 실행이 실패했습니다. 다음 행동을 결정하세요.

      ## 실패한 에이전트
      - ID: ${failedAgent.id}
      - 역할: ${failedAgent.capability?.summary ?: failedAgent.name}

      ## 결과
      - 점수: ${result.selfEvaluation?.overallScore ?: "N/A"}
      - 이유: ${result.selfEvaluation?.reasoning ?: "N/A"}

      ## 재시도 현황
      - 현재 재시도: ${context.getRetryCount(failedAgent.id)}
      - 최대 재시도: ${context.orchestratorConfig.maxRetryPerAgent}

      ## 가능한 행동
      1. RETRY: 같은 에이전트 재시도
      2. DELEGATE: 다른 에이전트로 위임
      3. SKIP: 이 에이전트 스킵
      4. ABORT: 워크플로우 중단
      5. REQUEST_INTERVENTION: 사용자 개입 요청

      JSON 형식으로 응답:
      {
        "action": "RETRY|DELEGATE|SKIP|ABORT|REQUEST_INTERVENTION",
        "targetAgentId": "위임 시 대상 에이전트 ID",
        "message": "결정 이유"
      }
    """.trimIndent()
  }

  private fun parseDecision(response: String): OrchestratorDecision {
    return try {
      val jsonContent = extractJsonFromResponse(response)
      objectMapper.readValue(jsonContent, OrchestratorDecision::class.java)
    } catch (e: Exception) {
      kLogger.warn { "결정 파싱 실패, 기본값(RETRY) 사용: ${e.message}" }
      OrchestratorDecision(action = OrchestratorAction.RETRY)
    }
  }

  private fun extractJsonFromResponse(response: String): String {
    val codeBlockPattern = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
    val match = codeBlockPattern.find(response)

    return if (match != null) {
      match.groupValues[1].trim()
    } else {
      val jsonPattern = Regex("""\{[\s\S]*\}""")
      jsonPattern.find(response)?.value ?: response
    }
  }
}
