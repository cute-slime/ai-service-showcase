package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.role_playing.Scenario.Companion.ScenarioBuilder
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 시나리오 장면 구성 노드
 *
 * 롤플레잉 시나리오의 장면(Scene)을 구성하고 배우(Actor)들을 선택하는 노드.
 * Scenario를 빌드하고 컨텍스트에 설정하며, 단일 배우 시나리오인 경우 해당 배우 ID를 반환한다.
 *
 * 입력:
 * - additionalPrompt (선택): 추가 프롬프트 텍스트
 *
 * 출력:
 * - 배우 ID (Long 또는 빈 문자열): 단일 배우인 경우 배우 ID, 아니면 빈 문자열
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["scene"])
class SceneNode<T : RolePlayingExecutionContext>(
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
    additionalPrompt?.let { context.addAdditionalPrompt(it) }

    // if (context.isPlaygroundRequest())
    //   sendEvent(MessageType.AI_AGENT_WORKFLOW_NODE_RESULT_CREATED, mutableMapOf("nodeId" to node.id, "data" to branch))
    context.storeOutput(node.id, build(context) ?: "")
  }

  fun build(context: T): Long? {
    val scenario = ScenarioBuilder()
      .build(session, context)
    context.setScenario(scenario)

    scenario.paper.insert(
      0,
      """# Instructions
You are the director in charge of leading the role-play.
The contents of these instructions are never sent out as a response and are only for reference in the play.
Please visualize the scene to be directed by synthesizing the given information, and select the actors who will perform.
This play is conducted in Korean.

"""
    )
    Thread.sleep(100)
    return if (scenario.actors.size == 1) scenario.actors.first().getId() else null
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * SceneNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("scene")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return SceneNode 인스턴스
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
      return SceneNode<RolePlayingExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}


// {{"Sarah's Goals"}}: [
//     "Join orchestra as first chair violinist to impress Jack",
//     "Leave violin sheet music in Jack's locker with lipstick marks"
// ]
// {{"What's Currently on Sarah's Mind"}}: [
//     "I think Mimi is just using me, she can be such a controlling freak",
//     "Would Jack secretly judging me after watching me making out with Luke?"
// ]
//
// {{"Sarah's relationship with Jack"}}: "Romantic interest",
// {{"friendliness"}}: "65/100",
// {{"attraction"}}: "80/100",
// {{"romance"}}: "25/100",
// {{"sexual encounters"}}: "0",
// {{"known facts"}}: [
//     "on the school basketball team",
//     "single???",
//     "has trouble openning up to people"
// ],
//
// [Character Relationship Guidance Start]
// How to interpret "Social Relationships" stats in your character card:
// - Relationship: This is what you think your relationship with the other person is.
// - Know Facts: These are things you know about the other person, it may not be comprehensive or hundred percent accurate, just things you learnt about that person.
// - Endearment: How do you address that person, this can be a nickname for a friend or a pet name for a lover.
// - Friendliness: How friendly do you feel toward that person, on a scale from -100 to 100. -100 means absolute rivalry and 100 means best friend.
// - Attraction/Chemistry: How much are you attracted to the other person, on a scale from -100 to 100. -100 means absolutely no interest, 100 means you find them hot as fuck. This can be physical/appearance attraction, mental/spiritual attraction, or simply just sexual attraction.
// - Romance: How much are you in love with that person, on a scale from -100 to 100. -100 means you would rather die than getting romantically involved with that person, and 100 means you are completely in love with that person.
// - Sexual Encounters: How many times have you had sexual encounter with that person.
//
// !!!Important!!! Friendliness, Attraction and Romance affect you independently, for example:
// - You may act unfriendly to a person (Friendliness: -50), but still find that person sexually irresistible (Attraction: 70).
// - You may find the other person boring and vanilla (Attraction: 10), but still deeply in love with them (Romance: 90).
// - You may be in love with the other person (Romance: 70), but you recently had a big fight (Friendliness: -10).
// [Character Relationship Guidance End]
