package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.EvaluationCriteria
import com.jongmin.ai.multiagent.model.MultiAgentNode
import com.jongmin.ai.multiagent.model.SelfEvaluation
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * Self-Evaluation 프로세서
 * 에이전트가 자체 평가 지표를 생성하고, 결과를 평가
 */
@Component
class SelfEvaluationProcessor(
  private val objectMapper: ObjectMapper,
  private val chatModelProvider: ChatModelProvider,
) {

  /**
   * 평가 지표 생성 (ACT 단계에서 결과와 함께 생성)
   */
  fun generateEvaluationCriteria(
    agentNode: MultiAgentNode,
    result: Any,
  ): EvaluationCriteria {
    kLogger.debug { "평가 지표 생성 - agentId: ${agentNode.id}" }

    val llmConfig = agentNode.autonomyConfig?.llmConfig
      ?: return EvaluationCriteria(emptyList())  // LLM 없으면 빈 지표

    return try {
      val prompt = buildCriteriaPrompt(agentNode, result)
      val llm = chatModelProvider.getChatModel(llmConfig)
      val response = llm.chat(UserMessage.from(prompt)).aiMessage()?.text() ?: ""

      parseCriteriaResponse(response)
    } catch (e: Exception) {
      kLogger.error(e) { "평가 지표 생성 실패 - agentId: ${agentNode.id}" }
      EvaluationCriteria(emptyList())
    }
  }

  /**
   * 자체 평가 수행 (REFLECT 단계)
   */
  fun performSelfEvaluation(
    result: Any,
    criteria: EvaluationCriteria,
    agentNode: MultiAgentNode,
  ): SelfEvaluation? {
    if (criteria.criteria.isEmpty()) {
      return null  // 평가 지표 없으면 평가 안 함
    }

    kLogger.debug { "Self-Evaluation 수행 - agentId: ${agentNode.id}" }

    val llmConfig = agentNode.autonomyConfig?.llmConfig ?: return null

    return try {
      val prompt = buildEvaluationPrompt(result, criteria)
      val llm = chatModelProvider.getChatModel(llmConfig)
      val response = llm.chat(UserMessage.from(prompt)).aiMessage()?.text() ?: ""

      parseEvaluationResponse(response)
    } catch (e: Exception) {
      kLogger.error(e) { "Self-Evaluation 수행 실패 - agentId: ${agentNode.id}" }
      null
    }
  }

  private fun buildCriteriaPrompt(agentNode: MultiAgentNode, result: Any): String {
    return """
      당신은 "${agentNode.capability?.summary ?: agentNode.name}" 역할의 에이전트입니다.
      다음 결과물을 검증하기 위한 평가 기준을 생성하세요.

      결과물: $result

      JSON 형식으로 응답:
      {
        "criteria": [
          {"name": "기준명", "description": "설명", "weight": 1.0}
        ],
        "passThreshold": 0.7,
        "expectedOutcome": "기대 결과 설명"
      }
    """.trimIndent()
  }

  private fun buildEvaluationPrompt(result: Any, criteria: EvaluationCriteria): String {
    return """
      다음 결과물을 주어진 평가 기준으로 평가하세요.

      결과물: $result

      평가 기준:
      ${criteria.criteria.joinToString("\n") { "- ${it.name}: ${it.description} (가중치: ${it.weight})" }}

      JSON 형식으로 응답:
      {
        "overallScore": 0.0~1.0,
        "criteriaScores": {"기준명": 점수, ...},
        "reasoning": "평가 근거",
        "confidence": 0.0~1.0,
        "suggestions": ["개선 제안1", ...]
      }
    """.trimIndent()
  }

  private fun parseCriteriaResponse(response: String): EvaluationCriteria {
    return try {
      val jsonContent = extractJsonFromResponse(response)
      objectMapper.readValue(jsonContent, EvaluationCriteria::class.java)
    } catch (e: Exception) {
      kLogger.warn { "평가 지표 파싱 실패: ${e.message}" }
      EvaluationCriteria(emptyList())
    }
  }

  private fun parseEvaluationResponse(response: String): SelfEvaluation? {
    return try {
      val jsonContent = extractJsonFromResponse(response)
      objectMapper.readValue(jsonContent, SelfEvaluation::class.java)
    } catch (e: Exception) {
      kLogger.warn { "Self-Evaluation 파싱 실패: ${e.message}" }
      null
    }
  }

  /**
   * LLM 응답에서 JSON 부분 추출
   */
  private fun extractJsonFromResponse(response: String): String {
    // ```json ... ``` 또는 ``` ... ``` 패턴 처리
    val codeBlockPattern = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
    val match = codeBlockPattern.find(response)

    return if (match != null) {
      match.groupValues[1].trim()
    } else {
      // 코드 블록이 없으면 { } 사이를 찾음
      val jsonPattern = Regex("""\{[\s\S]*\}""")
      jsonPattern.find(response)?.value ?: response
    }
  }
}
