package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.MultiAgentNode
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * Thinking 프로세서
 * 에이전트가 입력을 분석하고 실행 전략을 판단
 */
@Component
class ThinkingProcessor(
  private val objectMapper: ObjectMapper,
  private val chatModelProvider: ChatModelProvider,
) {

  /**
   * Thinking 수행 (THINK 단계)
   */
  fun performThinking(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): ThinkingResult {
    kLogger.debug { "Thinking 시작 - agentId: ${agentNode.id}" }

    val llmConfig = agentNode.autonomyConfig?.llmConfig
      ?: return ThinkingResult(shouldSkip = false)  // LLM 설정 없으면 스킵 판단 안 함

    return try {
      val prompt = buildThinkingPrompt(agentNode, input, context)
      val llm = chatModelProvider.getChatModel(llmConfig)
      val response = llm.chat(UserMessage.from(prompt)).aiMessage()?.text() ?: ""

      parseThinkingResponse(response)
    } catch (e: Exception) {
      kLogger.error(e) { "Thinking 수행 실패 - agentId: ${agentNode.id}" }
      ThinkingResult(shouldSkip = false)
    }
  }

  private fun buildThinkingPrompt(
    agentNode: MultiAgentNode,
    input: Any,
    context: MultiAgentExecutionContext,
  ): String {
    // 사용 가능한 스킬 목록 포함
    val skillsInfo = agentNode.skillInventory?.skills
      ?.filter { it.enabled }
      ?.joinToString("\n") { "- ${it.skillId}" }
      ?: "없음"

    return """
      당신은 "${agentNode.capability?.summary ?: agentNode.name}" 역할의 에이전트입니다.

      ## 입력
      $input

      ## 이전 실행된 에이전트
      ${context.getAllAgentResults().keys.joinToString(", ").ifEmpty { "없음" }}

      ## 사용 가능한 스킬
      $skillsInfo

      ---

      이 입력을 처리해야 할지 판단하세요.

      JSON 형식으로 응답:
      {
        "shouldSkip": false,
        "skipReason": null,
        "strategy": "처리 전략 설명",
        "suggestedSkills": ["skill-id-1", "skill-id-2"]
      }

      - shouldSkip: 이 에이전트가 처리할 필요 없으면 true
      - skipReason: 스킵 이유 (shouldSkip=true일 때)
      - strategy: 처리 전략 (shouldSkip=false일 때)
      - suggestedSkills: 사용하면 좋을 스킬 ID 목록
    """.trimIndent()
  }

  private fun parseThinkingResponse(response: String): ThinkingResult {
    return try {
      // JSON 블록 추출 (마크다운 코드 블록 처리)
      val jsonContent = extractJsonFromResponse(response)
      objectMapper.readValue(jsonContent, ThinkingResult::class.java)
    } catch (e: Exception) {
      kLogger.warn { "Thinking 응답 파싱 실패, 기본값 사용: ${e.message}" }
      ThinkingResult(shouldSkip = false)
    }
  }

  /**
   * LLM 응답에서 JSON 부분 추출
   * 마크다운 코드 블록이 있을 수 있으므로 처리
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

/**
 * Thinking 결과
 */
data class ThinkingResult(
  val shouldSkip: Boolean = false,
  val skipReason: String? = null,
  val strategy: String? = null,
  val suggestedSkills: List<String>? = null,  // 스킬 추천
)
