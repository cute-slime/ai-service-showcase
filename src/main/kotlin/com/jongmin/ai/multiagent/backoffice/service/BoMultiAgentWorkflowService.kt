package com.jongmin.ai.multiagent.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.multiagent.backoffice.dto.*
import com.jongmin.ai.multiagent.entity.MultiAgentWorkflow
import com.jongmin.ai.multiagent.model.*
import com.jongmin.ai.multiagent.repository.MultiAgentWorkflowRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter
import java.util.UUID

private val kLogger = KotlinLogging.logger {}

/**
 * 멀티 에이전트 워크플로우 백오피스 서비스
 *
 * 워크플로우 CRUD, 에이전트 노드 관리, 오케스트레이터 설정
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoMultiAgentWorkflowService(
  private val workflowRepository: MultiAgentWorkflowRepository,
) {

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  /**
   * 워크플로우 목록 조회
   */
  fun list(session: JSession, accountId: Long?, pageable: Pageable): Page<BoWorkflowListResponse> {
    kLogger.info { "워크플로우 목록 조회 - accountId: $accountId, admin: ${session.username}" }

    val result = if (accountId != null) {
      workflowRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
    } else {
      workflowRepository.findAll(pageable)
    }

    return result.map { it.toListResponse() }
  }

  /**
   * 워크플로우 상세 조회
   */
  fun get(session: JSession, id: Long): BoWorkflowDetailResponse {
    kLogger.info { "워크플로우 상세 조회 - id: $id, admin: ${session.username}" }

    val workflow = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $id") }

    return workflow.toDetailResponse()
  }

  /**
   * 워크플로우 생성
   */
  @Transactional
  fun create(session: JSession, request: BoCreateWorkflowRequest): BoWorkflowDetailResponse {
    kLogger.info { "워크플로우 생성 - accountId: ${request.accountId}, name: ${request.name}, admin: ${session.username}" }

    val workflow = MultiAgentWorkflow(
      id = System.currentTimeMillis(),  // 간단한 ID 생성 (실제로는 Snowflake 등 사용)
      accountId = request.accountId,
      ownerId = session.accountId,
      name = request.name,
      description = request.description,
      agents = emptyList(),
      edges = emptyList(),
      orchestratorConfig = request.orchestratorConfig ?: OrchestratorConfig(),
    )

    val saved = workflowRepository.save(workflow)
    kLogger.info { "워크플로우 생성 완료 - id: ${saved.id}" }

    return saved.toDetailResponse()
  }

  /**
   * 워크플로우 수정 (PATCH)
   */
  @Transactional
  fun patch(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "워크플로우 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val id = (data["id"] as Number).toLong()
    val workflow = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $id") }

    return merge(
      data,
      workflow,
      "id", "createdAt", "updatedAt", "accountId", "ownerId", "agents", "edges", "orchestratorConfig"
    )
  }

  /**
   * 워크플로우 삭제
   */
  @Transactional
  fun delete(session: JSession, id: Long): Boolean {
    kLogger.info { "워크플로우 삭제 - id: $id, admin: ${session.username}" }

    val workflow = workflowRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $id") }

    workflowRepository.delete(workflow)
    kLogger.info { "워크플로우 삭제 완료 - id: $id" }

    return true
  }

  /**
   * 에이전트 노드 추가
   */
  @Transactional
  fun addAgent(session: JSession, workflowId: Long, request: BoAddAgentRequest): BoWorkflowDetailResponse {
    kLogger.info { "에이전트 노드 추가 - workflowId: $workflowId, agentId: ${request.agentId}, admin: ${session.username}" }

    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $workflowId") }

    // 새 노드 ID 생성
    val nodeId = "agent-${request.agentId}-${UUID.randomUUID().toString().take(8)}"

    val newAgent = MultiAgentNode(
      id = nodeId,
      agentId = request.agentId,
      name = request.name,
      position = request.position ?: NodePosition(0.0, 0.0),
      capability = request.capability,
      autonomyConfig = request.autonomyConfig ?: AgentAutonomyConfig(),
    )

    // 기존 목록에 추가
    workflow.agents = workflow.agents + newAgent
    val saved = workflowRepository.save(workflow)

    kLogger.info { "에이전트 노드 추가 완료 - workflowId: $workflowId, nodeId: $nodeId" }

    return saved.toDetailResponse()
  }

  /**
   * 에이전트 노드 제거
   */
  @Transactional
  fun removeAgent(session: JSession, workflowId: Long, agentNodeId: String): BoWorkflowDetailResponse {
    kLogger.info { "에이전트 노드 제거 - workflowId: $workflowId, nodeId: $agentNodeId, admin: ${session.username}" }

    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $workflowId") }

    // 노드 제거
    workflow.agents = workflow.agents.filter { it.id != agentNodeId }

    // 연결된 Edge도 제거
    workflow.edges = workflow.edges.filter { it.source != agentNodeId && it.target != agentNodeId }

    val saved = workflowRepository.save(workflow)

    kLogger.info { "에이전트 노드 제거 완료 - workflowId: $workflowId, nodeId: $agentNodeId" }

    return saved.toDetailResponse()
  }

  /**
   * 에이전트 연결(Edge) 추가
   */
  @Transactional
  fun addEdge(session: JSession, workflowId: Long, request: BoAddEdgeRequest): BoWorkflowDetailResponse {
    kLogger.info { "에이전트 연결 추가 - workflowId: $workflowId, source: ${request.sourceNodeId}, target: ${request.targetNodeId}, admin: ${session.username}" }

    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $workflowId") }

    // 노드 존재 확인
    val sourceExists = workflow.agents.any { it.id == request.sourceNodeId }
    val targetExists = workflow.agents.any { it.id == request.targetNodeId }

    if (!sourceExists || !targetExists) {
      throw IllegalArgumentException("Source or target node not found")
    }

    // 새 Edge ID 생성
    val edgeId = "edge-${UUID.randomUUID().toString().take(8)}"

    val newEdge = AgentEdge(
      id = edgeId,
      source = request.sourceNodeId,
      target = request.targetNodeId,
      label = request.label,
    )

    workflow.edges = workflow.edges + newEdge
    val saved = workflowRepository.save(workflow)

    kLogger.info { "에이전트 연결 추가 완료 - workflowId: $workflowId, edgeId: $edgeId" }

    return saved.toDetailResponse()
  }

  /**
   * 에이전트 연결(Edge) 제거
   */
  @Transactional
  fun removeEdge(session: JSession, workflowId: Long, edgeId: String): BoWorkflowDetailResponse {
    kLogger.info { "에이전트 연결 제거 - workflowId: $workflowId, edgeId: $edgeId, admin: ${session.username}" }

    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $workflowId") }

    workflow.edges = workflow.edges.filter { it.id != edgeId }
    val saved = workflowRepository.save(workflow)

    kLogger.info { "에이전트 연결 제거 완료 - workflowId: $workflowId, edgeId: $edgeId" }

    return saved.toDetailResponse()
  }

  /**
   * 오케스트레이터 설정 업데이트
   */
  @Transactional
  fun updateOrchestratorConfig(
    session: JSession,
    workflowId: Long,
    request: BoOrchestratorConfigRequest
  ): BoWorkflowDetailResponse {
    kLogger.info { "오케스트레이터 설정 업데이트 - workflowId: $workflowId, admin: ${session.username}" }

    val workflow = workflowRepository.findById(workflowId)
      .orElseThrow { ObjectNotFoundException("Workflow not found: $workflowId") }

    val currentConfig = workflow.orchestratorConfig

    // 부분 업데이트
    workflow.orchestratorConfig = OrchestratorConfig(
      routingMode = request.routingMode ?: currentConfig.routingMode,
      maxExecutionCycles = request.maxExecutionCycles ?: currentConfig.maxExecutionCycles,
      maxConversationTurns = request.maxConversationTurns ?: currentConfig.maxConversationTurns,
      maxRetryPerAgent = request.maxRetryPerAgent ?: currentConfig.maxRetryPerAgent,
      retryWithGuidance = request.retryWithGuidance ?: currentConfig.retryWithGuidance,
      evaluationPassThreshold = request.evaluationPassThreshold ?: currentConfig.evaluationPassThreshold,
      humanReviewConfig = request.humanReviewConfig ?: currentConfig.humanReviewConfig,
    )

    val saved = workflowRepository.save(workflow)

    kLogger.info { "오케스트레이터 설정 업데이트 완료 - workflowId: $workflowId" }

    return saved.toDetailResponse()
  }

  // ========== 변환 헬퍼 ==========

  private fun MultiAgentWorkflow.toListResponse() = BoWorkflowListResponse(
    id = id,
    name = name,
    description = description,
    agentCount = agents.size,
    status = status.value(),
    createdAt = createdAt?.format(dateFormatter) ?: "",
  )

  private fun MultiAgentWorkflow.toDetailResponse() = BoWorkflowDetailResponse(
    id = id,
    accountId = accountId,
    ownerId = ownerId,
    name = name,
    description = description,
    agents = agents,
    edges = edges,
    orchestratorConfig = orchestratorConfig,
    status = status.value(),
    createdAt = createdAt?.format(dateFormatter) ?: "",
    updatedAt = updatedAt?.format(dateFormatter) ?: "",
  )
}
