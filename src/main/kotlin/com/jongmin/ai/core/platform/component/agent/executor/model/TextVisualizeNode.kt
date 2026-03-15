package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 텍스트 시각화 노드
 *
 * 입력받은 데이터를 텍스트로 변환하여 플레이그라운드에서 시각화하는 노드.
 * 워크플로우 디버깅 및 중간 결과 확인용으로 사용된다.
 *
 * 입력:
 * - input, reason: 시각화할 데이터 (Map 또는 String)
 *
 * 출력:
 * - 텍스트 문자열 (JSON 직렬화 또는 원본 String)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["text-visualize"])
class TextVisualizeNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : PreparedNodeExecutor<ExecutionContext>,
  NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun prepare(node: Node, context: ExecutionContext) = updateNodeStatus(node, context, NodeExecutionState.PREPARED)

  // 시각화 노드는 다음 노드에 대한 실행은 관여하지 않는다. //
  override fun executeInternal(node: Node, context: ExecutionContext) {
    val output = context.findAndGetInputForNode(node.id, "input", "reason") as Any
    val text = if (output is Map<*, *>) objectMapper.writeValueAsString(output) else if (output is String) output else "Not a valid output"
    logging(node, context, "  → 시각화 텍스트: ${truncateInput(text.replace("\n", " "), 200)}")
    context.storeOutput(node.id, text)
    if (context.isPlaygroundRequest())
      sendEvent(MessageType.NODE_RESULT_CREATED, mutableMapOf("nodeId" to node.id, "data" to text))
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) {
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * TextVisualizeNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("text-visualize")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return TextVisualizeNode 인스턴스
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
      return TextVisualizeNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
