package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * 상태 기반 라우터 노드
 *
 * StateManagerNode에서 추출한 상태값에 따라 라우팅을 결정하는 노드.
 * 모든 상태가 지정되면 "ready" 핸들로, 미지정 상태가 있으면 해당 필드 핸들로 라우팅한다.
 *
 * 입력:
 * - input: 라우팅할 입력 텍스트
 *
 * 출력:
 * - ready 핸들: 모든 상태가 지정된 경우 입력 전달
 * - 상태 필드 핸들: 미지정 상태가 있는 경우 각 필드별 description 전달
 *
 * 컨텍스트 의존성:
 * - context.get("states"): StateManagerNode에서 저장한 상태 맵 조회
 *
 * 설정:
 * - tools: 확인할 상태 필드 목록 (동적 핸들)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["stateful-router"])
class StatefulRouterNode<T : ExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<T>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: T): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: T) {
    val input = context.findAndGetInputForNode(node.id, "input") as String
    val routerFields = node.data.dynamicHandles["tools"] ?: emptyList()
    val store = context.get("states") as Map<*, *>?
    val output = mutableMapOf<String, Any>()
    val counter = AtomicInteger()
    routerFields.forEach { routerField ->
      Thread.sleep(50)
      val routerKey = routerField.name
      val storedKey = store?.get(routerKey) as? String
      when (storedKey) {
        null, "NOT_SPECIFIED" -> {
          @Suppress("UNCHECKED_CAST")
          val suggestions = context.getOrDefault("suggestions", mutableListOf<String>()) as MutableList<String>
          routerField.suggestions?.let { suggestions.addAll(it) }
          context.set("suggestions", suggestions)
          val edgeId = context.findDestHandle(node.id, routerField.id)
          output[edgeId] = routerField.description!!
        }

        else -> {
          val edgeId = context.findDestHandle(node.id, routerField.id)
          output[edgeId] = ""
          counter.addAndGet(1)
        }
      }
    }

    logging(node, context, "  → 출력값: $output")

    if (counter.get() == routerFields.size) {
      context.storeOutput(node.id, input)
    } else {
      context.storeOutput(node.id, output)
    }
  }

  override fun propagateOutput(node: Node, context: T) {
    val output = context.getOutputForNode(node.id)
    val outgoingEdges = context.workflow.edges.filter { it.source == node.id }
    if (output is Map<*, *>) {
      executeTargetNode(outgoingEdges.first { it.sourceHandle != "ready" }, context)
    } else {
      executeTargetNode(outgoingEdges.first { it.sourceHandle == "ready" }, context)
    }
  }

  private fun executeTargetNode(edge: Edge, context: T, async: Boolean = true) {
    val targetNode = context.workflow.nodes.first { it.id == edge.target }
    val executor = factory.getExecutor<T>(objectMapper, session, targetNode.type, topic, eventSender, emitter, canvasId)
    if (async) {
      executor.execute(targetNode, context)
    } else {
      executor.execute(targetNode, context, false)
    }
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * StatefulRouterNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("stateful-router")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return StatefulRouterNode 인스턴스
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
      return StatefulRouterNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
