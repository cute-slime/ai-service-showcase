package com.jongmin.ai.multiagent.skill.repository

import com.jongmin.ai.multiagent.skill.entity.ScriptExecutionEntity
import com.jongmin.ai.multiagent.skill.model.ExecutionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

/**
 * 스크립트 실행 기록 Repository
 */
@Repository
interface ScriptExecutionRepository : JpaRepository<ScriptExecutionEntity, Long> {

  /**
   * 계정별 실행 기록 조회 (페이징)
   */
  fun findByAccountIdAndStatusOrderByCreatedAtDesc(
    accountId: Long,
    status: Int,
    pageable: Pageable,
  ): Page<ScriptExecutionEntity>

  /**
   * 스킬별 실행 기록 조회 (페이징)
   */
  fun findBySkillIdAndStatusOrderByCreatedAtDesc(
    skillId: Long,
    status: Int,
    pageable: Pageable,
  ): Page<ScriptExecutionEntity>

  /**
   * 특정 실행 상태의 기록 조회
   */
  fun findByAccountIdAndExecutionStatusAndStatus(
    accountId: Long,
    executionStatus: ExecutionStatus,
    status: Int,
  ): List<ScriptExecutionEntity>

  /**
   * 특정 기간 내 실행 기록 조회
   */
  @Query("""
    SELECT e FROM ScriptExecutionEntity e
    WHERE e.accountId = :accountId
      AND e.createdAt >= :startDate
      AND e.createdAt < :endDate
      AND e.status = :status
    ORDER BY e.createdAt DESC
  """)
  fun findByAccountIdAndCreatedAtBetween(
    @Param("accountId") accountId: Long,
    @Param("startDate") startDate: ZonedDateTime,
    @Param("endDate") endDate: ZonedDateTime,
    @Param("status") status: Int,
    pageable: Pageable,
  ): Page<ScriptExecutionEntity>

  /**
   * 실행 통계 조회 (스킬별)
   */
  @Query("""
    SELECT
      e.skillId as skillId,
      COUNT(e) as totalCount,
      SUM(CASE WHEN e.executionStatus = 'COMPLETED' THEN 1 ELSE 0 END) as successCount,
      SUM(CASE WHEN e.executionStatus = 'FAILED' THEN 1 ELSE 0 END) as failedCount,
      SUM(CASE WHEN e.executionStatus = 'TIMEOUT' THEN 1 ELSE 0 END) as timeoutCount,
      AVG(e.durationMs) as avgDurationMs
    FROM ScriptExecutionEntity e
    WHERE e.accountId = :accountId
      AND e.status = :status
      AND e.createdAt >= :startDate
    GROUP BY e.skillId
  """)
  fun getExecutionStatsBySkill(
    @Param("accountId") accountId: Long,
    @Param("startDate") startDate: ZonedDateTime,
    @Param("status") status: Int,
  ): List<Map<String, Any>>

  /**
   * 진행 중인 실행 개수 조회 (동시 실행 제한용)
   */
  @Query("""
    SELECT COUNT(e) FROM ScriptExecutionEntity e
    WHERE e.accountId = :accountId
      AND e.executionStatus IN ('PENDING', 'RUNNING')
      AND e.status = :status
  """)
  fun countActiveExecutions(
    @Param("accountId") accountId: Long,
    @Param("status") status: Int,
  ): Long

  /**
   * 오래된 PENDING/RUNNING 상태 정리 (좀비 프로세스 처리)
   */
  @Modifying
  @Query("""
    UPDATE ScriptExecutionEntity e
    SET e.executionStatus = 'TIMEOUT',
        e.errorMessage = 'Marked as timeout due to stale state'
    WHERE e.executionStatus IN ('PENDING', 'RUNNING')
      AND e.createdAt < :cutoffTime
  """)
  fun markStaleExecutionsAsTimeout(
    @Param("cutoffTime") cutoffTime: ZonedDateTime,
  ): Int
}
