package com.jongmin.ai.dte.component

import com.jongmin.ai.core.platform.component.loop.LoopJobEventBridge
import com.jongmin.ai.core.platform.entity.LoopJobState
import com.jongmin.ai.core.platform.repository.LoopJobRepository
import com.jongmin.jspring.core.util.JTimeUtils.now
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Loop Job 상태 전이 전담 서비스
 *
 * 실행기에서 상태 변경 책임을 분리하여 오케스트레이션 로직을 단순화한다.
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class LoopJobStateService(
  private val loopJobRepository: LoopJobRepository,
  private val loopJobEventBridge: LoopJobEventBridge,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * LoopJob 정상 완료 처리
   */
  @Transactional
  fun updateJobCompleted(jobId: String) {
    try {
      loopJobRepository.findById(jobId).ifPresent { job ->
        job.state = LoopJobState.COMPLETED
        job.completedAt = now()
        loopJobRepository.save(job)
        kLogger.debug { "LoopJob 상태 COMPLETED로 변경 - jobId: $jobId" }

        loopJobEventBridge.emitStateChanged(jobId)
        loopJobEventBridge.emitComplete(jobId, "Job completed successfully")
      }
    } catch (e: Exception) {
      kLogger.error(e) { "LoopJob COMPLETED 상태 업데이트 실패 - jobId: $jobId" }
    }
  }

  /**
   * LoopJob 에러 처리
   */
  @Transactional
  fun updateJobError(jobId: String, errorMessage: String?) {
    try {
      loopJobRepository.findById(jobId).ifPresent { job ->
        job.state = LoopJobState.ERROR
        job.errorMessage = errorMessage
        job.completedAt = now()
        loopJobRepository.save(job)
        kLogger.debug { "LoopJob 상태 ERROR로 변경 - jobId: $jobId, error: $errorMessage" }

        loopJobEventBridge.emitStateChanged(jobId)
        loopJobEventBridge.emitError(jobId, errorMessage ?: "Unknown error")
      }
    } catch (e: Exception) {
      kLogger.error(e) { "LoopJob ERROR 상태 업데이트 실패 - jobId: $jobId" }
    }
  }
}
