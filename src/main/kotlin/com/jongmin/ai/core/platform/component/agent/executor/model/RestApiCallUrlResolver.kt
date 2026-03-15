package com.jongmin.ai.core.platform.component.agent.executor.model

import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.ObjectMapper

/**
 * REST API URL 처리기
 *
 * Path Variable 치환, Query Parameter 추가, Payload → Query Parameter 자동 변환 등
 * URL 관련 처리 로직을 담당한다.
 *
 * @author Claude Code
 * @since 2026.02.19 (RestApiCallToolNode에서 분리)
 */
object RestApiCallUrlResolver {

  private val kLogger = KotlinLogging.logger {}

  /**
   * URL 템플릿에서 Path Variable을 payload 값으로 치환
   *
   * @param urlTemplate URL 템플릿 (예: /users/{userId}/threads/{threadId})
   * @param mappings Path Variable 매핑 목록
   * @param payload 값을 추출할 payload 데이터
   * @return 치환된 URL
   */
  fun resolvePathVariables(
    urlTemplate: String,
    mappings: List<RestApiCallToolNode.PathVariableMapping>?,
    payload: Any?
  ): String {
    if (mappings.isNullOrEmpty()) return urlTemplate

    var resolvedUrl = urlTemplate

    for (mapping in mappings) {
      val value = extractValueFromPayload(payload, mapping.payloadPath)
      if (value != null) {
        resolvedUrl = resolvedUrl.replace("{${mapping.pathKey}}", value.toString())
        kLogger.debug { "[RestApiCallNode] Path Variable 치환: {${mapping.pathKey}} → $value" }
      } else {
        kLogger.warn { "[RestApiCallNode] Path Variable 값을 찾을 수 없음: ${mapping.payloadPath}" }
      }
    }

    return resolvedUrl
  }

  /**
   * payload에서 점(.) 구분 경로로 값을 추출
   *
   * @param payload 원본 데이터 (Map 또는 객체)
   * @param path 점 구분 경로 (예: "user.id", "message.sessionId")
   * @return 추출된 값, 없으면 null
   */
  fun extractValueFromPayload(payload: Any?, path: String): Any? {
    if (payload == null) return null

    val keys = path.split(".")
    var current: Any? = payload

    for (key in keys) {
      current = when (current) {
        is Map<*, *> -> current[key]
        else -> null
      }
      if (current == null) break
    }

    return current
  }

  /**
   * URL에 Query Parameter 추가
   */
  fun appendQueryParams(url: String, queryParams: Map<String, String>?): String {
    if (queryParams.isNullOrEmpty()) return url

    val separator = if (url.contains("?")) "&" else "?"
    val queryString = queryParams.entries.joinToString("&") { (key, value) ->
      "${java.net.URLEncoder.encode(key, Charsets.UTF_8)}=${java.net.URLEncoder.encode(value, Charsets.UTF_8)}"
    }

    return "$url$separator$queryString"
  }

  /**
   * payload를 Query Parameter Map으로 변환 (GET/DELETE용)
   *
   * 중첩된 객체는 camelCase로 flatten된다.
   * 예시:
   * - {user: {id: 1}} → {userId: "1"}
   * - {filter: {status: "active"}} → {filterStatus: "active"}
   * - {page: 1, size: 10} → {page: "1", size: "10"}
   */
  fun flattenPayloadToQueryParams(payload: Any, objectMapper: ObjectMapper): Map<String, String> {
    // payload가 String이면 JSON 파싱 시도
    val parsedPayload = when (payload) {
      is String -> {
        try {
          objectMapper.readValue(payload, Map::class.java)
        } catch (e: Exception) {
          kLogger.warn { "[RestApiCallNode] payload JSON 파싱 실패, 원본 사용: ${e.message}" }
          payload
        }
      }
      else -> payload
    }

    val result = mutableMapOf<String, String>()
    flattenRecursive(parsedPayload, "", result)
    return result
  }

  /**
   * 재귀적으로 객체를 flatten하여 Query Parameter로 변환
   */
  private fun flattenRecursive(current: Any?, prefix: String, result: MutableMap<String, String>) {
    when (current) {
      null -> {
        // null 값은 무시
      }
      is Map<*, *> -> {
        current.forEach { (key, value) ->
          val keyStr = key?.toString() ?: return@forEach
          val newPrefix = if (prefix.isEmpty()) {
            keyStr
          } else {
            // camelCase로 합침: "user" + "id" → "userId"
            prefix + keyStr.replaceFirstChar { it.uppercaseChar() }
          }
          flattenRecursive(value, newPrefix, result)
        }
      }
      is Collection<*> -> {
        current.forEachIndexed { index, value ->
          flattenRecursive(value, "$prefix[$index]", result)
        }
      }
      else -> {
        if (prefix.isNotEmpty()) {
          result[prefix] = current.toString()
        }
      }
    }
  }
}
