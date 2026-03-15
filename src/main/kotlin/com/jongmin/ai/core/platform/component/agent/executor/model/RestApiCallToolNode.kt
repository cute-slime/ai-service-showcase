package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import org.springframework.http.HttpMethod
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * REST API 호출 노드
 *
 * 워크플로우에서 외부 RESTful API를 호출하는 노드.
 * Postman과 유사하게 엔드포인트 URL, HTTP 메서드, 헤더, Path Variable 등을 설정하고,
 * input handle로 들어오는 데이터를 payload로 사용하여 API를 호출한다.
 *
 * ### 입력:
 * - payload: API 요청에 사용할 데이터 (JSON 객체)
 *   - POST/PUT/PATCH: Request Body로 전송
 *   - GET/DELETE: Query Parameter로 자동 변환 (camelCase flatten)
 *
 * ### 출력:
 * - statusCode: HTTP 상태 코드 (200, 404, 500 등)
 * - headers: 응답 헤더
 * - body: 응답 본문 (JSON 파싱 시도, 실패 시 String)
 * - success: 성공 여부 (2xx 상태 코드 = true)
 * - error: 실패 시 에러 메시지
 *
 * ### 설정:
 * - url: 엔드포인트 URL 템플릿 (예: /users/{userId}/threads/{threadId})
 * - method: HTTP 메서드 (GET, POST, PUT, DELETE, PATCH)
 * - headers: 요청 헤더 (인증 포함)
 * - pathVariables: Path Variable 매핑 (URL 변수명 → payload 경로)
 * - timeoutSeconds: 타임아웃 (기본 30초)
 * - retryCount: 재시도 횟수 (기본 0)
 * - retryDelayMs: 재시도 대기 시간 (기본 1000ms)
 * - proxy: 프록시 설정 (선택)
 * - skipSslVerification: SSL 검증 무시 (기본 false, 개발용)
 *
 * @author Claude Code
 * @since 2026.01.04
 * @modified 2026.02.19 SRP 분리 (HttpExecutor, UrlResolver)
 */
@NodeType(["rest-api-call-tool"])
class RestApiCallToolNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {

  companion object : NodeExecutorProvider {
    private const val DEFAULT_RETRY_COUNT = 0
    private const val DEFAULT_RETRY_DELAY_MS = 1000L

    override fun createExecutor(
      objectMapper: ObjectMapper,
      factory: NodeExecutorFactory,
      session: JSession,
      topic: String,
      eventSender: EventSender,
      emitter: FluxSink<String>?,
      canvasId: String?
    ): NodeExecutor<*> {
      return RestApiCallToolNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }

  // ========== 설정 DTO ==========

  /**
   * REST API 노드 설정
   */
  data class RestApiCallToolNodeConfig(
    /** 엔드포인트 URL 템플릿 (예: https://api.example.com/users/{userId}) */
    val url: String,
    /** HTTP 메서드 (GET, POST, PUT, DELETE, PATCH) */
    val method: String,
    /** 요청 헤더 (인증 포함) */
    val headers: Map<String, String>? = null,
    /** Path Variable 매핑 목록 */
    val pathVariables: List<PathVariableMapping>? = null,
    /** Query Parameter 목록 */
    val queryParams: Map<String, String>? = null,
    /** 타임아웃 (초, 기본 30) */
    val timeoutSeconds: Int? = null,
    /** 재시도 횟수 (기본 0) */
    val retryCount: Int? = null,
    /** 재시도 대기 시간 (ms, 기본 1000) */
    val retryDelayMs: Long? = null,
    /** 프록시 설정 */
    val proxy: ProxyConfig? = null,
    /** SSL 인증서 검증 무시 여부 (기본 false, 개발/테스트용) */
    val skipSslVerification: Boolean? = null
  )

  /**
   * Path Variable 매핑 설정
   */
  data class PathVariableMapping(
    /** URL의 변수명 (예: "userId") */
    val pathKey: String,
    /** payload에서 추출할 경로 (예: "user.id") */
    val payloadPath: String
  )

  /**
   * 프록시 설정
   */
  data class ProxyConfig(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null
  )

  // ========== NodeExecutor 구현 ==========

  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    kLogger.info { "[RestApiCallNode] REST API 호출 노드 구동 시작 - nodeId: ${node.id}" }

    // 0. 필수 설정 검증
    validateNodeConfig(node)

    // 1. 설정 파싱
    val config = parseConfig(node)
    val method = HttpMethod.valueOf(config.method.uppercase())
    kLogger.info { "[RestApiCallNode] 설정 파싱 완료 - URL: ${config.url}, Method: ${config.method}" }

    // 2. payload 가져오기
    val payload = context.findAndGetInputForNode(node.id, "payload")
    kLogger.debug { "[RestApiCallNode] Payload: ${truncateLog(payload?.toString())}" }

    // 3. POST/PUT/PATCH 메서드는 payload 필수 검증
    if (method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH) && payload == null) {
      val errorMessage = "[RestApiCallNode] ${method.name()} 요청에는 payload가 필수입니다. " +
          "nodeId: ${node.id}, inputs에 'payload' 핸들로 데이터를 전달해주세요."
      kLogger.error { errorMessage }
      throw IllegalArgumentException(errorMessage)
    }

    // 4. Path Variable 치환
    val resolvedUrl = RestApiCallUrlResolver.resolvePathVariables(config.url, config.pathVariables, payload)
    kLogger.info { "[RestApiCallNode] URL 치환 완료 - $resolvedUrl" }

    // 5. Query Parameter 추가 (config에서 설정된 값)
    var finalUrl = RestApiCallUrlResolver.appendQueryParams(resolvedUrl, config.queryParams)

    // 6. GET/DELETE: payload를 Query Parameter로 자동 변환
    if (method in listOf(HttpMethod.GET, HttpMethod.DELETE) && payload != null) {
      val autoQueryParams = RestApiCallUrlResolver.flattenPayloadToQueryParams(payload, objectMapper)
      finalUrl = RestApiCallUrlResolver.appendQueryParams(finalUrl, autoQueryParams)
      kLogger.info { "[RestApiCallNode] Payload → Query Params 변환 완료 - $autoQueryParams" }
    }

    // 7. WebClient 생성
    val webClient = RestApiCallHttpExecutor.createWebClient(config)

    // 8. HTTP 요청 실행 (재시도 포함)
    val result = RestApiCallHttpExecutor.executeWithRetry(
      retryCount = config.retryCount ?: DEFAULT_RETRY_COUNT,
      retryDelayMs = config.retryDelayMs ?: DEFAULT_RETRY_DELAY_MS
    ) {
      RestApiCallHttpExecutor.executeHttpRequest(webClient, config, finalUrl, payload, objectMapper)
    }

    // 9. 로깅 및 출력 저장
    logging(
      node,
      context,
      "  → URL: $finalUrl",
      "  → Method: ${config.method}",
      "  → Status: ${result["statusCode"]}",
      "  → Success: ${result["success"]}"
    )

    context.storeOutput(node.id, mapOf("response" to result))

    kLogger.info {
      "[RestApiCallNode] REST API 호출 완료 - " +
          "nodeId: ${node.id}, status: ${result["statusCode"]}, success: ${result["success"]}"
    }
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)

  // ========== 필수 설정 검증 ==========

  private fun validateNodeConfig(node: Node) {
    val config = node.data.config

    if (config == null) {
      val errorMessage = buildString {
        appendLine("[RestApiCallNode] 노드 설정(nodeConfig)이 누락되었습니다!")
        appendLine("  nodeId: ${node.id}, nodeType: ${node.type}")
        appendLine("  해결: nodeConfig에 url, method 설정을 포함해주세요.")
      }
      kLogger.error { errorMessage }
      throw IllegalArgumentException(errorMessage)
    }

    if (config.isEmpty()) {
      val errorMessage = buildString {
        appendLine("[RestApiCallNode] 노드 설정(nodeConfig)이 비어있습니다!")
        appendLine("  nodeId: ${node.id}")
        appendLine("  필수: url (REST API 엔드포인트 URL)")
        appendLine("  선택: method (기본 GET), headers, pathVariables, queryParams, timeoutSeconds")
      }
      kLogger.error { errorMessage }
      throw IllegalArgumentException(errorMessage)
    }

    val url = config["url"] as? String
    if (url.isNullOrBlank()) {
      val errorMessage = buildString {
        appendLine("[RestApiCallNode] URL이 설정되지 않았습니다!")
        appendLine("  nodeId: ${node.id}, 현재 config 키: ${config.keys}")
        appendLine("  해결: nodeConfig에 'url' 필드를 추가해주세요.")
      }
      kLogger.error { errorMessage }
      throw IllegalArgumentException(errorMessage)
    }

    kLogger.debug { "[RestApiCallNode] 노드 설정 검증 통과 - url: $url, method: ${config["method"] ?: "GET"}" }
  }

  // ========== 설정 파싱 ==========

  @Suppress("UNCHECKED_CAST")
  private fun parseConfig(node: Node): RestApiCallToolNodeConfig {
    val config = node.data.config
    if (config == null) {
      kLogger.error {
        "[RestApiCallNode] config가 null입니다! " +
            "nodeId: ${node.id}, nodeType: ${node.type}"
      }
      throw IllegalArgumentException(
        "REST API 노드 설정(config)이 null입니다. nodeId: ${node.id}."
      )
    }

    val url = config["url"] as? String
    if (url.isNullOrBlank()) {
      kLogger.error { "[RestApiCallNode] URL이 설정되지 않았습니다. nodeId: ${node.id}" }
      throw IllegalArgumentException("URL이 설정되지 않았습니다. nodeId: ${node.id}")
    }

    val method = (config["method"] as? String)?.uppercase() ?: "GET"
    val headers = config["headers"] as? Map<String, String>

    val pathVariablesRaw = config["pathVariables"] as? List<Map<String, String>>
    val pathVariables = pathVariablesRaw?.mapNotNull { pv ->
      val pathKey = pv["pathKey"]
      val payloadPath = pv["payloadPath"]
      if (pathKey != null && payloadPath != null) {
        PathVariableMapping(pathKey = pathKey, payloadPath = payloadPath)
      } else null
    }

    val queryParams = config["queryParams"] as? Map<String, String>
    val timeoutSeconds = (config["timeoutSeconds"] as? Number)?.toInt()
    val retryCount = (config["retryCount"] as? Number)?.toInt()
    val retryDelayMs = (config["retryDelayMs"] as? Number)?.toLong()

    val proxyRaw = config["proxy"] as? Map<String, Any>
    val proxy = proxyRaw?.let {
      val host = it["host"] as? String
      val port = (it["port"] as? Number)?.toInt()
      if (host != null && port != null) {
        ProxyConfig(
          host = host,
          port = port,
          username = it["username"] as? String,
          password = it["password"] as? String
        )
      } else null
    }

    val skipSslVerification = config["skipSslVerification"] as? Boolean

    return RestApiCallToolNodeConfig(
      url = url,
      method = method,
      headers = headers,
      pathVariables = pathVariables,
      queryParams = queryParams,
      timeoutSeconds = timeoutSeconds,
      retryCount = retryCount,
      retryDelayMs = retryDelayMs,
      proxy = proxy,
      skipSslVerification = skipSslVerification
    )
  }

  // ========== 로그 유틸리티 ==========

  private fun truncateLog(content: String?, maxLines: Int = 25): String {
    if (content == null) return "null"

    val lines = content.lines()
    return if (lines.size > maxLines) {
      lines.take(maxLines).joinToString("\n") + "\n... (${lines.size - maxLines}줄 생략)"
    } else {
      content
    }
  }
}
