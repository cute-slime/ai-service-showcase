package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

/**
 * 병합 노드 (Merge Node)
 *
 * 여러 입력을 하나의 JSON 객체로 병합하여 출력하는 노드.
 * 각 입력 핸들의 ID를 키로 사용하여 JSON 객체를 생성한다.
 *
 * Join 노드와의 차이점:
 * - Join: 여러 입력을 각각 그대로 출력 (pass-through, 동기화 목적)
 * - Merge: 여러 입력을 하나의 JSON 객체로 통합 (데이터 병합 목적)
 *
 * 병합 규칙:
 * - 키: 입력 핸들의 ID (예: "concept", "characters", "setting")
 * - 값: 입력 데이터 (JSON 문자열이면 파싱하여 객체로, 아니면 문자열 그대로)
 *
 * 입력:
 * - inputs: 병합할 입력 목록 (동적 핸들)
 *
 * 출력:
 * - output: 병합된 JSON 문자열
 *   예: {"concept": {...}, "characters": [...], "setting": {...}}
 *
 * @author Claude Code
 * @since 2025.12.25
 * @updated 2026.01.04 - JSON 객체 병합 방식으로 변경 (FE 요구사항 반영)
 */
@NodeType(["merge"])
class MergeNode<T : ExecutionContext>(
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
      val inputValue = context.findAndGetInputForNode(node.id, handle.id)
      inputValue == null
    }
  }

  /**
   * 입력들을 하나의 JSON 객체로 병합
   *
   * 각 입력 핸들의 ID를 키로, 입력 데이터를 값으로 하는 JSON 객체를 생성한다.
   * 입력이 JSON 문자열이면 파싱하여 객체로 저장하고, 아니면 문자열 그대로 저장한다.
   */
  override fun executeInternal(node: Node, context: T) {
    val inputs = node.data.dynamicHandles["inputs"] as? List<ToolHandle> ?: return

    // 병합된 JSON 객체
    val mergedObject = mutableMapOf<String, Any>()

    inputs.forEach { handle ->
      val inputValue = context.findAndGetInputForNode(node.id, handle.id)
      if (inputValue != null) {
        // 핸들 name을 키로, 입력값을 값으로 저장 (name이 없으면 id fallback)
        // JSON 문자열이면 파싱 시도, 아니면 그대로 저장
        val keyName = handle.name.ifEmpty { handle.id }
        mergedObject[keyName] = tryParseJson(inputValue)
        logger.debug { "  └─ [Merge] $keyName: ${inputValue.toString().take(100)}..." }
      }
    }

    // JSON 문자열로 직렬화하여 output 핸들에 저장
    val jsonString = objectMapper.writeValueAsString(mergedObject)

    logging(node, context, "  → Merge 완료: ${mergedObject.keys.joinToString(", ")} (${mergedObject.size}개 필드)")
    logging(node, context, "  → 결과 JSON: ${truncateInput(jsonString, 200)}")

    context.storeOutput(node.id, jsonString)
  }

  /**
   * JSON 문자열이면 파싱하여 객체로 변환, 아니면 원본 그대로 반환
   *
   * @param value 입력 값
   * @return 파싱된 객체 또는 원본 값
   */
  private fun tryParseJson(value: Any): Any {
    return when (value) {
      is String -> {
        val trimmed = value.trim()
        // JSON 형식으로 보이는 문자열만 파싱 시도
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
          (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
          try {
            objectMapper.readValue(trimmed, Any::class.java)
          } catch (e: Exception) {
            logger.debug { "  └─ JSON 파싱 실패, 원본 문자열 사용: ${e.message}" }
            value
          }
        } else {
          value
        }
      }

      else -> value // 이미 객체이면 그대로 사용
    }
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * MergeNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("merge")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return MergeNode 인스턴스
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
      return MergeNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
