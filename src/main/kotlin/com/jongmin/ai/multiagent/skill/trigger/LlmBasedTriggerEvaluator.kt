package com.jongmin.ai.multiagent.skill.trigger

import com.jongmin.ai.multiagent.executor.ChatModelProvider
import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.AgentLlmConfig
import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.skill.model.LlmTriggerConfig
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * LLM 기반 트리거 평가기
 * LLM이 문맥을 이해하고 스킬 실행 필요 여부 판단
 */
@Component
class LlmBasedTriggerEvaluator(
  private val chatModelProvider: ChatModelProvider,
  private val objectMapper: ObjectMapper,
) : SkillTriggerEvaluator {

  override val strategy = TriggerStrategy.LLM_BASED

  override fun shouldTrigger(
    skill: AgentSkill,
    output: Any,
    context: MultiAgentExecutionContext,
  ): TriggerEvaluationResult {
    val startTime = System.currentTimeMillis()
    kLogger.debug { "LLM 기반 트리거 평가 시작 - skill: ${skill.id}" }

    // LLM 설정 조회 (스킬에 설정이 없으면 기본값 사용)
    val llmConfig = getLlmConfig(skill)
    val prompt = buildEvaluationPrompt(skill, output, context, llmConfig)

    return try {
      // LLM 설정에서 모델 ID 가져오기, 없으면 기본 OpenAI 사용
      val agentLlmConfig = AgentLlmConfig(
        provider = "openai",
        model = llmConfig.modelId ?: "gpt-4o-mini",
        temperature = 0.3,
        maxTokens = llmConfig.maxTokens,
      )
      val chatModel = chatModelProvider.getChatModel(agentLlmConfig)
      val response = chatModel.chat(prompt)
      val result = parseEvaluationResponse(response, skill.id, llmConfig)

      val duration = System.currentTimeMillis() - startTime
      kLogger.debug {
        "LLM 트리거 평가 완료 - skill: ${skill.id}, " +
          "shouldTrigger: ${result.shouldTrigger}, " +
          "confidence: ${result.confidence}, " +
          "duration: ${duration}ms"
      }

      result.copy(evaluationTimeMs = duration)

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      kLogger.error(e) { "LLM 트리거 평가 실패 - skill: ${skill.id}" }

      TriggerEvaluationResult(
        shouldTrigger = false,
        reason = "LLM 평가 실패: ${e.message}",
        strategyUsed = TriggerStrategy.LLM_BASED,
        evaluationTimeMs = duration,
      )
    }
  }

  /**
   * 스킬에서 LLM 설정 조회
   */
  private fun getLlmConfig(skill: AgentSkill): LlmTriggerConfig {
    // executorConfig에서 llmTriggerConfig 추출 시도
    @Suppress("UNCHECKED_CAST")
    val configMap = skill.executorConfig?.get("llmTriggerConfig") as? Map<String, Any>

    return if (configMap != null) {
      try {
        objectMapper.convertValue(configMap, LlmTriggerConfig::class.java)
      } catch (e: Exception) {
        kLogger.warn { "LlmTriggerConfig 파싱 실패, 기본값 사용 - skill: ${skill.id}" }
        LlmTriggerConfig.DEFAULT
      }
    } else {
      LlmTriggerConfig.DEFAULT
    }
  }

  /**
   * 평가 프롬프트 생성
   */
  private fun buildEvaluationPrompt(
    skill: AgentSkill,
    output: Any,
    context: MultiAgentExecutionContext,
    llmConfig: LlmTriggerConfig,
  ): String {
    // 커스텀 프롬프트가 있으면 사용
    if (llmConfig.customPrompt != null) {
      return llmConfig.customPrompt
        .replace("{skill.id}", skill.id)
        .replace("{skill.name}", skill.name)
        .replace("{skill.description}", skill.description)
        .replace("{output}", output.toString())
    }

    // 기본 프롬프트
    return """
      |당신은 AI 에이전트 스킬 실행 여부를 판단하는 평가자입니다.
      |주어진 상황에서 특정 스킬이 실행되어야 하는지 판단해주세요.
      |
      |## 스킬 정보
      |- ID: ${skill.id}
      |- 이름: ${skill.name}
      |- 설명: ${skill.description}
      |
      |## 현재 상황
      |- 에이전트 출력:
      |```
      |${output.toString().take(2000)}
      |```
      |- 이전 에이전트 수: ${context.getExecutedAgentCount()}
      |
      |## 판단 기준
      |위 스킬의 설명을 참고하여, 현재 에이전트 출력 내용이 해당 스킬을 필요로 하는지 판단해주세요.
      |
      |## 응답 형식 (반드시 JSON으로만 응답)
      |```json
      |{
      |  "shouldTrigger": true 또는 false,
      |  "reason": "판단 사유를 간결하게",
      |  "confidence": 0.0에서 1.0 사이의 신뢰도
      |}
      |```
      |
      |JSON만 응답하세요.
    """.trimMargin()
  }

  /**
   * LLM 응답 파싱
   */
  private fun parseEvaluationResponse(
    response: String,
    skillId: String,
    llmConfig: LlmTriggerConfig,
  ): TriggerEvaluationResult {
    return try {
      // JSON 추출 (```json ... ``` 또는 그냥 JSON)
      val jsonStr = extractJson(response)
      val parsed = objectMapper.readValue(jsonStr, LlmTriggerResponse::class.java)

      val confidence = parsed.confidence ?: 0.5
      val shouldTrigger = parsed.shouldTrigger && confidence >= llmConfig.confidenceThreshold

      if (shouldTrigger) {
        TriggerEvaluationResult.triggeredByLlm(
          reason = parsed.reason ?: "LLM 판단",
          confidence = confidence,
        )
      } else {
        TriggerEvaluationResult.notTriggeredByLlm(
          reason = parsed.reason ?: "LLM 판단: 트리거 불필요",
          confidence = confidence,
        )
      }

    } catch (e: Exception) {
      kLogger.warn { "LLM 응답 파싱 실패 - skillId: $skillId, response: ${response.take(200)}" }
      TriggerEvaluationResult(
        shouldTrigger = false,
        reason = "응답 파싱 실패: ${e.message}",
        strategyUsed = TriggerStrategy.LLM_BASED,
      )
    }
  }

  /**
   * 텍스트에서 JSON 추출
   */
  private fun extractJson(text: String): String {
    // ```json ... ``` 블록 추출 시도
    val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)\\s*```")
    val match = jsonBlockRegex.find(text)
    if (match != null) {
      return match.groupValues[1].trim()
    }

    // ``` ... ``` 블록 추출 시도
    val codeBlockRegex = Regex("```\\s*([\\s\\S]*?)\\s*```")
    val codeMatch = codeBlockRegex.find(text)
    if (codeMatch != null) {
      return codeMatch.groupValues[1].trim()
    }

    // { } 로 감싸진 부분 추출 시도
    val jsonObjectRegex = Regex("\\{[\\s\\S]*}")
    val jsonMatch = jsonObjectRegex.find(text)
    if (jsonMatch != null) {
      return jsonMatch.value
    }

    // 그냥 텍스트 반환
    return text.trim()
  }

  /**
   * LLM 응답 파싱용 데이터 클래스
   */
  private data class LlmTriggerResponse(
    val shouldTrigger: Boolean = false,
    val reason: String? = null,
    val confidence: Double? = null,
  )
}
