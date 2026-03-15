package com.jongmin.ai.multiagent.skill

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.skill.loader.SkillLoader
import com.jongmin.ai.multiagent.skill.model.SkillDefinition
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 레지스트리
 * 시스템에 등록된 모든 스킬 관리
 *
 * Agent Skills 스펙 지원:
 * - AgentSkill: 기존 스킬 모델 (하위 호환)
 * - SkillDefinition: 3단계 Context Loading 지원 (scripts, references, assets)
 */
@Component
class SkillRegistry {

  // 기존 AgentSkill 저장소
  private val skills = ConcurrentHashMap<String, AgentSkill>()

  // SkillDefinition 저장소 (3단계 Context Loading용)
  private val skillDefinitions = ConcurrentHashMap<String, SkillDefinition>()

  init {
    // 기본 스킬 등록
    DefaultSkills.ALL.forEach { registerSkill(it) }
    kLogger.info { "기본 스킬 ${DefaultSkills.ALL.size}개 등록 완료" }
  }

  /**
   * 스킬 등록
   */
  fun registerSkill(skill: AgentSkill) {
    skills[skill.id] = skill
    kLogger.debug { "스킬 등록 - id: ${skill.id}, name: ${skill.name}" }
  }

  /**
   * 스킬 조회
   */
  fun getSkill(skillId: String): AgentSkill? {
    return skills[skillId]
  }

  /**
   * 모든 스킬 조회
   */
  fun getAllSkills(): List<AgentSkill> {
    return skills.values.toList()
  }

  /**
   * 태그로 스킬 검색
   */
  fun findSkillsByTag(tag: String): List<AgentSkill> {
    return skills.values.filter { it.tags?.contains(tag) == true }
  }

  /**
   * 스킬 제거
   */
  fun unregisterSkill(skillId: String) {
    skills.remove(skillId)
    kLogger.debug { "스킬 제거 - id: $skillId" }
  }

  /**
   * 스킬 존재 여부 확인
   */
  fun hasSkill(skillId: String): Boolean {
    return skills.containsKey(skillId)
  }

  /**
   * 등록된 스킬 수
   */
  fun count(): Int = skills.size

  // ========== SkillDefinition 관련 메서드 ==========

  /**
   * SkillDefinition 조회 (3단계 Context Loading용)
   */
  fun getSkillDefinition(skillId: String): SkillDefinition? {
    return skillDefinitions[skillId]
  }

  /**
   * SkillDefinition 등록
   * AgentSkill도 함께 등록됨
   */
  fun registerSkillDefinition(definition: SkillDefinition) {
    val skillId = definition.frontmatter.name
    skillDefinitions[skillId] = definition

    // AgentSkill도 함께 등록 (하위 호환)
    registerSkill(definition.toAgentSkill())

    kLogger.debug { "스킬 정의 등록 - id: $skillId, scripts: ${definition.scripts.size}, refs: ${definition.references.size}" }
  }

  /**
   * 파일에서 스킬 로드 및 등록
   */
  fun loadAndRegisterFromDirectory(skillDir: Path, skillLoader: SkillLoader) {
    try {
      val definition = skillLoader.loadFromDirectory(skillDir)
      registerSkillDefinition(definition)
    } catch (e: Exception) {
      kLogger.error(e) { "스킬 로드 실패 - dir: $skillDir" }
    }
  }

  /**
   * 디렉토리 내 모든 스킬 로드 및 등록
   */
  fun loadAllAndRegisterFromDirectory(skillsRootDir: Path, skillLoader: SkillLoader) {
    val definitions = skillLoader.loadAllFromDirectory(skillsRootDir)
    definitions.forEach { registerSkillDefinition(it) }
    kLogger.info { "스킬 일괄 등록 완료 - count: ${definitions.size}" }
  }

  /**
   * 모든 SkillDefinition 조회
   */
  fun getAllSkillDefinitions(): List<SkillDefinition> {
    return skillDefinitions.values.toList()
  }

  /**
   * SkillDefinition 존재 여부 확인
   */
  fun hasSkillDefinition(skillId: String): Boolean {
    return skillDefinitions.containsKey(skillId)
  }

  /**
   * SkillDefinition 제거
   * AgentSkill도 함께 제거됨
   */
  fun unregisterSkillDefinition(skillId: String) {
    skillDefinitions.remove(skillId)
    skills.remove(skillId)
    kLogger.debug { "스킬 정의 제거 - id: $skillId" }
  }

  /**
   * 등록된 SkillDefinition 수
   */
  fun countDefinitions(): Int = skillDefinitions.size
}
