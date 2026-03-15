package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import jakarta.persistence.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Loop Job 반복 실행 결과 엔티티
 *
 * LoopJob의 각 iteration(반복) 실행 기록을 저장한다.
 * 실행 결과 추적, 디버깅, 통계 분석에 사용된다.
 *
 * ## 사용 예시
 * ```kotlin
 * val iteration = LoopJobIteration(
 *     jobId = "uuid-job-id",
 *     seq = 1,
 * )
 * // 실행 완료 시
 * iteration.complete(LoopJobIterationStatus.SUCCESS)
 * ```
 *
 * @author Claude Code
 * @since 2026.01.05
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_loop_job_iteration_job_id", columnList = "jobId"),
    Index(name = "idx_loop_job_iteration_job_seq", columnList = "jobId, seq"),
    Index(name = "idx_loop_job_iteration_status", columnList = "status"),
    Index(name = "idx_loop_job_iteration_started_at", columnList = "startedAt"),
  ]
)
class LoopJobIteration(
  /** 반복 실행 고유 ID (UUID) */
  @Id
  @Column(nullable = false, updatable = false, length = 36)
  val id: String = UUID.randomUUID().toString(),

  /** 소속 Job ID (LoopJob FK) */
  @Column(nullable = false, updatable = false, length = 36, comment = "Loop Job ID")
  val jobId: String,

  /** 반복 순번 (1부터 시작) */
  @Column(nullable = false, updatable = false, comment = "반복 순번")
  val seq: Int,

  // ========== 실행 상태 ==========

  /** 실행 상태 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, comment = "실행 상태")
  var status: LoopJobIterationStatus = LoopJobIterationStatus.RUNNING,

  /** 시작 시간 */
  @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP", comment = "시작 시간")
  val startedAt: ZonedDateTime = now(),

  /** 종료 시간 (실행 중이면 null) */
  @Column(columnDefinition = "TIMESTAMP", comment = "종료 시간")
  var endedAt: ZonedDateTime? = null,

  // ========== 오류 정보 ==========

  /** 내부 오류 발생 횟수 (N건 이상 시 Job 중단 로직용) */
  @Column(nullable = false, comment = "내부 오류 횟수")
  var internalErrorCount: Int = 0,

  /** 에러 메시지 (실패 시) */
  @Column(columnDefinition = "TEXT", comment = "에러 메시지")
  var errorMessage: String? = null,
) {
  /**
   * 실행 완료 처리
   *
   * @param resultStatus 최종 상태 (SUCCESS, FAILED, CANCELLED)
   * @param error 에러 메시지 (실패 시)
   */
  fun complete(resultStatus: LoopJobIterationStatus, error: String? = null) {
    this.status = resultStatus
    this.endedAt = now()
    this.errorMessage = error
  }

  /**
   * 내부 오류 카운트 증가
   */
  fun incrementErrorCount() {
    this.internalErrorCount++
  }

  /**
   * 실행 시간 계산 (밀리초)
   *
   * @return 실행 시간 (ms), 아직 실행 중이면 현재까지 경과 시간
   */
  fun getDurationMs(): Long {
    val end = endedAt ?: now()
    return ChronoUnit.MILLIS.between(startedAt, end)
  }

  /**
   * 실행 시간 계산 (초)
   *
   * @return 실행 시간 (초), 아직 실행 중이면 현재까지 경과 시간
   */
  fun getDurationSeconds(): Long {
    return getDurationMs() / 1000
  }

  /**
   * 실행 중 여부
   */
  fun isRunning(): Boolean = status == LoopJobIterationStatus.RUNNING

  /**
   * 성공 여부
   */
  fun isSuccess(): Boolean = status == LoopJobIterationStatus.SUCCESS

  /**
   * 실패 여부 (FAILED 또는 CANCELLED)
   */
  fun isFailed(): Boolean = status in listOf(
    LoopJobIterationStatus.FAILED,
    LoopJobIterationStatus.CANCELLED,
  )

  override fun toString(): String {
    return "LoopJobIteration(jobId='$jobId', seq=$seq, status=$status, duration=${getDurationMs()}ms)"
  }
}

/**
 * Loop Job Iteration 실행 상태
 */
enum class LoopJobIterationStatus {
  /** 실행 중 */
  RUNNING,

  /** 성공적으로 완료 */
  SUCCESS,

  /** 실패 (에러 발생) */
  FAILED,

  /** 사용자에 의해 취소됨 */
  CANCELLED,
}
