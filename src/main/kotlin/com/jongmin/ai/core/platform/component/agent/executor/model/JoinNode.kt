package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * Join 노드 - 워크플로우 흐름 동기화
 *
 * Thread.join()과 유사하게 여러 병렬 흐름이 모두 완료될 때까지 대기한 후,
 * 입력으로 들어온 값들을 그대로 각각의 출력 핸들로 전달한다.
 *
 * Merge 노드와의 차이점:
 * - Merge: 여러 입력을 하나의 결과로 합침
 * - Join: 여러 입력을 각각 그대로 출력 (pass-through)
 *
 * 사용 예시:
 * ```
 * [컨셉] ──┐
 * [진실] ──┼──▶ [Join] ──▶ concept, truth, characters 각각 출력
 * [캐릭터]─┘
 * ```
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["join"])
class JoinNode<T : ExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<T>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {

  /**
   * 모든 입력이 준비될 때까지 대기
   *
   * dynamicHandles의 inputs에 정의된 모든 핸들에 값이 들어올 때까지 WAIT 상태 유지
   */
  override fun waitIfNotReady(node: Node, context: T): Boolean {
    val inputs = node.data.dynamicHandles["inputs"] as? List<ToolHandle> ?: return false

    return inputs.any { handle ->
      val prevNodeOutput = context.findAndGetInputForNode(node.id, handle.id)
      prevNodeOutput == null
    }
  }

  /**
   * 입력값들을 그대로 출력으로 전달 (pass-through)
   *
   * 각 입력 핸들의 값을 동일한 이름의 출력 핸들로 저장한다.
   * 데이터 변환 없이 그대로 전달하여 워크플로우 흐름만 동기화한다.
   */
  override fun executeInternal(node: Node, context: T) {
    val inputs = node.data.dynamicHandles["inputs"] as? List<ToolHandle> ?: return

    // 각 입력을 동일한 핸들 ID로 출력에 저장 (pass-through)
    val outputMap = mutableMapOf<String, Any>()

    inputs.forEach { handle ->
      val inputValue = context.findAndGetInputForNode(node.id, handle.id)
      if (inputValue != null) {
        outputMap[handle.id] = inputValue
        kLogger.debug { "  └─ [Join] ${handle.id}: ${inputValue.toString().take(100)}..." }
      }
    }

    logging(node, context, "  → Join 완료: ${outputMap.keys.joinToString(", ")} (${outputMap.size}개 핸들)")

    // Map 형태로 저장하면 각 핸들별로 출력됨
    context.storeOutput(node.id, outputMap)
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * JoinNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("join")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return JoinNode 인스턴스
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
      return JoinNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
