package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.truncateInput
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.atomic.AtomicReference

/**
 * 프롬프트 생성 노드
 *
 * 템플릿 문자열에 동적 입력값을 주입하여 최종 프롬프트를 생성하는 노드.
 * {{태그명}} 형식의 플레이스홀더를 실제 입력값으로 치환한다.
 *
 * 입력:
 * - template-tags: 템플릿 태그 목록 (동적 핸들)
 *
 * 출력:
 * - 완성된 프롬프트 문자열
 *
 * 설정:
 * - template: 프롬프트 템플릿 문자열 ({{태그}} 형식 지원)
 *
 * @author Claude Code
 * @since 2025.12.25
 */
@NodeType(["prompt-crafter"])
class PromptCrafterNode(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<ExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging) {
  override fun waitIfNotReady(node: Node, context: ExecutionContext): Boolean {
    return false
  }

  override fun executeInternal(node: Node, context: ExecutionContext) {
    val template = AtomicReference(node.data.config?.get("template") as String)
    val templateTags = node.data.dynamicHandles["template-tags"] as List<ToolHandle>
    templateTags.forEach { tag ->
      val input = context.findAndGetInputForNode(node.id, tag.id, tag.name) as String
      template.set(template.get().replace("{{${tag.name}}}", input))
    }
    logging(node, context, "  → 완성된 프롬프트: ${truncateInput(template.get(), 100)}".replace("\n", " "))

    context.storeOutput(node.id, template.get())
  }

  override fun propagateOutput(node: Node, context: ExecutionContext) = defaultPropagateOutput(node, context)

  // ========== 노드 팩토리 프로바이더 ==========

  companion object : NodeExecutorProvider {
    /**
     * PromptCrafterNode 인스턴스 생성
     *
     * NodeExecutorFactory의 자동 등록 시스템에서 호출됩니다.
     * @NodeType 어노테이션에 선언된 타입("prompt-crafter")으로 등록됩니다.
     *
     * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper
     * @param factory NodeExecutorFactory
     * @param session 현재 사용자 세션
     * @param topic 이벤트 전송 토픽
     * @param eventSender 이벤트 전송 서비스
     * @param emitter SSE 스트리밍용 FluxSink
     * @param canvasId 워크플로우 캔버스 ID
     * @return PromptCrafterNode 인스턴스
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
      return PromptCrafterNode(
        objectMapper, factory, session, topic, eventSender, emitter, canvasId, debugging = true
      )
    }
  }
}
