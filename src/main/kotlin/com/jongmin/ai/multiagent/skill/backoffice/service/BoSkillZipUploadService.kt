package com.jongmin.ai.multiagent.skill.backoffice.service

import com.jongmin.ai.multiagent.skill.backoffice.dto.SkillExistsInfo
import com.jongmin.ai.multiagent.skill.backoffice.dto.SkillExistsResponse
import com.jongmin.ai.multiagent.skill.backoffice.dto.SkillUploadAction
import com.jongmin.ai.multiagent.skill.backoffice.dto.SkillUploadInfo
import com.jongmin.ai.multiagent.skill.backoffice.dto.SkillUploadResponse
import com.jongmin.ai.multiagent.skill.entity.SkillDefinitionEntity
import com.jongmin.ai.multiagent.skill.loader.SkillLoader
import com.jongmin.ai.multiagent.skill.model.SkillDefinition
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import com.jongmin.ai.multiagent.skill.validator.SkillNameValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoSkillZipUploadService(
  private val skillRepository: SkillDefinitionRepository,
  private val skillLoader: SkillLoader,
) {
  companion object {
    private val kLogger = KotlinLogging.logger {}

    private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB
    private const val ZIP_CONTENT_TYPE = "application/zip"
    private const val ZIP_EXTENSION = ".zip"
  }

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  fun checkExists(session: JSession, name: String): SkillExistsResponse {
    kLogger.debug { "스킬 존재 여부 확인 - name: $name, admin: ${session.username}" }

    val existing = skillRepository.findByAccountIdAndNameAndStatusNot(
      session.accountId,
      name,
      StatusType.DELETED
    )

    return if (existing != null) {
      SkillExistsResponse(
        exists = true,
        skill = SkillExistsInfo(
          id = existing.id,
          name = existing.name,
          description = existing.description,
          updatedAt = existing.updatedAt?.format(dateFormatter),
        )
      )
    } else {
      SkillExistsResponse(exists = false)
    }
  }

  @Transactional
  fun uploadZip(session: JSession, file: MultipartFile): SkillUploadResponse {
    kLogger.info { "스킬 ZIP 업로드 - filename: ${file.originalFilename}, size: ${file.size}, admin: ${session.username}" }

    validateZipFile(file)
    val tempDir = extractZipToTempDir(file)

    try {
      val skillDir = findSkillDirectory(tempDir)
      val skillDefinition = skillLoader.loadFromDirectory(skillDir)

      SkillNameValidator.validate(skillDefinition.frontmatter.name)

      val skillDirName = skillDir.fileName.toString()
      val skillName = skillDefinition.frontmatter.name
      if (skillDirName != skillName) {
        throw BadRequestException(
          "폴더명($skillDirName)과 스킬명($skillName)이 일치하지 않습니다. " +
              "ZIP 파일 내 폴더명을 스킬명과 동일하게 변경해주세요."
        )
      }

      val existing = skillRepository.findByAccountIdAndNameAndStatusNot(
        session.accountId,
        skillDefinition.frontmatter.name,
        StatusType.DELETED
      )

      val (entity, action) = if (existing != null) {
        updateSkillFromDefinition(existing, skillDefinition) to SkillUploadAction.UPDATED
      } else {
        createSkillFromDefinition(session, skillDefinition) to SkillUploadAction.CREATED
      }

      val actionMessage = if (action == SkillUploadAction.CREATED) {
        "스킬이 성공적으로 생성되었습니다."
      } else {
        "스킬이 성공적으로 업데이트되었습니다."
      }

      kLogger.info { "스킬 ZIP 업로드 완료 - id: ${entity.id}, name: ${entity.name}, action: $action" }

      return SkillUploadResponse(
        success = true,
        action = action,
        skill = SkillUploadInfo(
          id = entity.id,
          name = entity.name,
          description = entity.description,
          license = entity.license,
          scriptsCount = entity.scripts.size,
          referencesCount = entity.references.size,
          assetsCount = entity.assets.size,
        ),
        message = actionMessage,
      )
    } finally {
      deleteRecursively(tempDir)
    }
  }

  private fun validateZipFile(file: MultipartFile) {
    if (file.size > MAX_FILE_SIZE) {
      throw BadRequestException("파일 크기가 50MB를 초과합니다: ${file.size / (1024 * 1024)}MB")
    }

    val filename = file.originalFilename?.lowercase() ?: ""
    if (!filename.endsWith(ZIP_EXTENSION)) {
      throw BadRequestException("ZIP 파일만 업로드 가능합니다: $filename")
    }

    val contentType = file.contentType?.lowercase()
    if (contentType != null &&
      contentType != ZIP_CONTENT_TYPE &&
      contentType != "application/x-zip-compressed" &&
      contentType != "application/octet-stream"
    ) {
      kLogger.warn { "예상치 못한 Content-Type: $contentType (파일명: $filename)" }
    }
  }

  private fun extractZipToTempDir(file: MultipartFile): Path {
    val tempDir = Files.createTempDirectory("skill-upload-")
    kLogger.debug { "임시 디렉토리 생성: $tempDir" }

    try {
      ZipInputStream(file.inputStream).use { zipIn ->
        var entry = zipIn.nextEntry
        var hasSkillMd = false

        while (entry != null) {
          val entryPath = tempDir.resolve(entry.name).normalize()

          if (!entryPath.startsWith(tempDir)) {
            throw BadRequestException("잘못된 ZIP 엔트리: ${entry.name}")
          }

          if (entry.isDirectory) {
            Files.createDirectories(entryPath)
          } else {
            Files.createDirectories(entryPath.parent)
            Files.newOutputStream(entryPath).use { out ->
              zipIn.copyTo(out)
            }

            if (entry.name == "SKILL.md" || entry.name.endsWith("/SKILL.md")) {
              hasSkillMd = true
            }
          }

          zipIn.closeEntry()
          entry = zipIn.nextEntry
        }

        if (!hasSkillMd) {
          throw BadRequestException("SKILL.md 파일이 없습니다. ZIP 파일 내에 SKILL.md가 포함되어야 합니다.")
        }
      }
    } catch (e: BadRequestException) {
      deleteRecursively(tempDir)
      throw e
    } catch (e: Exception) {
      deleteRecursively(tempDir)
      throw BadRequestException("ZIP 압축 해제 실패: ${e.message}")
    }

    return tempDir
  }

  private fun createSkillFromDefinition(session: JSession, definition: SkillDefinition): SkillDefinitionEntity {
    val entity = SkillDefinitionEntity.from(
      accountId = session.accountId,
      ownerId = session.accountId,
      definition = definition,
    )

    return skillRepository.save(entity)
  }

  private fun updateSkillFromDefinition(
    existing: SkillDefinitionEntity,
    definition: SkillDefinition
  ): SkillDefinitionEntity {
    existing.description = definition.frontmatter.description
    existing.license = definition.frontmatter.license
    existing.compatibility = definition.frontmatter.compatibility
    existing.metadata = definition.frontmatter.metadata
    existing.allowedTools = definition.frontmatter.allowedTools
    existing.body = definition.body
    existing.scripts = definition.scripts
    existing.references = definition.references
    existing.assets = definition.assets

    return existing
  }

  private fun deleteRecursively(path: Path) {
    try {
      if (Files.exists(path)) {
        Files.walk(path)
          .sorted(Comparator.reverseOrder())
          .forEach { Files.deleteIfExists(it) }
      }
    } catch (e: Exception) {
      kLogger.warn(e) { "임시 디렉토리 삭제 실패: $path" }
    }
  }

  private fun findSkillDirectory(tempDir: Path): Path {
    val rootSkillMd = tempDir.resolve("SKILL.md")
    if (Files.exists(rootSkillMd)) {
      throw BadRequestException(
        "ZIP 파일은 스킬명과 동일한 폴더로 감싸져 있어야 합니다. " +
            "예: my-skill.zip 내에 my-skill/SKILL.md 구조"
      )
    }

    val children = Files.list(tempDir)
      .filter { !it.fileName.toString().startsWith("__") }
      .filter { !it.fileName.toString().startsWith(".") }
      .toList()

    val directories = children.filter { Files.isDirectory(it) }

    if (directories.isEmpty()) {
      throw BadRequestException(
        "ZIP 파일 내에 스킬 폴더가 없습니다. " +
            "스킬명과 동일한 폴더로 감싸서 압축해주세요."
      )
    }
    if (directories.size > 1) {
      val folderNames = directories.joinToString(", ") { it.fileName.toString() }
      throw BadRequestException(
        "ZIP 파일 내에 여러 폴더가 있습니다: [$folderNames]. " +
            "단일 스킬 폴더만 허용됩니다."
      )
    }

    val skillDir = directories.first()
    if (!Files.exists(skillDir.resolve("SKILL.md"))) {
      throw BadRequestException("${skillDir.fileName} 폴더 내에 SKILL.md 파일이 없습니다.")
    }

    kLogger.debug { "스킬 디렉토리 발견: ${skillDir.fileName}" }
    return skillDir
  }
}
