package com.jongmin.ai.multiagent.skill.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 지원 스크립트 언어
 * Agent Skills 스펙에서 scripts/ 폴더의 스크립트 언어 타입
 */
enum class ScriptLanguage(private val typeCode: Int) {
  PYTHON(1),
  BASH(2),
  JAVASCRIPT(3),
  TYPESCRIPT(4),
  KOTLIN(5),
  GO(6),
  JAVA(7),
  GROOVY(8),
  JSHELL(9),
  ;

  companion object {
    private val map = entries.associateBy(ScriptLanguage::typeCode)
    private val extMap = mapOf(
      "py" to PYTHON,
      "sh" to BASH,
      "bash" to BASH,
      "js" to JAVASCRIPT,
      "ts" to TYPESCRIPT,
      "kt" to KOTLIN,
      "kts" to KOTLIN,
      "go" to GO,
      "java" to JAVA,
      "groovy" to GROOVY,
      "jsh" to JSHELL,
    )

    @JsonCreator
    fun getType(value: Int): ScriptLanguage = map[value] ?: BASH

    /**
     * 파일 확장자로 언어 감지
     */
    fun fromExtension(filename: String): ScriptLanguage {
      val ext = filename.substringAfterLast(".", "").lowercase()
      return extMap[ext] ?: BASH
    }
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
