package com.jongmin.ai.multiagent.skill.loader

import com.jongmin.ai.multiagent.skill.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.yaml.snakeyaml.Yaml
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 로더
 * 파일 시스템의 스킬 폴더 구조를 SkillDefinition으로 로드
 *
 * Agent Skills 스펙 지원:
 * skill-name/
 * ├── SKILL.md          (필수)
 * ├── scripts/          (선택)
 * ├── references/       (선택)
 * └── assets/           (선택)
 */
@Component
class SkillLoader(
  private val objectMapper: ObjectMapper,
) {

  private val yaml = Yaml()

  /**
   * 디렉토리에서 스킬 로드
   * @param skillDir 스킬 디렉토리 경로 (skill-name/)
   */
  fun loadFromDirectory(skillDir: Path): SkillDefinition {
    kLogger.info { "스킬 로드 시작 - path: $skillDir" }

    // 1. SKILL.md 파싱
    val skillMdPath = skillDir.resolve("SKILL.md")
    require(Files.exists(skillMdPath)) { "SKILL.md not found in $skillDir" }

    val (frontmatter, body) = parseSkillMd(skillMdPath)

    // 2. 디렉토리명과 name 일치 확인 (Agent Skills 스펙)
    val dirName = skillDir.fileName.toString()
    if (dirName != frontmatter.name) {
      kLogger.warn { "디렉토리명($dirName)과 스킬명(${frontmatter.name})이 다릅니다. 스펙에서는 일치를 권장합니다." }
    }

    // 3. LICENSE 파일 로드 (전체 텍스트)
    val licenseContent = loadLicenseFile(skillDir)
    val finalFrontmatter = if (licenseContent != null) {
      frontmatter.copy(license = licenseContent)
    } else {
      frontmatter
    }

    // 4. scripts/ 로드
    val scripts = loadScripts(skillDir.resolve("scripts"))

    // 5. references/ 로드
    val references = loadReferences(skillDir.resolve("references"))

    // 6. assets/ 로드
    val assets = loadAssets(skillDir.resolve("assets"))

    kLogger.info {
      "스킬 로드 완료 - name: ${finalFrontmatter.name}, " +
        "scripts: ${scripts.size}, refs: ${references.size}, assets: ${assets.size}, " +
        "license: ${if (licenseContent != null) "${licenseContent.length}자" else "없음"}"
    }

    return SkillDefinition(finalFrontmatter, body, scripts, references, assets)
  }

  /**
   * JSON/Map에서 스킬 로드 (DB에서 조회 시)
   */
  fun loadFromMap(data: Map<String, Any>): SkillDefinition {
    return objectMapper.convertValue(data, SkillDefinition::class.java)
  }

  /**
   * 스킬을 Map으로 변환 (DB 저장용)
   */
  fun toMap(skill: SkillDefinition): Map<String, Any> {
    return objectMapper.convertValue(skill, object : TypeReference<Map<String, Any>>() {})
  }

  /**
   * 디렉토리 내 모든 스킬 로드
   */
  fun loadAllFromDirectory(skillsRootDir: Path): List<SkillDefinition> {
    if (!Files.exists(skillsRootDir)) {
      kLogger.warn { "스킬 루트 디렉토리 없음: $skillsRootDir" }
      return emptyList()
    }

    return Files.list(skillsRootDir)
      .filter { Files.isDirectory(it) }
      .filter { Files.exists(it.resolve("SKILL.md")) }
      .map { dir ->
        try {
          loadFromDirectory(dir)
        } catch (e: Exception) {
          kLogger.error(e) { "스킬 로드 실패 - dir: $dir" }
          null
        }
      }
      .filter { it != null }
      .map { it!! }
      .collect(Collectors.toList())
  }

  // ========== Private Helpers ==========

  /**
   * SKILL.md 파싱 (YAML frontmatter + Markdown body)
   */
  private fun parseSkillMd(path: Path): Pair<SkillFrontmatter, String> {
    val content = Files.readString(path)
    val parts = content.split(Regex("^---\\s*$", RegexOption.MULTILINE), limit = 3)

    require(parts.size >= 3) {
      "Invalid SKILL.md format - expected YAML frontmatter between --- markers"
    }

    val frontmatterYaml = parts[1].trim()
    val body = parts[2].trim()

    // YAML 파싱
    @Suppress("UNCHECKED_CAST")
    val frontmatterMap = yaml.load<Map<String, Any>>(frontmatterYaml) as Map<String, Any>

    val frontmatter = SkillFrontmatter(
      name = frontmatterMap["name"] as? String
        ?: throw IllegalArgumentException("name is required in frontmatter"),
      description = frontmatterMap["description"] as? String
        ?: throw IllegalArgumentException("description is required in frontmatter"),
      license = frontmatterMap["license"] as? String,
      compatibility = parseCompatibility(frontmatterMap["compatibility"]),
      metadata = parseMetadata(frontmatterMap["metadata"]),
      allowedTools = parseAllowedTools(frontmatterMap["allowed-tools"]),
    )

    return frontmatter to body
  }

  /**
   * compatibility 파싱
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseCompatibility(value: Any?): SkillCompatibility? {
    if (value == null) return null

    return when (value) {
      is Map<*, *> -> {
        val map = value as Map<String, Any>
        SkillCompatibility(
          requiredProducts = map["requiredProducts"] as? List<String>,
          requiredPackages = map["requiredPackages"] as? List<String>,
          networkAccess = map["networkAccess"] as? Boolean ?: false,
          customRequirements = map["customRequirements"] as? List<String>,
        )
      }

      is String -> SkillCompatibility.fromSpecString(value)
      else -> null
    }
  }

  /**
   * metadata 파싱
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseMetadata(value: Any?): Map<String, Any> {
    return when (value) {
      is Map<*, *> -> value as Map<String, Any>
      else -> emptyMap()
    }
  }

  /**
   * allowed-tools 파싱 (공백 구분 문자열 또는 리스트)
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseAllowedTools(value: Any?): List<String>? {
    return when (value) {
      is String -> value.split(Regex("\\s+")).filter { it.isNotBlank() }
      is List<*> -> (value as List<Any>).mapNotNull { it?.toString() }
      else -> null
    }
  }

  /**
   * scripts/ 폴더 로드
   */
  private fun loadScripts(scriptsDir: Path): Map<String, SkillScript> {
    if (!Files.exists(scriptsDir)) return emptyMap()

    return Files.list(scriptsDir)
      .filter { Files.isRegularFile(it) }
      .collect(
        Collectors.toMap(
          { it.fileName.toString() },
          { path ->
            val filename = path.fileName.toString()
            SkillScript(
              filename = filename,
              language = ScriptLanguage.fromExtension(filename),
              content = Files.readString(path),
              entrypoint = SkillScript.isEntrypointFilename(filename),
              description = null,
            )
          }
        )
      )
  }

  /**
   * references/ 폴더 로드
   */
  private fun loadReferences(refsDir: Path): Map<String, SkillReference> {
    if (!Files.exists(refsDir)) return emptyMap()

    return Files.list(refsDir)
      .filter { it.fileName.toString().endsWith(".md") }
      .collect(
        Collectors.toMap(
          { it.fileName.toString() },
          { path ->
            val filename = path.fileName.toString()
            SkillReference(
              filename = filename,
              content = Files.readString(path),
              loadOnDemand = true,
              priority = SkillReference.determinePriority(filename),
            )
          }
        )
      )
  }

  /**
   * assets/ 폴더 로드
   */
  private fun loadAssets(assetsDir: Path): Map<String, SkillAsset> {
    if (!Files.exists(assetsDir)) return emptyMap()

    return Files.list(assetsDir)
      .filter { Files.isRegularFile(it) }
      .collect(
        Collectors.toMap(
          { it.fileName.toString() },
          { path ->
            val filename = path.fileName.toString()
            val isText = SkillAsset.isTextFile(filename)
            SkillAsset(
              filename = filename,
              type = AssetType.fromExtension(filename),
              content = if (isText) Files.readString(path) else null,
              path = if (!isText) path.toAbsolutePath().toString() else null,
              mimeType = SkillAsset.detectMimeType(filename),
            )
          }
        )
      )
  }

  /**
   * LICENSE 파일 로드 (전체 텍스트)
   *
   * 지원 파일명 (우선순위 순):
   * - LICENSE
   * - LICENSE.txt
   * - LICENSE.md
   * - license
   * - license.txt
   * - license.md
   *
   * @return 라이선스 파일 내용, 파일이 없으면 null
   */
  private fun loadLicenseFile(skillDir: Path): String? {
    val licenseFileNames = listOf(
      "LICENSE",
      "LICENSE.txt",
      "LICENSE.md",
      "license",
      "license.txt",
      "license.md",
    )

    for (filename in licenseFileNames) {
      val licensePath = skillDir.resolve(filename)
      if (Files.exists(licensePath) && Files.isRegularFile(licensePath)) {
        kLogger.debug { "LICENSE 파일 발견: $filename" }
        return Files.readString(licensePath)
      }
    }

    kLogger.debug { "LICENSE 파일 없음 - skillDir: $skillDir" }
    return null
  }
}
