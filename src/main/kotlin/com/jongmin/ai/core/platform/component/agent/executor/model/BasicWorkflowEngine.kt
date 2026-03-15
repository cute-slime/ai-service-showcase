package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.component.loop.CheckpointCallback
import com.jongmin.ai.core.platform.component.loop.LoopExecutionContext
import dev.langchain4j.data.message.ChatMessageType
import org.springframework.util.ObjectUtils
import reactor.core.publisher.FluxSink
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 워크플로우 실행 엔진
 *
 * @param checkpointCallback Loop Job에서 노드별 체크포인트 저장 시 사용 (optional).
 *                           null이면 일반 BasicExecutionContext 사용,
 *                           값이 있으면 LoopExecutionContext 사용하여 노드 상태 변경 시 콜백 호출.
 */
class BasicWorkflowEngine(
  val objectMapper: ObjectMapper,
  val factory: NodeExecutorFactory,
  val session: JSession,
  val workflow: Workflow,
  val topic: String,
  val eventSender: EventSender,
  private val emitter: FluxSink<String>? = null,
  private val cancellationManager: com.jongmin.ai.core.platform.component.AIInferenceCancellationManager? = null,
  private val cancellationKey: String? = null,
  onFinish: ((output: Any?) -> Unit)? = null,
  private val checkpointCallback: CheckpointCallback? = null
) : WorkflowEngine {
  // 체크포인트 콜백이 있으면 LoopExecutionContext, 없으면 BasicExecutionContext 사용
  private val context: ExecutionContext = if (checkpointCallback != null) {
    LoopExecutionContext(factory, workflow, checkpointCallback, onFinish)
  } else {
    BasicExecutionContext(factory, workflow, onFinish)
  }

  private fun prepareNodes() {
    workflow.nodes
      .filter { it.type.endsWith("-input") || it.type.endsWith("-visualize") }
      .forEach {
        val node = factory.getExecutor<ExecutionContext>(objectMapper, session, it.type, topic, eventSender, emitter, workflow.canvasId)
        if (PreparedNodeExecutor::class.java.isAssignableFrom(node::class.java)) {
          @Suppress("UNCHECKED_CAST")
          (node as PreparedNodeExecutor<ExecutionContext>).prepare(it, context)
        }
      }
  }

  /**
   * Workflow를 실행합니다.
   *
   * 플로우의 question으로 사용될 메시지는 messages의 마지막 메시지를 사용합니다.
   * messages가 null이면 시작 노드의 config 값을 이용하게됩니다.
   *
   * 만약 실행 노드의 컨피그 옵션에 multiturn이 true로 설정되어 있으면,
   * 전달된 메시지를 모두 첨부합니다.
   */
  override fun executeWorkflow(messages: List<IAiChatMessage>?) {
    // 취소 확인: Workflow 실행 전 취소 여부 체크
    if (cancellationManager != null && cancellationKey != null) {
      if (cancellationManager.isCancelled(cancellationKey)) {
        emitter?.complete()
        return
      }
    }

    val startNode = workflow.nodes.firstOrNull { it.type.endsWith("-input") && (it.data.startNode == true) }
    if (startNode == null) {
      val e = IllegalArgumentException("시작 노드가 없습니다.")
      emitter?.error(e)
      throw e
    }

    messages?.let {
      val config = startNode.data.config?.toMutableMap() ?: mutableMapOf()
      val message = messages.last()
      if (message.type() == ChatMessageType.TOOL_EXECUTION_RESULT
        && message.content().startsWith("{").and(message.content().endsWith("}"))
      ) {
        config["value"] = objectMapper.readValue(message.content(), Map::class.java)
      } else {
        config["value"] = message.content()
      }
      startNode.data.config = config
      context.setMessages(messages)
    } ?: run {
      if (startNode.type == "case-input") {
        val config = startNode.data.config?.toMutableMap() ?: mutableMapOf()
        val tools = startNode.data.dynamicHandles["tools"] ?: emptyList()
        var value = config["value"] as String? // 빈문자열과 NULL 에대해서는 빈 객체로 처리
        value = if (ObjectUtils.isEmpty(value)) "{}" else value
        val data = objectMapper.readValue(value, object : TypeReference<HashMap<String, Any>>() {})
        config["value"] = data
        tools
          .findLast { it.id == config["selectedTool"] }
          ?.let { sourceHandle -> config["value"] = mapOf(sourceHandle.name to data) }
        startNode.data.config = config
      }
    }

    prepareNodes()
    context.setQuestion(startNode.data.config?.get("value")!!)
    factory.getExecutor<ExecutionContext>(objectMapper, session, startNode.type, topic, eventSender, emitter, workflow.canvasId)
      .execute(startNode, context)
  }

  /**
   * 키-값 맵 기반으로 Workflow를 실행합니다.
   *
   * 이 메서드는 구조화된 데이터를 입력받아 워크플로우를 실행하며,
   * input의 각 키-값 쌍을 시작 노드의 config 값으로 설정합니다.
   *
   * @param input 워크플로우 실행에 필요한 입력 데이터 맵 (필수)
   * @throws IllegalArgumentException 시작 노드가 없는 경우
   */
  override fun executeWorkflow(input: Map<String, Any>) {
    // 취소 확인: Workflow 실행 전 취소 여부 체크
    if (cancellationManager != null && cancellationKey != null) {
      if (cancellationManager.isCancelled(cancellationKey)) {
        emitter?.complete()
        return
      }
    }

    // 시작 노드 찾기: -input으로 끝나고 startNode가 true인 노드
    val startNode = workflow.nodes.firstOrNull { it.type.endsWith("-input") && (it.data.startNode == true) }
    if (startNode == null) {
      val e = IllegalArgumentException("시작 노드가 없습니다.")
      emitter?.error(e)
      throw e
    }

    val config = (startNode.data.config?.toMutableMap() ?: input.toMutableMap())

    // 시작 노드 타입에 따라 분기 처리
    // case-input: JSON 형식의 value를 파싱하여 Map으로 변환
    // text-input 등: 일반 문자열 그대로 사용
    if (startNode.type == "case-input") {
      var value = config["value"] as String?
      value = if (ObjectUtils.isEmpty(value)) "{}" else value

      // JSON 문자열을 HashMap으로 파싱
      val data = objectMapper.readValue(value, object : TypeReference<HashMap<String, Any>>() {})
      config["value"] = data

      val tools = startNode.data.dynamicHandles["tools"] ?: emptyList()
      tools
        .findLast { it.id == config["selectedTool"] }
        ?.let { sourceHandle -> config["value"] = mapOf(sourceHandle.name to data) }
    }
    // text-input 등 다른 타입은 config["value"]를 그대로 사용 (문자열)

    startNode.data.config = config

    prepareNodes()
    context.setQuestion(startNode.data.config?.get("value")!!)
    factory.getExecutor<ExecutionContext>(objectMapper, session, startNode.type, topic, eventSender, emitter, workflow.canvasId)
      .execute(startNode, context)
  }
}
