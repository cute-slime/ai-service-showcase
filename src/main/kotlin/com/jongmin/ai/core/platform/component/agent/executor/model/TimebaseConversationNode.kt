package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.JTimeUtils
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Duration.between
import java.time.ZonedDateTime

/**
 * 시간 기반 대화 분기 노드
 *
 * 마지막 로그 시간을 기준으로 경과 시간을 계산하여 적절한 대화 분기로 라우팅하는 노드.
 * 롤플레잉 시나리오에서 시간 흐름에 따른 대화 흐름 제어에 사용된다.
 *
 * 입력:
 * - additionalPrompt (선택): 추가 프롬프트 텍스트
 *
 * 출력:
 * - 선택된 분기명 (String): "first", "1m", "5m", "1h", "1d", "else" 등
 *
 * 설정:
 * - tools (동적 핸들): 시간 기반 분기 목록 (예: ["first", "1m", "5m", "1h", "else"])
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["timebase-conversation"])
class TimebaseConversationNode<T : RolePlayingExecutionContext>(
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
    val additionalPrompt = context.findAndGetInputForNode(node.id, "additionalPrompt") as? String
    additionalPrompt?.let { context.addAdditionalPrompt(additionalPrompt) }
    // 이전 노드가 text-input 일 수도 있음, 가장 명확한건 selected 노드의 이전 노드여야 함
    context.getPreviousNode(node.id)?.let { context.getOutputForNode(it.id) } as Map<*, *>
    val branches = node.data.dynamicHandles["tools"] ?: emptyList()
    val lastLoggedAt = context.getLastLoggedAt()
    val branch = parseBranch(branches, lastLoggedAt)
    val selectedTool = branches.find { it.name == branch }
    if (selectedTool == null) throw BadRequestException("Router ${node.id}: No routing source '$branch' found")
    // context.addSituation(intent, additionalPrompt)
    logging(node, context, "  → 탐색 경로 결정: ${selectedTool.name}")

    if (context.isPlaygroundRequest())
      sendEvent(MessageType.NODE_RESULT_CREATED, mutableMapOf("nodeId" to node.id, "data" to branch))

    context.storeOutput(node.id, branch)
  }

  private fun parseBranch(branches: List<ToolHandle>, lastLoggedAt: ZonedDateTime?): String {
    val now = JTimeUtils.now()

    if (lastLoggedAt == null) return "first"

    val durationSinceLastLog = between(lastLoggedAt, now)

    // 가장 가까운 미래 시간대 찾기 (정렬되어있어야 정상 동작함)
    val closestFutureBranch = branches.filter { branch ->
      val timeUnit = branch.name.last()
      val timeValue = branch.name.dropLast(1).toLongOrNull() ?: 0L

      val duration = when (timeUnit) {
        'm' -> Duration.ofMinutes(timeValue)
        'h' -> Duration.ofHours(timeValue)
        'd' -> Duration.ofDays(timeValue)
        'w' -> Duration.ofDays(timeValue * 7)
        'y' -> Duration.ofDays(timeValue * 365)
        else -> Duration.ZERO
      }

      // 현재 시간과의 차이가 duration보다 작은 경우만 필터링 (미래 시간대)
      durationSinceLastLog < duration
    }.minByOrNull { branch ->
      val timeUnit = branch.name.last()
      val timeValue = branch.name.dropLast(1).toLongOrNull() ?: 0L

      val duration = when (timeUnit) {
        'm' -> Duration.ofMinutes(timeValue)
        'h' -> Duration.ofHours(timeValue)
        'd' -> Duration.ofDays(timeValue)
        'w' -> Duration.ofDays(timeValue * 7)
        'y' -> Duration.ofDays(timeValue * 365)
        else -> Duration.ZERO
      }

      // 미래 시간대 중 가장 가까운 시간 차이 계산
      (duration.toMillis() - durationSinceLastLog.toMillis())
    }

    return closestFutureBranch?.name ?: "else"
  }

  override fun propagateOutput(node: Node, context: T) {
    val routes = context.getOutputForNode(node.id) as Map<*, *>
    routes.keys.forEach { sourceHandle ->
      val outgoingEdges = context.workflow.edges.filter { it.source == node.id && it.sourceHandle == sourceHandle }
      outgoingEdges.forEach { edge ->
        val targetNode = context.workflow.nodes.first { it.id == edge.target }

        factory.getExecutor<T>(
          objectMapper,
          session,
          targetNode.type,
          topic,
          eventSender,
          emitter,
          canvasId
        ).execute(targetNode, context)
      }
    }
  }

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * TimebaseConversationNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("timebase-conversation")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return TimebaseConversationNode 인스턴스
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
      return TimebaseConversationNode<RolePlayingExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
//    rp params 를 통해 이전 대화를 확보
//    println("@@@@@@")
//    // XXX 현재 액션의 컨텍스트를 파악해야한다.
//    // 아래의 재료를 어떻게 사용할 것인지 파악해야한다.
//    println("additionalPrompt: $additionalPrompt")
//    println(getRolePlaying(context))
//    println(getWorldview(context))
//    println(getStage(context))
//    println(getPlace(context))
//    println(getCharacters(context))
//    // println(getMessages(context))
//    println("intent: $intent")
//
//    val situation = Situation
//      .builder(context)
//      .intent(intent)
//      .build()
//    println("situation: $situation")
//
//    val scene = Scene
//      .builder(situation)
//      .build()
//    println("scene: $scene")
//
//    scene.getActors().forEach { actor ->
//      println("actor: $actor")
//      actor.action()
//    }
//    println("@@@@@@")
