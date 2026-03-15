package com.jongmin.ai.multiagent.skill.backoffice.dto

import jakarta.validation.constraints.NotNull

// ========== 스킬 할당 요청 ==========

/**
 * 에이전트에 스킬 할당 요청 DTO
 */
data class BoAssignSkillRequest(
  @field:NotNull(message = "skillDefinitionId is required")
  val skillDefinitionId: Long,

  val priority: Int = 0,

  val enabled: Boolean = true,

  val overrideConfig: Map<String, Any>? = null,

  val aliases: List<String>? = null,

  val memo: String? = null,
)

/**
 * 에이전트 스킬 일괄 할당 요청 DTO
 */
data class BoBatchAssignSkillsRequest(
  val assignments: List<BoAssignSkillRequest>,
)

// ========== 스킬 매핑 수정 요청 ==========

/**
 * 스킬 매핑 수정 요청 DTO (PATCH)
 */
data class BoPatchSkillMappingRequest(
  var id: Long? = null,  // PathVariable에서 설정

  val priority: Int? = null,

  val enabled: Boolean? = null,

  val overrideConfig: Map<String, Any>? = null,

  val aliases: List<String>? = null,

  val memo: String? = null,
)

// ========== 응답 ==========

/**
 * 에이전트 스킬 매핑 응답 DTO
 */
data class BoAgentSkillMappingResponse(
  val id: Long,
  val agentId: Long,
  val skillDefinitionId: Long,
  val skillName: String,
  val priority: Int,
  val enabled: Boolean,
  val overrideConfig: Map<String, Any>?,
  val aliases: List<String>?,
  val memo: String?,
  val createdAt: String,
  val updatedAt: String,
)

/**
 * 에이전트 스킬 목록 응답 DTO (스킬 상세 포함)
 */
data class BoAgentSkillDetailResponse(
  val mappingId: Long,
  val skillId: Long,
  val skillName: String,
  val skillDescription: String,
  val priority: Int,
  val enabled: Boolean,
  val scriptsCount: Int,
  val referencesCount: Int,
  val aliases: List<String>?,
)

/**
 * 스킬 사용 에이전트 목록 응답 DTO
 */
data class BoSkillUsageResponse(
  val mappingId: Long,
  val agentId: Long,
  val agentName: String?,
  val priority: Int,
  val enabled: Boolean,
)
