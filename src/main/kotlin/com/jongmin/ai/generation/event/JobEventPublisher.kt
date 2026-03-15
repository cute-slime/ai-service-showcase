package com.jongmin.ai.generation.event

import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 작업 이벤트 발행기
 *
 * generation 도메인의 진행 이벤트를 DTE 공용 이벤트 브릿지에 위임한다.
 * 실제 채널 publish/subscribe는 jspring-dte의 DistributedJobEventBridge가 담당한다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Component
class JobEventPublisher(
  private val eventBridge: DistributedJobEventBridge,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 데이터 이벤트 발행
   *
   * 비즈니스 로직에서 발생하는 실시간 데이터를 SSE 구독자에게 전달한다.
   * 예: 생성 진행 상황, 단계 업데이트 등
   *
   * @param jobId Job ID
   * @param type 태스크 타입
   * @param data 비즈니스 데이터 (JSON 문자열)
   * @param correlationId 연관 요청 ID (선택)
   */
  fun emitData(jobId: String, type: String, data: String, correlationId: String? = null) {
    eventBridge.emitData(jobId, type, data, correlationId)
  }

  /**
   * 스트림 완료 이벤트 발행
   *
   * 비즈니스 로직의 스트리밍이 정상 종료되었음을 알린다.
   * 이 이벤트 후 SSE 클라이언트는 연결을 종료해야 한다.
   *
   * @param jobId Job ID
   * @param type 태스크 타입
   * @param correlationId 연관 요청 ID (선택)
   */
  fun emitComplete(jobId: String, type: String, correlationId: String? = null) {
    eventBridge.emitComplete(jobId, type, correlationId)
    kLogger.info { "스트림 완료 이벤트 발행 - jobId: $jobId" }
  }

  /**
   * 에러 이벤트 발행
   *
   * 비즈니스 로직에서 에러가 발생했음을 알린다.
   * 이 이벤트 후 SSE 클라이언트는 에러를 처리하고 연결을 종료해야 한다.
   *
   * @param jobId Job ID
   * @param type 태스크 타입
   * @param errorMessage 에러 메시지
   * @param correlationId 연관 요청 ID (선택)
   */
  fun emitError(jobId: String, type: String, errorMessage: String, correlationId: String? = null) {
    eventBridge.emitError(jobId, type, errorMessage, correlationId)
    kLogger.warn { "스트림 에러 이벤트 발행 - jobId: $jobId, error: $errorMessage" }
  }
}
