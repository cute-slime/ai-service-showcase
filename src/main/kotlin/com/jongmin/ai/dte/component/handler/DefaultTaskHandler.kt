package com.jongmin.ai.dte.component.handler

import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 기본 태스크 핸들러
 *
 * 등록되지 않은 태스크 타입에 대한 기본(폴백) 핸들러입니다.
 * 10초 대기 후 완료 처리합니다.
 *
 * 이 핸들러는 TaskHandlerRegistry에서 폴백으로 사용됩니다.
 * 다른 핸들러와 달리 특정 타입에 매핑되지 않습니다.
 */
@Component
class DefaultTaskHandler : TaskHandler {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // 기본 핸들러는 레지스트리에 타입으로 등록되지 않음
    const val TASK_TYPE = "__DEFAULT__"
  }

  override val type: String = TASK_TYPE

  /**
   * 기본 태스크를 실행합니다.
   *
   * 알 수 없는 타입의 작업에 대해 10초 대기 후 완료 처리합니다.
   *
   * @param job 실행할 분산 작업
   */
  override fun execute(job: DistributedJob) {
    kLogger.warn { "알 수 없는 작업 타입, 기본 핸들러 실행 - jobId: ${job.id}, type: ${job.type}" }

    // 샘플: 10초 대기
    kLogger.info { "작업 처리 중 (3초 대기) - jobId: ${job.id}" }
    TimeUnit.SECONDS.sleep(3)
  }
}
