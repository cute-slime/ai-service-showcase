package com.jongmin.ai.core.platform.entity

/**
 * Loop Job 상태
 *
 * Loop Workflow Job의 실행 상태를 나타낸다.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
enum class LoopJobState {
  /** 대기 중 - Job이 생성되었지만 아직 실행되지 않음 */
  PENDING,

  /** 실행 중 - Job이 현재 실행 중 */
  RUNNING,

  /** 일시 중지 - 사용자에 의해 일시 중지됨 */
  PAUSED,

  /** 정상 완료 - 모든 반복이 성공적으로 완료됨 */
  COMPLETED,

  /** 사용자 취소 - 사용자에 의해 취소됨 */
  CANCELLED,

  /** 에러 발생 - 실행 중 오류로 중단됨 */
  ERROR,

  /** 복구 중 - 장애 후 복구 진행 중 */
  RECOVERING,
}

/**
 * Loop Job 에러 처리 정책
 *
 * 반복 실행 중 에러 발생 시 동작 방식을 정의한다.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
enum class LoopJobErrorHandling {
  /** 즉시 중단 - 에러 발생 시 Job 즉시 종료 */
  STOP,

  /** 계속 진행 - 해당 반복 스킵, 다음 반복 계속 */
  CONTINUE,

  /** 재시도 - 해당 반복 재시도 (maxRetries까지) */
  RETRY,
}

/**
 * Loop Job 체크포인트 상태
 *
 * 노드 실행 상태를 기록하여 장애 복구 시 사용한다.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
enum class LoopJobCheckpointState {
  /** 노드 구동 시작됨 - 복구 시 이 노드부터 재실행 */
  STARTED,

  /** 노드 실행 완료됨 - 복구 시 다음 노드부터 실행 */
  COMPLETED,

  /** 노드 실행 실패 - 복구 시 이 노드부터 재실행 */
  FAILED,
}
