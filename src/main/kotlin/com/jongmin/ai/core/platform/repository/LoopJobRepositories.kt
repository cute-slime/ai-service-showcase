package com.jongmin.ai.core.platform.repository

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * Loop Job JPA Repository
 *
 * Loop Workflow Job 엔티티에 대한 CRUD 및 커스텀 쿼리 제공.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Repository
interface LoopJobRepository : JpaRepository<LoopJob, String>, QuerydslPredicateExecutor<LoopJob> {

  /**
   * 워크플로우 ID와 상태로 Job 조회
   */
  fun findByWorkflowIdAndState(workflowId: Long, state: LoopJobState): LoopJob?

  /**
   * 워크플로우 ID로 모든 Job 조회
   */
  fun findByWorkflowId(workflowId: Long): List<LoopJob>

  /**
   * 계정 ID로 모든 Job 조회
   */
  fun findByAccountId(accountId: Long): List<LoopJob>

  /**
   * 상태로 Job 목록 조회 (전체)
   *
   * 서버 시작 시 복구 등 전체 목록이 필요한 경우 사용.
   */
  fun findByState(state: LoopJobState): List<LoopJob>

  /**
   * 상태로 Job 목록 조회 (페이징)
   */
  fun findByState(state: LoopJobState, pageable: Pageable): Page<LoopJob>

  /**
   * 여러 상태로 Job 목록 조회 (페이징)
   */
  fun findByStateIn(states: List<LoopJobState>, pageable: Pageable): Page<LoopJob>

  /**
   * 좀비 Job 조회 - RUNNING 상태이면서 Heartbeat가 오래된 Job
   *
   * @param state Job 상태 (RUNNING)
   * @param cutoffTime Heartbeat 기준 시간
   * @return 좀비 Job 목록
   */
  fun findByStateAndLastHeartbeatBefore(
    state: LoopJobState,
    cutoffTime: ZonedDateTime
  ): List<LoopJob>

  /**
   * 인스턴스 ID로 실행 중인 Job 조회
   */
  fun findByInstanceIdAndState(instanceId: String, state: LoopJobState): List<LoopJob>

  /**
   * Job 상태 업데이트 (Bulk Update)
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.state = :state, j.updatedAt = :now WHERE j.id = :id")
  fun updateState(
    @Param("id") id: String,
    @Param("state") state: LoopJobState,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * Job 상태와 에러 메시지 업데이트
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.state = :state, j.errorMessage = :errorMessage, j.updatedAt = :now WHERE j.id = :id")
  fun updateStateWithError(
    @Param("id") id: String,
    @Param("state") state: LoopJobState,
    @Param("errorMessage") errorMessage: String?,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * Heartbeat 업데이트
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.lastHeartbeat = :now, j.updatedAt = :now WHERE j.id = :id")
  fun updateHeartbeat(
    @Param("id") id: String,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * 반복 카운터 증가
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.currentCount = j.currentCount + 1, j.updatedAt = :now WHERE j.id = :id")
  fun incrementCount(
    @Param("id") id: String,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * 성공 카운터 증가
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.successCount = j.successCount + 1, j.updatedAt = :now WHERE j.id = :id")
  fun incrementSuccessCount(
    @Param("id") id: String,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * 실패 카운터 증가
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.failureCount = j.failureCount + 1, j.updatedAt = :now WHERE j.id = :id")
  fun incrementFailureCount(
    @Param("id") id: String,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * 취소 플래그 설정
   */
  @Modifying
  @Transactional
  @Query("UPDATE LoopJob j SET j.state = 'CANCELLED', j.cancelReason = :reason, j.completedAt = :now, j.updatedAt = :now WHERE j.id = :id AND j.state IN ('PENDING', 'RUNNING', 'PAUSED')")
  fun cancel(
    @Param("id") id: String,
    @Param("reason") reason: String?,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * 완료된 오래된 Job 삭제 (정리용)
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM LoopJob j WHERE j.state IN ('COMPLETED', 'CANCELLED', 'ERROR') AND j.completedAt < :cutoffTime")
  fun deleteOldCompletedJobs(@Param("cutoffTime") cutoffTime: ZonedDateTime): Int

  /**
   * Job 재시작 (상태 및 카운터 초기화)
   *
   * 종료된 Job을 처음부터 다시 실행하기 위해 상태와 카운터를 초기화한다.
   * updatable = false 필드는 이 쿼리로 변경하지 않는다.
   *
   * @param id Job ID
   * @param state 새 상태 (PENDING)
   * @param instanceId 실행 인스턴스 ID
   * @param now 현재 시간
   */
  @Modifying
  @Transactional
  @Query(
    """
        UPDATE LoopJob j SET
            j.state = :state,
            j.currentCount = 0,
            j.successCount = 0,
            j.failureCount = 0,
            j.errorMessage = null,
            j.cancelReason = null,
            j.completedAt = null,
            j.lastHeartbeat = null,
            j.instanceId = :instanceId,
            j.updatedAt = :now
        WHERE j.id = :id
    """
  )
  fun resetForRestart(
    @Param("id") id: String,
    @Param("state") state: LoopJobState,
    @Param("instanceId") instanceId: String,
    @Param("now") now: ZonedDateTime = now()
  ): Int

  /**
   * Job 설정 업데이트 (재시작 시 옵션 변경용)
   *
   * updatable = false 필드를 네이티브 쿼리로 직접 업데이트한다.
   * null인 파라미터는 기존 값을 유지한다.
   *
   * @param id Job ID
   * @param maxCount 최대 반복 횟수 (null이면 변경 안 함)
   * @param delayMs 딜레이 (null이면 변경 안 함)
   * @param onError 에러 처리 정책 (null이면 변경 안 함)
   * @param maxRetries 최대 재시도 횟수 (null이면 변경 안 함)
   * @param autoRestart 자동 재시작 여부 (null이면 변경 안 함)
   */
  @Modifying
  @Transactional
  @Query(
    value = """
        UPDATE loop_job SET
            max_count = COALESCE(:maxCount, max_count),
            delay_ms = COALESCE(:delayMs, delay_ms),
            on_error = COALESCE(:onError, on_error),
            max_retries = COALESCE(:maxRetries, max_retries),
            auto_restart = COALESCE(:autoRestart, auto_restart)
        WHERE id = :id
    """, nativeQuery = true
  )
  fun updateSettings(
    @Param("id") id: String,
    @Param("maxCount") maxCount: Int?,
    @Param("delayMs") delayMs: Long?,
    @Param("onError") onError: String?,
    @Param("maxRetries") maxRetries: Int?,
    @Param("autoRestart") autoRestart: Boolean?,
  ): Int
}

/**
 * Loop Job Checkpoint JPA Repository
 *
 * Loop Job 체크포인트 엔티티에 대한 CRUD 제공.
 * Job당 최신 1개만 유지하는 것이 권장됨.
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Repository
interface LoopJobCheckpointRepository : JpaRepository<LoopJobCheckpoint, String>, QuerydslPredicateExecutor<LoopJobCheckpoint> {

  /**
   * Job ID로 최신 체크포인트 조회
   */
  fun findFirstByJobIdOrderByTimestampDesc(jobId: String): LoopJobCheckpoint?

  /**
   * Job ID로 모든 체크포인트 조회 (디버깅용)
   */
  fun findByJobId(jobId: String): List<LoopJobCheckpoint>

  /**
   * Job ID로 체크포인트 삭제 (새 체크포인트 저장 전 호출)
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM LoopJobCheckpoint c WHERE c.jobId = :jobId")
  fun deleteByJobId(@Param("jobId") jobId: String): Int

  /**
   * 오래된 체크포인트 삭제 (정리용)
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM LoopJobCheckpoint c WHERE c.timestamp < :cutoffTime")
  fun deleteOldCheckpoints(@Param("cutoffTime") cutoffTime: ZonedDateTime): Int
}

/**
 * Loop Job Iteration JPA Repository
 *
 * Loop Job의 각 반복(iteration) 실행 결과를 저장하고 조회한다.
 * 실행 이력 추적, 디버깅, 통계 분석에 사용.
 *
 * @author Claude Code
 * @since 2026.01.05
 */
@Repository
interface LoopJobIterationRepository : JpaRepository<LoopJobIteration, String>, QuerydslPredicateExecutor<LoopJobIteration> {

  /**
   * Job ID로 모든 반복 결과 조회 (seq 오름차순)
   */
  fun findByJobIdOrderBySeqAsc(jobId: String): List<LoopJobIteration>

  /**
   * Job ID로 모든 반복 결과 조회 (페이징, seq 내림차순 - 최신순)
   */
  fun findByJobIdOrderBySeqDesc(jobId: String, pageable: Pageable): Page<LoopJobIteration>

  /**
   * Job ID와 seq로 특정 반복 조회
   */
  fun findByJobIdAndSeq(jobId: String, seq: Int): LoopJobIteration?

  /**
   * Job ID의 마지막 반복 조회
   */
  fun findFirstByJobIdOrderBySeqDesc(jobId: String): LoopJobIteration?

  /**
   * Job ID와 상태로 반복 결과 조회
   */
  fun findByJobIdAndStatus(jobId: String, status: LoopJobIterationStatus): List<LoopJobIteration>

  /**
   * Job ID의 실행 중인 반복 조회
   */
  fun findFirstByJobIdAndStatus(jobId: String, status: LoopJobIterationStatus): LoopJobIteration?

  /**
   * Job ID의 성공한 반복 수 조회
   */
  @Query("SELECT COUNT(i) FROM LoopJobIteration i WHERE i.jobId = :jobId AND i.status = 'SUCCESS'")
  fun countSuccessByJobId(@Param("jobId") jobId: String): Long

  /**
   * Job ID의 실패한 반복 수 조회
   */
  @Query("SELECT COUNT(i) FROM LoopJobIteration i WHERE i.jobId = :jobId AND i.status = 'FAILED'")
  fun countFailedByJobId(@Param("jobId") jobId: String): Long

  /**
   * Job ID의 총 내부 오류 수 합계 조회
   */
  @Query("SELECT COALESCE(SUM(i.internalErrorCount), 0) FROM LoopJobIteration i WHERE i.jobId = :jobId")
  fun sumInternalErrorCountByJobId(@Param("jobId") jobId: String): Long

  /**
   * Job ID로 모든 반복 삭제 (Job 삭제 시 호출)
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM LoopJobIteration i WHERE i.jobId = :jobId")
  fun deleteByJobId(@Param("jobId") jobId: String): Int

  /**
   * 오래된 반복 기록 삭제 (정리용)
   */
  @Modifying
  @Transactional
  @Query("DELETE FROM LoopJobIteration i WHERE i.startedAt < :cutoffTime")
  fun deleteOldIterations(@Param("cutoffTime") cutoffTime: ZonedDateTime): Int

  /**
   * 특정 내부 오류 횟수 이상인 반복 조회 (모니터링용)
   */
  fun findByInternalErrorCountGreaterThanEqual(minErrorCount: Int): List<LoopJobIteration>
}
