package com.jongmin.ai.core.platform.component.loop

import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Loop Job 이벤트 브릿지
 *
 * Loop Job 전용 이벤트를 `DistributedJobEventBridge` 표준 채널로 발행하는 어댑터다.
 *
 * @author Claude Code
 * @since 2026.01.06
 */
@Component
class LoopJobEventBridge(
  private val eventBridge: DistributedJobEventBridge,
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val LOOP_JOB_EVENT_TYPE = "LOOP_JOB"
    private const val SCHEMA_VERSION = 1
    private const val DOMAIN = "loop-job"
  }

  /**
   * 상태 변경 이벤트 발행
   */
  fun emitStateChanged(jobId: String) {
    emitData(jobId, "STATE_CHANGED")
    kLogger.debug { "Loop Job 상태 변경 이벤트 발행 - jobId: $jobId" }
  }

  /**
   * 완료 이벤트 발행
   */
  fun emitComplete(jobId: String, message: String = "Job completed") {
    emitData(jobId, "COMPLETE", message)
    eventBridge.emitComplete(jobId, LOOP_JOB_EVENT_TYPE)
    kLogger.info { "Loop Job 완료 이벤트 발행 - jobId: $jobId, message: $message" }
  }

  /**
   * 에러 이벤트 발행
   */
  fun emitError(jobId: String, message: String) {
    emitData(jobId, "ERROR", message)
    eventBridge.emitError(jobId, LOOP_JOB_EVENT_TYPE, message)
    kLogger.warn { "Loop Job 에러 이벤트 발행 - jobId: $jobId, message: $message" }
  }

  private fun emitData(jobId: String, eventName: String, message: String? = null) {
    val payload = linkedMapOf<String, Any?>(
      "schemaVersion" to SCHEMA_VERSION,
      "domain" to DOMAIN,
      "eventName" to eventName,
      "payload" to linkedMapOf<String, Any?>(
        "jobId" to jobId,
        "message" to message
      )
    )

    runCatching { objectMapper.writeValueAsString(payload) }
      .onSuccess { serialized ->
        eventBridge.emitData(jobId, LOOP_JOB_EVENT_TYPE, serialized)
      }
      .onFailure { e ->
        kLogger.error(e) { "Loop Job DATA 이벤트 직렬화 실패 - jobId: $jobId, eventName: $eventName" }
      }
  }

}
