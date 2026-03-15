package com.jongmin.ai.product_agent.platform.component

import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.CopyWriteEventType
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingData
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 카피라이팅 SSE 이벤트 이미터
 *
 * SSE 이벤트 메시지 생성 및 전송을 담당하는 컴포넌트입니다.
 *
 * ### 주요 책임:
 * - SSE 이벤트 메시지 생성 (createEventMessage)
 * - 오류 이벤트 처리 (handleError)
 * - 취소 이벤트 처리 (handleCancelledResponse)
 * - 단계별 진행 상황 이벤트 전송
 *
 * ### SSE 메시지 구조:
 * ```json
 * {
 *   "type": "AI_COPYWRITING_STATUS_CHANGED",
 *   "eventType": "INPUT_ANALYSIS|COPYWRITING|COMPLETED|ERROR",
 *   "status": "ACTIVE|IN_PROGRESS|DONE|FAILED",
 *   "data": {...}
 * }
 * ```
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property cancellationManager AI 추론 취소 관리자
 */
@Component
class CopywritingSseEmitter(
  private val objectMapper: ObjectMapper,
  private val cancellationManager: AIInferenceCancellationManager
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * SSE 이벤트 메시지 생성
   *
   * @param eventType 카피라이팅 이벤트 타입
   * @param status 현재 처리 상태
   * @param data 추가 데이터 (단계별 처리 결과 등)
   * @return JSON 형식의 이벤트 메시지
   */
  fun createEventMessage(
    eventType: CopyWriteEventType,
    status: StatusType,
    data: Any? = null
  ): String {
    return objectMapper.writeValueAsString(
      mapOf(
        "type" to MessageType.AI_COPYWRITING_STATUS_CHANGED,
        "eventType" to eventType,
        "status" to status,
        "data" to data,
      )
    )
  }

  /**
   * 오류 처리
   *
   * ### 오류 처리 흐름:
   * 1. 오류 로깅 (ERROR 레벨)
   * 2. ERROR 이벤트 전송 (오류 메시지 포함)
   * 3. 스트림 종료
   *
   * @param e 발생한 예외
   * @param emitter SSE Emitter
   */
  fun handleError(e: Exception, emitter: FluxSink<String>) {
    kLogger.error(e) { "카피라이팅 생성 중 오류 발생: ${e.message}" }

    // 에러 이벤트 전송
    emitter.next(
      createEventMessage(
        CopyWriteEventType.ERROR,
        StatusType.FAILED,
        data = mapOf(
          "errorType" to (e::class.simpleName ?: "Unknown"),
          "message" to (e.message ?: "알 수 없는 오류가 발생했습니다")
        )
      )
    )

    // 스트림 종료
    emitter.complete()
  }

  /**
   * 취소된 경우의 응답 처리
   *
   * ### 처리 흐름:
   * 1. 취소 로깅
   * 2. 부분 결과 포함한 COPYWRITING 완료 이벤트 전송
   * 3. COMPLETED 이벤트 전송 (cancelled: true 포함)
   * 4. 추론 등록 해제
   * 5. 스트림 종료
   *
   * @param emitter SSE Flux Emitter
   * @param inferenceId 추론 ID
   * @param content 부분 생성된 콘텐츠
   * @param copywritingData 원본 요청 데이터
   * @param duration 소요 시간
   * @param tokenCount 생성된 토큰 수
   */
  fun handleCancelledResponse(
    emitter: FluxSink<String>,
    inferenceId: String,
    content: String,
    copywritingData: CopywritingData,
    duration: Long,
    tokenCount: Int
  ) {
    kLogger.info { "카피라이팅 생성 취소됨 - ID: $inferenceId, 토큰: $tokenCount, 길이: ${content.length}" }

    // 카피라이팅 완료 이벤트 (부분 결과)
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.DONE,
        data = mapOf(
          "content" to content,
          "length" to content.length,
          "duration" to duration,
          "style" to copywritingData.copyStyle,
          "cancelled" to true,
          "tokenCount" to tokenCount
        )
      )
    )

    // 전체 프로세스 완료 이벤트 (취소됨)
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COMPLETED,
        StatusType.DONE,
        data = mapOf(
          "totalDuration" to duration,
          "completedAt" to System.currentTimeMillis(),
          "cancelled" to true
        )
      )
    )

    // 추론 등록 해제
    cancellationManager.unregisterInference(inferenceId)

    // 스트림 종료
    emitter.complete()
  }

  /**
   * 입력 분석 시작 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param accountId 계정 ID
   * @param copywritingData 카피라이팅 요청 데이터
   * @param hasImages 이미지 포함 여부
   */
  fun emitInputAnalysisActive(
    emitter: FluxSink<String>,
    accountId: Long?,
    copywritingData: CopywritingData,
    hasImages: Boolean
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.INPUT_ANALYSIS,
        StatusType.ACTIVE,
        data = mapOf(
          "accountId" to accountId,
          "copyStyle" to copywritingData.copyStyle,
          "productName" to copywritingData.productBasicInfo?.productName,
          "hasImages" to hasImages
        )
      )
    )
  }

  /**
   * 입력 분석 완료 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   */
  fun emitInputAnalysisDone(emitter: FluxSink<String>) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.INPUT_ANALYSIS,
        StatusType.DONE,
        data = mapOf("message" to "프롬프트 생성 완료")
      )
    )
  }

  /**
   * 카피라이팅 생성 시작 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   */
  fun emitCopywritingActive(emitter: FluxSink<String>) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.ACTIVE,
        data = mapOf("generating" to true, "phase" to "copywriting")
      )
    )
  }

  /**
   * 카피라이팅 토큰 스트리밍 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param token 생성된 토큰
   * @param currentLength 현재 콘텐츠 길이
   */
  fun emitCopywritingToken(
    emitter: FluxSink<String>,
    token: String,
    currentLength: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.IN_PROGRESS,
        data = mapOf(
          "token" to token,
          "currentLength" to currentLength,
          "phase" to "copywriting"
        )
      )
    )
  }

  /**
   * 추론 토큰 스트리밍 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param reasoningText 추론 텍스트
   * @param index 추론 인덱스
   */
  fun emitReasoningToken(
    emitter: FluxSink<String>,
    reasoningText: String,
    index: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.IN_PROGRESS,
        data = mapOf(
          "reasoning" to reasoningText,
          "reasoningIndex" to index,
          "phase" to "reasoning"
        )
      )
    )
  }

  /**
   * 카피라이팅 생성 완료 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param parsedCopywriting 파싱된 카피라이팅 결과
   * @param duration 소요 시간
   * @param tokenCount 토큰 수
   */
  fun emitCopywritingDone(
    emitter: FluxSink<String>,
    parsedCopywriting: Any,
    duration: Long,
    tokenCount: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.DONE,
        data = mapOf(
          "copywriting" to parsedCopywriting,
          "duration" to duration,
          "tokenCount" to tokenCount,
          "phase" to "copywriting_completed"
        )
      )
    )
  }

  /**
   * 마케팅 인사이트 생성 시작 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   */
  fun emitMarketingInsightsActive(emitter: FluxSink<String>) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.MARKETING_INSIGHTS,
        StatusType.ACTIVE,
        data = mapOf("generating" to true, "phase" to "marketing_insights")
      )
    )
  }

  /**
   * 마케팅 인사이트 토큰 스트리밍 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param token 생성된 토큰
   * @param currentLength 현재 콘텐츠 길이
   */
  fun emitMarketingInsightsToken(
    emitter: FluxSink<String>,
    token: String,
    currentLength: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.MARKETING_INSIGHTS,
        StatusType.IN_PROGRESS,
        data = mapOf(
          "token" to token,
          "currentLength" to currentLength,
          "phase" to "marketing_insights"
        )
      )
    )
  }

  /**
   * 마케팅 인사이트 추론 토큰 스트리밍 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param reasoningText 추론 텍스트
   * @param index 추론 인덱스
   */
  fun emitMarketingInsightsReasoningToken(
    emitter: FluxSink<String>,
    reasoningText: String,
    index: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.MARKETING_INSIGHTS,
        StatusType.IN_PROGRESS,
        data = mapOf(
          "reasoning" to reasoningText,
          "reasoningIndex" to index,
          "phase" to "reasoning"
        )
      )
    )
  }

  /**
   * 마케팅 인사이트 생성 완료 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param parsedMarketingInsights 파싱된 마케팅 인사이트 결과
   * @param duration 소요 시간
   * @param tokenCount 토큰 수
   */
  fun emitMarketingInsightsDone(
    emitter: FluxSink<String>,
    parsedMarketingInsights: Any,
    duration: Long,
    tokenCount: Int
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.MARKETING_INSIGHTS,
        StatusType.DONE,
        data = mapOf(
          "marketingInsights" to parsedMarketingInsights,
          "duration" to duration,
          "tokenCount" to tokenCount,
          "phase" to "marketing_insights_completed"
        )
      )
    )
  }

  /**
   * 마케팅 인사이트 생성 실패 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   */
  fun emitMarketingInsightsFailed(emitter: FluxSink<String>) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.MARKETING_INSIGHTS,
        StatusType.DONE,
        data = mapOf(
          "error" to "마케팅 인사이트 생성 실패",
          "phase" to "marketing_insights_failed"
        )
      )
    )
  }

  /**
   * 전체 프로세스 완료 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param result 최종 결과
   * @param totalDuration 총 소요 시간
   * @param copywritingDuration 카피라이팅 소요 시간
   * @param marketingInsightsDuration 마케팅 인사이트 소요 시간 (nullable)
   * @param totalTokenCount 총 토큰 수
   */
  fun emitCompleted(
    emitter: FluxSink<String>,
    result: Any,
    totalDuration: Long,
    copywritingDuration: Long,
    marketingInsightsDuration: Long? = null,
    totalTokenCount: Int
  ) {
    val data = mutableMapOf<String, Any>(
      "result" to result,
      "totalDuration" to totalDuration,
      "copywritingDuration" to copywritingDuration,
      "totalTokenCount" to totalTokenCount,
      "completedAt" to System.currentTimeMillis()
    )

    marketingInsightsDuration?.let {
      data["marketingInsightsDuration"] = it
    }

    emitter.next(
      createEventMessage(
        CopyWriteEventType.COMPLETED,
        StatusType.DONE,
        data = data
      )
    )
  }

  /**
   * 마케팅 인사이트 실패 시 폴백 완료 이벤트 전송
   *
   * @param emitter SSE Flux Emitter
   * @param fallbackResponse 폴백 응답
   * @param totalDuration 총 소요 시간
   * @param copywritingDuration 카피라이팅 소요 시간
   */
  fun emitCompletedWithMarketingInsightsFailed(
    emitter: FluxSink<String>,
    fallbackResponse: Any,
    totalDuration: Long,
    copywritingDuration: Long
  ) {
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COMPLETED,
        StatusType.DONE,
        data = mapOf(
          "result" to fallbackResponse,
          "totalDuration" to totalDuration,
          "copywritingDuration" to copywritingDuration,
          "marketingInsightsFailed" to true,
          "completedAt" to System.currentTimeMillis()
        )
      )
    )
  }
}
