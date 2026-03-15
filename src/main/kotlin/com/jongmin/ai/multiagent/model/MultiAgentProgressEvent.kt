package com.jongmin.ai.multiagent.model

import java.time.Instant

/**
 * 멀티 에이전트 진행 상황 이벤트
 * SSE를 통한 실시간 진행 상황 스트리밍용
 */
data class MultiAgentProgressEvent(
  val type: EventType,
  val timestamp: Instant = Instant.now(),
  val data: Map<String, Any?>,
) {
  /**
   * 진행 상황 이벤트 타입
   */
  enum class EventType {
    INITIALIZED,              // 워크플로우 초기화
    AGENT_STARTED,            // 에이전트 실행 시작
    AGENT_COMPLETED,          // 에이전트 실행 완료
    RETRY_STARTED,            // 재시도 시작
    HUMAN_REVIEW_REQUESTED,   // Human Review 요청
    HUMAN_REVIEW_COMPLETED,   // Human Review 완료
    COMPLETED,                // 워크플로우 완료
    FAILED                    // 워크플로우 실패
  }

  companion object {
    /**
     * 초기화 이벤트
     */
    fun initialized(workflowId: Long, totalAgents: Int) = MultiAgentProgressEvent(
      type = EventType.INITIALIZED,
      data = mapOf("workflowId" to workflowId, "totalAgents" to totalAgents)
    )

    /**
     * 에이전트 시작 이벤트
     */
    fun agentStarted(agentId: String, agentName: String) = MultiAgentProgressEvent(
      type = EventType.AGENT_STARTED,
      data = mapOf("agentId" to agentId, "agentName" to agentName)
    )

    /**
     * 에이전트 완료 이벤트
     */
    fun agentCompleted(agentId: String, score: Double?, progress: Double) = MultiAgentProgressEvent(
      type = EventType.AGENT_COMPLETED,
      data = mapOf("agentId" to agentId, "score" to score, "progress" to progress)
    )

    /**
     * 재시도 시작 이벤트
     */
    fun retryStarted(agentId: String, attemptNumber: Int, reason: String) = MultiAgentProgressEvent(
      type = EventType.RETRY_STARTED,
      data = mapOf("agentId" to agentId, "attemptNumber" to attemptNumber, "reason" to reason)
    )

    /**
     * Human Review 요청 이벤트
     */
    fun humanReviewRequested(reviewRequestId: String, agentId: String, reason: String) = MultiAgentProgressEvent(
      type = EventType.HUMAN_REVIEW_REQUESTED,
      data = mapOf("reviewRequestId" to reviewRequestId, "agentId" to agentId, "reason" to reason)
    )

    /**
     * Human Review 완료 이벤트
     */
    fun humanReviewCompleted(reviewRequestId: String, action: String) = MultiAgentProgressEvent(
      type = EventType.HUMAN_REVIEW_COMPLETED,
      data = mapOf("reviewRequestId" to reviewRequestId, "action" to action)
    )

    /**
     * 완료 이벤트
     */
    fun completed(output: Any) = MultiAgentProgressEvent(
      type = EventType.COMPLETED,
      data = mapOf("output" to output)
    )

    /**
     * 실패 이벤트
     */
    fun failed(error: String) = MultiAgentProgressEvent(
      type = EventType.FAILED,
      data = mapOf("error" to error)
    )
  }
}
