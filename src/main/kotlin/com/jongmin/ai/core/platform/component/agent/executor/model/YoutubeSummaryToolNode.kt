package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import com.jongmin.ai.common.tools.YoutubeTranscriptTool
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 유튜브 요약 도구 노드
 *
 * 유튜브 영상의 자막(transcript)을 시간 기반으로 추출하여 반환하는 노드.
 * YoutubeTranscriptTool을 통해 타임스탬프 포함 자막 데이터를 제공한다.
 *
 * 입력:
 * - input: 유튜브 URL (String)
 *
 * 출력:
 * - 타임스탬프 포함 자막 텍스트 (String)
 *
 * 설정:
 * - 없음
 *
 * @author Claude Code
 * @since 2025.12.27
 */
@NodeType(["youtube-summary-tool"])
class YoutubeSummaryToolNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  emitter: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val url = context.findAndGetInputForNode(node.id, "input") as String
    val tool = factory.applicationContext.getBean(YoutubeTranscriptTool::class.java)
    val result = tool.getTranscriptToTimeBased(url)
    logging(
      node,
      context,
      "  → URL: $url",
      "  → Script: ${truncateInput(result, 200).replace("\n", "|")}"
    )
    context.storeOutput(node.id, result)
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * YoutubeSummaryToolNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("youtube-summary-tool")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return YoutubeSummaryToolNode 인스턴스
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
      return YoutubeSummaryToolNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}

