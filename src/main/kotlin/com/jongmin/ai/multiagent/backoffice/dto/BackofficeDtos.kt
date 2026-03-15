package com.jongmin.ai.multiagent.backoffice.dto

import com.jongmin.ai.multiagent.model.*

// ========== 목록 조회 응답 ==========

/**
 * 워크플로우 목록 응답 DTO
 */
data class BoWorkflowListResponse(
  val id: Long,
  val name: String,
  val description: String?,
  val agentCount: Int,
  val status: Int,
  val createdAt: String,
)

// ========== 상세 조회 응답 ==========

/**
 * 워크플로우 상세 응답 DTO
 */
data class BoWorkflowDetailResponse(
  val id: Long,
  val accountId: Long,
  val ownerId: Long,
  val name: String,
  val description: String?,
  val agents: List<MultiAgentNode>,
  val edges: List<AgentEdge>,
  val orchestratorConfig: OrchestratorConfig,
  val status: Int,
  val createdAt: String,
  val updatedAt: String,
)

// ========== 생성 요청 ==========

/**
 * 워크플로우 생성 요청 DTO
 */
data class BoCreateWorkflowRequest(
  val accountId: Long,
  val name: String,
  val description: String? = null,
  val orchestratorConfig: OrchestratorConfig? = null,
)

// ========== 수정 요청 ==========

/**
 * 워크플로우 PATCH 요청 DTO
 */
data class BoPatchWorkflowRequest(
  var id: Long? = null,
  val name: String? = null,
  val description: String? = null,
)

// ========== 에이전트 노드 관리 ==========

/**
 * 에이전트 노드 추가 요청 DTO
 */
data class BoAddAgentRequest(
  val agentId: Long,
  val name: String,
  val position: NodePosition? = null,
  val capability: AgentCapability? = null,
  val autonomyConfig: AgentAutonomyConfig? = null,
)

/**
 * 에이전트 연결(Edge) 추가 요청 DTO
 */
data class BoAddEdgeRequest(
  val sourceNodeId: String,
  val targetNodeId: String,
  val label: String? = null,
)

// ========== 오케스트레이터 설정 ==========

/**
 * 오케스트레이터 설정 업데이트 요청 DTO
 */
data class BoOrchestratorConfigRequest(
  val routingMode: RoutingMode? = null,
  val maxExecutionCycles: Int? = null,
  val maxConversationTurns: Int? = null,
  val maxRetryPerAgent: Int? = null,
  val retryWithGuidance: Boolean? = null,
  val evaluationPassThreshold: Double? = null,
  val humanReviewConfig: HumanReviewConfig? = null,
)
