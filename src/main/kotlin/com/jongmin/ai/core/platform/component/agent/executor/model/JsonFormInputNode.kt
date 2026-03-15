package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * JSON Form Input 노드
 *
 * FE에서 동적 key-value 폼으로 입력받은 데이터를 JSON 형태로 변환하여 출력하는 노드.
 * 시나리오 생성 입력, API 파라미터 구성 등 다양한 용도로 재사용 가능한 범용 노드.
 *
 * 입력:
 * - 없음 (시작 노드로 사용)
 *
 * 설정 (config):
 * - formData: Map<String, Any> - FE에서 입력받은 key-value 데이터
 *
 * 출력:
 * - JSON 문자열 (formData를 직렬화한 결과)
 *
 * @author Claude Code
 * @since 2025.12.26
 */
@NodeType(["json-form-input"])
class JsonFormInputNode<T : ExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : PreparedNodeExecutor<T>,
  NodeExecutor<T>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {

  /**
   * 시작 노드이므로 항상 준비 완료
   */
  override fun waitIfNotReady(node: Node, context: T): Boolean = false

  /**
   * 노드 준비 단계
   * formData를 JSON으로 직렬화하여 value로 설정하고 출력에 저장
   */
  override fun prepare(node: Node, context: T) {
    val originalConfig = node.data.config ?: emptyMap()

    // formData 추출
    val formData = originalConfig["formData"]

    val jsonOutput = when (formData) {
      is Map<*, *> -> objectMapper.writeValueAsString(formData)
      is String -> formData
      null -> "{}"
      else -> objectMapper.writeValueAsString(formData)
    }

    // value 키로 설정 (BasicWorkflowEngine이 기대하는 형식)
    val newConfig = originalConfig.toMutableMap() as MutableMap<String, Any>
    newConfig["value"] = jsonOutput
    node.data.config = newConfig

    // 출력 저장
    context.storeOutput(node.id, jsonOutput)
    updateNodeStatus(node, context, NodeExecutionState.PREPARED)

    kLogger.info { "📝 [JsonFormInput] 준비 완료 - nodeId: ${node.id}" }
    kLogger.debug { "  └─ formData (raw): $formData" }
    kLogger.debug { "  └─ jsonOutput: $jsonOutput" }
  }

  /**
   * 실행 단계 - prepare에서 이미 처리했으므로 비어있음
   */
  override fun executeInternal(node: Node, context: T) {
    // prepare에서 이미 처리됨
  }

  /**
   * 기본 출력 전파
   */
  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * JsonFormInputNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("json-form-input")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return JsonFormInputNode 인스턴스
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
      return JsonFormInputNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
