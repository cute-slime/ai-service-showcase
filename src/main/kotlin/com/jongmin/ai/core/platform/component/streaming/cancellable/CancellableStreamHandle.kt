package com.jongmin.ai.core.platform.component.streaming.cancellable

import dev.langchain4j.model.chat.response.StreamingHandle
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 취소 가능한 스트림 핸들
 *
 * LangChain4J의 StreamingHandle을 래핑하여 취소 시 추가 동작을 수행합니다.
 * 향후 LangChain4J에서 Issue #2968이 해결되면 이 클래스를 제거하고
 * 직접 StreamingHandle을 사용할 수 있습니다.
 *
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/2968">LangChain4J Issue #2968</a>
 *
 * @param streamId 스트림 식별자 (로깅 및 관리용)
 * @param onCancelled 취소 시 호출되는 콜백 (ChatMemory 업데이트 방지 등)
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
class CancellableStreamHandle(
  val streamId: String,
  private val onCancelled: (() -> Unit)? = null
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 취소 상태 플래그
   * AtomicBoolean으로 스레드 안전성 보장
   */
  private val cancelled = AtomicBoolean(false)

  /**
   * LangChain4J의 StreamingHandle 참조
   * 스트리밍 시작 후 설정됨
   */
  @Volatile
  private var streamingHandle: StreamingHandle? = null

  /**
   * StreamingHandle 설정
   * 스트리밍 시작 시 context에서 받은 핸들을 설정합니다.
   */
  fun setStreamingHandle(handle: StreamingHandle) {
    this.streamingHandle = handle
  }

  /**
   * 스트리밍 취소
   *
   * 1. 취소 플래그 설정
   * 2. onCancelled 콜백 호출 (ChatMemory 업데이트 방지 등)
   * 3. LangChain4J StreamingHandle.cancel() 호출
   *
   * @return 취소 성공 여부 (이미 취소된 경우 false)
   */
  fun cancel(): Boolean {
    // 이미 취소된 경우 무시
    if (!cancelled.compareAndSet(false, true)) {
      kLogger.debug { "[스트림 취소] 이미 취소됨 - streamId: $streamId" }
      return false
    }

    kLogger.info { "[스트림 취소] 요청됨 - streamId: $streamId" }

    // 취소 콜백 호출 (ChatMemory 업데이트 방지 등)
    try {
      onCancelled?.invoke()
    } catch (e: Exception) {
      kLogger.warn(e) { "[스트림 취소] 콜백 실행 중 오류 - streamId: $streamId" }
    }

    // LangChain4J StreamingHandle 취소
    try {
      streamingHandle?.cancel()
      kLogger.debug { "[스트림 취소] StreamingHandle.cancel() 호출 완료 - streamId: $streamId" }
    } catch (e: Exception) {
      kLogger.warn(e) { "[스트림 취소] StreamingHandle.cancel() 실패 - streamId: $streamId" }
    }

    return true
  }

  /**
   * 취소 여부 확인
   */
  fun isCancelled(): Boolean = cancelled.get()

  /**
   * 취소되지 않은 경우에만 동작 실행
   *
   * @param action 실행할 동작
   * @return 동작이 실행되었으면 true, 취소되어 스킵되었으면 false
   */
  fun executeIfNotCancelled(action: () -> Unit): Boolean {
    if (cancelled.get()) {
      return false
    }
    action()
    return true
  }

  companion object {
    /**
     * 스트림 ID 생성
     */
    fun generateStreamId(): String = "stream-${System.currentTimeMillis()}-${(Math.random() * 10000).toInt()}"
  }
}
