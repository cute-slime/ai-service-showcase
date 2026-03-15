package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.role_playing.CharacterType
import com.jongmin.ai.role_playing.IntentType
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Case Input 노드
 *
 * 롤플레잉 게임의 시나리오 시작 노드.
 * 사용자의 도구 선택(selectedTool)에 따라 동적 핸들로 Intent를 라우팅한다.
 *
 * 입력:
 * - 없음 (시작 노드로 사용)
 *
 * 설정 (config):
 * - value: Map<*, *> - 시나리오 시작 데이터
 * - selectedTool: String - 사용자가 선택한 도구 ID (필수)
 * - placeId: Number - 장소 ID (필수)
 * - stageId: Number - 스테이지 ID (필수)
 * - characterIds: Collection<Number> - 캐릭터 ID 목록 (필수)
 *
 * 출력:
 * - value Map (동적 핸들을 통해 Intent로 전달)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["case-input"])
class CaseInputNode<T : RolePlayingExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : PreparedRpNodeExecutor<T>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: T): Boolean {
    context.getStage()
    return false
  }

  /**
   * 이 과정은 WorkflowEngine.executeWorkflow()에서 호출됩니다.
   * 시작노드의 경우 config.value는 엔진에서 설정됩니다. (시작 노드의 경우만 설정됨)
   * 서비스에서 호출된 경우 워크 플로우 엔진을 호출하기 전에 이 설정이 결정되어야 함.
   */
  override fun prepare(node: Node, context: T) {
    val config = node.data.config ?: emptyMap()
    val value = config["value"] as Map<*, *>?
    val selectedToolId = config["selectedTool"] as? String

    if (value == null || selectedToolId == null) {
      throw BadRequestException("시나리오 노드의 value 값이나 selectedTool이 비어있습니다.")
    }

    // 필수값 설정 및 출력 저장한다, 시작 노드에서는 상황(situation)을 설정하지 않는다.
    context.set("placeId", (config["placeId"] as Number).toLong())
    context.set("stageId", (config["stageId"] as Number).toLong())
    context.set("characterIds", (config["characterIds"] as Collection<*>).map { (it as Number).toLong() }.toSet())
    context.storeOutput(node.id, value)

    updateNodeStatus(node, context, NodeExecutionState.PREPARED)
  }

  // 입력 노드는 다음 노드에 대한 실행에 관여하지 않는다.
  override fun executeInternal(node: Node, context: T) {}

  override fun propagateOutput(node: Node, context: T) {
    val routed = AtomicBoolean(false)
    val value = context.getOutputForNode(node.id) as Map<*, *>
    val tools = node.data.dynamicHandles["tools"] ?: emptyList()
    tools.findLast { value[it.name] != null }?.let { sourceHandle ->
      context.workflow.edges
        .filter { it.source == node.id && it.sourceHandle == sourceHandle.id }
        .forEach { edge ->
          val targetNode = context.workflow.nodes.first { it.id == edge.target }
          context.storeOutput(
            edge.sourceHandle!!,
            context.setIntent(
              IntentType.valueOf(sourceHandle.name.uppercase()),
              session.accountId,
              CharacterType.USER,
              (value[sourceHandle.name] as Map<*, *>)["content"] as String
            )
          )
          factory.getExecutor<T>(
            objectMapper,
            session,
            targetNode.type,
            topic,
            eventSender,
            emitter,
            canvasId
          ).execute(targetNode, context)
          routed.set(true)
        }
    }

    if (!routed.get()) emitter?.complete()
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * CaseInputNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("case-input")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return CaseInputNode 인스턴스
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
      return CaseInputNode<RolePlayingExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
