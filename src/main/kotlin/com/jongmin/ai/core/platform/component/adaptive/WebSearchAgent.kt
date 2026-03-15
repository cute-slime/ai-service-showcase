//package com.jongmin.ai.core.platform.component.adaptive
//
///**
// */
//import com.jongmin.jspring.messaging.event.EventSender
//import com.jongmin.jspring.data.entity.StatusType
//import com.jongmin.ai.core.AgentCommand.*
//import com.jongmin.ai.core.AiAssistantType
//import com.jongmin.ai.AiChatMessage
//import com.jongmin.ai.core.AiRunStatus.READY
//import com.jongmin.ai.core.QuestionRouterType.LLM_DIRECT
//import com.jongmin.ai.core.QuestionRouterType.WEB_SEARCH
//import com.jongmin.ai.core.RunnableAiAssistant
//import com.jongmin.ai.core.platform.component.AdaptiveAnswerer
//import com.jongmin.ai.core.platform.component.QuestionAnswerer
//import com.jongmin.ai.core.platform.component.SingleInputSingleOutputGenerator
//import com.jongmin.ai.core.platform.service.AiAssistantService
//import com.jongmin.service.common.dto.SearchResponse
//import com.jongmin.service.common.component.SearXng
//import dev.langchain4j.data.message.ChatMessageType
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
//@Deprecated("Use AiAgentExecutor instead")
//@Component
//class WebSearchAgent(
//  @param:Value($$"${app.stream.event.topic.event-app}") private val topic: String,
//  private val eventSender: EventSender,
//  private val aiAssistantService: AiAssistantService,
//  private val searXng: SearXng,
//) {
//
//  private val kLogger = KotlinLogging.logger {}
//
//  private val executors = Executors.newScheduledThreadPool(32)
//
//  private val debugger = true
//
//  val id: Long = 1
//
//  @PreDestroy
//  private fun destroy() {
//    kLogger.info { "WebSearchAgent 스레드 풀 종료 시작" }
//    executors.shutdown() // 새로운 작업을 받지 않음
//    try {
//      if (!executors.awaitTermination(1, TimeUnit.MINUTES)) executors.shutdownNow()
//    } catch (e: InterruptedException) {
//      executors.shutdownNow() // 인터럽트 발생 시 강제 종료
//      Thread.currentThread().interrupt() // 인터럽트 상태 복원
//    }
//    kLogger.info { "WebSearchAgent 스레드 풀 종료 완료" }
//  }
//
//  fun logging(message: String) {
//    if (debugger) kLogger.info { message }
//  }
//
//  fun execute(
//    accountId: Long,
//    aiThreadId: Long,
//    aiRunId: Long,
//    question: String,
//    debugPicture: Boolean = false,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null,
//    onEnded: ((nodeOutput: NodeOutput<State>) -> Unit)? = null
//  ): Long {
//    executors.submit {
//      kLogger.info { "WebSearchAgent 추론 시작됨 - runId: $aiRunId" }
//      val output = AtomicReference<NodeOutput<State>>()
//      try {
//        val graph: CompiledGraph<State> = buildGraph(
//          mapOf(
//            // QUESTION.lower() to question, 정상 동작하면 이 주석 지울 것
//            ACCOUNT_ID.lower() to accountId,
//            AI_THREAD_ID.lower() to aiThreadId,
//            AI_RUN_ID.lower() to aiRunId,
//            STATUS.lower() to READY
//          ),
//          onStatusChanged
//        ).compile()
//        graph.setMaxIterations(10) // default: 25
//        graph.stream(mapOf(QUESTION.lower() to question)).forEach { output.set(it) }
//        if (debugPicture) println(graph.getGraph(GraphRepresentation.Type.MERMAID))
//      } catch (e: Exception) {
//        kLogger.error { "WebSearchAgent 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
//      } finally {
//        onEnded?.invoke(output.get())
//        kLogger.info { "WebSearchAgent 추론 완료됨 - runId: $aiRunId" }
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
//      .addNode(LLM_DIRECT.lower(), node_async { state: State -> directAnswer(state, onStatusChanged) })
//      .addNode(WEB_SEARCH.lower(), node_async { state: State -> webSearch(state, onStatusChanged) })
//      .addNode(GENERATE.lower(), node_async { state: State -> generate(state, onStatusChanged) })
//      .addConditionalEdges(
//        StateGraph.START, edge_async { state: State -> routeQuestion(state, onStatusChanged) },
//        mapOf(
//          LLM_DIRECT.lower() to LLM_DIRECT.lower(),
//          WEB_SEARCH.lower() to WEB_SEARCH.lower(),
//        )
//      )
//      .addEdge(LLM_DIRECT.lower(), StateGraph.END)
//      .addEdge(WEB_SEARCH.lower(), GENERATE.lower())
//      .addEdge(GENERATE.lower(), StateGraph.END)
//  }
//
//  /**
//   * 엣지: 질문을 직접 답변 또는 웹 검색 이나 RAG 로 라우팅
//   * @return 다음에 호출할 노드
//   */
//  private fun routeQuestion(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): String {
//    val question = state.question()
//    val agentAssistant = aiAssistantService.findFirst(AiAssistantType.QUESTION_ROUTER)
//    return SingleInputSingleOutputGenerator(agentAssistant, onStatusChanged).apply(
//      agentAssistant.getInstructionsWithCurrentTime(),
//      question
//    )
//  }
//
//  /**
//   * 노드: LLM 직접 답변
//   * @return LLM 직접 답변 추론값
//   */
//  private fun directAnswer(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): Map<String, Any> {
//    val agentAssistant = aiAssistantService.findFirst(AiAssistantType.QUESTION_ANSWERER)
//    val messages = listOf(AiChatMessage.from(ChatMessageType.USER, state.question()))
//    val result =
//      QuestionAnswerer(agentAssistant, eventSender, topic, state.accountId(), state.aiThreadId(), state.aiRunId(), onStatusChanged)
//        .apply(messages)
//    return mapOf(GENERATE.lower() to result)
//  }
//
//  private fun webSearch(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): Map<String, Any> {
//    val question = state.question()
//    val assi = aiAssistantService.findFirst(AiAssistantType.WEB_SEARCH_EXPERT)
//    val searchTerms = SingleInputSingleOutputGenerator(assi, onStatusChanged).apply(assi.getInstructionsWithCurrentTime(), question)
//    val searchFuture = CompletableFuture<SearchResponse?>()
//    searXng.search(searchTerms, 10) { searchFuture.complete(it) }
//    val snippets = searchFuture.get()?.let { it.results?.map { result -> result.snippet() } ?: emptyList() } ?: emptyList()
//    logging("웹 검색 요청: $question")
//    logging("가공된 검색어: $searchTerms")
//    logging("웹 검색 요약: $snippets")
//    return mapOf(DOCUMENTS.lower() to snippets)
//  }
//
//  private fun generate(
//    state: State,
//    onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
//  ): Map<String, Any> {
//    kLogger.info { "---답변 생성---" }
//    val question = state.question()
//    val documents = state.documents()
//    val agentAssistant = aiAssistantService.findFirst(AiAssistantType.ADAPTIVE_ANSWERER)
//    val adaptiveAnswerer: String = AdaptiveAnswerer(
//      agentAssistant, eventSender, topic, state.accountId(), state.aiThreadId(), state.aiRunId(), onStatusChanged
//    )
//      .apply(question, documents)
//    logging("생성된 답변: $adaptiveAnswerer")
//    return mapOf(GENERATE.lower() to adaptiveAnswerer)
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
//    fun aiThreadId(): Long {
//      val result: Optional<Long> = value(AI_THREAD_ID.lower())
//      return result.orElseThrow { IllegalStateException("ai thread id is not set!") }
//    }
//
//    fun aiRunId(): Long {
//      val result: Optional<Long> = value(AI_RUN_ID.lower())
//      return result.orElseThrow { IllegalStateException("ai run id is not set!") }
//    }
//  }
//}
