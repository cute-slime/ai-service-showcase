package com.jongmin.ai.multiagent.skill.model

/**
 * 스킬 참조 문서
 * Agent Skills 스펙의 references/ 폴더 내 마크다운 문서 표현
 */
data class SkillReference(
  // 파일명 (키로도 사용)
  val filename: String = "",              // "REFERENCE.md", "API_GUIDE.md"

  // 마크다운 내용
  val content: String = "",

  // 필요 시에만 로드 여부 (Context Loading Phase 3)
  val loadOnDemand: Boolean = true,

  // 로드 우선순위 (높을수록 먼저)
  val priority: Int = 0,
) {
  companion object {
    // 우선순위가 높은 파일명 패턴
    private val PRIORITY_MAP = mapOf(
      "readme.md" to 100,
      "reference.md" to 90,
      "guide.md" to 80,
      "usage.md" to 70,
      "api.md" to 60,
    )

    /**
     * 파일명에서 우선순위 결정
     */
    fun determinePriority(filename: String): Int {
      return PRIORITY_MAP[filename.lowercase()] ?: 0
    }
  }
}
