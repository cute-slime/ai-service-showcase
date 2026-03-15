package com.jongmin.ai.core.platform.component.streaming.cancellable

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.memory.ChatMemory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * 취소 가능한 ChatMemory 래퍼
 *
 * LangChain4J의 ChatMemory를 래핑하여 스트리밍 취소 시
 * AI 응답이 메모리에 저장되지 않도록 합니다.
 *
 * 향후 LangChain4J에서 Issue #2968이 해결되면 이 클래스를 제거하고
 * 표준 ChatMemory를 사용할 수 있습니다.
 *
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/2968">LangChain4J Issue #2968</a>
 *
 * @param delegate 실제 ChatMemory 구현체
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
class CancellableChatMemory(
  private val delegate: ChatMemory
) : ChatMemory {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 취소된 메모리 ID 목록
   * 해당 ID의 AI 응답은 저장되지 않음
   */
  private val cancelledMemoryIds = ConcurrentHashMap.newKeySet<Any>()

  /**
   * 특정 메모리 ID를 취소 상태로 마킹
   * 이후 해당 ID로 AI 메시지 추가 시 무시됨
   *
   * @param memoryId 취소할 메모리 ID
   */
  fun markCancelled(memoryId: Any) {
    cancelledMemoryIds.add(memoryId)
    kLogger.debug { "[CancellableChatMemory] 취소 마킹 - memoryId: $memoryId" }
  }

  /**
   * 특정 메모리 ID의 취소 상태 해제
   *
   * @param memoryId 취소 해제할 메모리 ID
   */
  fun clearCancellation(memoryId: Any) {
    cancelledMemoryIds.remove(memoryId)
    kLogger.debug { "[CancellableChatMemory] 취소 해제 - memoryId: $memoryId" }
  }

  /**
   * 특정 메모리 ID가 취소되었는지 확인
   *
   * @param memoryId 확인할 메모리 ID
   * @return 취소되었으면 true
   */
  fun isCancelled(memoryId: Any): Boolean = cancelledMemoryIds.contains(memoryId)

  override fun id(): Any = delegate.id()

  /**
   * 메시지 추가
   *
   * AI 응답(AiMessage)이고 취소된 메모리인 경우 저장을 스킵합니다.
   * 사용자 메시지(UserMessage)는 취소 여부와 무관하게 저장됩니다.
   */
  override fun add(message: ChatMessage) {
    val memoryId = delegate.id()

    // AI 응답이고 취소된 상태이면 무시
    if (message is AiMessage && cancelledMemoryIds.contains(memoryId)) {
      kLogger.info {
        "[CancellableChatMemory] AI 응답 저장 스킵 (취소됨) - memoryId: $memoryId, " +
            "messageLength: ${message.text()?.length ?: 0}"
      }
      // 취소 후 한 번만 스킵하고 플래그 해제
      cancelledMemoryIds.remove(memoryId)
      return
    }

    delegate.add(message)
  }

  override fun messages(): List<ChatMessage> = delegate.messages()

  override fun clear() {
    cancelledMemoryIds.remove(delegate.id())
    delegate.clear()
  }

  companion object {
    /**
     * 기존 ChatMemory를 CancellableChatMemory로 래핑
     *
     * @param chatMemory 래핑할 ChatMemory
     * @return CancellableChatMemory 인스턴스
     */
    fun wrap(chatMemory: ChatMemory): CancellableChatMemory {
      return if (chatMemory is CancellableChatMemory) {
        chatMemory
      } else {
        CancellableChatMemory(chatMemory)
      }
    }
  }
}
