package com.jongmin.ai.core.platform.component.tracking

import dev.langchain4j.model.anthropic.AnthropicTokenUsage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.PartialResponse
import dev.langchain4j.model.chat.response.PartialResponseContext
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.output.TokenUsage
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 토큰 사용량 추적 스트리밍 핸들러
 *
 * LangChain4J의 StreamingChatResponseHandler를 래핑하여
 * 응답 완료 시 자동으로 토큰 사용량을 추적합니다.
 *
 * 주요 기능:
 * - 원본 핸들러에 모든 이벤트 위임
 * - 완료 시 ChatResponse에서 usage 정보 추출
 * - LlmExecutionTracker를 통해 DB에 저장
 *
 * @param delegate 원본 스트리밍 핸들러
 * @param tracker LLM 실행 추적 컴포넌트
 * @param context 실행 컨텍스트
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
class TrackedStreamingHandler(
  private val delegate: StreamingChatResponseHandler,
  private val tracker: LlmExecutionTracker,
  private val context: LlmExecutionContext
) : StreamingChatResponseHandler {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 신버전 API - PartialResponse와 PartialResponseContext 사용
   * LangChain4J 1.9.x의 새로운 인터페이스
   */
  override fun onPartialResponse(
    partialResponse: PartialResponse,
    ctx: PartialResponseContext
  ) {
    // 원본 핸들러에 위임
    delegate.onPartialResponse(partialResponse, ctx)
  }

  /**
   * 구버전 API - String만 사용
   * 기존 코드와의 호환성을 위해 유지
   */
  override fun onPartialResponse(partialResponse: String) {
    // 원본 핸들러에 위임
    delegate.onPartialResponse(partialResponse)
  }

  override fun onCompleteResponse(response: ChatResponse) {
    kLogger.debug {
      "[TrackedHandler] 응답 완료 - aiRunStepId: ${context.aiRunStepId}"
    }

    // 1. 원본 핸들러에 위임
    delegate.onCompleteResponse(response)

    // 2. usage 정보 추출 및 추적
    try {
      val rawUsage = extractUsageFromResponse(response)
      tracker.completeExecution(context, rawUsage)
    } catch (e: Exception) {
      kLogger.error(e) {
        "[TrackedHandler] 사용량 추적 실패 - aiRunStepId: ${context.aiRunStepId}"
      }
      // 추적 실패해도 원본 응답은 이미 처리됨
    }
  }

  override fun onError(error: Throwable) {
    kLogger.warn(error) {
      "[TrackedHandler] 오류 발생 - aiRunStepId: ${context.aiRunStepId}"
    }

    // 1. 원본 핸들러에 위임
    delegate.onError(error)

    // 2. 실패 상태 추적
    try {
      tracker.failExecution(context, error)
    } catch (e: Exception) {
      kLogger.error(e) {
        "[TrackedHandler] 실패 상태 추적 오류 - aiRunStepId: ${context.aiRunStepId}"
      }
    }
  }

  /**
   * ChatResponse에서 usage 정보 추출
   *
   * LangChain4J의 ChatResponse에서 토큰 사용량을 Map으로 변환합니다.
   * 프로바이더별로 다른 필드 구조를 지원합니다.
   *
   * 지원 프로바이더:
   * - Anthropic: AnthropicTokenUsage를 통해 캐시 토큰 추출
   * - OpenAI 호환: prompt_tokens_details.cached_tokens 형식으로 변환
   */
  private fun extractUsageFromResponse(response: ChatResponse): Map<String, Any> {
    val usage = mutableMapOf<String, Any>()

    try {
      // LangChain4J TokenUsage 객체에서 추출
      val tokenUsage = response.metadata()?.tokenUsage()

      if (tokenUsage != null) {
        // 기본 토큰 정보 (OpenAI 호환 형식)
        usage["prompt_tokens"] = tokenUsage.inputTokenCount() ?: 0
        usage["completion_tokens"] = tokenUsage.outputTokenCount() ?: 0
        usage["total_tokens"] = tokenUsage.totalTokenCount() ?: 0

        // Anthropic 형식으로도 제공 (파서 호환성)
        usage["input_tokens"] = tokenUsage.inputTokenCount() ?: 0
        usage["output_tokens"] = tokenUsage.outputTokenCount() ?: 0

        // 프로바이더별 캐시 토큰 추출
        extractCacheTokens(tokenUsage, usage)
      }

      kLogger.debug {
        "[TrackedHandler] Usage 추출 완료 - " +
            "input: ${usage["prompt_tokens"]}, output: ${usage["completion_tokens"]}, " +
            "cacheRead: ${usage["cache_read_input_tokens"] ?: 0}, " +
            "cacheCreation: ${usage["cache_creation_input_tokens"] ?: 0}"
      }

    } catch (e: Exception) {
      kLogger.warn(e) { "[TrackedHandler] Usage 추출 실패" }
    }

    return usage
  }

  /**
   * 프로바이더별 캐시 토큰 정보 추출
   *
   * LangChain4J의 TokenUsage 구현체를 확인하여
   * 프로바이더별 캐시 토큰을 추출합니다.
   *
   * @param tokenUsage LangChain4J TokenUsage 객체
   * @param usage 추출된 정보를 저장할 Map
   */
  private fun extractCacheTokens(tokenUsage: TokenUsage, usage: MutableMap<String, Any>) {
    // Anthropic 프로바이더 처리
    if (tokenUsage is AnthropicTokenUsage) {
      // Anthropic 형식: cache_read_input_tokens, cache_creation_input_tokens
      val cacheReadTokens = tokenUsage.cacheReadInputTokens() ?: 0
      val cacheCreationTokens = tokenUsage.cacheCreationInputTokens() ?: 0

      usage["cache_read_input_tokens"] = cacheReadTokens
      usage["cache_creation_input_tokens"] = cacheCreationTokens

      // OpenAI 호환 형식으로도 제공 (prompt_tokens_details.cached_tokens)
      // 일부 시스템에서 이 형식을 기대할 수 있음
      if (cacheReadTokens > 0) {
        usage["prompt_tokens_details"] = mapOf("cached_tokens" to cacheReadTokens)
      }

      kLogger.debug {
        "[TrackedHandler] Anthropic 캐시 토큰 추출 - " +
            "cacheRead: $cacheReadTokens, cacheCreation: $cacheCreationTokens"
      }
    }

    // OpenAI 호환 프로바이더의 경우
    // LangChain4J 1.9.x에서는 아직 OpenAiTokenUsage 같은 별도 클래스가 없을 수 있음
    // 향후 LangChain4J 업데이트 시 여기에 추가 가능
  }

  companion object {
    /**
     * TrackedStreamingHandler 팩토리 메서드
     *
     * @param delegate 원본 핸들러
     * @param tracker 추적 컴포넌트
     * @param context 실행 컨텍스트
     * @return TrackedStreamingHandler
     */
    fun wrap(
      delegate: StreamingChatResponseHandler,
      tracker: LlmExecutionTracker,
      context: LlmExecutionContext
    ): TrackedStreamingHandler {
      return TrackedStreamingHandler(delegate, tracker, context)
    }
  }
}
