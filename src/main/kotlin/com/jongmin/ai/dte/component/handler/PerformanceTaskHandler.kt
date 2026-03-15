package com.jongmin.ai.dte.component.handler

import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * PERFORMANCE 타입 태스크 핸들러
 *
 * 성능 테스트용 작업을 처리합니다.
 * 실제 운영 환경의 작업 흐름을 시뮬레이션하기 위해
 * 10ms ~ 500ms 범위의 랜덤한 처리 시간을 갖습니다.
 */
@Component
class PerformanceTaskHandler : TaskHandler {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val TASK_TYPE = "PERFORMANCE"

    // 기본 지연 시간 설정
    private const val DEFAULT_MIN_DELAY_MS = 10L
    private const val DEFAULT_MAX_DELAY_MS = 500L
  }

  override val type: String = TASK_TYPE

  /**
   * PERFORMANCE 작업을 실행합니다.
   *
   * payload에서 다음 값을 참조합니다:
   * - minDelayMs: 최소 지연 시간 (기본값: 10)
   * - maxDelayMs: 최대 지연 시간 (기본값: 500)
   *
   * @param job 실행할 작업
   */
  override fun execute(job: DistributedJob) {
    // payload에서 지연 시간 설정 추출
    val minDelayMs = (job.payload["minDelayMs"] as? Number)?.toLong() ?: DEFAULT_MIN_DELAY_MS
    val maxDelayMs = (job.payload["maxDelayMs"] as? Number)?.toLong() ?: DEFAULT_MAX_DELAY_MS

    // 랜덤 지연 시간 계산
    val delayMs = if (maxDelayMs > minDelayMs) {
      minDelayMs + (Math.random() * (maxDelayMs - minDelayMs)).toLong()
    } else {
      minDelayMs
    }

    kLogger.debug { "PERFORMANCE 작업 실행 - jobId: ${job.id}, delay: ${delayMs}ms" }

    // 작업 처리 시뮬레이션
    TimeUnit.MILLISECONDS.sleep(delayMs)
  }
}
