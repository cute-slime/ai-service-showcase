package com.jongmin.ai.multiagent.skill.model

/**
 * 스킬 호환성 정보
 * Agent Skills 스펙의 compatibility 필드 확장
 *
 * 오픈 스펙에서는 문자열 형태지만,
 * 구조화된 관리를 위해 객체로 확장
 */
data class SkillCompatibility(
  // 지원 제품 목록
  val requiredProducts: List<String>? = null,      // ["claude-code", "cursor"]

  // 필요 패키지/환경
  val requiredPackages: List<String>? = null,      // ["python>=3.10", "node>=18"]

  // 네트워크 접근 필요 여부
  val networkAccess: Boolean = false,

  // 기타 요구사항 (자유 텍스트)
  val customRequirements: List<String>? = null,
) {
  /**
   * 오픈 스펙 형식의 문자열로 변환
   * "Requires git, docker, jq, and access to the internet"
   */
  fun toSpecString(): String {
    val parts = mutableListOf<String>()

    requiredProducts?.let {
      if (it.isNotEmpty()) parts.add("Designed for ${it.joinToString(", ")}")
    }

    requiredPackages?.let {
      if (it.isNotEmpty()) parts.add("Requires ${it.joinToString(", ")}")
    }

    if (networkAccess) {
      parts.add("Network access required")
    }

    customRequirements?.let {
      parts.addAll(it)
    }

    return parts.joinToString(". ")
  }

  companion object {
    /**
     * 오픈 스펙 형식의 문자열에서 파싱
     */
    fun fromSpecString(spec: String): SkillCompatibility {
      val lines = spec.lines().map { it.trim() }.filter { it.isNotEmpty() }

      val hasNetwork = lines.any {
        it.contains("network", ignoreCase = true) ||
          it.contains("internet", ignoreCase = true)
      }

      return SkillCompatibility(
        networkAccess = hasNetwork,
        customRequirements = lines.ifEmpty { null },
      )
    }
  }
}
