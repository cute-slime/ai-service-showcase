package com.jongmin.ai.product_agent.platform.component.writing

import com.jongmin.ai.product_agent.platform.dto.request.WritingRejectionReason
import com.jongmin.ai.product_agent.platform.dto.response.WritingCompletedData
import com.jongmin.ai.product_agent.platform.dto.response.WritingErrorCode
import com.jongmin.ai.product_agent.platform.dto.response.WritingEventType
import com.jongmin.ai.product_agent.platform.dto.response.WritingStatus
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeoutException

/**
 * 글쓰기 도구 SSE 이벤트 이미터
 *
 * SSE 이벤트 메시지 생성 및 전송을 담당하는 컴포넌트입니다.
 *
 * ### 이벤트 타입:
 * - STATUS: 상태 변경 (PROCESSING, EVALUATING, GENERATING 등)
 * - TOKEN: 토큰 스트리밍
 * - PROMPT_GENERATED: 프롬프트 생성 완료
 * - EVALUATION_REJECTED: 평가 거부
 * - COMPLETED: 작업 완료
 * - ERROR: 에러 발생
 *
 * @property objectMapper JSON 직렬화/역직렬화
 */
@Component
class WritingSseEmitter(
  private val objectMapper: ObjectMapper
) {
  private val kLogger = KotlinLogging.logger {}

  // ==================== 상태 이벤트 ====================

  /**
   * 상태 이벤트를 전송합니다.
   *
   * @param emitter SSE Emitter
   * @param status 상태 값
   * @param message 메시지
   */
  fun emitStatus(emitter: EventBridgeFluxSink, status: String, message: String) {
    val event = mapOf(
      "type" to WritingEventType.STATUS,
      "status" to status,
      "message" to message,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  /**
   * 처리 시작 이벤트 전송
   */
  fun emitProcessing(emitter: EventBridgeFluxSink) {
    emitStatus(emitter, WritingStatus.PROCESSING, "글쓰기 작업을 시작합니다...")
  }

  /**
   * 평가 시작 이벤트 전송
   */
  fun emitEvaluating(emitter: EventBridgeFluxSink) {
    emitStatus(emitter, WritingStatus.EVALUATING, "입력 텍스트를 검증하고 있습니다...")
  }

  /**
   * 평가 통과 이벤트 전송
   */
  fun emitEvaluationPassed(emitter: EventBridgeFluxSink) {
    emitStatus(emitter, WritingStatus.EVALUATION_PASSED, "입력 검증을 통과했습니다.")
  }

  /**
   * 프롬프트 생성 시작 이벤트 전송
   */
  fun emitGeneratingPrompt(emitter: EventBridgeFluxSink) {
    emitStatus(emitter, WritingStatus.GENERATING_PROMPT, "최적화된 프롬프트를 생성하고 있습니다...")
  }

  /**
   * 텍스트 생성 시작 이벤트 전송
   */
  fun emitGenerating(emitter: EventBridgeFluxSink) {
    emitStatus(emitter, WritingStatus.GENERATING, "텍스트를 생성하고 있습니다...")
  }

  // ==================== 프롬프트 생성 완료 이벤트 ====================

  /**
   * 프롬프트 생성 완료 이벤트 전송
   */
  fun emitPromptGenerated(emitter: EventBridgeFluxSink) {
    val event = mapOf(
      "type" to WritingEventType.PROMPT_GENERATED,
      "status" to WritingStatus.PROMPT_GENERATED,
      "message" to "최적화된 프롬프트가 생성되었습니다.",
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 토큰 스트리밍 이벤트 ====================

  /**
   * 토큰 스트리밍 이벤트 전송
   *
   * @param emitter SSE Emitter
   * @param token 생성된 토큰
   * @param currentLength 현재까지 생성된 텍스트 길이
   */
  fun emitToken(emitter: EventBridgeFluxSink, token: String, currentLength: Int) {
    val event = mapOf(
      "type" to WritingEventType.TOKEN,
      "status" to WritingStatus.GENERATING,
      "token" to token,
      "currentLength" to currentLength,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 평가 거부 이벤트 ====================

  /**
   * 평가 거부 이벤트 전송
   *
   * @param emitter SSE Emitter
   * @param reason 거부 사유
   */
  fun emitEvaluationRejected(emitter: EventBridgeFluxSink, reason: WritingRejectionReason) {
    kLogger.info { "글쓰기 입력 평가 거부 - reason: ${reason.code()}" }

    val event = mapOf(
      "type" to WritingEventType.EVALUATION_REJECTED,
      "status" to WritingStatus.REJECTED,
      "message" to reason.message,
      "rejectionReason" to reason.code(),
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 완료 이벤트 ====================

  /**
   * 완료 이벤트 전송
   *
   * @param emitter SSE Emitter
   * @param data 완료 데이터
   */
  fun emitCompleted(emitter: EventBridgeFluxSink, data: WritingCompletedData) {
    val event = mapOf(
      "type" to WritingEventType.COMPLETED,
      "status" to WritingStatus.COMPLETED,
      "message" to "글쓰기 작업이 완료되었습니다.",
      "output" to data.output,
      "writeType" to data.type,
      "originalText" to data.originalText,
      "tokenCount" to data.tokenCount,
      "duration" to data.duration,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 에러 이벤트 ====================

  /**
   * 에러 이벤트 전송
   *
   * @param emitter SSE Emitter
   * @param errorCode 에러 코드
   * @param message 에러 메시지
   */
  fun emitError(emitter: EventBridgeFluxSink, errorCode: String, message: String) {
    val event = mapOf(
      "type" to WritingEventType.ERROR,
      "status" to WritingStatus.FAILED,
      "errorCode" to errorCode,
      "message" to message,
      "timestamp" to System.currentTimeMillis()
    )
    try {
      emitter.next(objectMapper.writeValueAsString(event))
    } catch (e: Exception) {
      kLogger.warn(e) { "에러 이벤트 전송 실패" }
    }
  }

  /**
   * 에러 처리 및 이벤트 전송
   *
   * 에러 타입에 따라 적절한 에러 코드와 메시지를 생성합니다.
   *
   * @param emitter SSE Emitter
   * @param e 발생한 예외
   */
  fun handleError(emitter: EventBridgeFluxSink, e: Exception) {
    if (emitter.isCompleted()) return

    kLogger.error(e) { "글쓰기 작업 중 오류 발생: ${e.message}" }

    val (errorCode, userMessage) = when (e) {
      is TimeoutException -> WritingErrorCode.TIMEOUT to "작업 시간이 초과되었습니다. 다시 시도해주세요."
      is IllegalArgumentException -> WritingErrorCode.INVALID_TEXT to (e.message ?: "입력이 올바르지 않습니다.")
      is IllegalStateException -> WritingErrorCode.LLM_ERROR to "텍스트 처리 중 오류가 발생했습니다. 다시 시도해주세요."
      else -> WritingErrorCode.UNKNOWN_ERROR to "예기치 않은 오류가 발생했습니다. 다시 시도해주세요."
    }

    emitError(emitter, errorCode, userMessage)
  }
}
