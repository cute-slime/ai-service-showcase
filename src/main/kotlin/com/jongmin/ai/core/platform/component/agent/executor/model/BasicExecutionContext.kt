package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.ai.core.IAiChatMessage

open class BasicExecutionContext(
  final override val factory: NodeExecutorFactory,
  final override val workflow: Workflow,
  final override val onFinish: ((output: Any?) -> Unit)?
) : ExecutionContext {
  private val nodeOutputs = mutableMapOf<String, Any>()
  private val userInputs = mutableMapOf<String, Any>()
  private val store = workflow.payload?.toMutableMap() ?: mutableMapOf()

  override fun setQuestion(input: Any) = input.also { userInputs["question"] = it }

  override fun setMessages(messages: List<IAiChatMessage>) = messages.also { userInputs["messages"] = it }

  @Suppress("UNCHECKED_CAST")
  override fun getMessages(): List<IAiChatMessage>? = userInputs["messages"] as? List<IAiChatMessage>

  override fun isPlaygroundRequest(): Boolean = userInputs["messages"] == null

  override fun storeOutput(nodeId: String, output: Any) {
    nodeOutputs[nodeId] = output
  }

  override fun get(key: String): Any? {
    return store[key]
  }

  override fun set(key: String, value: Any) {
    store[key] = value
  }

  override fun getNextNode(nodeId: String): Node? {
    val nextEdges = workflow.edges.filter { it.source == nodeId }
    if (nextEdges.isEmpty()) return null

    return nextEdges.firstOrNull()?.let { edge ->
      workflow.nodes.firstOrNull { it.id == edge.target }
    }
  }

  override fun getNextNodeByType(nodeId: String, nodeType: String): Node? {
    val nextEdges = workflow.edges.filter { it.source == nodeId }
    if (nextEdges.isEmpty()) return null

    return nextEdges.firstOrNull()?.let { edge ->
      workflow.nodes.firstOrNull { it.id == edge.target && it.type == nodeType }
    }
  }

  override fun getPreviousNodeByType(nodeId: String, nodeType: String): Node? {
    val previousEdges = workflow.edges.filter { it.target == nodeId }

    if (previousEdges.isEmpty()) return null
    return previousEdges.firstOrNull()?.let { edge ->
      workflow.nodes.firstOrNull { it.id == edge.source && it.type == nodeType }
    }
  }

  override fun getPreviousNode(nodeId: String): Node? {
    val previousEdges = workflow.edges.filter { it.target == nodeId }

    if (previousEdges.isEmpty()) return null
    return previousEdges.firstOrNull()?.let { edge ->
      workflow.nodes.firstOrNull { it.id == edge.source }
    }
  }

  override fun findAndGetInputForNode(nodeId: String, inputHandle: String, valueMapper: String?): Any? {
    val sourceInfo = workflow.edges.firstOrNull { it.target == nodeId && it.targetHandle == inputHandle }
      ?.let { edge ->
        nodeOutputs[edge.source]?.let { output ->
          if (output is Map<*, *>) output[edge.sourceHandle] ?: output[valueMapper] ?: output
          else output
        }
      }
    return sourceInfo ?: userInputs[inputHandle]
  }

  override fun findDestHandle(nodeId: String, sourceHandle: String): String {
    return workflow.edges.firstOrNull { it.source == nodeId && it.sourceHandle == sourceHandle }?.targetHandle
      ?: throw IllegalAccessException("존재하지 않는 엣지입니다.")
  }

  override fun getOutputForNode(nodeId: String): Any? = nodeOutputs[nodeId]

  override fun updateNodeStatus(id: String, status: NodeExecutionState) {
    val node = workflow.nodes.first { it.id == id }
    node.data.executionState = ExecutionState(status)
  }

  override fun getOrDefault(s: String, default: Any): Any {
    val value = get(s)
    return value ?: default
  }
}
