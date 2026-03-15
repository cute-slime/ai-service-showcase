package com.jongmin.ai.multiagent.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.multiagent.model.AgentEdge
import com.jongmin.ai.multiagent.model.MultiAgentNode
import com.jongmin.ai.multiagent.model.OrchestratorConfig
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 멀티 에이전트 워크플로우 Entity
 * 여러 AI 에이전트들의 협업 워크플로우를 정의
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_multi_agent_workflow_accountId", columnList = "accountId"),
    Index(name = "idx_multi_agent_workflow_ownerId", columnList = "ownerId"),
    Index(name = "idx_multi_agent_workflow_status", columnList = "status"),
  ]
)
data class MultiAgentWorkflow(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false)
  val accountId: Long,

  @Column(nullable = false, updatable = false)
  val ownerId: Long,

  @Column(length = 100, nullable = false)
  var name: String,

  @Column(length = 500)
  var description: String? = null,

  // 에이전트 노드 목록 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var agents: List<MultiAgentNode> = emptyList(),

  // 에이전트 간 연결 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var edges: List<AgentEdge> = emptyList(),

  // 오케스트레이터 설정 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var orchestratorConfig: OrchestratorConfig = OrchestratorConfig(),

) : BaseTimeAndStatusEntity()

