package com.jongmin.ai.multiagent.skill.model

import com.jongmin.ai.multiagent.skill.validator.SkillNameValidator

/**
 * SKILL.md 파일의 YAML frontmatter
 * Agent Skills 스펙 필수/선택 필드 표현
 *
 * 스펙 참조: https://agentskills.io/specification
 */
data class SkillFrontmatter(
  // ======== 필수 필드 ========

  // 스킬 식별자 (1-64자, lowercase + hyphen)
  val name: String,

  // 스킬 설명 (1-1024자, 스킬 사용 시점 판단용)
  val description: String,

  // ======== 선택 필드 ========

  // 라이선스 ("Apache-2.0", "MIT", 또는 파일 참조)
  val license: String? = null,

  // 호환성 요구사항
  val compatibility: SkillCompatibility? = null,

  // 추가 메타데이터 (author, version 등)
  val metadata: Map<String, Any> = emptyMap(),

  // 허용된 도구 목록 (실험적 기능)
  val allowedTools: List<String>? = null,
) {
  init {
    // 생성 시 name 검증 (Agent Skills 스펙 준수)
    SkillNameValidator.validate(name)

    // description 길이 검증
    require(description.isNotBlank()) {
      "Description is required"
    }
    require(description.length <= 1024) {
      "Description must be 1024 characters or less"
    }
  }

  /**
   * 버전 정보 추출 (metadata에서)
   */
  val version: String
    get() = metadata["version"]?.toString() ?: "1.0"

  /**
   * 작성자 정보 추출 (metadata에서)
   */
  val author: String?
    get() = metadata["author"]?.toString()

  /**
   * 태그 목록 추출 (metadata에서)
   */
  @Suppress("UNCHECKED_CAST")
  val tags: List<String>
    get() = (metadata["tags"] as? List<String>) ?: emptyList()
}
