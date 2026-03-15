package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * Delay 노드 - 설정된 시간만큼 대기 후 실행
 *
 * 워크플로우에서 의도적으로 시간 지연을 삽입하는 유틸리티 노드.
 * 모든 입력 엣지가 준비된 후 설정된 딜레이만큼 대기했다가
 * 입력값들을 그대로 출력으로 전달한다.
 *
 * ### 지원 시간 단위
 * - ms: 밀리초
 * - s: 초
 * - m: 분
 * - h: 시간
 * - d: 일
 *
 * ### 제한사항
 * - 최대 대기 시간: 7일 (604,800,000ms)
 * - 0 이하의 딜레이는 즉시 실행으로 처리
 *
 * ### 사용 예시
 * ```
 * [API 호출] ──▶ [Delay: 5초] ──▶ [다음 처리]
 * ```
 *
 * ### FE 노드 설정 (config)
 * - delayValue: Long - 딜레이 값 (숫자)
 * - delayUnit: String - 시간 단위 ("ms", "s", "m", "h", "d")
 *
 * @author Claude Code
 * @since 2026.01.04
 */
@NodeType(["delay"])
class DelayNode<T : ExecutionContext>(
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
   * 최대 딜레이 (7일 = 604,800,000ms)
   */
  private val maxDelayMs: Long = 7L * 24 * 60 * 60 * 1000

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
   * 설정된 딜레이만큼 대기 후 입력값을 출력으로 전달
   *
   * 1. config에서 딜레이 설정 추출
   * 2. 단위를 밀리초로 변환
   * 3. 최대 7일 제한 적용
   * 4. Thread.sleep()으로 대기
   * 5. 입력값들을 동일한 핸들 ID로 출력에 저장 (pass-through)
   */
  override fun executeInternal(node: Node, context: T) {
    // 딜레이 설정 추출
    val config = node.data.config ?: emptyMap()
    val delayValue = extractDelayValue(config)
    val delayUnit = config["delayUnit"] as? String ?: "ms"

    // 밀리초로 변환
    val delayMs = convertToMilliseconds(delayValue, delayUnit)

    // 최대 딜레이 제한 적용
    val actualDelayMs = delayMs.coerceIn(0L, maxDelayMs)

    kLogger.info { "⏳ [Delay] 노드 시작 - 대기 시간: ${delayValue}${delayUnit} (${actualDelayMs}ms)" }

    // 딜레이 시작 이벤트 전송 (FE에서 카운트다운 표시용)
    sendDelayStartEvent(node, actualDelayMs)

    // 딜레이가 0보다 크면 대기
    if (actualDelayMs > 0) {
      try {
        Thread.sleep(actualDelayMs)
      } catch (e: InterruptedException) {
        kLogger.warn { "⚠️ [Delay] 대기 중 인터럽트 발생" }
        Thread.currentThread().interrupt()
      }
    }

    kLogger.info { "✅ [Delay] 대기 완료 - ${actualDelayMs}ms" }

    // 입력값들을 그대로 출력으로 전달 (pass-through)
    val inputs = node.data.dynamicHandles["inputs"] as? List<ToolHandle>
    if (inputs.isNullOrEmpty()) {
      // 입력 핸들이 없으면 빈 출력
      context.storeOutput(node.id, mapOf("completed" to true))
    } else {
      val outputMap = mutableMapOf<String, Any>()
      inputs.forEach { handle ->
        val inputValue = context.findAndGetInputForNode(node.id, handle.id)
        if (inputValue != null) {
          outputMap[handle.id] = inputValue
          kLogger.debug { "  └─ [Delay] pass-through: ${handle.id}" }
        }
      }
      context.storeOutput(node.id, outputMap)
    }

    logging(node, context, "  → Delay 완료: ${actualDelayMs}ms 대기 후 실행")
  }

  /**
   * config에서 delayValue 추출
   *
   * Number 타입의 다양한 형태를 안전하게 Long으로 변환
   */
  private fun extractDelayValue(config: Map<String, Any>): Long {
    return when (val value = config["delayValue"]) {
      is Number -> value.toLong()
      is String -> value.toLongOrNull() ?: 0L
      else -> 0L
    }
  }

  /**
   * 딜레이 값을 밀리초로 변환
   *
   * @param value 딜레이 값
   * @param unit 시간 단위 (ms, s, m, h, d)
   * @return 밀리초 단위 딜레이
   */
  private fun convertToMilliseconds(value: Long, unit: String): Long {
    return when (unit.lowercase()) {
      "ms" -> value
      "s" -> value * 1000
      "m" -> value * 60 * 1000
      "h" -> value * 60 * 60 * 1000
      "d" -> value * 24 * 60 * 60 * 1000
      else -> value // 알 수 없는 단위는 밀리초로 간주
    }
  }

  /**
   * 딜레이 시작 이벤트 전송
   *
   * FE에서 카운트다운 UI를 표시할 수 있도록 딜레이 정보 전송
   */
  private fun sendDelayStartEvent(node: Node, delayMs: Long) {
    val payload = mutableMapOf<String, Any>(
      "nodeId" to node.id,
      "nodeType" to node.type,
      "delayMs" to delayMs,
      "startedAt" to System.currentTimeMillis()
    )
    sendEvent(MessageType.NODE_DELAY_STARTED, payload)
  }

  override fun propagateOutput(node: Node, context: T) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * DelayNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("delay")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return DelayNode 인스턴스
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
      return DelayNode<ExecutionContext>(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
