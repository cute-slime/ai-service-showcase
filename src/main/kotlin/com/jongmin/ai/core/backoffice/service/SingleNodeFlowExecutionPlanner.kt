package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.platform.component.agent.executor.model.Node
import com.jongmin.ai.core.platform.component.agent.executor.model.Workflow
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Single Node Flow 실행 계획/준비 전담 컴포넌트
 *
 * Flow 모드의 워크플로우 파싱, 실행 순서 계산, 노드 config 적용 책임을 분리한다.
 */
@Component
class SingleNodeFlowExecutionPlanner(
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  fun parseWorkflow(workflowData: Any?): Workflow? {
    return try {
      when (workflowData) {
        is Map<*, *> -> objectMapper.convertValue(workflowData, Workflow::class.java)
        is String -> objectMapper.readValue(workflowData, Workflow::class.java)
        else -> null
      }
    } catch (e: Exception) {
      kLogger.error(e) { "워크플로우 파싱 실패" }
      null
    }
  }

  fun getExecutionOrder(workflow: Workflow, startNodeId: String): List<Node> {
    val nodeMap = workflow.nodes.associateBy { it.id }
    val adjacencyList = mutableMapOf<String, MutableList<String>>()

    workflow.edges.forEach { edge ->
      adjacencyList.getOrPut(edge.source) { mutableListOf() }.add(edge.target)
    }

    val reachableNodes = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(startNodeId)
    reachableNodes.add(startNodeId)

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      adjacencyList[current]?.forEach { next ->
        if (next !in reachableNodes) {
          reachableNodes.add(next)
          queue.add(next)
        }
      }
    }

    val inDegree = mutableMapOf<String, Int>()
    reachableNodes.forEach { inDegree[it] = 0 }

    workflow.edges.filter { it.source in reachableNodes && it.target in reachableNodes }
      .forEach { edge ->
        inDegree[edge.target] = (inDegree[edge.target] ?: 0) + 1
      }

    val sortedOrder = mutableListOf<String>()
    val zeroInDegree = ArrayDeque<String>()

    if (startNodeId in reachableNodes) {
      zeroInDegree.add(startNodeId)
      inDegree.remove(startNodeId)
    }

    inDegree.filter { it.value == 0 }.keys.forEach {
      if (it != startNodeId) zeroInDegree.add(it)
    }

    while (zeroInDegree.isNotEmpty()) {
      val current = zeroInDegree.removeFirst()
      sortedOrder.add(current)

      adjacencyList[current]?.filter { it in reachableNodes }?.forEach { next ->
        val newDegree = (inDegree[next] ?: 1) - 1
        inDegree[next] = newDegree
        if (newDegree == 0) {
          zeroInDegree.add(next)
        }
      }
    }

    return sortedOrder.mapNotNull { nodeMap[it] }
  }

  fun applyConfigToNode(
    node: Node,
    aiAssistantId: Long?,
    streaming: Boolean,
    overrides: Map<String, Any>?
  ): Node {
    val config = (node.data.config?.toMutableMap() ?: mutableMapOf()).apply {
      aiAssistantId?.let { this["aiAssistantId"] = it }
      this["streaming"] = streaming
      overrides?.forEach { (k, v) -> this[k] = v }
    }
    return node.copy(data = node.data.copy(config = config))
  }
}
