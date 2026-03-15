package com.jongmin.ai.multiagent.skill.backoffice.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JBeanUtils.merge
import com.jongmin.ai.multiagent.skill.backoffice.dto.*
import com.jongmin.ai.multiagent.skill.entity.SkillDefinitionEntity
import com.jongmin.ai.multiagent.skill.model.*
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import com.jongmin.ai.multiagent.skill.validator.SkillNameValidator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.format.DateTimeFormatter

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 정의 백오피스 서비스
 * CRUD + 조회 지원
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class BoSkillDefinitionService(
  private val skillRepository: SkillDefinitionRepository,
  private val zipUploadService: BoSkillZipUploadService,
) {

  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  // ========== 생성 ==========

  /**
   * 스킬 생성
   */
  @Transactional
  fun create(session: JSession, request: BoCreateSkillRequest): BoSkillDetailResponse {
    kLogger.info { "스킬 생성 - name: ${request.name}, admin: ${session.username}" }

    // 1. name 검증 (Agent Skills 스펙)
    SkillNameValidator.validate(request.name)

    // 2. 중복 체크
    val existing = skillRepository.findByAccountIdAndNameAndStatusNot(
      session.accountId,
      request.name,
      StatusType.DELETED
    )
    require(existing == null) { "Skill already exists: ${request.name}" }

    // 3. 스크립트 변환
    val scripts = request.scripts?.associate { script ->
      val language = script.language
        ?: ScriptLanguage.fromExtension(script.filename)
      script.filename to SkillScript(
        filename = script.filename,
        language = language,
        content = script.content,
        entrypoint = script.entrypoint,
        description = script.description,
      )
    } ?: emptyMap()

    // 4. 참조문서 변환
    val references = request.references?.associate { ref ->
      ref.filename to SkillReference(
        filename = ref.filename,
        content = ref.content,
        loadOnDemand = ref.loadOnDemand,
        priority = ref.priority,
      )
    } ?: emptyMap()

    // 5. Entity 생성
    val entity = SkillDefinitionEntity(
      accountId = session.accountId,
      ownerId = session.accountId,  // memberId 대신 accountId 사용
      name = request.name,
      description = request.description,
      license = request.license,
      compatibility = request.compatibility?.toModel(),
      metadata = request.metadata ?: emptyMap(),
      allowedTools = request.allowedTools,
      body = request.body,
      scripts = scripts,
      references = references,
      assets = emptyMap(),  // 에셋은 별도 API로 관리 (파일 업로드 필요)
      networkPolicy = request.networkPolicy,
      allowedDomains = request.allowedDomains,
      // Phase 4: 트리거 전략 설정
      triggerStrategy = request.triggerStrategy,
      llmTriggerConfig = request.llmTriggerConfig?.toModel(),
    )

    val saved = skillRepository.save(entity)
    kLogger.info { "스킬 생성 완료 - id: ${saved.id}, name: ${saved.name}" }

    return saved.toDetailResponse()
  }

  // ========== 수정 ==========

  /**
   * 스킬 수정 (PATCH)
   */
  @Transactional
  fun patch(session: JSession, data: Map<String, Any>): Map<String, Any?> {
    kLogger.info { "스킬 수정 - id: ${data["id"]}, admin: ${session.username}" }

    val id = (data["id"] as Number).toLong()
    val target = skillRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Skill not found: $id") }

    // name 변경 시 검증
    data["name"]?.let { newName ->
      SkillNameValidator.validate(newName.toString())

      // 중복 체크 (자신 제외)
      val existing = skillRepository.findByAccountIdAndNameAndStatusNot(
        target.accountId,
        newName.toString(),
        StatusType.DELETED
      )
      if (existing != null && existing.id != id) {
        throw IllegalArgumentException("Skill name already exists: $newName")
      }
    }

    // compatibility 변환 처리
    val processedData = data.toMutableMap()
    @Suppress("UNCHECKED_CAST")
    (data["compatibility"] as? Map<*, *>)?.let { compatMap ->
      val compat = compatMap as Map<String, Any?>
      processedData["compatibility"] = SkillCompatibility(
        requiredProducts = compat["requiredProducts"] as? List<String>,
        requiredPackages = compat["requiredPackages"] as? List<String>,
        networkAccess = compat["networkAccess"] as? Boolean == true,
        customRequirements = compat["customRequirements"] as? List<String>,
      )
    }

    // scripts 변환 처리 (List → Map)
    @Suppress("UNCHECKED_CAST")
    (data["scripts"] as? List<Map<String, Any?>>)?.let { scriptsList ->
      processedData["scripts"] = scriptsList.associate { script ->
        val filename = script["filename"] as String
        val language = script["language"]?.let {
          ScriptLanguage.valueOf(it.toString())
        } ?: ScriptLanguage.fromExtension(filename)

        filename to SkillScript(
          filename = filename,
          language = language,
          content = script["content"] as? String ?: "",
          entrypoint = script["entrypoint"] as? Boolean == true,
          description = script["description"] as? String,
        )
      }
    }

    // references 변환 처리 (List → Map)
    @Suppress("UNCHECKED_CAST")
    (data["references"] as? List<Map<String, Any?>>)?.let { refsList ->
      processedData["references"] = refsList.associate { ref ->
        val filename = ref["filename"] as String
        filename to SkillReference(
          filename = filename,
          content = ref["content"] as? String ?: "",
          loadOnDemand = ref["loadOnDemand"] as? Boolean != false,
          priority = (ref["priority"] as? Number)?.toInt() ?: 0,
        )
      }
    }

    // assets 변환 처리 (List → Map)
    @Suppress("UNCHECKED_CAST")
    (data["assets"] as? List<Map<String, Any?>>)?.let { assetsList ->
      processedData["assets"] = assetsList.associate { asset ->
        val filename = asset["filename"] as String
        val assetType = asset["type"]?.let {
          try {
            AssetType.valueOf(it.toString())
          } catch (_: IllegalArgumentException) {
            AssetType.fromExtension(filename)
          }
        } ?: AssetType.fromExtension(filename)

        filename to SkillAsset(
          filename = filename,
          type = assetType,
          content = asset["content"] as? String,
          path = asset["path"] as? String,
          mimeType = asset["mimeType"] as? String ?: SkillAsset.detectMimeType(filename),
        )
      }
    }

    // Phase 4: triggerStrategy 변환 처리
    (data["triggerStrategy"] as? String)?.let { strategyStr ->
      processedData["triggerStrategy"] = try {
        TriggerStrategy.valueOf(strategyStr.uppercase())
      } catch (_: IllegalArgumentException) {
        TriggerStrategy.RULE_BASED
      }
    }

    // Phase 4: llmTriggerConfig 변환 처리
    @Suppress("UNCHECKED_CAST")
    (data["llmTriggerConfig"] as? Map<String, Any?>)?.let { configMap ->
      processedData["llmTriggerConfig"] = LlmTriggerConfig(
        modelId = configMap["modelId"] as? String,
        confidenceThreshold = (configMap["confidenceThreshold"] as? Number)?.toDouble() ?: 0.7,
        maxTokens = (configMap["maxTokens"] as? Number)?.toInt() ?: 500,
        customPrompt = configMap["customPrompt"] as? String,
        timeoutMs = (configMap["timeoutMs"] as? Number)?.toLong() ?: 10000,
        fallbackToRuleOnError = configMap["fallbackToRuleOnError"] as? Boolean != false,
      )
    }

    return merge(
      processedData,
      target,
      "id", "accountId", "ownerId", "createdAt", "updatedAt"
    )
  }

  // ========== 삭제 ==========

  /**
   * 스킬 삭제 (소프트 삭제)
   */
  @Transactional
  fun delete(session: JSession, id: Long) {
    kLogger.info { "스킬 삭제 - id: $id, admin: ${session.username}" }

    val target = skillRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Skill not found: $id") }

    // 소프트 삭제
    target.status = StatusType.DELETED
    kLogger.info { "스킬 삭제 완료 - id: $id, name: ${target.name}" }
  }

  /**
   * 스킬 목록 조회
   */
  fun list(
    session: JSession,
    accountId: Long?,
    keyword: String?,
    tags: List<String>?,
    pageable: Pageable,
  ): Page<BoSkillListResponse> {
    kLogger.info { "스킬 목록 조회 - accountId: $accountId, keyword: $keyword, admin: ${session.username}" }

    // accountId 필터 (없으면 세션의 accountId 사용)
    val targetAccountId = accountId ?: session.accountId

    val skillPage = skillRepository.findByAccountIdAndStatusNot(
      targetAccountId,
      StatusType.DELETED,
      pageable
    )

    // keyword, tags 필터 (메모리 필터링 - 추후 QueryDSL로 개선 가능)
    var filtered = skillPage.content

    keyword?.let { kw ->
      filtered = filtered.filter {
        it.name.contains(kw, ignoreCase = true) ||
            it.description.contains(kw, ignoreCase = true)
      }
    }

    tags?.let { tagList ->
      if (tagList.isNotEmpty()) {
        filtered = filtered.filter { entity ->
          @Suppress("UNCHECKED_CAST")
          val entityTags = entity.metadata["tags"] as? List<String> ?: emptyList()
          tagList.any { tag -> entityTags.contains(tag) }
        }
      }
    }

    val responses = filtered.map { it.toListResponse() }

    return PageImpl(responses, pageable, skillPage.totalElements)
  }

  /**
   * 스킬 상세 조회
   * scripts, references, assets 전체 데이터 포함
   */
  fun get(session: JSession, id: Long): BoSkillDetailResponse {
    kLogger.info { "스킬 상세 조회 - id: $id, admin: ${session.username}" }

    val skill = skillRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Skill not found: $id") }

    return skill.toDetailResponse()
  }

  // ========== ZIP 업로드 ==========

  /**
   * 스킬 존재 여부 확인
   * ZIP 업로드 전 FE에서 호출하여 덮어쓰기 경고 표시용
   */
  fun checkExists(session: JSession, name: String): SkillExistsResponse {
    return zipUploadService.checkExists(session, name)
  }

  /**
   * ZIP 파일 업로드 및 스킬 생성/업데이트
   */
  @Transactional
  fun uploadZip(session: JSession, file: MultipartFile): SkillUploadResponse {
    return zipUploadService.uploadZip(session, file)
  }

  // ========== Private Helpers ==========

  private fun SkillDefinitionEntity.toListResponse(): BoSkillListResponse {
    @Suppress("UNCHECKED_CAST")
    val tags = metadata["tags"] as? List<String> ?: emptyList()

    return BoSkillListResponse(
      id = id,
      name = name,
      description = description,
      license = license,
      scriptsCount = scripts.size,
      referencesCount = references.size,
      assetsCount = assets.size,
      tags = tags,
      triggerStrategy = triggerStrategy,  // Phase 4
      status = status.value(),
      createdAt = createdAt.format(dateFormatter),
      updatedAt = updatedAt?.format(dateFormatter) ?: "",
    )
  }

  private fun SkillDefinitionEntity.toDetailResponse(): BoSkillDetailResponse {
    // scripts 변환 (코드 포함)
    val scriptResponses = scripts.map { (filename, script) ->
      BoSkillScriptDetailResponse(
        filename = filename,
        language = script.language.name,
        entrypoint = script.entrypoint,
        description = script.description,
        content = script.content,
        lineCount = script.content.lines().size,
      )
    }.sortedByDescending { it.entrypoint }

    // references 변환 (내용 포함)
    val referenceResponses = references.map { (filename, ref) ->
      BoSkillReferenceDetailResponse(
        filename = filename,
        loadOnDemand = ref.loadOnDemand,
        priority = ref.priority,
        content = ref.content,
      )
    }.sortedByDescending { it.priority }

    // assets 변환 (내용 포함)
    val assetResponses = assets.map { (filename, asset) ->
      BoSkillAssetDetailResponse(
        filename = filename,
        type = asset.type.name,
        mimeType = asset.mimeType,
        content = asset.content,
        path = asset.path,
      )
    }

    return BoSkillDetailResponse(
      id = id,
      accountId = accountId,
      ownerId = ownerId,
      name = name,
      description = description,
      license = license,
      compatibility = BoCompatibilityResponse.from(compatibility),
      metadata = metadata,
      allowedTools = allowedTools,
      body = body,
      scripts = scriptResponses,
      references = referenceResponses,
      assets = assetResponses,
      networkPolicy = networkPolicy,
      allowedDomains = allowedDomains,
      // Phase 4: 트리거 전략 설정
      triggerStrategy = triggerStrategy,
      llmTriggerConfig = BoLlmTriggerConfigResponse.from(llmTriggerConfig),
      status = status.value(),
      createdAt = createdAt.format(dateFormatter),
      updatedAt = updatedAt?.format(dateFormatter) ?: "",
    )
  }
}
