package com.jongmin.ai.core.platform.component.streaming.cancellable

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.PartialResponse
import dev.langchain4j.model.chat.response.PartialResponseContext
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 취소 가능한 스트리밍 채팅 서비스
 *
 * LangChain4J의 AiServices 대신 저수준 API를 직접 사용하여
 * 스트리밍 취소 시 ChatMemory 업데이트를 방지합니다.
 *
 * 향후 LangChain4J에서 Issue #2968이 해결되면 이 클래스를 제거하고
 * 표준 AiServices를 사용할 수 있습니다.
 *
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/2968">LangChain4J Issue #2968</a>
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
class CancellableStreamingChatService(
  private val streamingChatModel: StreamingChatModel,
  private val chatMemory: CancellableChatMemory? = null
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 취소 가능한 스트리밍 채팅 수행
   *
   * @param messages 전송할 메시지 목록
   * @param memoryId 메모리 식별자 (ChatMemory 사용 시)
   * @param onPartialResponse 부분 응답 수신 시 콜백
   * @param onComplete 완료 시 콜백
   * @param onError 오류 발생 시 콜백
   * @return CancellableStreamHandle - cancel() 호출로 취소 가능
   */
  fun chat(
    messages: List<ChatMessage>,
    memoryId: String? = null,
    onPartialResponse: (String) -> Unit,
    onComplete: (AiMessage) -> Unit,
    onError: (Throwable) -> Unit
  ): CancellableStreamHandle {
    val streamId = CancellableStreamHandle.generateStreamId()

    kLogger.debug { "[CancellableStreaming] 시작 - streamId: $streamId, memoryId: $memoryId" }

    // 취소 핸들 생성
    val handle = CancellableStreamHandle(
      streamId = streamId,
      onCancelled = {
        // 취소 시 ChatMemory에 마킹
        if (memoryId != null && chatMemory != null) {
          chatMemory.markCancelled(memoryId)
        }
      }
    )

    val accumulatedResponse = StringBuilder()

    // 스트리밍 핸들러
    val handler = object : StreamingChatResponseHandler {
      override fun onPartialResponse(
        partialResponse: PartialResponse,
        context: PartialResponseContext
      ) {
        // 첫 번째 응답에서 StreamingHandle 설정
        handle.setStreamingHandle(context.streamingHandle())

        // 취소됐으면 무시
        if (handle.isCancelled()) {
          return
        }

        val text = partialResponse.text() ?: return
        accumulatedResponse.append(text)
        onPartialResponse(text)
      }

      override fun onCompleteResponse(response: ChatResponse) {
        if (handle.isCancelled()) {
          kLogger.info {
            "[CancellableStreaming] 취소됨 - streamId: $streamId, " +
                "accumulatedLength: ${accumulatedResponse.length}"
          }
          // 취소된 경우 ChatMemory에 저장하지 않음 (이미 markCancelled됨)
          return
        }

        val aiMessage = response.aiMessage()

        // ChatMemory에 AI 응답 저장 (취소되지 않은 경우만)
        if (chatMemory != null && memoryId != null) {
          chatMemory.add(aiMessage)
        }

        kLogger.debug {
          "[CancellableStreaming] 완료 - streamId: $streamId, " +
              "responseLength: ${aiMessage.text()?.length ?: 0}"
        }

        onComplete(aiMessage)
      }

      override fun onError(error: Throwable) {
        if (handle.isCancelled()) {
          kLogger.debug { "[CancellableStreaming] 취소된 스트림의 오류 무시 - streamId: $streamId" }
          return
        }

        kLogger.error(error) { "[CancellableStreaming] 오류 발생 - streamId: $streamId" }
        onError(error)
      }
    }

    // ChatRequest 생성 및 스트리밍 시작
    val chatRequest = ChatRequest.builder()
      .messages(messages)
      .build()

    streamingChatModel.chat(chatRequest, handler)

    return handle
  }

  /**
   * 사용자 메시지를 추가하고 취소 가능한 스트리밍 채팅 수행
   *
   * @param userMessage 사용자 메시지
   * @param memoryId 메모리 식별자
   * @param onPartialResponse 부분 응답 수신 시 콜백
   * @param onComplete 완료 시 콜백
   * @param onError 오류 발생 시 콜백
   * @return CancellableStreamHandle
   */
  fun chatWithUserMessage(
    userMessage: String,
    memoryId: String,
    onPartialResponse: (String) -> Unit,
    onComplete: (AiMessage) -> Unit,
    onError: (Throwable) -> Unit
  ): CancellableStreamHandle {
    // 사용자 메시지 추가 (취소 여부와 무관하게 항상 저장)
    val userMsg = UserMessage.from(userMessage)
    chatMemory?.add(userMsg)

    // 기존 메시지 목록 조회
    val messages = chatMemory?.messages() ?: listOf(userMsg)

    return chat(
      messages = messages,
      memoryId = memoryId,
      onPartialResponse = onPartialResponse,
      onComplete = onComplete,
      onError = onError
    )
  }

  companion object {
    /**
     * 빌더 패턴으로 서비스 생성
     */
    fun builder() = Builder()

    class Builder {
      private var streamingChatModel: StreamingChatModel? = null
      private var chatMemory: CancellableChatMemory? = null

      fun streamingChatModel(model: StreamingChatModel) = apply {
        this.streamingChatModel = model
      }

      fun chatMemory(memory: CancellableChatMemory?) = apply {
        this.chatMemory = memory
      }

      fun build(): CancellableStreamingChatService {
        requireNotNull(streamingChatModel) { "StreamingChatModel is required" }
        return CancellableStreamingChatService(
          streamingChatModel = streamingChatModel!!,
          chatMemory = chatMemory
        )
      }
    }
  }
}
