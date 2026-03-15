package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import com.jongmin.ai.common.tools.SearXng
import com.jongmin.ai.common.tools.SearchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 웹 검색 도구 노드
 *
 * SearXng를 통해 웹 검색을 수행하고 결과를 반환하는 노드.
 * 타임아웃(200초) 설정으로 무한 대기 방지 기능 포함.
 *
 * 입력:
 * - search-term: 검색어 (String)
 *
 * 출력:
 * - results: 검색 결과 스니펫 목록 (String, 줄바꿈 구분)
 *
 * 설정:
 * - 없음
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["web-search-tool"])
class WebSearchToolNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  emitter: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging) {

  companion object : NodeExecutorProvider {
    // SearXng 타임아웃(60초) × 3회 재시도 + 여유 시간
    private const val SEARCH_TIMEOUT_SECONDS = 200L
    private val kLogger = KotlinLogging.logger {}

    /**
     * WebSearchToolNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("web-search-tool")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return WebSearchToolNode 인스턴스
     */
    override fun createExecutor(
      objectMapper: ObjectMapper,
      factory: NodeExecutorFactory,
      session: JSession,
      topic: String,
      eventSender: EventSender,
      emitter: FluxSink<String>?,
      canvasId: String?
    ): NodeExecutor<*> {
      return WebSearchToolNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }

  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val searchTerm = context.findAndGetInputForNode(node.id, "search-term") as String
    kLogger.info { "🌐 [WebSearchNode] 웹 검색 노드 구동 시작 - nodeId: ${node.id}, 검색어: '$searchTerm'" }

    val searXng = factory.applicationContext.getBean(SearXng::class.java)
    val searchFuture = CompletableFuture<SearchResponse?>()

    kLogger.info { "🌐 [WebSearchNode] SearXng 큐 검색 요청 - 타임아웃: ${SEARCH_TIMEOUT_SECONDS}초" }
    val startTime = System.currentTimeMillis()
    searXng.search(searchTerm, 10) { searchFuture.complete(it) }

    // 타임아웃을 설정하여 무한 대기 방지
    val snippets = try {
      val response = searchFuture.get(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      val elapsedTime = System.currentTimeMillis() - startTime
      kLogger.info { "🌐 [WebSearchNode] 검색 결과 수신 완료 - 소요시간: ${elapsedTime}ms" }
      response?.let { it.results?.map { result -> result.snippet() } ?: emptyList() } ?: emptyList()
    } catch (e: TimeoutException) {
      val elapsedTime = System.currentTimeMillis() - startTime
      kLogger.error { "🌐 [WebSearchNode] 웹 검색 타임아웃 - 검색어: '$searchTerm', 대기시간: ${elapsedTime}ms (${SEARCH_TIMEOUT_SECONDS}초 초과)" }
      emptyList()
    } catch (e: Exception) {
      val elapsedTime = System.currentTimeMillis() - startTime
      kLogger.error(e) { "🌐 [WebSearchNode] 웹 검색 예외 발생 - 검색어: '$searchTerm', 대기시간: ${elapsedTime}ms" }
      emptyList()
    }

    val results = mutableMapOf<String, Any?>()
    results["results"] = if (snippets.isEmpty()) {
      kLogger.warn { "🌐 [WebSearchNode] 검색 결과 없음 - 검색어: '$searchTerm'" }
      "검색 결과를 가져올 수 없습니다."
    } else {
      kLogger.info { "🌐 [WebSearchNode] 검색 성공 - 검색어: '$searchTerm', 결과: ${snippets.size}건" }
      snippets.joinToString("  \n\n")
    }

    logging(
      node,
      context,
      "  → 검색어: ${truncateInput(searchTerm).replace("\n", " ")}",
      "  → 검색 결과: ${truncateInput(results["results"].toString()).replace("\n", "|")}"
    )
    context.storeOutput(node.id, results)
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)
}
