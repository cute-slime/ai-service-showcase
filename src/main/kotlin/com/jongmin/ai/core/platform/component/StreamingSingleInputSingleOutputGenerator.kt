package com.jongmin.ai.core.platform.component

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import com.jongmin.ai.core.platform.service.OpenLlmBackendService.Companion.CANCELLATION_CHECK_INTERVAL
import com.jongmin.ai.core.platform.service.OpenLlmBackendService.Companion.sendReasoningChunk
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.*
import java.util.function.BiFunction

/**
 * 단일 입력을 받아 단일 출력을 생성하는 스트리밍 AI 생성기
 *
 * 이 클래스는 시스템 프롬프트와 사용자 입력을 받아 AI 어시스턴트를 통해 응답을 생성하고,
 * 생성 과정을 실시간으로 이벤트 스트림으로 전송하는 역할을 합니다.
 *
 * ## 리즈닝(Reasoning) 처리 통합
 *
 * 이 클래스는 [ReasoningProcessingUtil]을 활용하여 thinking 모델의 추론 처리를 완벽하게 지원합니다:
 *
 * ### 주요 기능:
 *
 * 1. **자동 리즈닝 전처리**
 *    - [ReasoningProcessingUtil.createChatRequestFromMessages]를 사용하여 ChatRequest 생성
 *    - thinking 모델이 활성화되고 reasoningEffort가 NONE인 경우, 마지막 user 메시지에
 *      no-think 트리거를 자동으로 추가하여 빠른 응답을 유도
 *
 * 2. **스트리밍 응답 분리**
 *    - [ReasoningProcessingUtil.createReasoningAwareStreamingHandler]를 사용하여
 *      `<think>...</think>` 태그를 실시간으로 파싱
 *    - 추론 과정(reasoning)과 최종 답변(content)을 명확하게 분리하여 처리
 *
 * 3. **이벤트 스트림 전송**
 *    - **Content 이벤트**: 최종 답변을 [MessageType.AI_INFERENCE_DELTA]로 전송
 *    - **Reasoning 이벤트**: 추론 과정을 [MessageType.AI_REASONING_DELTA]로 전송
 *    - 클라이언트는 두 이벤트를 구분하여 UI에 표시 가능 (예: 추론 과정은 회색, 최종 답변은 일반 색상)
 *
 * ### 처리 흐름:
 *
 * ```
 * prompt + input
 *     ↓
 * [SystemMessage, UserMessage] 생성
 *     ↓
 * ReasoningProcessingUtil.createChatRequestFromMessages
 *     → no-think 트리거 자동 적용 (필요시)
 *     ↓
 * assistant.streamingChatModel().chat(chatRequest, handler)
 *     ↓
 * ReasoningProcessingUtil.createReasoningAwareStreamingHandler
 *     → <think> 태그 파싱 및 분리
 *     ↓
 * ┌─────────────────┬─────────────────┐
 * │   onReasoning   │    onContent    │
 * │ (추론 과정 전송) │ (최종 답변 전송) │
 * └─────────────────┴─────────────────┘
 *     ↓                    ↓
 * AI_REASONING_DELTA  AI_INFERENCE_DELTA
 * ```
 *
 * ### 사용 예시:
 *
 * ```kotlin
 * val generator = StreamingSingleInputSingleOutputGenerator(
 *   assistant = myAssistant,
 *   eventSender = eventSender,
 *   chatTopic = "ai-events",
 *   accountId = 123,
 *   watchId = "canvas-456"
 * )
 *
 * val result = generator.apply(
 *   prompt = "You are a helpful assistant.",
 *   input = "Explain quantum computing"
 * )
 * // 클라이언트는 스트리밍 이벤트를 받으며:
 * // - AI_REASONING_DELTA: 추론 과정 (thinking 모델의 경우)
 * // - AI_INFERENCE_DELTA: 최종 답변
 * ```
 *
 * @property assistant 실행할 AI 어시스턴트 (리즈닝 설정 포함)
 * @property eventSender 이벤트를 전송하는 컴포넌트
 * @property chatTopic 이벤트를 전송할 Kafka 토픽
 * @property accountId 이벤트를 받을 계정 ID
 * @property watchId 클라이언트가 구독하는 대상 ID (예: canvasId, threadId)
 * @property watchKey 이벤트 페이로드에 포함될 키 이름 (기본값: "canvasId")
 * @property messageType content 이벤트의 메시지 타입 (기본값: AI_INFERENCE_DELTA)
 * @property onStatusChanged 상태 변경 시 호출될 콜백 (선택)
 *
 * @see ReasoningProcessingUtil
 * @see RunnableAiAssistant
 */
class StreamingSingleInputSingleOutputGenerator(
  private val objectMapper: ObjectMapper,
  private val cancellationManager: AIInferenceCancellationManager,
  private val llmGateway: LlmGateway,
  private val assistant: RunnableAiAssistant,
  private val emitter: FluxSink<String>?,
  private val eventSender: EventSender,
  private val chatTopic: String,
  private val accountId: Long,
  private val watchId: String,
  private val watchKey: String = "canvasId",
  private val messageType: MessageType = MessageType.AI_INFERENCE_DELTA,
  private val onStatusChanged: ((status: StatusType, input: String?, output: String?, runnableAiAssistant: RunnableAiAssistant?) -> Unit)? = null
) : BiFunction<String, String, String> {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val GENERATION_ID_PREFIX = "pg-"
  }

  /**
   * AI 어시스턴트를 실행하여 입력에 대한 응답을 생성합니다.
   *
   * 이 메서드는 시스템 프롬프트와 사용자 입력을 받아 AI 모델을 통해 응답을 생성하며,
   * 생성 과정을 실시간으로 스트리밍하여 이벤트로 전송합니다.
   *
   * ### 리즈닝 처리:
   * 1. ChatRequest 생성 시 [ReasoningProcessingUtil.createChatRequestFromMessages] 사용
   *    - thinking 모델의 경우 no-think 트리거가 자동 적용됨 (reasoningEffort=NONE일 때)
   * 2. 스트리밍 핸들러는 [ReasoningProcessingUtil.createReasoningAwareStreamingHandler] 사용
   *    - `<think>` 태그를 실시간으로 파싱하여 reasoning과 content를 분리
   * 3. 이벤트 전송:
   *    - Reasoning: [MessageType.AI_REASONING_DELTA] (추론 과정)
   *    - Content: [messageType] (최종 답변, 기본값: AI_INFERENCE_DELTA)
   *
   * @param prompt 시스템 프롬프트 (AI의 역할 및 지시사항)
   * @param input 사용자 입력 (질문 또는 요청)
   * @return AI가 생성한 최종 응답 텍스트
   * @throws Exception 추론 중 오류가 발생한 경우
   */
  override fun apply(prompt: String, input: String): String {
    val id = "$GENERATION_ID_PREFIX${UUID.randomUUID()}"
    val created = System.currentTimeMillis()
    cancellationManager.registerInference(id)

    // 취소 확인을 위한 변수들
    var tokenCount = 0 // 토큰 카운터
    var isCancelled = false // 취소 플래그

    onStatusChanged?.invoke(StatusType.RUNNING, prompt, null, assistant)

    // 메시지 리스트 생성
    val messages = listOf(
      SystemMessage.from(prompt),
      UserMessage.from(input)
    )

    // ReasoningProcessingUtil을 사용하여 ChatRequest 생성
    // - 리즈닝 모델의 경우 no-think 트리거가 자동으로 적용됨
    // - reasoningEffort에 따라 추론 깊이가 조절됨
    val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(assistant = assistant, messages = messages)

    val result = StringBuilder()

    // LlmGateway를 통한 스트리밍 실행
    // - Rate Limiter 관리와 추적(AiRun/AiRunStep)은 LlmGateway가 자동 처리
    val future = llmGateway.executeStreamingWithHandler(
      assistant = assistant,
      chatRequest = chatRequest,
      callerComponent = "StreamingSingleInputSingleOutputGenerator",
      requestDescription = "스트리밍 단일 입출력 생성"
    ) { onStreamingComplete, onStreamingError ->
      // 리즈닝 인식 스트리밍 핸들러 생성
      // - <think> 태그를 자동으로 분리하여 처리
      // - reasoning과 content를 구분하여 각각의 콜백으로 전달
      val internalHandler = ReasoningProcessingUtil.createReasoningAwareStreamingHandler(
        assistant = assistant,
        onContent = { content ->
          if (!isCancelled) {
            result.append(content)
            emitter?.next(
              " ${
                objectMapper.writeValueAsString(
                  mapOf(
                    watchKey to watchId,
                    "type" to "AI_INFERENCE_DELTA",
                    "delta" to content
                  )
                )
              }"
            )
          }
        },
        onReasoning = { reasoningText, index ->
          if (!isCancelled) {
            sendReasoningChunk(emitter, objectMapper, id, assistant, created, reasoningText, index)
          }
        },
        onComplete = {
          // Rate Limiter 관리는 LlmGateway가 처리하므로 여기서는 로깅과 emitter 처리만 수행
          if (isCancelled) {
            kLogger.info { "스트리밍 채팅 요청 취소됨 - ID: $id, 취소 시점까지 생성: 토큰 ${tokenCount}개" }
          } else {
            kLogger.info { "스트리밍 응답 생성 완료 - ID: $id, 총 토큰: $tokenCount" }
          }
          emitter?.complete()
          cancellationManager.unregisterInference(id)
        },
        onError = { error ->
          // Rate Limiter 관리는 LlmGateway가 처리
          kLogger.error(error) { "스트리밍 채팅 요청 실패 - ID: $id" }
          cancellationManager.unregisterInference(id)
          emitter?.error(error)
        }
      )

      // 취소 체크를 위한 래핑 핸들러 반환
      object : StreamingChatResponseHandler {
        override fun onPartialResponse(partialResponse: String) {
          tokenCount++
          if (tokenCount % CANCELLATION_CHECK_INTERVAL == 0) {
            if (cancellationManager.isCancelled(id)) {
              kLogger.warn { "스트리밍 중 추론 취소 감지 - ID: $id, 토큰 수: $tokenCount" }
              isCancelled = true
              return // 추가 처리 중단
            }
          }
          if (!isCancelled) internalHandler.onPartialResponse(partialResponse)
        }

        override fun onCompleteResponse(completeResponse: ChatResponse) {
          internalHandler.onCompleteResponse(completeResponse)
          // LlmGateway 콜백 호출 - Rate Limiter 반환 및 추적 완료 처리
          onStreamingComplete(result.toString(), completeResponse)
        }

        override fun onError(error: Throwable) {
          internalHandler.onError(error)
          // LlmGateway 콜백 호출 - Rate Limiter 반환 및 추적 실패 처리
          onStreamingError(error)
        }
      }
    }

    val output = future.get()
    onStatusChanged?.invoke(StatusType.ENDED, null, output, assistant)
    return output.cleanJsonString()
  }
}
