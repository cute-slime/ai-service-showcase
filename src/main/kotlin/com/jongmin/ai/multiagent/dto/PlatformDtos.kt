package com.jongmin.ai.multiagent.dto

/**
 * 워크플로우 실행 요청 DTO
 */
data class ExecuteWorkflowRequest(
  val input: Any,
  val options: Map<String, Any>? = null,
)

/**
 * 워크플로우 실행 응답 DTO
 */
data class ExecuteWorkflowResponse(
  val jobId: String,
  val workflowId: Long,
  val status: String,
)

/**
 * 실행 상태 응답 DTO
 */
data class ExecutionStatusResponse(
  val jobId: String,
  val status: String,
  val progress: Double,
  val currentAgent: String?,
  val completedAgents: Int,
  val totalAgents: Int,
)

/**
 * Human Review 응답 요청 DTO
 */
data class ReviewResponseRequest(
  val action: String,
  val modifiedOutput: Any? = null,
  val hint: String? = null,
  val comment: String? = null,
)

/**
 * 대기 중인 Human Review 응답 DTO
 */
data class PendingReviewResponse(
  val reviewId: String,
  val agentId: String,
  val guardType: String,
  val reason: String,
  val options: List<ReviewOptionDto>,
  val expiresAt: String,
)

/**
 * Review 옵션 DTO
 */
data class ReviewOptionDto(
  val action: String,
  val label: String,
  val description: String,
  val isDefault: Boolean,
)
