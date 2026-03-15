package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 텍스트 입력 노드
 *
 * FE에서 사용자가 입력한 텍스트를 워크플로우 시작점으로 제공하는 노드.
 * 설정(config)의 value 필드에서 텍스트를 읽어 출력으로 전달한다.
 *
 * 입력:
 * - 없음 (시작 노드로 사용)
 *
 * 설정 (config):
 * - value: String - 사용자 입력 텍스트 (필수, 비어있으면 예외 발생)
 *
 * 출력:
 * - value 문자열
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["text-input"])
class TextInputNode(
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

  override fun prepare(node: Node, context: ExecutionContext) {
    val value = node.data.config?.get("value") as String
    if (value.isBlank()) {
      kLogger.info { "텍스트 입력 노드의 value 값이 비어있습니다. - nodeId: ${node.id}" }
      throw BadRequestException("텍스트 입력 노드의 value 값이 비어있습니다.")
    }
    context.storeOutput(node.id, value)
    updateNodeStatus(node, context, NodeExecutionState.PREPARED)
  }

  // 인풋노드는 다음 노드에 대한 실행은 관여하지 않는다. //
  override fun executeInternal(node: Node, context: ExecutionContext) {
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) {
    defaultPropagateOutput(node, context)
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * TextInputNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("text-input")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return TextInputNode 인스턴스
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
      return TextInputNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
