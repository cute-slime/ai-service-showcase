package com.jongmin.ai.dte.component.handler

import com.jongmin.ai.core.backoffice.dto.request.ExecuteModel
import com.jongmin.ai.core.platform.component.adaptive.SimpleAgent
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * BO AI Assistant Playground 직접 추론 실행 핸들러
 *
 * FE -> ai-service(작업 등록) -> DTE Worker(본 핸들러) -> backbone SSE
 * 흐름에서 실제 추론 실행을 담당한다.
 */
@Component
class BoAiAssistantExecuteTaskHandler(
  private val objectMapper: ObjectMapper,
  private val simpleAgent: SimpleAgent,
  private val eventBridge: DistributedJobEventBridge,
) : TaskHandler {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val TASK_TYPE = "BO_AI_ASSISTANT_EXECUTE"
  }

  override val type: String = TASK_TYPE

  override fun execute(job: DistributedJob) {
    val requesterId = job.requesterId ?: throw BadRequestException("requesterId가 필요합니다.")
    val request = objectMapper.convertValue(job.payload, ExecuteModel::class.java)
    val emitter = EventBridgeFluxSink(
      jobId = job.id,
      jobType = TASK_TYPE,
      eventBridge = eventBridge,
      correlationId = job.correlationId ?: request.canvasId
    )

    kLogger.info {
      "BO Assistant Direct Inference 실행 - jobId: ${job.id}, requesterId: $requesterId, canvasId: ${request.canvasId}, aiAssistantId: ${request.aiAssistantId}"
    }

    try {
      simpleAgent.executeDirectInference(
        emitter = emitter,
        accountId = requesterId,
        dto = request
      )

      if (!emitter.isCompleted()) {
        emitter.complete()
      }
    } catch (e: Exception) {
      if (!emitter.isCompleted()) {
        emitter.error(e)
      }
      throw e
    }
  }
}
