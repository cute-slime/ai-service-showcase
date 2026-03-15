package com.jongmin.ai.multiagent.skill.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.ai.multiagent.skill.dto.*
import com.jongmin.ai.multiagent.skill.repository.AgentSkillMappingRepository
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 정의 Platform 서비스
 * 3단계 Context Loading 지원
 *
 * Phase 1: Discovery (~100 tokens)
 * Phase 2: Activation (<5000 tokens)
 * Phase 3: Execution (on-demand)
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class SkillDefinitionService(
  private val skillRepository: SkillDefinitionRepository,
  private val mappingRepository: AgentSkillMappingRepository,
) {

  // 삭제 상태값
  private val DELETED_STATUS = StatusType.DELETED

  // Discovery description 최대 길이
  private val DISCOVERY_DESCRIPTION_MAX_LENGTH = 200

  /**
   * Discovery 정보 조회 (~100 tokens)
   * 여러 스킬의 name + description만 반환
   */
  fun getDiscoveryInfo(
    session: JSession,
    skillIds: List<String>,
  ): List<SkillDiscoveryResponse> {
    kLogger.debug { "스킬 Discovery 조회 - skillIds: ${skillIds.size}개" }

    if (skillIds.isEmpty()) return emptyList()

    val skills = skillRepository.findByAccountIdAndNameInAndStatusNot(
      session.accountId,
      skillIds,
      DELETED_STATUS
    )

    return skills.map { skill ->
      SkillDiscoveryResponse(
        id = skill.name,
        name = skill.name,
        description = skill.description.take(DISCOVERY_DESCRIPTION_MAX_LENGTH),
      )
    }
  }

  /**
   * Activation 정보 조회 (<5000 tokens)
   * 전체 instructions + 메타데이터
   */
  fun getActivationContext(
    session: JSession,
    skillId: String,
  ): SkillActivationResponse {
    kLogger.debug { "스킬 Activation 조회 - skillId: $skillId" }

    val skill = skillRepository.findByAccountIdAndNameAndStatusNot(
      session.accountId,
      skillId,
      DELETED_STATUS
    ) ?: throw ObjectNotFoundException("Skill not found: $skillId")

    return SkillActivationResponse(
      id = skill.name,
      name = skill.name,
      description = skill.description,
      instructions = skill.body,
      allowedTools = skill.allowedTools ?: emptyList(),
      metadata = skill.metadata,
      scriptsAvailable = skill.scripts.keys.toList(),
      referencesAvailable = skill.references.keys.toList(),
    )
  }

  /**
   * 스크립트 실행 정보 조회
   */
  fun getScriptForExecution(
    session: JSession,
    skillId: String,
    filename: String,
  ): SkillScriptExecutionResponse {
    kLogger.debug { "스킬 스크립트 조회 - skillId: $skillId, filename: $filename" }

    val skill = skillRepository.findByAccountIdAndNameAndStatusNot(
      session.accountId,
      skillId,
      DELETED_STATUS
    ) ?: throw ObjectNotFoundException("Skill not found: $skillId")

    val script = skill.scripts[filename]
      ?: throw ObjectNotFoundException("Script not found: $filename")

    return SkillScriptExecutionResponse(
      filename = filename,
      language = script.language.name,
      content = script.content,
      entrypoint = script.entrypoint,
    )
  }

  /**
   * 참조문서 조회
   */
  fun getReferenceForExecution(
    session: JSession,
    skillId: String,
    filename: String,
  ): SkillReferenceExecutionResponse {
    kLogger.debug { "스킬 참조문서 조회 - skillId: $skillId, filename: $filename" }

    val skill = skillRepository.findByAccountIdAndNameAndStatusNot(
      session.accountId,
      skillId,
      DELETED_STATUS
    ) ?: throw ObjectNotFoundException("Skill not found: $skillId")

    val ref = skill.references[filename]
      ?: throw ObjectNotFoundException("Reference not found: $filename")

    return SkillReferenceExecutionResponse(
      filename = filename,
      content = ref.content,
    )
  }

  /**
   * 스킬 ID로 전체 정의 조회 (내부용)
   */
  fun getSkillDefinitionByName(
    session: JSession,
    skillId: String,
  ) = skillRepository.findByAccountIdAndNameAndStatusNot(
    session.accountId,
    skillId,
    DELETED_STATUS
  )?.toSkillDefinition()

  // ========== 에이전트 기반 스킬 조회 ==========

  /**
   * 에이전트에 할당된 스킬의 Discovery 정보 조회
   * 에이전트 실행 시 사용 가능한 스킬 목록 제공
   */
  fun getAgentSkillsDiscovery(
    session: JSession,
    agentId: Long,
  ): List<AgentSkillDiscoveryResponse> {
    kLogger.debug { "에이전트 스킬 Discovery 조회 - agentId: $agentId" }

    val mappings = mappingRepository.findByAgentIdAndEnabledOrderByPriorityDesc(agentId, true)

    return mappings.mapNotNull { mapping ->
      skillRepository.findByAccountIdAndNameAndStatusNot(
        session.accountId,
        mapping.skillName,
        DELETED_STATUS
      )?.let { skill ->
        AgentSkillDiscoveryResponse(
          id = skill.name,
          name = skill.name,
          description = skill.description.take(DISCOVERY_DESCRIPTION_MAX_LENGTH),
          priority = mapping.priority,
          aliases = mapping.aliases,
        )
      }
    }
  }

  /**
   * 에이전트에 할당된 스킬 ID 목록 조회 (간단 버전)
   */
  fun getAgentSkillIds(
    session: JSession,
    agentId: Long,
  ): List<String> {
    return mappingRepository.findEnabledSkillNamesByAgentId(agentId)
  }
}
