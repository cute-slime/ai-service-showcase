package com.jongmin.ai.multiagent.skill.context

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.skill.SkillRegistry
import com.jongmin.ai.multiagent.skill.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 컨텍스트 프로바이더
 * Agent Skills 스펙의 3단계 Context Loading 구현
 *
 * 1. Discovery: name + description (~100 tokens)
 * 2. Activation: 전체 instructions (<5000 tokens)
 * 3. Execution: scripts, references 필요 시 로드
 */
@Component
class SkillContextProvider(
  private val skillRegistry: SkillRegistry,
) {

  // ========== Phase 1: Discovery ==========

  /**
   * Discovery 컨텍스트 조회
   * 모든 사용 가능한 스킬의 name + description만 반환
   * LLM이 어떤 스킬이 적합한지 판단할 때 사용
   */
  fun getDiscoveryContext(skillIds: List<String>): List<SkillDiscoveryContext> {
    kLogger.debug { "Discovery 컨텍스트 조회 - skillIds: ${skillIds.size}개" }

    return skillIds.mapNotNull { id ->
      skillRegistry.getSkill(id)?.let { skill ->
        SkillDiscoveryContext(
          id = skill.id,
          name = skill.name,
          description = skill.description.take(DISCOVERY_DESCRIPTION_MAX_LENGTH),
        )
      }
    }
  }

  /**
   * 모든 등록된 스킬의 Discovery 컨텍스트
   */
  fun getAllDiscoveryContext(): List<SkillDiscoveryContext> {
    return skillRegistry.getAllSkills().map { skill ->
      SkillDiscoveryContext(
        id = skill.id,
        name = skill.name,
        description = skill.description.take(DISCOVERY_DESCRIPTION_MAX_LENGTH),
      )
    }
  }

  // ========== Phase 2: Activation ==========

  /**
   * Activation 컨텍스트 조회
   * 스킬이 선택되었을 때 전체 instructions 로드
   */
  fun getActivationContext(skillId: String): SkillActivationContext? {
    kLogger.debug { "Activation 컨텍스트 조회 - skillId: $skillId" }

    val skillDef = skillRegistry.getSkillDefinition(skillId)

    // SkillDefinition이 있으면 3단계 Context Loading 사용
    if (skillDef != null) {
      return SkillActivationContext(
        id = skillDef.frontmatter.name,
        name = skillDef.frontmatter.name,
        description = skillDef.frontmatter.description,
        instructions = skillDef.body,
        allowedTools = skillDef.frontmatter.allowedTools ?: emptyList(),
        metadata = skillDef.frontmatter.metadata,
        scriptsAvailable = skillDef.scripts.keys.toList(),
        referencesAvailable = skillDef.references.keys.toList(),
      )
    }

    // 없으면 기본 AgentSkill에서 생성 (하위 호환)
    val skill = skillRegistry.getSkill(skillId) ?: return null
    return createActivationFromAgentSkill(skill)
  }

  // ========== Phase 3: Execution ==========

  /**
   * 스크립트 조회 (실행용)
   */
  fun getScript(skillId: String, scriptName: String): SkillScript? {
    kLogger.debug { "스크립트 조회 - skillId: $skillId, script: $scriptName" }

    return skillRegistry.getSkillDefinition(skillId)?.scripts?.get(scriptName)
  }

  /**
   * Entrypoint 스크립트 조회
   */
  fun getEntrypointScript(skillId: String): SkillScript? {
    val skill = skillRegistry.getSkillDefinition(skillId) ?: return null

    // 1. entrypoint=true인 스크립트 찾기
    return skill.scripts.values.find { it.entrypoint }
    // 2. 없으면 첫 번째 스크립트
      ?: skill.scripts.values.firstOrNull()
  }

  /**
   * 참조문서 조회
   */
  fun getReference(skillId: String, refName: String): SkillReference? {
    kLogger.debug { "참조문서 조회 - skillId: $skillId, ref: $refName" }

    return skillRegistry.getSkillDefinition(skillId)?.references?.get(refName)
  }

  /**
   * 우선순위 순으로 참조문서 조회
   */
  fun getReferencesOrdered(skillId: String): List<SkillReference> {
    val skill = skillRegistry.getSkillDefinition(skillId) ?: return emptyList()

    return skill.references.values
      .sortedByDescending { it.priority }
  }

  /**
   * 에셋 조회
   */
  fun getAsset(skillId: String, assetName: String): SkillAsset? {
    kLogger.debug { "에셋 조회 - skillId: $skillId, asset: $assetName" }

    return skillRegistry.getSkillDefinition(skillId)?.assets?.get(assetName)
  }

  // ========== Context Token 추정 ==========

  /**
   * Discovery 컨텍스트 토큰 수 추정
   */
  fun estimateDiscoveryTokens(skillIds: List<String>): Int {
    // 대략 4글자 = 1 토큰으로 계산
    val context = getDiscoveryContext(skillIds)
    val totalChars = context.sumOf { it.name.length + it.description.length }
    return totalChars / CHARS_PER_TOKEN
  }

  /**
   * Activation 컨텍스트 토큰 수 추정
   */
  fun estimateActivationTokens(skillId: String): Int {
    val context = getActivationContext(skillId) ?: return 0
    val totalChars = context.name.length +
      context.description.length +
      context.instructions.length
    return totalChars / CHARS_PER_TOKEN
  }

  // ========== Private Helpers ==========

  /**
   * AgentSkill에서 ActivationContext 생성 (하위 호환)
   */
  private fun createActivationFromAgentSkill(skill: AgentSkill): SkillActivationContext {
    return SkillActivationContext(
      id = skill.id,
      name = skill.name,
      description = skill.description,
      instructions = skill.description, // body가 없으면 description 사용
      allowedTools = emptyList(),
      metadata = skill.executorConfig ?: emptyMap(),
      scriptsAvailable = emptyList(),
      referencesAvailable = emptyList(),
    )
  }

  companion object {
    // Discovery description 최대 길이
    private const val DISCOVERY_DESCRIPTION_MAX_LENGTH = 200

    // 토큰 추정 시 글자당 토큰 수 (대략 4글자 = 1토큰)
    private const val CHARS_PER_TOKEN = 4
  }
}

/**
 * Discovery 단계 컨텍스트
 * ~100 tokens 목표
 */
data class SkillDiscoveryContext(
  val id: String,
  val name: String,
  val description: String,  // 200자 제한
)

/**
 * Activation 단계 컨텍스트
 * <5000 tokens 목표
 */
data class SkillActivationContext(
  val id: String,
  val name: String,
  val description: String,
  val instructions: String,
  val allowedTools: List<String>,
  val metadata: Map<String, Any>,
  val scriptsAvailable: List<String>,
  val referencesAvailable: List<String>,
)
