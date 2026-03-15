package com.jongmin.ai.multiagent.skill.model

/**
 * 스킬 스크립트 정의
 * Agent Skills 스펙의 scripts/ 폴더 내 개별 스크립트 표현
 */
data class SkillScript(
  // 파일명 (키로도 사용)
  val filename: String = "",              // "run.py", "validate.sh"

  // 스크립트 언어
  val language: ScriptLanguage = ScriptLanguage.BASH,  // PYTHON, BASH, JAVASCRIPT

  // 스크립트 내용
  val content: String = "",

  // 메인 실행 스크립트 여부 (entrypoint)
  val entrypoint: Boolean = false,

  // 스크립트 설명 (선택)
  val description: String? = null,
) {
  companion object {
    // entrypoint로 간주되는 파일명 패턴
    private val ENTRYPOINT_NAMES = setOf(
      "run", "main", "index", "entrypoint", "execute"
    )

    /**
     * 파일명이 entrypoint 패턴인지 확인
     */
    fun isEntrypointFilename(filename: String): Boolean {
      val baseName = filename.substringBeforeLast(".")
      return baseName.lowercase() in ENTRYPOINT_NAMES
    }
  }
}
