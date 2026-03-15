package com.jongmin.ai.multiagent.skill.executor

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.SkillExecutionResult
import com.jongmin.ai.multiagent.skill.SkillExecutionContext
import com.jongmin.ai.multiagent.skill.SkillExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 웹 검색 스킬 실행기
 * 인터넷에서 최신 정보를 검색하는 스킬
 */
@Component
class WebSearchSkillExecutor : SkillExecutor {

  override val skillType: String = "WEB_SEARCH"

  override fun canExecute(skill: AgentSkill, context: SkillExecutionContext): Boolean {
    // 웹 검색 가능 여부 확인
    // TODO: API 키 및 검색 서비스 연결 상태 확인
    return true
  }

  override fun execute(skill: AgentSkill, context: SkillExecutionContext): SkillExecutionResult {
    kLogger.debug { "웹 검색 스킬 실행 - input: ${context.input}" }

    return try {
      // TODO: 실제 웹 검색 로직 구현 (Tavily, Google Search API 등)
      // 현재는 더미 구현
      val query = extractSearchQuery(context.input)
      val searchResults = performSearch(query)

      SkillExecutionResult(
        skillId = skill.id,
        success = true,
        output = mapOf(
          "query" to query,
          "results" to searchResults,
          "resultCount" to searchResults.size
        )
      )
    } catch (e: Exception) {
      kLogger.error(e) { "웹 검색 스킬 실행 실패" }
      SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = e.message
      )
    }
  }

  /**
   * 입력에서 검색 쿼리 추출
   */
  private fun extractSearchQuery(input: Any): String {
    return when (input) {
      is String -> input
      is Map<*, *> -> input["query"]?.toString() ?: input.toString()
      else -> input.toString()
    }
  }

  /**
   * 검색 수행 (더미 구현)
   */
  private fun performSearch(query: String): List<Map<String, Any>> {
    // TODO: 실제 검색 API 연동
    kLogger.info { "웹 검색 수행 - query: $query" }
    return listOf(
      mapOf(
        "title" to "검색 결과 1",
        "url" to "https://example.com/1",
        "snippet" to "검색 결과 요약 1..."
      ),
      mapOf(
        "title" to "검색 결과 2",
        "url" to "https://example.com/2",
        "snippet" to "검색 결과 요약 2..."
      )
    )
  }
}
