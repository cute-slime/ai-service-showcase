package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.product_agent.platform.component.ComfyUiException
import com.jongmin.ai.product_agent.platform.component.prompt.PromptEvaluationResult
import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 상품 에이전트 SSE 이벤트 헬퍼
 *
 * 이미지 생성/합성 작업에서 공통으로 사용하는 SSE 이벤트 전송 기능을 제공합니다.
 * 각 Executor에서 이 헬퍼를 주입받아 사용합니다.
 *
 * ### 주요 기능:
 * - EventBridgeFluxSink 생성
 * - 상태/완료/에러 이벤트 전송
 * - 평가 거부/프롬프트 생성 완료 이벤트 전송
 *
 * @property objectMapper JSON 직렬화
 * @property eventBridge 이벤트 브릿지
 */
@Component
class ProductAgentSseHelper(
  private val objectMapper: ObjectMapper,
  private val eventBridge: DistributedJobEventBridge
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // 작업 타임아웃 설정 (분)
    const val TIMEOUT_MINUTES = 10L

    // Presigned URL 유효 시간 (분)
    const val PRESIGNED_URL_EXPIRATION_MINUTES = 10

    // S3 이미지 저장 경로 프리픽스
    const val S3_IMAGE_PATH_PREFIX = "product-images"
  }

  // ==================== Emitter 생성 및 관리 ====================

  /**
   * EventBridgeFluxSink를 생성합니다.
   *
   * @param job 분산 작업
   * @return 생성된 EventBridgeFluxSink
   */
  fun createEmitter(job: DistributedJob): EventBridgeFluxSink {
    return EventBridgeFluxSink(
      jobId = job.id,
      jobType = job.type,
      eventBridge = eventBridge,
      correlationId = job.correlationId
    )
  }

  /**
   * 스트리밍 완료를 대기하고, 타임아웃 시 예외를 발생시킵니다.
   *
   * @param emitter SSE Emitter
   * @param jobId 작업 ID
   * @param timeoutMinutes 타임아웃 (분)
   * @throws TimeoutException 타임아웃 발생 시
   */
  fun awaitCompletionOrThrow(
    emitter: EventBridgeFluxSink,
    jobId: String,
    timeoutMinutes: Long = TIMEOUT_MINUTES
  ) {
    val completed = emitter.awaitCompletion(timeoutMinutes, TimeUnit.MINUTES)
    if (!completed) {
      throw TimeoutException("PRODUCT_AGENT 작업 타임아웃 - jobId: $jobId")
    }
  }

  // ==================== 상태 이벤트 ====================

  /**
   * 진행 상태를 SSE로 전송합니다.
   *
   * @param emitter SSE Emitter
   * @param status 상태 값
   * @param message 메시지
   */
  fun emitStatus(emitter: EventBridgeFluxSink, status: String, message: String) {
    val event = mapOf(
      "type" to "STATUS",
      "status" to status,
      "message" to message,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 완료 이벤트 ====================

  /**
   * 이미지 생성/합성 완료 상태와 결과를 SSE로 전송합니다.
   *
   * @param emitter SSE Emitter
   * @param outputId 출력 ID
   * @param imageUrls 생성된 이미지 URL 목록
   */
  fun emitImageCompleted(emitter: EventBridgeFluxSink, outputId: Long, imageUrls: List<String>) {
    val event = mapOf(
      "type" to "COMPLETED",
      "status" to "COMPLETED",
      "message" to "이미지 생성이 완료되었습니다.",
      "outputId" to outputId,
      "generatedImageUrls" to imageUrls,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 평가 관련 이벤트 ====================

  /**
   * 프롬프트 평가 거부 결과를 SSE로 전송합니다.
   *
   * 보안상 rejectionDetail은 사용자에게 노출하지 않습니다.
   *
   * @param emitter SSE Emitter
   * @param result 평가 결과
   */
  fun emitEvaluationRejected(emitter: EventBridgeFluxSink, result: PromptEvaluationResult) {
    kLogger.info { "프롬프트 평가 거부 - reason: ${result.rejectionReasonCode}, detail: ${result.rejectionDetail}" }

    val event = mapOf(
      "type" to "EVALUATION_REJECTED",
      "status" to "REJECTED",
      "message" to "프롬프트가 정책에 부합하지 않아 이미지 생성이 거부되었습니다.",
      "rejectionReason" to result.rejectionReasonCode,
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  /**
   * 프롬프트 생성 완료 상태를 SSE로 전송합니다.
   *
   * 보안상 실제 프롬프트 내용은 클라이언트에 전달하지 않습니다.
   *
   * @param emitter SSE Emitter
   */
  fun emitPromptGenerated(emitter: EventBridgeFluxSink) {
    val event = mapOf(
      "type" to "PROMPT_GENERATED",
      "status" to "PROMPT_GENERATED",
      "message" to "최적화된 프롬프트가 생성되었습니다.",
      "timestamp" to System.currentTimeMillis()
    )
    emitter.next(objectMapper.writeValueAsString(event))
  }

  // ==================== 에러 이벤트 ====================

  /**
   * 에러 이벤트를 SSE로 전송합니다.
   *
   * @param emitter SSE Emitter
   * @param errorCode 에러 코드
   * @param message 에러 메시지
   */
  fun emitError(emitter: EventBridgeFluxSink, errorCode: String, message: String) {
    val event = mapOf(
      "type" to "ERROR",
      "status" to "FAILED",
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
   * 에러 발생 시 emitter에 사용자 친화적 에러 이벤트를 발행합니다.
   *
   * 에러 타입에 따라 적절한 메시지를 생성하여 클라이언트에 전송합니다.
   *
   * @param emitter SSE Emitter
   * @param e 발생한 예외
   */
  fun handleError(emitter: EventBridgeFluxSink, e: Exception) {
    if (emitter.isCompleted()) return

    // 에러 타입별 사용자 친화적 메시지 생성
    val (errorCode, userMessage) = when (e) {
      is TimeoutException -> "TIMEOUT" to "이미지 생성 시간이 초과되었습니다. 다시 시도해주세요."
      is ComfyUiException -> "COMFYUI_ERROR" to "AI 이미지 생성 중 오류가 발생했습니다: ${e.message}"
      is IllegalArgumentException -> "INVALID_REQUEST" to "요청 데이터가 올바르지 않습니다: ${e.message}"
      is IllegalStateException -> "PROCESSING_ERROR" to "처리 중 오류가 발생했습니다: ${e.message}"
      is software.amazon.awssdk.services.s3.model.S3Exception -> "S3_ERROR" to "이미지 저장 중 오류가 발생했습니다. 다시 시도해주세요."
      else -> "UNKNOWN_ERROR" to "예기치 않은 오류가 발생했습니다. 다시 시도해주세요."
    }

    emitError(emitter, errorCode, userMessage)
  }
}
