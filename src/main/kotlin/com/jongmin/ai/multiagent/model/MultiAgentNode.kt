package com.jongmin.ai.multiagent.model

/**
 * 멀티 에이전트 워크플로우의 노드 (에이전트)
 */
data class MultiAgentNode(
  val id: String = "",                                 // 노드 고유 ID
  val agentId: Long = 0,                               // 참조하는 AiAgent ID
  val name: String = "",                               // 표시 이름
  val position: NodePosition? = null,                  // UI 위치 (선택)

  // Phase 2 추가
  val capability: AgentCapability? = null,             // 에이전트 역량
  val autonomyConfig: AgentAutonomyConfig? = null,     // 자율성 설정

  // Phase 3 추가 (Skills)
  val skillInventory: AgentSkillInventory? = null,     // 스킬 인벤토리
)

/**
 * 노드의 UI 위치 정보
 */
data class NodePosition(
  val x: Double = 0.0,
  val y: Double = 0.0,
)
