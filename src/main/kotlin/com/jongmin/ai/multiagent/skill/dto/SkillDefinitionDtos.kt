package com.jongmin.ai.multiagent.skill.dto

/**
 * 스킬 정의 Platform API DTO
 * 3단계 Context Loading 지원
 */

// ========== Discovery Phase (Phase 1) ==========

/**
 * Discovery 응답 DTO
 * 스킬 이름과 설명만 (~100 tokens)
 */
data class SkillDiscoveryResponse(
  val id: String,
  val name: String,
  val description: String,  // 200자 제한
)

// ========== Activation Phase (Phase 2) ==========

/**
 * Activation 응답 DTO
 * 전체 instructions 포함 (<5000 tokens)
 */
data class SkillActivationResponse(
  val id: String,
  val name: String,
  val description: String,
  val instructions: String,
  val allowedTools: List<String>,
  val metadata: Map<String, Any>,
  val scriptsAvailable: List<String>,
  val referencesAvailable: List<String>,
)

// ========== Execution Phase (Phase 3) ==========

/**
 * 스크립트 실행 정보 응답 DTO
 */
data class SkillScriptExecutionResponse(
  val filename: String,
  val language: String,
  val content: String,
  val entrypoint: Boolean,
)

/**
 * 참조문서 실행 정보 응답 DTO
 */
data class SkillReferenceExecutionResponse(
  val filename: String,
  val content: String,
)

// ========== 에이전트 기반 조회 응답 ==========

/**
 * 에이전트 스킬 Discovery 응답 DTO
 * 에이전트에 할당된 스킬의 기본 정보 (우선순위, 별칭 포함)
 */
data class AgentSkillDiscoveryResponse(
  val id: String,
  val name: String,
  val description: String,  // 200자 제한
  val priority: Int,
  val aliases: List<String>?,
)
