package com.jongmin.ai.dte.dto

import com.jongmin.ai.core.platform.entity.LoopJobErrorHandling

/**
 * 워크플로우 DTE Job Payload
 *
 * DTE의 WORKFLOW 타입 작업 payload에서 추출되는 데이터입니다.
 * 워크플로우 실행에 필요한 모든 정보를 담습니다.
 *
 * workflowId와 workflowData 중 하나 이상 필수.
 * 둘 다 제공 시 workflowData가 우선 적용됩니다.
 *
 * @property workflowId AI 에이전트 ID (워크플로우 정의 조회용) - workflowData 없으면 필수
 * @property workflowData 워크플로우 JSON (직접 전달 시, workflowId보다 우선 적용)
 * @property input 워크플로우 입력값 (노드 실행에 전달되는 초기 데이터)
 * @property messages 메시지 입력 (채팅 형식의 입력 데이터)
 * @property canvasId 캔버스 ID (SSE 토픽 구분용)
 * @property streaming SSE 스트리밍 여부 (false면 백그라운드 실행)
 * @property isLoop Loop Job 여부 (true면 반복 실행)
 * @property loopConfig Loop 설정 (isLoop=true일 때만 사용)
 * @property accountId 실행 계정 ID (필수)
 * @property sessionData 세션 정보 (직렬화된 JSession 데이터)
 */
data class WorkflowJobPayload(
  /** AI 에이전트 ID (워크플로우 정의 조회용) - workflowData 없으면 필수 */
  val workflowId: Long? = null,

  /** 워크플로우 JSON (직접 전달 시, workflowId보다 우선) */
  val workflowData: Map<String, Any>? = null,

  /** 워크플로우 입력값 */
  val input: Map<String, Any>? = null,

  /** 메시지 입력 (채팅 형식) */
  val messages: List<Map<String, Any>>? = null,

  /** 캔버스 ID (SSE 토픽용) */
  val canvasId: String? = null,

  /** SSE 스트리밍 여부 */
  val streaming: Boolean = true,

  /** Loop Job 여부 */
  val isLoop: Boolean = false,

  /** Loop 설정 (isLoop=true일 때만 사용) */
  val loopConfig: LoopConfig? = null,

  /** 실행 계정 ID */
  val accountId: Long,

  /** 세션 정보 (직렬화된 JSession) */
  val sessionData: Map<String, Any>? = null,
) {
  init {
    // workflowId 또는 workflowData 중 하나 이상 필수
    require(workflowId != null || workflowData != null) {
      "workflowId 또는 workflowData 중 하나 이상 필수"
    }
    // isLoop=true일 때 loopConfig 필수
    if (isLoop) {
      requireNotNull(loopConfig) { "isLoop=true일 때 loopConfig 필수" }
    }
  }
}

/**
 * Loop 워크플로우 설정
 *
 * 반복 실행 워크플로우의 설정 정보를 담는다.
 * Loop Job의 반복 횟수, 지연 시간, 에러 처리 정책 등을 정의한다.
 *
 * @property maxCount 최대 반복 횟수 (1 이상 필수)
 * @property delayMs 반복 간 지연 시간 (밀리초, 0 이상)
 * @property onError 에러 발생 시 처리 정책 (기존 LoopJobErrorHandling enum 재사용)
 * @property maxRetries 재시도 최대 횟수 (onError=RETRY일 때 사용, 0 이상)
 * @author Claude Code
 * @since 2026.01.09
 */
data class LoopConfig(
  /** 최대 반복 횟수 (1 이상 필수) */
  val maxCount: Int,

  /** 반복 간 지연 시간 (밀리초, 기본값: 0) */
  val delayMs: Long = 0,

  /** 에러 발생 시 처리 정책 (기본값: STOP - 에러 시 즉시 중단) */
  val onError: LoopJobErrorHandling = LoopJobErrorHandling.STOP,

  /** 재시도 최대 횟수 (onError=RETRY일 때 사용, 기본값: 3) */
  val maxRetries: Int = 3,
) {
  init {
    // maxCount는 1 이상이어야 함
    require(maxCount >= 1) { "maxCount는 1 이상이어야 합니다: $maxCount" }

    // delayMs는 0 이상이어야 함
    require(delayMs >= 0) { "delayMs는 0 이상이어야 합니다: $delayMs" }

    // maxRetries는 0 이상이어야 함
    require(maxRetries >= 0) { "maxRetries는 0 이상이어야 합니다: $maxRetries" }
  }
}
