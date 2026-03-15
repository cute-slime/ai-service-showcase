package com.jongmin.ai.role_playing.platform.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.component.agent.executor.model.*
import com.jongmin.ai.role_playing.platform.entity.RolePlaying
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

@Component
class RolePlayingExecutor(
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.stream.event.topic.event-app}") private val topic: String,
  private val eventSender: EventSender,
  private val factory: NodeExecutorFactory,
) {
  fun execute(
    session: JSession,
    rolePlaying: RolePlaying,
    emitter: FluxSink<String>? = null,
    messages: List<IAiChatMessage>? = null,
    onFinish: ((output: Any?) -> Unit)? = null
  ) {
    val workflow = objectMapper.convertValue(rolePlaying.workflow, Workflow::class.java)
    // TODO payload 가 아닌 rolePlayingContext 로 변경, 많은 유틸리티 메소드들을 저장해야한다. ex) 데이터 캐싱
    // 역할극에서 반드시 참조할 수 있는 컨텍스트를 미리 할당한다. 그외 레퍼런스는 런타임에 노드에서 참조한다.
    workflow.payload = mapOf("rolePlayingId" to rolePlaying.id, "worldviewId" to rolePlaying.worldviewId)
    workflow.nodes.forEach { node -> node.data.executionState = ExecutionState(NodeExecutionState.IDLE) }
    RolePlayingWorkflowEngine(objectMapper, factory, session.deepCopy(), workflow, topic, eventSender, emitter, onFinish)
      .executeWorkflow(messages)
  }
}
