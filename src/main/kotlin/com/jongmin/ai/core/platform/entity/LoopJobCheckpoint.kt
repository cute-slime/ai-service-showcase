package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import jakarta.persistence.*
import java.time.ZonedDateTime
import java.util.*

/**
 * Loop Job 체크포인트 엔티티
 *
 * 노드 구동 시작/완료를 기록하여 장애 복구에 사용한다.
 * Job당 최신 1개만 유지 (덮어쓰기).
 *
 * ## 복구 판단 로직
 * - STARTED: 복구 시 해당 노드부터 재실행
 * - COMPLETED: 복구 시 다음 노드부터 실행
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_loop_job_checkpoint_job_id", columnList = "jobId"),
  ]
)
class LoopJobCheckpoint(
  /** 체크포인트 고유 ID (UUID) */
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  val id: String = UUID.randomUUID().toString(),

  /** Loop Job ID */
  @Column(nullable = false, updatable = false, length = 36, comment = "Loop Job ID")
  val jobId: String,

  /** 반복 횟수 (현재 몇 번째 반복인지) */
  @Column(nullable = false, comment = "반복 횟수")
  val iterationCount: Int,

  /** 현재 실행 중인 노드 ID */
  @Column(nullable = false, length = 100, comment = "현재 노드 ID")
  val currentNodeId: String,

  /** 노드 타입 (예: generate-text, scenario-concept) */
  @Column(length = 50, comment = "노드 타입")
  val nodeType: String? = null,

  /** 노드 상태 (STARTED, COMPLETED, FAILED) */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, comment = "노드 상태")
  val nodeState: LoopJobCheckpointState,

  /** 체크포인트 시간 */
  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "체크포인트 시간")
  val timestamp: ZonedDateTime = now(),

  /** 노드 출력값 (JSON, 복구용) */
  @Column(columnDefinition = "JSON", comment = "노드 출력값 (JSON)")
  val nodeOutputsJson: String? = null,

  /** 에러 메시지 (노드 실행 실패 시) */
  @Column(columnDefinition = "TEXT", comment = "에러 메시지")
  val errorMessage: String? = null,
) {
  /**
   * 복구 시 이 노드부터 재실행해야 하는지 확인
   */
  fun shouldRestartFromThisNode(): Boolean = nodeState == LoopJobCheckpointState.STARTED

  /**
   * 복구 시 다음 노드부터 실행해야 하는지 확인
   */
  fun shouldContinueFromNextNode(): Boolean = nodeState == LoopJobCheckpointState.COMPLETED

  override fun toString(): String {
    return "LoopJobCheckpoint(jobId='$jobId', iteration=$iterationCount, nodeId='$currentNodeId', state=$nodeState)"
  }
}
