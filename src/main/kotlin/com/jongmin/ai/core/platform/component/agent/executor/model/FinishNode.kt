package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 워크플로우 종료 노드
 *
 * 워크플로우의 실행을 종료하고 최종 결과를 수집하는 노드.
 * 이전 노드들의 출력값을 모아서 onFinish 콜백으로 전달하고, 스트리밍을 완료한다.
 *
 * 입력:
 * - 이전 노드들의 모든 출력값 (자동 수집)
 *
 * 출력:
 * - 없음 (워크플로우 종료)
 *
 * 동작:
 * - 이전 노드들의 출력값을 Map으로 수집
 * - context.onFinish 콜백 호출 (결과 전달)
 * - SSE 스트리밍 종료 (emitter.complete())
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["finish"])
class FinishNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    logging(node, context)

    // Finish 노드로 들어오는 모든 엣지를 가져와 이전 노드의 출력값 출력
    val incomingEdges = context.workflow.edges.filter { it.target == node.id }
    val results = mutableMapOf<String, Any>()
    if (incomingEdges.isEmpty()) kLogger.debug { "  - Result - 이전 노드의 출력값이 없습니다." } else {
      incomingEdges.forEach { edge ->
        val output = context.getOutputForNode(edge.source)
        if (output != null) results[edge.source] = output
      }
    }
    context.onFinish?.invoke(results)
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) {
    // XXX: 현재 로직 오류로 두번 호출 될 수 있음.
//    // Finish 노드로 들어오는 모든 엣지를 가져와 이전 노드의 출력값 출력
//    val incomingEdges = context.workflow.edges.filter { it.target == node.id }
//    val results = mutableMapOf<String, Any>()
//    if (incomingEdges.isEmpty()) {
//      kLogger.debug { "이전 노드의 출력값이 없어 종료를 트리거 하지 않음" }
//      return
//    } else {
//      incomingEdges.forEach { edge ->
//        val output = context.getOutputForNode(edge.source)
//        if (output == null) {
//          kLogger.debug { "이전 노드의 출력값이 없어 종료를 트리거 하지 않음" }
//          return
//        }
//        results[edge.source] = output
//      }
//    }
    // 개선이 필요하다. 베스트 솔루션은 실행된 노드 수를 저장하고 모든 노드가 실행되면 완료 메시지를 보내는 것이다.
    Thread.sleep(500)

    // context.onFinish?.invoke(results)

    // 임시적으로 다른 노드가 종료될 시간을 기다리고 있다가 모든 노드가 종료되면 완료 메시지를 보내는 방법을 사용한다.
    emitter?.complete()
    // TODO 완성된 메시지 ws로 전송
    kLogger.debug { "---------------------------------------- finished ----------------------------------------" }
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * FinishNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("finish")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return FinishNode 인스턴스
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
      return FinishNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
