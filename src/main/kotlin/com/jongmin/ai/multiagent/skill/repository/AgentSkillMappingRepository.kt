package com.jongmin.ai.multiagent.skill.repository

import com.jongmin.ai.multiagent.skill.entity.AgentSkillMappingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * 에이전트-스킬 매핑 Repository
 */
interface AgentSkillMappingRepository : JpaRepository<AgentSkillMappingEntity, Long> {

  /**
   * 에이전트의 활성화된 스킬 매핑 조회 (우선순위 내림차순)
   */
  fun findByAgentIdAndEnabledOrderByPriorityDesc(
    agentId: Long,
    enabled: Boolean = true,
  ): List<AgentSkillMappingEntity>

  /**
   * 에이전트의 모든 스킬 매핑 조회 (우선순위 내림차순)
   */
  fun findByAgentIdOrderByPriorityDesc(
    agentId: Long,
  ): List<AgentSkillMappingEntity>

  /**
   * 특정 스킬을 사용하는 모든 매핑 조회
   */
  fun findBySkillDefinitionId(
    skillDefinitionId: Long,
  ): List<AgentSkillMappingEntity>

  /**
   * 계정의 모든 매핑 조회
   */
  fun findByAccountId(
    accountId: Long,
  ): List<AgentSkillMappingEntity>

  /**
   * 특정 에이전트-스킬 매핑 조회
   */
  fun findByAgentIdAndSkillDefinitionId(
    agentId: Long,
    skillDefinitionId: Long,
  ): AgentSkillMappingEntity?

  /**
   * 특정 에이전트-스킬 이름으로 매핑 조회
   */
  fun findByAgentIdAndSkillName(
    agentId: Long,
    skillName: String,
  ): AgentSkillMappingEntity?

  /**
   * 에이전트의 스킬 매핑 삭제
   */
  fun deleteByAgentIdAndSkillDefinitionId(
    agentId: Long,
    skillDefinitionId: Long,
  )

  /**
   * 에이전트의 모든 스킬 매핑 삭제
   */
  fun deleteByAgentId(agentId: Long)

  /**
   * 스킬 정의 삭제 시 관련 매핑 삭제
   */
  fun deleteBySkillDefinitionId(skillDefinitionId: Long)

  /**
   * 에이전트의 스킬 매핑 수 조회
   */
  fun countByAgentId(agentId: Long): Long

  /**
   * 스킬이 사용되는 에이전트 수 조회
   */
  fun countBySkillDefinitionId(skillDefinitionId: Long): Long

  /**
   * 에이전트의 활성화된 스킬 ID 목록 조회
   */
  @Query("""
    SELECT m.skillDefinitionId
    FROM AgentSkillMappingEntity m
    WHERE m.agentId = :agentId AND m.enabled = true
    ORDER BY m.priority DESC
  """)
  fun findEnabledSkillIdsByAgentId(agentId: Long): List<Long>

  /**
   * 에이전트의 활성화된 스킬 이름 목록 조회
   */
  @Query("""
    SELECT m.skillName
    FROM AgentSkillMappingEntity m
    WHERE m.agentId = :agentId AND m.enabled = true
    ORDER BY m.priority DESC
  """)
  fun findEnabledSkillNamesByAgentId(agentId: Long): List<String>
}
