package com.jongmin.ai.core.platform.component.agent.executor.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.jongmin.jspring.core.util.JTimeUtils
import java.time.ZonedDateTime

data class Workflow(
  var canvasId: String? = null,
  val id: String = "",
  val nodes: List<Node> = emptyList(),
  val edges: List<Edge> = emptyList(),
  val dependencies: Map<String, List<Dependency>> = emptyMap(),
  val executionOrder: List<String> = emptyList(),
  /**
   * payload 는 저장되지 않고 request로 전달받은 데이터를 저장 및 운용하기 위한 용도로 사용된다.
   */
  @field:JsonIgnore
  var payload: Map<String, Any>? = emptyMap()
)

data class Node(
  val id: String = "",
  val type: String = "",
  val data: NodeData = NodeData()
) {
  fun isExecutable(): Boolean {
    return !(type.endsWith("-input") || type.endsWith("-visualize"))
  }

  fun getExecutionStatus(): NodeExecutionState? = data.executionState?.status

  fun isExecuted(): Boolean {
    val status = data.executionState?.status ?: return false
    return !(status == NodeExecutionState.IDLE
        || status == NodeExecutionState.WAIT
        || status == NodeExecutionState.READY
        || status == NodeExecutionState.PREPARED)
  }

  fun isDone(): Boolean {
    return data.executionState?.status == NodeExecutionState.SUCCESS
  }

  fun isNotExecuted(): Boolean {
    return !isExecuted()
  }

  fun isFinishNode(): Boolean {
    return type == "finish"
  }

  fun isNotFinishNode(): Boolean {
    return !isFinishNode()
  }
}

data class ToolHandle(
  val id: String = "",
  val name: String = "",
  val description: String? = "",
  val optional: String? = "",
  val suggestions: List<String>? = emptyList(),
)

data class NodeData(
  var config: Map<String, Any>? = null,
  val startNode: Boolean? = null,
  var executionState: ExecutionState? = null,
  val inputs: List<NodeInput> = emptyList(),
  val dynamicHandles: Map<String, List<ToolHandle>> = emptyMap()
)

data class NodeInput(val id: String = "", val title: String = "")

data class ExecutionState(
  val status: NodeExecutionState = NodeExecutionState.IDLE,
  val timestamp: ZonedDateTime = JTimeUtils.now()
)

data class Edge(
  val source: String = "",
  val target: String = "",
  val sourceHandle: String? = null,
  val targetHandle: String? = null
)

data class Dependency(
  val node: String = "",
  val sourceHandle: String? = null
)

enum class NodeExecutionState {
  PREPARED, IDLE, WAIT, READY, IN_PROGRESS, SUCCESS, ERROR
}
