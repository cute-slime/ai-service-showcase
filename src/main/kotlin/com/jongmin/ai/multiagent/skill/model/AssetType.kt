package com.jongmin.ai.multiagent.skill.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 에셋 타입
 * Agent Skills 스펙에서 assets/ 폴더의 리소스 타입
 */
enum class AssetType(private val typeCode: Int) {
  TEMPLATE(1),   // JSON, YAML 템플릿
  IMAGE(2),      // PNG, JPG, SVG
  DATA(3),       // CSV, JSON 데이터
  CONFIG(4),     // 설정 파일
  OTHER(99),     // 기타
  ;

  companion object {
    private val map = entries.associateBy(AssetType::typeCode)
    private val extMap = mapOf(
      "json" to TEMPLATE,
      "yaml" to TEMPLATE,
      "yml" to TEMPLATE,
      "png" to IMAGE,
      "jpg" to IMAGE,
      "jpeg" to IMAGE,
      "gif" to IMAGE,
      "svg" to IMAGE,
      "webp" to IMAGE,
      "csv" to DATA,
      "tsv" to DATA,
      "conf" to CONFIG,
      "ini" to CONFIG,
      "toml" to CONFIG,
    )

    @JsonCreator
    fun getType(value: Int): AssetType = map[value] ?: OTHER

    /**
     * 파일 확장자로 타입 감지
     */
    fun fromExtension(filename: String): AssetType {
      val ext = filename.substringAfterLast(".", "").lowercase()
      return extMap[ext] ?: OTHER
    }
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
