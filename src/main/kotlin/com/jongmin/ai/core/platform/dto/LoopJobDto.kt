package com.jongmin.ai.core.platform.dto

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.entity.*
import java.time.ZonedDateTime

/**
 * Loop Job 생성 요청 DTO
 *
 * @author Claude Code
 * @since 2026.01.03
 */
data class CreateLoopJobRequest(
  /** 실행할 워크플로우 ID (AiAgent ID) */
  val workflowId: Long,

  /** 캔버스 ID (선택) */
  val canvasId: String? = null,

  /** 최대 반복 횟수 (-1: 무한) */
  val maxCount: Int,

  /** 반복 간 딜레이 (ms, 기본: 0) */
  val delayMs: Long? = null,

  /** 에러 발생 시 동작 (기본: STOP) */
  val onError: LoopJobErrorHandling? = null,

  /** 최대 재시도 횟수 (onError=RETRY인 경우, 기본: 3) */
  val maxRetries: Int? = null,

  /** 좀비 복구 시 자동 재시작 여부 (false면 PAUSED 상태로 전환, 기본: false) */
  val autoRestart: Boolean? = null,
)

/**
 * Loop Job 재시작 요청 DTO
 *
 * 종료된 Job(COMPLETED, CANCELLED, ERROR)을 초기화하고 다시 실행한다.
 * 모든 필드는 선택적이며, null인 경우 기존 Job의 설정을 유지한다.
 *
 * @author Claude Code
 * @since 2026.01.05
 */
data class RestartLoopJobRequest(
  /** 최대 반복 횟수 (null이면 기존 값 유지, -1: 무한) */
  val maxCount: Int? = null,

  /** 반복 간 딜레이 (ms, null이면 기존 값 유지) */
  val delayMs: Long? = null,

  /** 에러 발생 시 동작 (null이면 기존 값 유지) */
  val onError: LoopJobErrorHandling? = null,

  /** 최대 재시도 횟수 (null이면 기존 값 유지) */
  val maxRetries: Int? = null,

  /** 좀비 복구 시 자동 재시작 여부 (null이면 기존 값 유지) */
  val autoRestart: Boolean? = null,
) {
  /**
   * 옵션이 하나라도 설정되었는지 확인
   */
  fun hasAnyOption(): Boolean {
    return maxCount != null || delayMs != null || onError != null || maxRetries != null || autoRestart != null
  }
}

/**
 * Loop Job 응답 DTO
 *
 * @author Claude Code
 * @since 2026.01.03
 */
data class LoopJobResponse(
  val id: String,
  val workflowId: Long,
  val canvasId: String?,
  val accountId: Long,
  val state: LoopJobState,
  val currentCount: Int,
  val maxCount: Int,
  val delayMs: Long,
  val onError: LoopJobErrorHandling,
  val maxRetries: Int,
  val autoRestart: Boolean,
  val createdAt: ZonedDateTime,
  val updatedAt: ZonedDateTime,
  val lastHeartbeat: ZonedDateTime?,
  val completedAt: ZonedDateTime?,
  val errorMessage: String?,
  val instanceId: String?,
  val cancelReason: String?,
  val successCount: Int,
  val failureCount: Int,

  /** 진행률 (0.0 ~ 1.0, 무한 반복 시 null) */
  val progress: Double?,

  /** 마지막 체크포인트 정보 */
  val lastCheckpoint: CheckpointInfo?,

  /** 반복 실행 히스토리 (단건 조회 시에만 포함, 목록 조회 시 null) */
  val iterations: List<LoopJobIterationItem>? = null,
) {
  companion object {
    /**
     * 목록 조회용 (iterations 미포함)
     */
    fun from(job: LoopJob, checkpoint: LoopJobCheckpoint? = null): LoopJobResponse {
      return LoopJobResponse(
        id = job.id,
        workflowId = job.workflowId,
        canvasId = job.canvasId,
        accountId = job.accountId,
        state = job.state,
        currentCount = job.currentCount,
        maxCount = job.maxCount,
        delayMs = job.delayMs,
        onError = job.onError,
        maxRetries = job.maxRetries,
        autoRestart = job.autoRestart,
        createdAt = job.createdAt,
        updatedAt = job.updatedAt,
        lastHeartbeat = job.lastHeartbeat,
        completedAt = job.completedAt,
        errorMessage = job.errorMessage,
        instanceId = job.instanceId,
        cancelReason = job.cancelReason,
        successCount = job.successCount,
        failureCount = job.failureCount,
        progress = job.getProgress(),
        lastCheckpoint = checkpoint?.let { CheckpointInfo.from(it) },
        iterations = null,
      )
    }

    /**
     * 단건 조회용 (iterations 포함)
     *
     * @param job Job 엔티티
     * @param checkpoint 마지막 체크포인트
     * @param iterations 반복 실행 히스토리 (최근 N개)
     */
    fun fromWithIterations(
      job: LoopJob,
      checkpoint: LoopJobCheckpoint? = null,
      iterations: List<LoopJobIteration> = emptyList(),
    ): LoopJobResponse {
      return LoopJobResponse(
        id = job.id,
        workflowId = job.workflowId,
        canvasId = job.canvasId,
        accountId = job.accountId,
        state = job.state,
        currentCount = job.currentCount,
        maxCount = job.maxCount,
        delayMs = job.delayMs,
        onError = job.onError,
        maxRetries = job.maxRetries,
        autoRestart = job.autoRestart,
        createdAt = job.createdAt,
        updatedAt = job.updatedAt,
        lastHeartbeat = job.lastHeartbeat,
        completedAt = job.completedAt,
        errorMessage = job.errorMessage,
        instanceId = job.instanceId,
        cancelReason = job.cancelReason,
        successCount = job.successCount,
        failureCount = job.failureCount,
        progress = job.getProgress(),
        lastCheckpoint = checkpoint?.let { CheckpointInfo.from(it) },
        iterations = iterations.map { LoopJobIterationItem.from(it) },
      )
    }
  }
}

/**
 * Loop Job Iteration 간략 정보
 *
 * 성능을 위해 필요한 필드만 포함.
 * 전체 iteration 목록 조회 시 사용.
 *
 * @author Claude Code
 * @since 2026.01.05
 */
data class LoopJobIterationItem(
  /** 반복 순번 (1부터 시작) */
  val seq: Int,

  /** 실행 상태 */
  val status: LoopJobIterationStatus,

  /** 시작 시간 */
  val startedAt: ZonedDateTime,

  /** 종료 시간 (실행 중이면 null) */
  val endedAt: ZonedDateTime?,

  /** 실행 시간 (ms) */
  val durationMs: Long,

  /** 내부 오류 횟수 */
  val internalErrorCount: Int,

  /** 에러 메시지 (실패 시) */
  val errorMessage: String?,
) {
  companion object {
    fun from(iteration: LoopJobIteration): LoopJobIterationItem {
      return LoopJobIterationItem(
        seq = iteration.seq,
        status = iteration.status,
        startedAt = iteration.startedAt,
        endedAt = iteration.endedAt,
        durationMs = iteration.getDurationMs(),
        internalErrorCount = iteration.internalErrorCount,
        errorMessage = iteration.errorMessage,
      )
    }
  }
}

/**
 * 체크포인트 정보
 */
data class CheckpointInfo(
  val iterationCount: Int,
  val currentNodeId: String,
  val nodeState: LoopJobCheckpointState,
  val timestamp: ZonedDateTime,
) {
  companion object {
    fun from(checkpoint: LoopJobCheckpoint): CheckpointInfo {
      return CheckpointInfo(
        iterationCount = checkpoint.iterationCount,
        currentNodeId = checkpoint.currentNodeId,
        nodeState = checkpoint.nodeState,
        timestamp = checkpoint.timestamp,
      )
    }
  }
}

/**
 * Loop Job 목록 응답 DTO (간략 버전)
 */
data class LoopJobSummary(
  val id: String,
  val workflowId: Long,
  val state: LoopJobState,
  val currentCount: Int,
  val maxCount: Int,
  val progress: Double?,
  val createdAt: ZonedDateTime,
  val lastHeartbeat: ZonedDateTime?,
) {
  companion object {
    fun from(job: LoopJob): LoopJobSummary {
      return LoopJobSummary(
        id = job.id,
        workflowId = job.workflowId,
        state = job.state,
        currentCount = job.currentCount,
        maxCount = job.maxCount,
        progress = job.getProgress(),
        createdAt = job.createdAt,
        lastHeartbeat = job.lastHeartbeat,
      )
    }
  }
}

/**
 * Job 취소 요청 DTO
 */
data class CancelLoopJobRequest(
  /** 취소 사유 (선택) */
  val reason: String? = null,
)

/**
 * 좀비 Job 정보
 */
data class ZombieJobInfo(
  val id: String,
  val workflowId: Long,
  val state: LoopJobState,
  val lastHeartbeat: ZonedDateTime?,
  val secondsSinceHeartbeat: Long,
  val instanceId: String?,
) {
  companion object {
    fun from(job: LoopJob, currentTime: ZonedDateTime = now()): ZombieJobInfo {
      val secondsSince = job.lastHeartbeat?.let {
        java.time.Duration.between(it, currentTime).seconds
      } ?: Long.MAX_VALUE

      return ZombieJobInfo(
        id = job.id,
        workflowId = job.workflowId,
        state = job.state,
        lastHeartbeat = job.lastHeartbeat,
        secondsSinceHeartbeat = secondsSince,
        instanceId = job.instanceId,
      )
    }
  }
}

/**
 * 좀비 Job 목록 응답
 */
data class ZombieJobListResponse(
  val zombieJobs: List<ZombieJobInfo>,
  val count: Int,
)

/**
 * 좀비 Job 감지 결과 응답
 */
data class ZombieDetectionResponse(
  val detected: Int,
  val recovered: Int,
  val timestamp: ZonedDateTime = now(),
)

/**
 * 일반 작업 결과 응답
 */
data class LoopJobActionResponse(
  val success: Boolean,
  val message: String,
  val checkpoint: CheckpointInfo? = null,
)
