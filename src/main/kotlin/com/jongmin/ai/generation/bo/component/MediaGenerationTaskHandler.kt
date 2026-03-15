package com.jongmin.ai.generation.bo.component

import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.dto.GenerationResult
import com.jongmin.ai.generation.dto.ProgressEvent
import com.jongmin.ai.generation.event.JobEventPublisher
import com.jongmin.ai.generation.provider.AssetGenerationProviderRegistry
import com.jongmin.jspring.core.util.convert
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * MEDIA_GENERATION 타입 DTE 태스크 핸들러
 *
 * DTE(Distributed Task Executor)에서 비동기로 실행되며,
 * 프로바이더를 통해 미디어(이미지, 영상, BGM)를 생성한다.
 *
 * ### 처리 흐름:
 * ```
 * DTE Worker → execute(job)
 *   1. payload → GenerationContext 변환
 *   2. providerRegistry.getProvider(providerCode)
 *   3. provider.generate(context) 호출 (blocking)
 *      → AbstractAssetGenerationProvider가 emitProgress() 자동 호출
 *      → JobEventPublisher → Redis Pub/Sub → backbone-service SSE
 *   4. 성공/실패 시 rateLimiter.release(accountId)
 * ```
 *
 * ### 이벤트 발행:
 * AbstractAssetGenerationProvider.generate()가 이벤트 발행을 자동 처리하므로
 * 별도 EventPublisher 호출이 불필요하다.
 */
@Component
class MediaGenerationTaskHandler(
  private val providerRegistry: AssetGenerationProviderRegistry,
  private val rateLimiter: MediaGenerationRateLimiter,
  private val jobEventPublisher: JobEventPublisher,
  private val objectMapper: ObjectMapper,
) : TaskHandler {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val TASK_TYPE = "MEDIA_GENERATION"
  }

  override val type: String = TASK_TYPE

  /**
   * 미디어 생성 작업을 실행한다.
   *
   * 가상 스레드에서 비동기로 호출되며,
   * 프로바이더를 통해 미디어를 생성하고 결과를 처리한다.
   *
   * @param job 실행할 DTE Job
   */
  override fun execute(job: DistributedJob) {
    kLogger.info { "[MediaGeneration] 작업 실행 시작 - jobId: ${job.id}" }

    // 1. payload → GenerationContext 변환
    val context = objectMapper.convert(job.payload, GenerationContext::class.java)
    val accountId = job.requesterId ?: context.requesterId ?: 0L

    try {
      // 2. 프로바이더 조회
      val provider = providerRegistry.getProvider(context.providerCode)

      kLogger.info {
        "[MediaGeneration] 프로바이더 실행 - jobId: ${job.id}, " +
            "provider: ${context.providerCode}, mediaType: ${context.mediaType}"
      }

      // 3. 생성 실행 (blocking)
      // AbstractAssetGenerationProvider.generate()가 내부에서:
      // - 초기화/진행/완료 이벤트를 자동 발행
      // - JobEventPublisher → Redis Pub/Sub → backbone SSE
      val result = provider.generate(context)

      if (result.success) {
        kLogger.info {
          "[MediaGeneration] 생성 성공 - jobId: ${job.id}, " +
              "outputUrl: ${result.outputUrl}, duration: ${result.durationMs}ms"
        }

        // FE 완료 처리 호환을 위해 JOB_COMPLETED(DATA) → COMPLETE 순서로 명시 발행한다.
        publishCompletionEvents(job, context, result)
      } else {
        kLogger.warn {
          "[MediaGeneration] 생성 실패 - jobId: ${job.id}, " +
              "errorCode: ${result.errorCode}, error: ${result.errorMessage}"
        }
      }
    } catch (e: Exception) {
      kLogger.error(e) {
        "[MediaGeneration] 작업 실행 예외 - jobId: ${job.id}, provider: ${context.providerCode}"
      }
      // SSE 에러 이벤트 발행 (FE가 에러 상태를 인지할 수 있도록)
      try {
        jobEventPublisher.emitError(
          jobId = job.id,
          type = TASK_TYPE,
          errorMessage = e.message ?: "미디어 생성 중 예외 발생",
          correlationId = job.correlationId,
        )
      } catch (publishError: Exception) {
        kLogger.warn(publishError) { "[MediaGeneration] 에러 이벤트 발행 실패 - jobId: ${job.id}" }
      }
      throw e
    } finally {
      // 4. 슬롯 반환 (성공/실패 무관하게 반드시 실행)
      // 과거 이력/비정상 케이스 방어를 위해 job.id와 context.jobId를 모두 해제한다.
      val releaseJobIds = linkedSetOf(job.id, context.jobId)
      releaseJobIds.forEach { releaseJobId ->
        rateLimiter.release(accountId, releaseJobId)
        kLogger.debug {
          "[MediaGeneration] 슬롯 반환 완료 - accountId: $accountId, jobId: $releaseJobId"
        }
      }

      if (job.id != context.jobId) {
        kLogger.warn {
          "[MediaGeneration] Job ID 불일치 감지 - dteJobId: ${job.id}, contextJobId: ${context.jobId}"
        }
      }
    }
  }

  private fun publishCompletionEvents(
    job: DistributedJob,
    context: GenerationContext,
    result: GenerationResult,
  ) {
    val completedEvent = ProgressEvent.jobCompleted(
      jobId = context.jobId,
      providerCode = context.providerCode,
      totalItems = context.totalItems,
      successCount = 1,
      failedCount = 0,
      totalDurationMs = result.durationMs,
    ).let { event ->
      val mergedMetadata = buildMap<String, Any> {
        event.metadata?.let { putAll(it) }
        result.outputUrl?.let { put("outputUrl", it) }
        if (result.metadata.isNotEmpty()) {
          putAll(result.metadata)
        }
      }

      if (mergedMetadata.isEmpty()) event else event.copy(metadata = mergedMetadata)
    }

    val completedEventJson = objectMapper.writeValueAsString(completedEvent)
    jobEventPublisher.emitData(
      jobId = context.jobId,
      type = completedEvent.type,
      data = completedEventJson,
      correlationId = job.correlationId,
    )
    jobEventPublisher.emitComplete(
      jobId = context.jobId,
      type = completedEvent.type,
      correlationId = job.correlationId,
    )

    kLogger.info { "[MediaGeneration] 완료 이벤트 발행 - jobId: ${context.jobId}, phase: JOB_COMPLETED" }
  }
}
