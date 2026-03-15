package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.*
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 재시도 가이던스 생성기
 * 오케스트레이터 LLM을 사용하여 개선 지침 생성 (멘토링 패턴)
 */
@Component
class RetryGuidanceGenerator(
  private val objectMapper: ObjectMapper,
  private val chatModelProvider: ChatModelProvider,
) {

  /**
   * 재시도 가이던스 생성
   */
  fun generateGuidance(
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    attemptNumber: Int,
    orchestratorLlmConfig: AgentLlmConfig,
    passThreshold: Double,
  ): RetryGuidance {
    kLogger.info { "재시도 가이던스 생성 - agent: ${agentNode.id}, attempt: $attemptNumber" }

    return try {
      val prompt = buildGuidancePrompt(agentNode, result, attemptNumber, passThreshold)
      val llm = chatModelProvider.getChatModel(orchestratorLlmConfig)
      val response = llm.chat(UserMessage.from(prompt)).aiMessage()?.text() ?: ""

      parseGuidanceResponse(response, agentNode.id, attemptNumber, result, passThreshold)
    } catch (e: Exception) {
      kLogger.error(e) { "가이던스 생성 실패 - agent: ${agentNode.id}" }
      createDefaultGuidance(agentNode.id, attemptNumber, result, passThreshold)
    }
  }

  private fun buildGuidancePrompt(
    agentNode: MultiAgentNode,
    result: AgentExecutionResult,
    attemptNumber: Int,
    passThreshold: Double,
  ): String {
    val selfEval = result.selfEvaluation
    val criteria = result.evaluationCriteria

    return """
      당신은 멀티 에이전트 시스템의 오케스트레이터입니다.
      하위 에이전트가 작업을 수행했지만 기준 점수에 미달했습니다.
      에이전트가 다음 시도에서 성공할 수 있도록 구체적인 개선 지침을 작성하세요.

      ## 에이전트 정보
      - ID: ${agentNode.id}
      - 역할: ${agentNode.capability?.summary ?: agentNode.name}
      - 재시도 횟수: $attemptNumber

      ## 평가 결과
      - 종합 점수: ${selfEval?.overallScore ?: 0.0}
      - 목표 점수: $passThreshold
      - 기준별 점수: ${selfEval?.criteriaScores ?: emptyMap<String, Double>()}
      - 평가 근거: ${selfEval?.reasoning ?: "없음"}

      ## 평가 기준
      ${criteria.criteria.joinToString("\n") { "- ${it.name}: ${it.description}" }}

      ## 에이전트 출력
      ${result.output}

      ---

      다음 JSON 형식으로 개선 지침을 작성하세요:
      {
        "issues": [
          {
            "criterionName": "미달 기준명",
            "currentScore": 현재점수,
            "targetScore": 목표점수,
            "description": "무엇이 문제인지",
            "improvement": "어떻게 개선해야 하는지"
          }
        ],
        "suggestions": [
          "구체적인 개선 제안 1",
          "구체적인 개선 제안 2"
        ],
        "contextEnrichment": {
          "추가컨텍스트키": "값"
        },
        "priority": "LOW|MEDIUM|HIGH|CRITICAL"
      }

      주의사항:
      - 추상적인 조언이 아닌 구체적이고 실행 가능한 지침을 작성하세요
      - 에이전트가 즉시 적용할 수 있는 예시를 포함하세요
      - 가장 개선이 필요한 항목부터 우선순위로 정렬하세요
    """.trimIndent()
  }

  private fun parseGuidanceResponse(
    response: String,
    agentId: String,
    attemptNumber: Int,
    result: AgentExecutionResult,
    passThreshold: Double,
  ): RetryGuidance {
    return try {
      val jsonContent = extractJsonFromResponse(response)
      val parsed = objectMapper.readValue(jsonContent, GuidanceResponseDto::class.java)

      RetryGuidance(
        agentId = agentId,
        attemptNumber = attemptNumber,
        previousScore = result.selfEvaluation?.overallScore ?: 0.0,
        targetScore = passThreshold,
        issues = parsed.issues,
        suggestions = parsed.suggestions,
        contextEnrichment = parsed.contextEnrichment,
        priority = try {
          GuidancePriority.valueOf(parsed.priority.uppercase())
        } catch (e: Exception) {
          GuidancePriority.MEDIUM
        }
      )
    } catch (e: Exception) {
      kLogger.warn { "가이던스 파싱 실패, 기본 가이던스 생성: ${e.message}" }
      createDefaultGuidance(agentId, attemptNumber, result, passThreshold)
    }
  }

  /**
   * 기본 가이던스 생성 (파싱 실패 시)
   */
  private fun createDefaultGuidance(
    agentId: String,
    attemptNumber: Int,
    result: AgentExecutionResult,
    passThreshold: Double,
  ): RetryGuidance {
    return RetryGuidance(
      agentId = agentId,
      attemptNumber = attemptNumber,
      previousScore = result.selfEvaluation?.overallScore ?: 0.0,
      targetScore = passThreshold,
      issues = emptyList(),
      suggestions = result.selfEvaluation?.suggestions ?: listOf("결과물의 품질을 개선하세요"),
      contextEnrichment = null,
      priority = GuidancePriority.MEDIUM
    )
  }

  /**
   * LLM 응답에서 JSON 추출
   */
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

  /**
   * 가이던스 응답 DTO (파싱용)
   */
  private data class GuidanceResponseDto(
    val issues: List<GuidanceIssue> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val contextEnrichment: Map<String, Any>? = null,
    val priority: String = "MEDIUM",
  )
}
