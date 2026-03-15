package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import com.jongmin.ai.common.tools.Crawl4Ai
import com.jongmin.ai.common.tools.Crawl4AiRequest
import com.jongmin.ai.common.tools.Crawl4AiResponse
import net.dankito.readability4j.Article
import net.dankito.readability4j.Readability4J
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture

/**
 * 아티클 분석 도구 노드
 *
 * Crawl4Ai를 통해 웹 페이지를 크롤링하고, Readability4J로 본문을 추출하는 노드.
 * HTML에서 주요 콘텐츠를 추출하여 마크다운 형식으로 반환한다.
 *
 * 입력:
 * - input: 웹 페이지 URL (String)
 *
 * 출력:
 * - 마크다운 형식의 아티클 본문 (제목 + 텍스트 콘텐츠)
 *
 * 설정:
 * - 없음
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["article-analyzer-tool"])
class ArticleAnalyzerToolNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  emitter: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val url = context.findAndGetInputForNode(node.id, "input") as String
    val crawl4Ai = context.factory.applicationContext.getBean(Crawl4Ai::class.java)
    val crawlFuture = CompletableFuture<Crawl4AiResponse?>()
    crawl4Ai.crawlImmediately(Crawl4AiRequest(setOf(url))) { crawlFuture.complete(it) }
    crawlFuture.get()?.let { crawl4AiResponse ->
      val data = crawl4AiResponse.results?.first()
      val readability4J = Readability4J(url, data?.html!!) // url is just needed to resolve relative urls
      val article: Article = readability4J.parse()
      // println("title: ${article.title}")
      // println("textContent: ${article.textContent}")
      // // println("content: ${article.content}") // HTML
      // // println("byline: ${article.byline}") // 작성자
      // // println("excerpt: ${article.excerpt}") // 발췌 (핵심 내용으로 보임)
      context.storeOutput(
        node.id, """
        |# ${article.title}
        |${article.textContent}
      """.trimIndent()
      )
    } ?: run {
      context.storeOutput(node.id, "No data")
    }

    logging(
      node,
      context,
      "  → URL: $url",
      "  → Content: ${truncateInput("will markdown data filled", 200).replace("\n", "|")}"
    )
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * ArticleAnalyzerToolNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("article-analyzer-tool")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return ArticleAnalyzerToolNode 인스턴스
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
      return ArticleAnalyzerToolNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}

