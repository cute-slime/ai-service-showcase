package com.jongmin.ai.multiagent.skill.model

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.SkillTriggerConfig
import com.jongmin.ai.multiagent.model.SkillTriggerMode

/**
 * SKILL.md 파일 + 지원 폴더 완전 표현
 * Agent Skills 스펙의 전체 스킬 구조
 *
 * 구조:
 * skill-name/
 * ├── SKILL.md          → frontmatter + body
 * ├── scripts/          → scripts Map
 * ├── references/       → references Map
 * └── assets/           → assets Map
 */
data class SkillDefinition(
  // ======== SKILL.md Frontmatter ========
  val frontmatter: SkillFrontmatter,

  // ======== SKILL.md Body (Markdown Instructions) ========
  val body: String,

  // ======== 지원 폴더 구조 (Map 기반) ========
  // 키: 파일명, 값: 해당 모델
  val scripts: Map<String, SkillScript> = emptyMap(),
  val references: Map<String, SkillReference> = emptyMap(),
  val assets: Map<String, SkillAsset> = emptyMap(),
) {
  /**
   * 스킬 ID (= frontmatter.name)
   */
  val id: String
    get() = frontmatter.name

  /**
   * 스킬 이름 (= frontmatter.name)
   */
  val name: String
    get() = frontmatter.name

  /**
   * 스킬 설명 (= frontmatter.description)
   */
  val description: String
    get() = frontmatter.description

  /**
   * Entrypoint 스크립트 조회
   */
  val entrypointScript: SkillScript?
    get() = scripts.values.find { it.entrypoint }
      ?: scripts.values.firstOrNull()

  /**
   * 내부 AgentSkill 모델로 변환
   * 기존 Phase 3/4 시스템과 호환
   */
  fun toAgentSkill(): AgentSkill {
    return AgentSkill(
      id = frontmatter.name,
      name = frontmatter.name,
      description = frontmatter.description,
      triggerConfig = SkillTriggerConfig(
        triggerKeywords = extractKeywords(frontmatter.description),
        triggerMode = SkillTriggerMode.ON_DEMAND,
      ),
      inputSchema = null,
      outputSchema = null,
      executorType = determineExecutorType(),
      executorConfig = buildExecutorConfig(),
      version = frontmatter.version,
      tags = frontmatter.tags.ifEmpty { null },
    )
  }

  /**
   * description에서 키워드 추출 (간단 버전)
   * LLM이 스킬 선택 시 참고할 키워드
   */
  private fun extractKeywords(description: String): List<String> {
    // 한글/영문 단어 추출 (4자 이상)
    val regex = Regex("[가-힣a-zA-Z]{4,}")
    return regex.findAll(description)
      .map { it.value.lowercase() }
      .distinct()
      .take(5)
      .toList()
  }

  /**
   * 스크립트 기반으로 실행 타입 결정
   */
  private fun determineExecutorType(): String {
    if (scripts.isEmpty()) return "CUSTOM"

    val entryScript = entrypointScript ?: return "CUSTOM"

    return when (entryScript.language) {
      ScriptLanguage.PYTHON -> "PYTHON_SCRIPT"
      ScriptLanguage.BASH -> "BASH_SCRIPT"
      ScriptLanguage.JAVASCRIPT, ScriptLanguage.TYPESCRIPT -> "JS_SCRIPT"
      ScriptLanguage.KOTLIN -> "KOTLIN_SCRIPT"
      ScriptLanguage.GO -> "GO_SCRIPT"
      ScriptLanguage.JAVA, ScriptLanguage.GROOVY, ScriptLanguage.JSHELL -> "JBANG_SCRIPT"
    }
  }

  /**
   * 실행 설정 빌드
   */
  private fun buildExecutorConfig(): Map<String, Any> {
    val config = mutableMapOf<String, Any>()

    // entrypoint 스크립트 정보
    entrypointScript?.let { script ->
      config["entrypoint"] = script.filename
      config["language"] = script.language.name
    }

    // 허용 도구
    frontmatter.allowedTools?.let {
      config["allowedTools"] = it
    }

    // 스크립트/참조문서 목록
    if (scripts.isNotEmpty()) {
      config["scripts"] = scripts.keys.toList()
    }
    if (references.isNotEmpty()) {
      config["references"] = references.keys.toList()
    }

    return config
  }

  companion object {
    /**
     * 빈 SkillDefinition 생성 (테스트용)
     */
    fun empty(name: String, description: String): SkillDefinition {
      return SkillDefinition(
        frontmatter = SkillFrontmatter(
          name = name,
          description = description,
        ),
        body = "",
      )
    }
  }
}
