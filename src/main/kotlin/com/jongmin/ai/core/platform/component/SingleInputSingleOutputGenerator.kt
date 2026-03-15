package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.function.BiFunction

/**
 * 단일 입력 단일 출력 생성기
 *
 * LlmGateway를 통해 모든 AI 호출이 자동으로 추적됩니다.
 *
 * @param llmGateway LLM 게이트웨이 (추적 자동화)
 * @param assistant AI 어시스턴트
 * @param onStatusChanged 상태 변경 콜백
 * @param callerComponent 호출자 컴포넌트 식별자 (기본값: SingleInputSingleOutputGenerator)
 */
class SingleInputSingleOutputGenerator(
  private val llmGateway: LlmGateway,
  private val assistant: RunnableAiAssistant,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null,
  private val callerComponent: String = "SingleInputSingleOutputGenerator"
) : BiFunction<String, String, String> {
  private val kLogger = KotlinLogging.logger {}

  override fun apply(prompt: String, question: String): String {
    kLogger.info { "AI 추론 시작 - type: ${assistant.type}" }
    onStatusChanged?.invoke(StatusType.RUNNING, prompt, null, assistant)

    // ChatRequest 빌드
    val chatRequest = ChatRequest.builder()
      .messages(listOf(SystemMessage.from(prompt), UserMessage.from(question)))
      .build()

    // LlmGateway를 통한 생성 (자동 추적)
    val generated = llmGateway.generateWithChatRequest(
      assistant = assistant,
      chatRequest = chatRequest,
      callerComponent = callerComponent,
      requestDescription = "단일 입출력 생성"
    )

    onStatusChanged?.invoke(StatusType.ENDED, null, generated, assistant)
    kLogger.info { "AI 추론 완료 - type: ${assistant.type}" }
    return generated
  }
}
