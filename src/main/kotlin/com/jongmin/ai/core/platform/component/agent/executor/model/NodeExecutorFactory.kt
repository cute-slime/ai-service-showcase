package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

@Component
class NodeExecutorFactory(
  val applicationContext: ApplicationContext,
  private val registry: NodeExecutorRegistry
) {

  /**
   * 노드 타입에 해당하는 NodeExecutor 인스턴스를 생성한다.
   *
   * NodeExecutorRegistry를 통해 @NodeType 어노테이션으로 등록된 노드들을 자동으로 조회한다.
   * Registry에 없는 타입은 IllegalArgumentException을 발생시킨다.
   *
   * @param objectMapper JSON 직렬화/역직렬화를 위한 ObjectMapper
   * @param session 현재 사용자 세션 정보
   * @param type 노드 타입 문자열 (예: "scenario-concept", "text-visualize")
   * @param topic Kafka 토픽명
   * @param eventSender 이벤트 전송 컴포넌트
   * @param emitter SSE(Server-Sent Events) 스트림 (선택)
   * @param canvasId 캔버스 ID (선택)
   * @return 생성된 NodeExecutor 인스턴스
   * @throws IllegalArgumentException Registry에 등록되지 않은 타입인 경우
   */
  private fun getTypeOfExecutor(
    objectMapper: ObjectMapper,
    session: JSession,
    type: String,
    topic: String,
    eventSender: EventSender,
    emitter: FluxSink<String>? = null,
    canvasId: String?
  ): NodeExecutor<out ExecutionContext> {
    // NodeExecutorRegistry에서 프로바이더 조회 (자동 등록된 모든 노드)
    val provider = registry.getProvider(type)
      ?: throw IllegalArgumentException(
        "Unknown node type: $type. Available types: ${registry.getSupportedTypes().sorted().joinToString()}"
      )

    // 프로바이더를 통해 노드 인스턴스 생성
    return provider.createExecutor(objectMapper, this, session, topic, eventSender, emitter, canvasId)
  }

  fun <E : ExecutionContext> getExecutor(
    objectMapper: ObjectMapper,
    session: JSession,
    type: String,
    topic: String,
    eventSender: EventSender,
    emitter: FluxSink<String>? = null,
    canvasId: String?
  ): NodeExecutor<E> {
    val executor = getTypeOfExecutor(objectMapper, session, type, topic, eventSender, emitter, canvasId)

    try {
      @Suppress("UNCHECKED_CAST")
      return executor as NodeExecutor<E>
    } catch (e: ClassCastException) {
      throw IllegalArgumentException("Executor for type '$type' cannot handle context of type", e)
    }
  }
}
