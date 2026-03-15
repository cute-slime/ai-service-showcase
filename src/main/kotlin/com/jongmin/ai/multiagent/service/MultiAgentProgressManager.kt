package com.jongmin.ai.multiagent.service

import com.jongmin.ai.multiagent.model.MultiAgentProgressEvent
import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

private val kLogger = KotlinLogging.logger {}

/**
 * 멀티 에이전트 진행 상황 관리자
 * SSE를 통한 실시간 진행 상황 스트리밍
 */
@Component
class MultiAgentProgressManager(
  private val eventBridge: DistributedJobEventBridge,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    private const val JOB_TYPE = "MULTI_AGENT_WORKFLOW"
    private const val SCHEMA_VERSION = 1
    private const val DOMAIN = "multi-agent"
  }

  // Task별 SSE Sink
  private val progressSinks = ConcurrentHashMap<String, Sinks.Many<MultiAgentProgressEvent>>()

  // Task별 진행 상황 캐시
  private val progressCache = ConcurrentHashMap<String, MultiAgentProgress>()

  /**
   * 진행 상황 초기화
   */
  fun initializeProgress(jobId: String, workflowId: Long, totalAgents: Int) {
    val progress = MultiAgentProgress(
      jobId = jobId,
      workflowId = workflowId,
      totalAgents = totalAgents,
      completedAgents = 0,
      currentAgentId = null,
      status = ProgressStatus.INITIALIZED
    )
    progressCache[jobId] = progress

    // Sink 생성 (멀티캐스트)
    progressSinks[jobId] = Sinks.many().multicast().onBackpressureBuffer()

    // 초기화 이벤트 발송
    emitEvent(jobId, MultiAgentProgressEvent.initialized(workflowId, totalAgents))
  }

  /**
   * 에이전트 실행 시작
   */
  fun onAgentStarted(jobId: String, agentId: String, agentName: String) {
    progressCache[jobId]?.let { progress ->
      progress.currentAgentId = agentId
      progress.status = ProgressStatus.AGENT_RUNNING

      emitEvent(jobId, MultiAgentProgressEvent.agentStarted(agentId, agentName))
    }
  }

  /**
   * 에이전트 실행 완료
   */
  fun onAgentCompleted(jobId: String, agentId: String, score: Double?) {
    progressCache[jobId]?.let { progress ->
      progress.completedAgents++
      progress.currentAgentId = null
      progress.status = ProgressStatus.AGENT_COMPLETED

      emitEvent(jobId, MultiAgentProgressEvent.agentCompleted(
        agentId = agentId,
        score = score,
        progress = progress.completedAgents.toDouble() / progress.totalAgents
      ))
    }
  }

  /**
   * 재시도 시작
   */
  fun onRetryStarted(jobId: String, agentId: String, attemptNumber: Int, reason: String) {
    emitEvent(jobId, MultiAgentProgressEvent.retryStarted(agentId, attemptNumber, reason))
  }

  /**
   * Human Review 요청
   */
  fun onHumanReviewRequested(jobId: String, reviewRequestId: String, agentId: String, reason: String) {
    progressCache[jobId]?.status = ProgressStatus.WAITING_REVIEW

    emitEvent(jobId, MultiAgentProgressEvent.humanReviewRequested(
      reviewRequestId = reviewRequestId,
      agentId = agentId,
      reason = reason
    ))
  }

  /**
   * Human Review 완료
   */
  fun onHumanReviewCompleted(jobId: String, reviewRequestId: String, action: String) {
    progressCache[jobId]?.status = ProgressStatus.AGENT_RUNNING

    emitEvent(jobId, MultiAgentProgressEvent.humanReviewCompleted(reviewRequestId, action))
  }

  /**
   * 워크플로우 완료
   */
  fun onWorkflowCompleted(jobId: String, output: Any) {
    progressCache[jobId]?.status = ProgressStatus.COMPLETED

    emitEvent(jobId, MultiAgentProgressEvent.completed(output))
    eventBridge.emitComplete(jobId, JOB_TYPE)

    // 정리
    cleanup(jobId)
  }

  /**
   * 워크플로우 실패
   */
  fun onWorkflowFailed(jobId: String, error: String) {
    progressCache[jobId]?.status = ProgressStatus.FAILED

    emitEvent(jobId, MultiAgentProgressEvent.failed(error))
    eventBridge.emitError(jobId, JOB_TYPE, error)

    // 정리
    cleanup(jobId)
  }

  /**
   * SSE 스트림 구독
   */
  fun subscribe(jobId: String): Flux<MultiAgentProgressEvent> {
    val sink = progressSinks[jobId]
      ?: return Flux.error(IllegalArgumentException("Task not found: $jobId"))

    return sink.asFlux()
  }

  /**
   * 현재 진행 상황 조회
   */
  fun getProgress(jobId: String): MultiAgentProgress? = progressCache[jobId]

  /**
   * Task가 존재하는지 확인
   */
  fun hasTask(jobId: String): Boolean = progressCache.containsKey(jobId)

  private fun emitEvent(jobId: String, event: MultiAgentProgressEvent) {
    progressSinks[jobId]?.tryEmitNext(event)
    emitDataToEventBridge(jobId, event)
    kLogger.debug { "진행 상황 이벤트 - jobId: $jobId, type: ${event.type}" }
  }

  private fun emitDataToEventBridge(jobId: String, event: MultiAgentProgressEvent) {
    val envelope = mapOf(
      "schemaVersion" to SCHEMA_VERSION,
      "domain" to DOMAIN,
      "eventName" to event.type.name,
      "timestamp" to event.timestamp.toEpochMilli(),
      "payload" to event.data
    )

    runCatching { objectMapper.writeValueAsString(envelope) }
      .onSuccess { serialized -> eventBridge.emitData(jobId, JOB_TYPE, serialized) }
      .onFailure { e ->
        kLogger.error(e) { "진행 이벤트 직렬화 실패 - jobId: $jobId, type: ${event.type}" }
      }
  }

  private fun cleanup(jobId: String) {
    progressSinks[jobId]?.tryEmitComplete()
    progressSinks.remove(jobId)
    // progressCache는 일정 시간 후 정리 (조회용 유지)
    // TODO: TTL 기반 정리 또는 스케줄러 추가
  }
}

/**
 * 진행 상황 상태
 */
enum class ProgressStatus {
  INITIALIZED,
  AGENT_RUNNING,
  AGENT_COMPLETED,
  WAITING_REVIEW,
  COMPLETED,
  FAILED
}

/**
 * 진행 상황 캐시
 */
data class MultiAgentProgress(
  val jobId: String,
  val workflowId: Long,
  val totalAgents: Int,
  var completedAgents: Int,
  var currentAgentId: String?,
  var status: ProgressStatus,
)
