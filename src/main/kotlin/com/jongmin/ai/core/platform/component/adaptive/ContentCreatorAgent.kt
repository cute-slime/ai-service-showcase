//package com.jongmin.ai.core.platform.component.adaptive
//
//import com.jongmin.jspring.messaging.event.EventSender
//import com.jongmin.jspring.core.dto.MessageType
//import com.jongmin.jspring.data.entity.StatusType
//import com.jongmin.ai.core.AgentCommand.*
//import com.jongmin.ai.core.AiAssistantType
//import com.jongmin.ai.core.AiAssistantType.CONTENT_CREATIVE_ROUTER
//import com.jongmin.ai.core.AiAssistantType.WEB_SEARCH_EXPERT
//import com.jongmin.ai.core.AiRunStatus.READY
//import com.jongmin.ai.core.RunnableAiAssistant
//import com.jongmin.ai.core.platform.component.SingleInputSingleOutputGenerator
//import com.jongmin.ai.core.platform.component.StreamingSingleInputSingleOutputGenerator
//import com.jongmin.ai.core.platform.dto.request.CreateAiCreativeContent
//import com.jongmin.ai.core.platform.service.AiAssistantService
//import com.jongmin.service.common.dto.SearchResponse
//import com.jongmin.service.common.component.Crawl4Ai
//import com.jongmin.service.common.component.SearXng
//import com.jongmin.service.suggest_content.ContentCreativeRouteType.CONTINUE_WRITE
//import com.jongmin.service.suggest_content.ContentCreativeRouteType.WEB_SEARCH
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.bsc.langgraph4j.CompiledGraph
//import org.bsc.langgraph4j.GraphRepresentation
//import org.bsc.langgraph4j.NodeOutput
//import org.bsc.langgraph4j.StateGraph
//import org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async
//import org.bsc.langgraph4j.action.AsyncNodeAction.node_async
//import org.bsc.langgraph4j.state.AgentState
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.stereotype.Component
//import java.util.*
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.Executors
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.AtomicReference
//import javax.annotation.PreDestroy
//
//@Component
//class ContentCreatorAgent(
//  @param:Value($$"${app.stream.event.topic.event-app}") private val topic: String,
//  private val eventSender: EventSender,
//  private val aiAssistantService: AiAssistantService,
//  private val searXng: SearXng,
//  private val crawl4Ai: Crawl4Ai,
//) {
//
//  private val kLogger = KotlinLogging.logger {}
//
//  private val executors = Executors.newScheduledThreadPool(32)
//
//  private val debugger = true
//
//  val id: Long = 3
//
//  @PreDestroy
//  private fun destroy() {
//    kLogger.info { "ContentCreatorAgent 스레드 풀 종료 시작" }
//    executors.shutdown() // 새로운 작업을 받지 않음
//    try {
//      if (!executors.awaitTermination(1, TimeUnit.MINUTES)) executors.shutdownNow()
//    } catch (e: InterruptedException) {
//      executors.shutdownNow() // 인터럽트 발생 시 강제 종료
//      Thread.currentThread().interrupt() // 인터럽트 상태 복원
//    }
//    kLogger.info { "ContentCreatorAgent 스레드 풀 종료 완료" }
//  }
//
//  fun logging(message: String) {
//    if (debugger) kLogger.info { message }
//  }
//
//  fun execute(
//    accountId: Long,
//    aiRunId: Long,
//    dto: CreateAiCreativeContent,
//    debugPicture: Boolean = false,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null,
//    onEnded: ((nodeOutput: NodeOutput<State>) -> Unit)? = null
//  ): Long {
//    executors.submit {
//      kLogger.info { "ContentCreatorAgent 추론 시작됨 - runId: $aiRunId" }
//      val output = AtomicReference<NodeOutput<State>>()
//      try {
//        val graph: CompiledGraph<State> = buildGraph(
//          mapOf(
//            ACCOUNT_ID.lower() to accountId,
//            CANVAS_ID.lower() to dto.canvasId!!,
//            AI_RUN_ID.lower() to aiRunId,
//            STATUS.lower() to READY
//          ),
//          onStatusChanged
//        ).compile()
//
//        graph.setMaxIterations(14)
//
//        for (r in graph.stream(
//          mapOf(
//            QUESTION.lower() to dto.title!!,
////            USER_REQUEST.lower() to dto.userRequest!!,
////            USER_REQUEST_DETAIL.lower() to dto.userRequestDetail!!,
////            CONTENT_TYPE.lower() to dto.contentType!!,
////            CONTENT_LENGTH.lower() to dto.contentLength!!,
////            TONE_AND_MANNER.lower() to dto.toneAndManner!!,
////            RESEARCH_LEVEL.lower() to dto.researchLevel!!
//          )
//        )) output.set(r)
//
//        if (debugPicture) println(graph.getGraph(GraphRepresentation.Type.MERMAID))
//      } catch (e: Exception) {
//        kLogger.error { "ContentCreatorAgent 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
//      } finally {
//        onEnded?.invoke(output.get())
//        kLogger.info { "ContentCreatorAgent 추론 완료됨 - runId: $aiRunId" }
//      }
//    }
//
//    return aiRunId
//  }
//
//  private fun buildGraph(
//    payload: Map<String, Any>,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): StateGraph<State> {
//    return StateGraph { initData: Map<String, Any> -> State(initData + payload) }
//      .addNode(CONTINUE_WRITE.lower(), node_async { state: State -> webSearch(state, onStatusChanged) })
//      .addNode(WEB_SEARCH.lower(), node_async { state: State -> webSearch(state, onStatusChanged) })
//      .addNode(GENERATE.lower(), node_async { state: State -> generate(state, onStatusChanged) })
//      .addConditionalEdges(
//        StateGraph.START,
//        edge_async { state: State -> routeQuestion(state, onStatusChanged) },
//        mapOf(
//          CONTINUE_WRITE.lower() to GENERATE.lower(),
//          WEB_SEARCH.lower() to WEB_SEARCH.lower(),
//        )
//      )
//      .addEdge(CONTINUE_WRITE.lower(), StateGraph.END)
//      .addEdge(WEB_SEARCH.lower(), GENERATE.lower())
//      .addEdge(GENERATE.lower(), StateGraph.END)
//  }
//
//  private fun routeQuestion(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): String {
//    val assi = aiAssistantService.findFirst(CONTENT_CREATIVE_ROUTER)
//    return SingleInputSingleOutputGenerator(assi, onStatusChanged).apply(assi.getInstructionsWithCurrentTime(), state.question())
//  }
//
//  private fun webSearch(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): Map<String, Any> {
//    val assi = aiAssistantService.findFirst(WEB_SEARCH_EXPERT)
//    val searchTerms = SingleInputSingleOutputGenerator(assi, onStatusChanged).apply(assi.getInstructionsWithCurrentTime(), state.question())
//    val searchFuture = CompletableFuture<SearchResponse?>()
//    searXng.search(searchTerms, 10) { searchFuture.complete(it) }
//    val snippets = searchFuture.get()?.let { it.results?.map { result -> result.snippet() } ?: emptyList() } ?: emptyList()
//    return mapOf(DOCUMENTS.lower() to snippets)
//  }
//
//
//  private fun generate(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): Map<String, Any> {
//    kLogger.info { "---글 생성---" }
//    val assi = aiAssistantService.findFirst(AiAssistantType.CONTENT_WRITER_FOR_QA)
//    val prompt = assi.getInstructionsWithCurrentTime().replace("{{documents}}", state.documents().joinToString("\n"))
//    val article: String =
//      StreamingSingleInputSingleOutputGenerator(
//        assi,
//        eventSender,
//        topic,
//        state.accountId(),
//        state.canvasId(),
//        "canvasId",
//        MessageType.AI_INFERENCE_DELTA,
//        onStatusChanged
//      )
//        .apply(prompt, state.question())
//    return mapOf(GENERATE.lower() to article)
//  }
//
//  class State(initData: Map<String, Any>) : AgentState(initData) {
//    fun question(): String {
//      val result: Optional<String> = value(QUESTION.lower())
//      return result.orElseThrow { IllegalStateException("question is not set!") }
//    }
//
//    fun generation(): Optional<String> {
//      return value(GENERATE.lower())
//    }
//
//    fun documents(): List<String> {
//      return value<List<String>>(DOCUMENTS.lower()).orElse(emptyList())
//    }
//
//    fun accountId(): Long {
//      val result: Optional<Long> = value(ACCOUNT_ID.lower())
//      return result.orElseThrow { IllegalStateException("account id is not set!") }
//    }
//
//    fun canvasId(): String {
//      val result: Optional<String> = value(CANVAS_ID.lower())
//      return result.orElseThrow { IllegalStateException("canvas id is not set!") }
//    }
//
//    fun aiRunId(): Long {
//      val result: Optional<Long> = value(AI_RUN_ID.lower())
//      return result.orElseThrow { IllegalStateException("ai run id is not set!") }
//    }
//
//    fun userRequest(): String {
//      val result: Optional<String> = value(USER_REQUEST.lower())
//      return result.orElseThrow { IllegalStateException("user request is not set!") }
//    }
//
//    fun userRequestDetail(): String {
//      val result: Optional<String> = value(USER_REQUEST_DETAIL.lower())
//      return result.orElseThrow { IllegalStateException("user request detail is not set!") }
//    }
//
//    fun contentType(): String {
//      val result: Optional<String> = value(CONTENT_TYPE.lower())
//      return result.orElseThrow { IllegalStateException("content type is not set!") }
//    }
//
//    fun contentLength(): String {
//      val result: Optional<String> = value(CONTENT_LENGTH.lower())
//      return result.orElseThrow { IllegalStateException("content length is not set!") }
//    }
//
//    fun toneAndManner(): String {
//      val result: Optional<String> = value(TONE_AND_MANNER.lower())
//      return result.orElseThrow { IllegalStateException("tone and manner is not set!") }
//    }
//
//    fun researchLevel(): String {
//      val result: Optional<String> = value(RESEARCH_LEVEL.lower())
//      return result.orElseThrow { IllegalStateException("research level is not set!") }
//    }
//  }
//}
