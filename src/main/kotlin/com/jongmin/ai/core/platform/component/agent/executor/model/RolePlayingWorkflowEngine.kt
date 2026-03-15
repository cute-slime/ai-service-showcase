package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.IAiChatMessage
import dev.langchain4j.data.message.ChatMessageType
import org.springframework.util.ObjectUtils
import reactor.core.publisher.FluxSink
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

class RolePlayingWorkflowEngine(
  val objectMapper: ObjectMapper,
  val factory: NodeExecutorFactory,
  val session: JSession,
  val workflow: Workflow,
  val topic: String,
  val eventSender: EventSender,
  private val emitter: FluxSink<String>? = null,
  onFinish: ((output: Any?) -> Unit)? = null
) : WorkflowEngine {
  private val context = RolePlayingExecutionContext(factory, workflow, onFinish)

  private fun prepareNodes() {
    workflow.nodes
      .filter { it.type.endsWith("-input") || it.type.endsWith("-visualize") }
      .forEach {
        val node =
          factory.getExecutor<RolePlayingExecutionContext>(objectMapper, session, it.type, topic, eventSender, emitter, workflow.canvasId)
        if (PreparedNodeExecutor::class.java.isAssignableFrom(node::class.java)) {
          @Suppress("UNCHECKED_CAST")
          (node as PreparedNodeExecutor<RolePlayingExecutionContext>).prepare(it, context)
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
        // 매핑되는 툴이 있을 경우 값을 재설정한다.
        tools
          .findLast { it.id == config["selectedTool"] }
          ?.let { sourceHandle -> config["value"] = mapOf(sourceHandle.name to data) }
        // //////////////////////////////
        startNode.data.config = config
      }
    }

    prepareNodes()
    context.setQuestion(startNode.data.config?.get("value")!!)
    factory.getExecutor<RolePlayingExecutionContext>(objectMapper, session, startNode.type, topic, eventSender, emitter, workflow.canvasId)
      .execute(startNode, context)
  }

  override fun executeWorkflow(input: Map<String, Any>) {
    TODO("Not yet implemented")
  }
}
