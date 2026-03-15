package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import jakarta.persistence.*
import java.time.ZonedDateTime
import java.util.*

/**
 * Loop Workflow Job 엔티티
 *
 * 워크플로우를 N회 반복 실행하는 Job의 영구 저장용 엔티티.
 * 백오피스에서 조회/관리되며, 실행 이력이 보존된다.
 *
 * ## 사용 예시
 * ```kotlin
 * val job = LoopJob(
 *     workflowId = 1001L,
 *     accountId = 123L,
 *     maxCount = 100,
 *     delayMs = 1000L,
 *     onError = LoopJobErrorHandling.CONTINUE,
 * )
 * ```
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_loop_job_workflow_id", columnList = "workflowId"),
    Index(name = "idx_loop_job_account_id", columnList = "accountId"),
    Index(name = "idx_loop_job_state", columnList = "state"),
    Index(name = "idx_loop_job_created_at", columnList = "createdAt"),
  ]
)
class LoopJob(
  /** Job 고유 ID (UUID) */
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  val id: String = UUID.randomUUID().toString(),

  /** 실행할 워크플로우 ID */
  @Column(nullable = false, updatable = false, comment = "워크플로우 ID")
  val workflowId: Long,

  /** 캔버스 ID (선택 - SSE 이벤트용) */
  @Column(updatable = false, comment = "캔버스 ID")
  val canvasId: String? = null,

  /** 실행 계정 ID */
  @Column(nullable = false, updatable = false, comment = "실행 계정 ID")
  val accountId: Long,

  // ========== 실행 상태 ==========

  /** Job 상태 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, comment = "Job 상태")
  var state: LoopJobState = LoopJobState.PENDING,

  /** 현재 반복 횟수 */
  @Column(nullable = false, comment = "현재 반복 횟수")
  var currentCount: Int = 0,

  /** 최대 반복 횟수 (-1: 무한) */
  @Column(nullable = false, updatable = false, comment = "최대 반복 횟수 (-1: 무한)")
  val maxCount: Int,

  // ========== 설정 ==========

  /** 반복 간 딜레이 (ms) */
  @Column(nullable = false, updatable = false, comment = "반복 간 딜레이 (ms)")
  val delayMs: Long = 0,

  /** 에러 발생 시 동작 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false, comment = "에러 처리 정책")
  val onError: LoopJobErrorHandling = LoopJobErrorHandling.STOP,

  /** 재시도 최대 횟수 (onError=RETRY인 경우) */
  @Column(nullable = false, updatable = false, comment = "최대 재시도 횟수")
  val maxRetries: Int = 3,

  /** 좀비 복구 시 자동 재시작 여부 (false면 PAUSED 상태로 전환) */
  @Column(nullable = false, updatable = false, comment = "자동 재시작 여부")
  val autoRestart: Boolean = false,

  // ========== 타임스탬프 ==========

  /** 생성 시간 */
  @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP", comment = "생성 시간")
  val createdAt: ZonedDateTime = now(),

  /** 마지막 업데이트 */
  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "마지막 업데이트")
  var updatedAt: ZonedDateTime = now(),

  /** 마지막 Heartbeat (실행 중일 때만 갱신) */
  @Column(columnDefinition = "TIMESTAMP", comment = "마지막 Heartbeat")
  var lastHeartbeat: ZonedDateTime? = null,

  /** 완료 시간 */
  @Column(columnDefinition = "TIMESTAMP", comment = "완료 시간")
  var completedAt: ZonedDateTime? = null,

  // ========== 메타 정보 ==========

  /** 에러 메시지 (에러 발생 시) */
  @Column(columnDefinition = "TEXT", comment = "에러 메시지")
  var errorMessage: String? = null,

  /** 실행 중인 인스턴스 ID */
  @Column(length = 100, comment = "실행 인스턴스 ID")
  var instanceId: String? = null,

  /** 취소 사유 (취소된 경우) */
  @Column(length = 500, comment = "취소 사유")
  var cancelReason: String? = null,

  // ========== 통계 ==========

  /** 성공한 반복 횟수 */
  @Column(nullable = false, comment = "성공한 반복 횟수")
  var successCount: Int = 0,

  /** 실패한 반복 횟수 */
  @Column(nullable = false, comment = "실패한 반복 횟수")
  var failureCount: Int = 0,
) {
  /**
   * 상태 변경 시 updatedAt 자동 갱신
   */
  @PreUpdate
  fun onPreUpdate() {
    updatedAt = now()
  }

  /**
   * 무한 반복 여부 확인
   */
  fun isInfinite(): Boolean = maxCount < 0

  /**
   * 완료 여부 확인 (정상 완료, 취소, 에러 모두 포함)
   */
  fun isTerminated(): Boolean = state in listOf(
    LoopJobState.COMPLETED,
    LoopJobState.CANCELLED,
    LoopJobState.ERROR,
  )

  /**
   * 실행 가능 여부 확인
   */
  fun canRun(): Boolean = state in listOf(
    LoopJobState.PENDING,
    LoopJobState.PAUSED,
    LoopJobState.RECOVERING,
  )

  /**
   * 진행률 계산 (0.0 ~ 1.0, 무한 반복 시 null)
   */
  fun getProgress(): Double? {
    if (isInfinite()) return null
    if (maxCount == 0) return 1.0
    return currentCount.toDouble() / maxCount.toDouble()
  }

  override fun toString(): String {
    return "LoopJob(id='$id', workflowId=$workflowId, state=$state, progress=${currentCount}/${if (isInfinite()) "∞" else maxCount})"
  }
}
