package com.jongmin.ai.multiagent.skill.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.core.AiAgentRepository
import com.jongmin.ai.multiagent.skill.backoffice.dto.*
import com.jongmin.ai.multiagent.skill.entity.AgentSkillMappingEntity
import com.jongmin.ai.multiagent.skill.repository.AgentSkillMappingRepository
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

private val kLogger = KotlinLogging.logger {}

/**
 * 에이전트-스킬 매핑 백오피스 서비스
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoAgentSkillMappingService(
  private val mappingRepository: AgentSkillMappingRepository,
  private val skillRepository: SkillDefinitionRepository,
  private val agentRepository: AiAgentRepository,
) {

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  // ========== 스킬 할당 ==========

  /**
   * 에이전트에 스킬 할당
   */
  @Transactional
  fun assignSkill(
    session: JSession,
    agentId: Long,
    request: BoAssignSkillRequest,
  ): BoAgentSkillMappingResponse {
    kLogger.info { "스킬 할당 - agentId: $agentId, skillId: ${request.skillDefinitionId}, admin: ${session.username}" }

    // 1. 에이전트 존재 확인
    val agent = agentRepository.findById(agentId)
      .orElseThrow { ObjectNotFoundException("Agent not found: $agentId") }

    // 2. 스킬 존재 확인
    val skill = skillRepository.findById(request.skillDefinitionId)
      .orElseThrow { ObjectNotFoundException("Skill not found: ${request.skillDefinitionId}") }

    // 3. 중복 할당 체크
    val existing = mappingRepository.findByAgentIdAndSkillDefinitionId(agentId, request.skillDefinitionId)
    require(existing == null) {
      "Skill already assigned to agent: agentId=$agentId, skillId=${request.skillDefinitionId}"
    }

    // 4. 매핑 생성
    val entity = AgentSkillMappingEntity(
      accountId = session.accountId,
      agentId = agentId,
      skillDefinitionId = request.skillDefinitionId,
      skillName = skill.name,
      priority = request.priority,
      enabled = request.enabled,
      overrideConfig = request.overrideConfig,
      aliases = request.aliases,
      memo = request.memo,
    )

    val saved = mappingRepository.save(entity)
    kLogger.info { "스킬 할당 완료 - mappingId: ${saved.id}, agentId: $agentId, skillName: ${skill.name}" }

    return saved.toResponse()
  }

  /**
   * 에이전트에 스킬 일괄 할당
   */
  @Transactional
  fun batchAssignSkills(
    session: JSession,
    agentId: Long,
    request: BoBatchAssignSkillsRequest,
  ): List<BoAgentSkillMappingResponse> {
    kLogger.info { "스킬 일괄 할당 - agentId: $agentId, count: ${request.assignments.size}, admin: ${session.username}" }

    return request.assignments.map { assignment ->
      assignSkill(session, agentId, assignment)
    }
  }

  // ========== 스킬 매핑 조회 ==========

  /**
   * 에이전트의 스킬 매핑 목록 조회
   */
  fun getAgentSkillMappings(
    session: JSession,
    agentId: Long,
  ): List<BoAgentSkillMappingResponse> {
    kLogger.debug { "에이전트 스킬 매핑 조회 - agentId: $agentId" }

    return mappingRepository.findByAgentIdOrderByPriorityDesc(agentId)
      .map { it.toResponse() }
  }

  /**
   * 에이전트의 스킬 상세 목록 조회 (스킬 정보 포함)
   */
  fun getAgentSkillDetails(
    session: JSession,
    agentId: Long,
  ): List<BoAgentSkillDetailResponse> {
    kLogger.debug { "에이전트 스킬 상세 조회 - agentId: $agentId" }

    val mappings = mappingRepository.findByAgentIdOrderByPriorityDesc(agentId)

    return mappings.mapNotNull { mapping ->
      skillRepository.findById(mapping.skillDefinitionId).orElse(null)?.let { skill ->
        BoAgentSkillDetailResponse(
          mappingId = mapping.id,
          skillId = skill.id,
          skillName = skill.name,
          skillDescription = skill.description,
          priority = mapping.priority,
          enabled = mapping.enabled,
          scriptsCount = skill.scripts.size,
          referencesCount = skill.references.size,
          aliases = mapping.aliases,
        )
      }
    }
  }

  /**
   * 스킬을 사용하는 에이전트 목록 조회
   */
  fun getSkillUsage(
    session: JSession,
    skillDefinitionId: Long,
  ): List<BoSkillUsageResponse> {
    kLogger.debug { "스킬 사용 에이전트 조회 - skillId: $skillDefinitionId" }

    val mappings = mappingRepository.findBySkillDefinitionId(skillDefinitionId)

    return mappings.map { mapping ->
      val agent = agentRepository.findById(mapping.agentId).orElse(null)
      BoSkillUsageResponse(
        mappingId = mapping.id,
        agentId = mapping.agentId,
        agentName = agent?.name,
        priority = mapping.priority,
        enabled = mapping.enabled,
      )
    }
  }

  // ========== 스킬 매핑 수정 ==========

  /**
   * 스킬 매핑 수정 (PATCH)
   */
  @Transactional
  fun patchMapping(
    session: JSession,
    agentId: Long,
    mappingId: Long,
    data: Map<String, Any>,
  ): Map<String, Any?> {
    kLogger.info { "스킬 매핑 수정 - agentId: $agentId, mappingId: $mappingId, admin: ${session.username}" }

    val target = mappingRepository.findById(mappingId)
      .orElseThrow { ObjectNotFoundException("Mapping not found: $mappingId") }

    // 에이전트 ID 일치 확인
    require(target.agentId == agentId) {
      "Mapping does not belong to agent: mappingId=$mappingId, agentId=$agentId"
    }

    return merge(
      data,
      target,
      "id", "accountId", "agentId", "skillDefinitionId", "skillName", "createdAt", "updatedAt"
    )
  }

  // ========== 스킬 매핑 삭제 ==========

  /**
   * 스킬 매핑 삭제 (할당 해제)
   */
  @Transactional
  fun unassignSkill(
    session: JSession,
    agentId: Long,
    mappingId: Long,
  ) {
    kLogger.info { "스킬 할당 해제 - agentId: $agentId, mappingId: $mappingId, admin: ${session.username}" }

    val target = mappingRepository.findById(mappingId)
      .orElseThrow { ObjectNotFoundException("Mapping not found: $mappingId") }

    // 에이전트 ID 일치 확인
    require(target.agentId == agentId) {
      "Mapping does not belong to agent: mappingId=$mappingId, agentId=$agentId"
    }

    mappingRepository.delete(target)
    kLogger.info { "스킬 할당 해제 완료 - mappingId: $mappingId, skillName: ${target.skillName}" }
  }

  /**
   * 에이전트의 모든 스킬 할당 해제
   */
  @Transactional
  fun unassignAllSkills(
    session: JSession,
    agentId: Long,
  ) {
    kLogger.info { "에이전트 스킬 전체 해제 - agentId: $agentId, admin: ${session.username}" }

    mappingRepository.deleteByAgentId(agentId)
    kLogger.info { "에이전트 스킬 전체 해제 완료 - agentId: $agentId" }
  }

  // ========== Private Helpers ==========

  private fun AgentSkillMappingEntity.toResponse(): BoAgentSkillMappingResponse {
    return BoAgentSkillMappingResponse(
      id = id,
      agentId = agentId,
      skillDefinitionId = skillDefinitionId,
      skillName = skillName,
      priority = priority,
      enabled = enabled,
      overrideConfig = overrideConfig,
      aliases = aliases,
      memo = memo,
      createdAt = createdAt.format(dateFormatter),
      updatedAt = updatedAt?.format(dateFormatter) ?: "",
    )
  }
}
