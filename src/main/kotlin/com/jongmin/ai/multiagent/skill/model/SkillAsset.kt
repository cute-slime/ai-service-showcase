package com.jongmin.ai.multiagent.skill.model

/**
 * 스킬 에셋
 * Agent Skills 스펙의 assets/ 폴더 내 정적 리소스 표현
 */
data class SkillAsset(
  // 파일명 (키로도 사용)
  val filename: String = "",              // "template.json", "logo.png"

  // 에셋 타입
  val type: AssetType = AssetType.OTHER,

  // 텍스트 기반 에셋 내용 (JSON, YAML 등)
  val content: String? = null,

  // 바이너리 에셋 경로 (S3 URL 등)
  val path: String? = null,

  // MIME 타입
  val mimeType: String? = null,
) {
  companion object {
    // 텍스트 파일로 간주되는 확장자
    private val TEXT_EXTENSIONS = setOf(
      "txt", "md", "json", "yaml", "yml", "xml",
      "csv", "html", "css", "js", "ts", "py", "sh"
    )

    // MIME 타입 매핑
    private val MIME_MAP = mapOf(
      "json" to "application/json",
      "yaml" to "application/x-yaml",
      "yml" to "application/x-yaml",
      "xml" to "application/xml",
      "png" to "image/png",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "gif" to "image/gif",
      "svg" to "image/svg+xml",
      "webp" to "image/webp",
      "csv" to "text/csv",
      "txt" to "text/plain",
      "md" to "text/markdown",
    )

    /**
     * 파일이 텍스트 파일인지 확인
     */
    fun isTextFile(filename: String): Boolean {
      val ext = filename.substringAfterLast(".", "").lowercase()
      return ext in TEXT_EXTENSIONS
    }

    /**
     * 파일 확장자에서 MIME 타입 추론
     */
    fun detectMimeType(filename: String): String? {
      val ext = filename.substringAfterLast(".", "").lowercase()
      return MIME_MAP[ext]
    }
  }
}
