package com.jongmin.ai.multiagent.model

/**
 * 에이전트 간 연결 정보
 */
data class AgentEdge(
  val id: String = "",                   // 엣지 고유 ID
  val source: String = "",               // 출발 노드 ID
  val target: String = "",               // 도착 노드 ID
  val sourceHandle: String? = null,      // 출발 핸들 (선택)
  val targetHandle: String? = null,      // 도착 핸들 (선택)
  val label: String? = null,             // 엣지 라벨 (선택)
)
