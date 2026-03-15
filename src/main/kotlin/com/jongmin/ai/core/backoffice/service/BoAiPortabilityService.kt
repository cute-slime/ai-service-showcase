package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.AiApiKeyRepository
import com.jongmin.ai.core.AiAssistantRepository
import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.core.backoffice.dto.PortableAiAgentData
import com.jongmin.ai.core.backoffice.dto.PortableAiApiKeyData
import com.jongmin.ai.core.backoffice.dto.PortableAiApiKeyRef
import com.jongmin.ai.core.backoffice.dto.PortableAiAssistantData
import com.jongmin.ai.core.backoffice.dto.PortableAiAssistantRef
import com.jongmin.ai.core.backoffice.dto.PortableAiModelData
import com.jongmin.ai.core.backoffice.dto.PortableAiModelRef
import com.jongmin.ai.core.backoffice.dto.PortableAiProviderData
import com.jongmin.ai.core.backoffice.dto.PortableBatchImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableImportAction
import com.jongmin.ai.core.backoffice.dto.PortableImportResponse
import com.jongmin.ai.core.backoffice.dto.PortableSkillData
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAgent
import com.jongmin.ai.core.backoffice.dto.request.CreateAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.CreateAiModel
import com.jongmin.ai.core.backoffice.dto.request.CreateAiProvider
import com.jongmin.ai.core.backoffice.dto.request.PatchAiAgent
import com.jongmin.ai.core.backoffice.dto.request.PatchAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.PatchAiModel
import com.jongmin.ai.core.backoffice.dto.request.PatchAiProvider
import com.jongmin.ai.core.platform.entity.AiAgent
import com.jongmin.ai.core.platform.entity.AiApiKey
import com.jongmin.ai.core.platform.entity.AiAssistant
import com.jongmin.ai.core.platform.entity.AiModel
import com.jongmin.ai.core.platform.entity.AiProvider
import com.jongmin.ai.core.platform.entity.QAiAgent.aiAgent
import com.jongmin.ai.core.platform.entity.QAiAssistant.aiAssistant
import com.jongmin.ai.core.platform.entity.QAiModel.aiModel
import com.jongmin.ai.core.platform.entity.QAiProvider.aiProvider
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoAssetRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoCreateSkillRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoLlmTriggerConfigRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoPatchSkillRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoReferenceRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoScriptRequest
import com.jongmin.ai.multiagent.skill.backoffice.service.BoSkillDefinitionService
import com.jongmin.ai.multiagent.skill.entity.SkillDefinitionEntity
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.jspring.web.entity.JSession
import org.jasypt.util.text.TextEncryptor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Service
class BoAiPortabilityService(
  private val objectMapper: ObjectMapper,
  private val textEncryptor: TextEncryptor,
  private val aiProviderRepository: AiProviderRepository,
  private val aiApiKeyRepository: AiApiKeyRepository,
  private val aiModelRepository: AiModelRepository,
  private val aiAssistantRepository: AiAssistantRepository,
  private val aiAgentRepository: com.jongmin.ai.core.AiAgentRepository,
  private val skillDefinitionRepository: SkillDefinitionRepository,
  private val boAiProviderService: BoAiProviderService,
  private val boAiModelService: BoAiModelService,
  private val boAiAssistantService: BoAiAssistantService,
  private val boAiAgentService: BoAiAgentService,
  private val boSkillDefinitionService: BoSkillDefinitionService,
) {

  @Transactional(readOnly = true)
  fun exportProvider(id: Long): PortableAiProviderData {
    val provider = aiProviderRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Provider not found: $id") }
    val apiKeys = aiApiKeyRepository.findAll()
      .filter { it.aiProviderId == provider.id && it.status != StatusType.DELETED }
      .map { PortableAiApiKeyData(key = textEncryptor.decrypt(it.encryptedKey)) }

    return PortableAiProviderData(
      name = provider.name,
      description = provider.description,
      baseUrl = provider.baseUrl,
      status = provider.status,
      apiKeys = apiKeys,
    )
  }

  @Transactional
  fun importProvider(session: JSession, dto: PortableAiProviderData): PortableImportResponse {
    if (dto.apiKeys.isEmpty()) {
      throw BadRequestException("apiKeys", "최소 1개 이상의 API Key가 필요합니다.")
    }

    val existing = aiProviderRepository.findOne(aiProvider.name.eq(dto.name)).orElse(null)
    val apiKeys = dto.apiKeys
      .map { CommonDto.LongKeyStringKey(id = null, key = it.key) }
      .toSet()

    return if (existing == null) {
      val created = boAiProviderService.create(
        session,
        CreateAiProvider(
          id = null,
          name = dto.name,
          description = dto.description,
          baseUrl = dto.baseUrl,
          status = dto.status ?: StatusType.ACTIVE,
          apiKeys = apiKeys,
        ),
        apiKeys,
      )

      PortableImportResponse(
        id = created.id ?: 0L,
        name = created.name.orEmpty(),
        action = PortableImportAction.CREATED,
      )
    } else {
      boAiProviderService.patch(
        session,
        objectMapper.convertValue(
          PatchAiProvider(
            id = existing.id,
            description = dto.description,
            baseUrl = dto.baseUrl,
            status = dto.status ?: existing.status,
            apiKeys = apiKeys,
          ),
          object : TypeReference<Map<String, Any?>>() {}
        ),
        apiKeys,
      )

      PortableImportResponse(
        id = existing.id,
        name = existing.name,
        action = PortableImportAction.UPDATED,
      )
    }
  }

  @Transactional
  fun importProviders(session: JSession, dtos: List<PortableAiProviderData>): PortableBatchImportResponse {
    require(dtos.isNotEmpty()) { "최소 1개 이상의 provider 데이터가 필요합니다." }
    val results = dtos.map { importProvider(session, it) }
    return PortableBatchImportResponse(importedCount = results.size, results = results)
  }

  @Transactional(readOnly = true)
  fun exportModel(id: Long): PortableAiModelData {
    val model = aiModelRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Model not found: $id") }
    val provider = resolveProvider(model.aiProviderId)

    return PortableAiModelData(
      providerName = provider.name,
      name = model.name,
      description = model.description,
      status = model.status,
      supportsReasoning = model.supportsReasoning,
      reasoningEffort = model.reasoningEffort,
      type = model.type,
      maxTokens = model.maxTokens,
      inputTokenPrice = model.inputTokenPrice,
      outputTokenPrice = model.outputTokenPrice,
      inputTokenPriceInService = model.inputTokenPriceInService,
      outputTokenPriceInService = model.outputTokenPriceInService,
    )
  }

  @Transactional
  fun importModel(session: JSession, dto: PortableAiModelData): PortableImportResponse {
    val provider = resolveProviderByName(dto.providerName)
    val existing = findModel(provider.id, dto.name, dto.reasoningEffort)

    return if (existing == null) {
      val created = boAiModelService.create(
        session,
        CreateAiModel(
          id = null,
          accountId = session.accountId,
          name = dto.name,
          description = dto.description,
          aiProviderId = provider.id,
          status = dto.status ?: StatusType.ACTIVE,
          supportsReasoning = dto.supportsReasoning,
          reasoningEffort = dto.reasoningEffort,
          type = dto.type,
          maxTokens = dto.maxTokens,
          inputTokenPrice = dto.inputTokenPrice,
          outputTokenPrice = dto.outputTokenPrice,
          inputTokenPriceInService = dto.inputTokenPriceInService,
          outputTokenPriceInService = dto.outputTokenPriceInService,
        ),
      )

      PortableImportResponse(
        id = created.id ?: 0L,
        name = created.name.orEmpty(),
        action = PortableImportAction.CREATED,
      )
    } else {
      if (existing.type != dto.type) {
        throw BadRequestException("type", "동일한 모델 키(provider/name/reasoning)에 서로 다른 type을 사용할 수 없습니다.")
      }

      boAiModelService.patch(
        session,
        objectMapper.convertValue(
          PatchAiModel(
            id = existing.id,
            name = dto.name,
            description = dto.description,
            status = dto.status ?: existing.status,
            supportsReasoning = dto.supportsReasoning,
            reasoningEffort = dto.reasoningEffort,
            maxTokens = dto.maxTokens,
            inputTokenPrice = dto.inputTokenPrice,
            outputTokenPrice = dto.outputTokenPrice,
            inputTokenPriceInService = dto.inputTokenPriceInService,
            outputTokenPriceInService = dto.outputTokenPriceInService,
          ),
          object : TypeReference<Map<String, Any?>>() {}
        ),
      )

      PortableImportResponse(
        id = existing.id,
        name = existing.name,
        action = PortableImportAction.UPDATED,
      )
    }
  }

  @Transactional
  fun importModels(session: JSession, dtos: List<PortableAiModelData>): PortableBatchImportResponse {
    require(dtos.isNotEmpty()) { "최소 1개 이상의 model 데이터가 필요합니다." }
    val results = dtos.map { importModel(session, it) }
    return PortableBatchImportResponse(importedCount = results.size, results = results)
  }

  @Transactional(readOnly = true)
  fun exportAssistant(id: Long): PortableAiAssistantData {
    val assistant = aiAssistantRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Assistant not found: $id") }
    val primaryModel = resolveModel(assistant.primaryModelId)
    val primaryProvider = resolveProvider(primaryModel.aiProviderId)
    val primaryApiKey = resolveApiKey(assistant.primaryApiKeyId)

    return PortableAiAssistantData(
      name = assistant.name,
      type = assistant.type,
      description = assistant.description,
      status = assistant.status,
      primaryModel = PortableAiModelRef(
        providerName = primaryProvider.name,
        name = primaryModel.name,
        reasoningEffort = primaryModel.reasoningEffort,
      ),
      primaryApiKey = PortableAiApiKeyRef(
        providerName = primaryProvider.name,
        key = textEncryptor.decrypt(primaryApiKey.encryptedKey),
      ),
      instructions = assistant.instructions,
      temperature = assistant.temperature,
      topP = assistant.topP,
      responseFormat = assistant.responseFormat,
      maxTokens = assistant.maxTokens,
    )
  }

  @Transactional
  fun importAssistant(session: JSession, dto: PortableAiAssistantData): PortableImportResponse {
    val primaryModel = resolveModelRef(dto.primaryModel)
    val primaryApiKey = resolveApiKeyRef(dto.primaryApiKey)
    boAiAssistantService.verifyModelAndKey(primaryModel.id, primaryApiKey.id)

    val existing = aiAssistantRepository.findOne(aiAssistant.name.eq(dto.name)).orElse(null)

    return if (existing == null) {
      val created = boAiAssistantService.create(
        session,
        CreateAiAssistant(
          id = null,
          accountId = null,
          ownerId = null,
          name = dto.name,
          type = dto.type,
          description = dto.description,
          primaryModelId = primaryModel.id,
          primaryApiKeyId = primaryApiKey.id,
          modelId = null,
          apiKeyId = null,
          instructions = dto.instructions,
          temperature = dto.temperature,
          topP = dto.topP,
          responseFormat = dto.responseFormat,
          maxTokens = dto.maxTokens,
          status = dto.status ?: StatusType.ACTIVE,
        ),
      )

      PortableImportResponse(
        id = created.id ?: 0L,
        name = created.name.orEmpty(),
        action = PortableImportAction.CREATED,
      )
    } else {
      boAiAssistantService.patch(
        session,
        objectMapper.convertValue(
          PatchAiAssistant(
            id = existing.id,
            name = dto.name,
            type = dto.type,
            description = dto.description,
            modelId = null,
            apiKeyId = null,
            primaryModelId = primaryModel.id,
            primaryApiKeyId = primaryApiKey.id,
            instructions = dto.instructions,
            temperature = dto.temperature,
            topP = dto.topP,
            responseFormat = dto.responseFormat,
            maxTokens = dto.maxTokens,
            status = dto.status ?: existing.status,
          ),
          object : TypeReference<Map<String, Any?>>() {}
        ),
      )

      PortableImportResponse(
        id = existing.id,
        name = existing.name,
        action = PortableImportAction.UPDATED,
      )
    }
  }

  @Transactional
  fun importAssistants(session: JSession, dtos: List<PortableAiAssistantData>): PortableBatchImportResponse {
    require(dtos.isNotEmpty()) { "최소 1개 이상의 assistant 데이터가 필요합니다." }
    val results = dtos.map { importAssistant(session, it) }
    return PortableBatchImportResponse(importedCount = results.size, results = results)
  }

  @Transactional(readOnly = true)
  fun exportAgent(id: Long): PortableAiAgentData {
    val agentEntity = aiAgentRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Agent not found: $id") }

    return PortableAiAgentData(
      name = agentEntity.name,
      description = agentEntity.description,
      status = agentEntity.status,
      workflow = exportWorkflow(agentEntity.workflow),
    )
  }

  @Transactional
  fun importAgent(session: JSession, dto: PortableAiAgentData): PortableImportResponse {
    val existing = aiAgentRepository.findOne(aiAgent.name.eq(dto.name)).orElse(null)
    val importedWorkflow = importWorkflow(dto.workflow)

    return if (existing == null) {
      val created = boAiAgentService.create(
        session,
        CreateAiAgent(
          id = null,
          accountId = null,
          ownerId = null,
          name = dto.name,
          type = null,
          description = dto.description,
          status = dto.status ?: StatusType.ACTIVE,
          workflow = emptyMap(),
        ),
      )

      boAiAgentService.patch(
        session,
        objectMapper.convertValue(
          PatchAiAgent(
            id = created.id,
            name = dto.name,
            type = null,
            description = dto.description,
            workflow = importedWorkflow,
            status = dto.status ?: StatusType.ACTIVE,
          ),
          object : TypeReference<Map<String, Any?>>() {}
        ),
      )

      PortableImportResponse(
        id = created.id ?: 0L,
        name = created.name.orEmpty(),
        action = PortableImportAction.CREATED,
      )
    } else {
      boAiAgentService.patch(
        session,
        objectMapper.convertValue(
          PatchAiAgent(
            id = existing.id,
            name = dto.name,
            type = null,
            description = dto.description,
            workflow = importedWorkflow,
            status = dto.status ?: existing.status,
          ),
          object : TypeReference<Map<String, Any?>>() {}
        ),
      )

      PortableImportResponse(
        id = existing.id,
        name = existing.name,
        action = PortableImportAction.UPDATED,
      )
    }
  }

  @Transactional
  fun importAgents(session: JSession, dtos: List<PortableAiAgentData>): PortableBatchImportResponse {
    require(dtos.isNotEmpty()) { "최소 1개 이상의 agent 데이터가 필요합니다." }
    val results = dtos.map { importAgent(session, it) }
    return PortableBatchImportResponse(importedCount = results.size, results = results)
  }

  @Transactional(readOnly = true)
  fun exportSkill(id: Long): PortableSkillData {
    val skill = skillDefinitionRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Skill not found: $id") }

    return PortableSkillData(
      name = skill.name,
      description = skill.description,
      license = skill.license,
      compatibility = skill.compatibility?.let {
        com.jongmin.ai.multiagent.skill.backoffice.dto.BoCompatibilityResponse.from(it)
      }?.let {
        com.jongmin.ai.multiagent.skill.backoffice.dto.BoCompatibilityRequest(
          requiredProducts = it.requiredProducts,
          requiredPackages = it.requiredPackages,
          networkAccess = it.networkAccess,
          customRequirements = it.customRequirements,
        )
      },
      metadata = skill.metadata,
      allowedTools = skill.allowedTools,
      body = skill.body,
      scripts = skill.scripts.values.map { script ->
        BoScriptRequest(
          filename = script.filename,
          language = script.language,
          content = script.content,
          entrypoint = script.entrypoint,
          description = script.description,
        )
      },
      references = skill.references.values.map { reference ->
        BoReferenceRequest(
          filename = reference.filename,
          content = reference.content,
          loadOnDemand = reference.loadOnDemand,
          priority = reference.priority,
        )
      },
      assets = skill.assets.values.map { asset ->
        BoAssetRequest(
          filename = asset.filename,
          type = asset.type.name,
          mimeType = asset.mimeType,
          content = asset.content,
          path = asset.path,
        )
      },
      networkPolicy = skill.networkPolicy,
      allowedDomains = skill.allowedDomains,
      triggerStrategy = skill.triggerStrategy,
      llmTriggerConfig = skill.llmTriggerConfig?.let { config ->
        BoLlmTriggerConfigRequest(
          modelId = config.modelId,
          confidenceThreshold = config.confidenceThreshold,
          maxTokens = config.maxTokens,
          customPrompt = config.customPrompt,
          timeoutMs = config.timeoutMs,
          fallbackToRuleOnError = config.fallbackToRuleOnError,
        )
      },
      status = skill.status.value(),
    )
  }

  @Transactional
  fun importSkill(session: JSession, dto: PortableSkillData): PortableImportResponse {
    val existing = skillDefinitionRepository.findByAccountIdAndName(session.accountId, dto.name)

    return if (existing == null) {
      val created = boSkillDefinitionService.create(
        session,
        BoCreateSkillRequest(
          name = dto.name,
          description = dto.description,
          license = dto.license,
          compatibility = dto.compatibility,
          metadata = dto.metadata,
          allowedTools = dto.allowedTools,
          body = dto.body,
          scripts = dto.scripts,
          references = dto.references,
          networkPolicy = dto.networkPolicy,
          allowedDomains = dto.allowedDomains,
          triggerStrategy = dto.triggerStrategy,
          llmTriggerConfig = dto.llmTriggerConfig,
        ),
      )

      if (dto.assets.isNotEmpty() || dto.status != null) {
        boSkillDefinitionService.patch(
          session,
          portableSkillPatchMap(
            BoPatchSkillRequest(
              id = created.id,
              name = dto.name,
              description = dto.description,
              license = dto.license,
              compatibility = dto.compatibility,
              metadata = dto.metadata,
              allowedTools = dto.allowedTools,
              body = dto.body,
              status = dto.status,
              scripts = dto.scripts,
              references = dto.references,
              assets = dto.assets,
              networkPolicy = dto.networkPolicy,
              allowedDomains = dto.allowedDomains,
              triggerStrategy = dto.triggerStrategy,
              llmTriggerConfig = dto.llmTriggerConfig,
            )
          ),
        )
      }

      PortableImportResponse(
        id = created.id,
        name = created.name,
        action = PortableImportAction.CREATED,
      )
    } else {
      val targetStatus = dto.status ?: if (existing.status == StatusType.DELETED) {
        StatusType.ACTIVE.value()
      } else {
        existing.status.value()
      }

      boSkillDefinitionService.patch(
        session,
        portableSkillPatchMap(
          BoPatchSkillRequest(
            id = existing.id,
            name = dto.name,
            description = dto.description,
            license = dto.license,
            compatibility = dto.compatibility,
            metadata = dto.metadata,
            allowedTools = dto.allowedTools,
            body = dto.body,
            status = targetStatus,
            scripts = dto.scripts,
            references = dto.references,
            assets = dto.assets,
            networkPolicy = dto.networkPolicy,
            allowedDomains = dto.allowedDomains,
            triggerStrategy = dto.triggerStrategy,
            llmTriggerConfig = dto.llmTriggerConfig,
          )
        ),
      )

      PortableImportResponse(
        id = existing.id,
        name = existing.name,
        action = PortableImportAction.UPDATED,
      )
    }
  }

  @Transactional
  fun importSkills(session: JSession, dtos: List<PortableSkillData>): PortableBatchImportResponse {
    require(dtos.isNotEmpty()) { "최소 1개 이상의 skill 데이터가 필요합니다." }
    val results = dtos.map { importSkill(session, it) }
    return PortableBatchImportResponse(importedCount = results.size, results = results)
  }

  private fun resolveProvider(id: Long): AiProvider {
    return aiProviderRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Provider not found: $id") }
  }

  private fun resolveProviderByName(name: String): AiProvider {
    return aiProviderRepository.findOne(aiProvider.name.eq(name)).orElseThrow {
      ObjectNotFoundException("Provider not found by name: $name")
    }
  }

  private fun findModel(providerId: Long, name: String, reasoningEffort: ReasoningEffort?): AiModel? {
    val reasoningPredicate = reasoningEffort?.let { aiModel.reasoningEffort.eq(it) } ?: aiModel.reasoningEffort.isNull
    return aiModelRepository.findOne(
      aiModel.aiProviderId.eq(providerId)
        .and(aiModel.name.eq(name))
        .and(reasoningPredicate)
    ).orElse(null)
  }

  private fun resolveModel(id: Long): AiModel {
    return aiModelRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("Model not found: $id") }
  }

  private fun resolveModelRef(ref: PortableAiModelRef): AiModel {
    val provider = resolveProviderByName(ref.providerName)
    return findModel(provider.id, ref.name, ref.reasoningEffort)
      ?: throw ObjectNotFoundException("Model not found: ${ref.providerName}/${ref.name}")
  }

  private fun resolveApiKey(id: Long): AiApiKey {
    return aiApiKeyRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("API key not found: $id") }
  }

  private fun resolveApiKeyRef(ref: PortableAiApiKeyRef): AiApiKey {
    val provider = resolveProviderByName(ref.providerName)
    return aiApiKeyRepository.findAll()
      .firstOrNull { apiKey ->
        apiKey.aiProviderId == provider.id &&
          textEncryptor.decrypt(apiKey.encryptedKey) == ref.key
      }
      ?: throw ObjectNotFoundException("API key not found for provider: ${ref.providerName}")
  }

  private fun resolveAssistantRef(ref: PortableAiAssistantRef): AiAssistant {
    return aiAssistantRepository.findOne(aiAssistant.name.eq(ref.name)).orElseThrow {
      ObjectNotFoundException("Assistant not found: ${ref.name}")
    }
  }

  private fun exportWorkflow(workflow: Map<String, Any>?): Map<String, Any> {
    return transformWorkflowValue(workflow ?: emptyMap<String, Any>(), WorkflowTransformMode.EXPORT) as Map<String, Any>
  }

  private fun importWorkflow(workflow: Map<String, Any>?): Map<String, Any> {
    return transformWorkflowValue(workflow ?: emptyMap<String, Any>(), WorkflowTransformMode.IMPORT) as Map<String, Any>
  }

  private fun transformWorkflowValue(value: Any?, mode: WorkflowTransformMode): Any? {
    return when (value) {
      is Map<*, *> -> {
        val mapped = value.entries.associate { (key, nestedValue) ->
          key.toString() to transformWorkflowValue(nestedValue, mode)
        }
        transformWorkflowMap(mapped, mode)
      }
      is List<*> -> value.map { nestedValue -> transformWorkflowValue(nestedValue, mode) }
      else -> value
    }
  }

  private fun transformWorkflowMap(
    source: Map<String, Any?>,
    mode: WorkflowTransformMode,
  ): Map<String, Any?> {
    val mutable = source.toMutableMap()

    if (mode == WorkflowTransformMode.EXPORT) {
      (mutable["aiAssistantId"] as? Number)?.toLong()?.let { assistantId ->
        val assistantEntity = aiAssistantRepository.findById(assistantId)
          .orElseThrow { ObjectNotFoundException("Assistant not found: $assistantId") }
        mutable.remove("aiAssistantId")
        mutable["aiAssistantRef"] = mapOf("name" to assistantEntity.name)
      }

      (mutable["aiProviderId"] as? Number)?.toLong()?.let { providerId ->
        val providerEntity = resolveProvider(providerId)
        mutable.remove("aiProviderId")
        mutable["aiProviderRef"] = mapOf("name" to providerEntity.name)
      }

      (mutable["aiModelId"] as? Number)?.toLong()?.let { modelId ->
        val modelEntity = resolveModel(modelId)
        val providerEntity = resolveProvider(modelEntity.aiProviderId)
        mutable.remove("aiModelId")
        mutable["aiModelRef"] = mapOf(
          "providerName" to providerEntity.name,
          "name" to modelEntity.name,
          "reasoningEffort" to modelEntity.reasoningEffort,
        )
      }

      (mutable["aiApiKeyId"] as? Number)?.toLong()?.let { apiKeyId ->
        val apiKeyEntity = resolveApiKey(apiKeyId)
        val providerEntity = resolveProvider(apiKeyEntity.aiProviderId)
        mutable.remove("aiApiKeyId")
        mutable["aiApiKeyRef"] = mapOf(
          "providerName" to providerEntity.name,
          "key" to textEncryptor.decrypt(apiKeyEntity.encryptedKey),
        )
      }
    } else {
      (mutable["aiAssistantRef"] as? Map<*, *>)?.let { assistantRef ->
        val resolved = resolveAssistantRef(objectMapper.convertValue(assistantRef, PortableAiAssistantRef::class.java))
        mutable.remove("aiAssistantRef")
        mutable["aiAssistantId"] = resolved.id
      }

      (mutable["aiProviderRef"] as? Map<*, *>)?.let { providerRef ->
        val resolved = resolveProviderByName(providerRef["name"]?.toString() ?: "")
        mutable.remove("aiProviderRef")
        mutable["aiProviderId"] = resolved.id
      }

      (mutable["aiModelRef"] as? Map<*, *>)?.let { modelRef ->
        val resolved = resolveModelRef(objectMapper.convertValue(modelRef, PortableAiModelRef::class.java))
        mutable.remove("aiModelRef")
        mutable["aiModelId"] = resolved.id
      }

      (mutable["aiApiKeyRef"] as? Map<*, *>)?.let { apiKeyRef ->
        val resolved = resolveApiKeyRef(objectMapper.convertValue(apiKeyRef, PortableAiApiKeyRef::class.java))
        mutable.remove("aiApiKeyRef")
        mutable["aiApiKeyId"] = resolved.id
      }
    }

    return mutable.filterValues { it != null }
  }

  private fun portableSkillPatchMap(dto: BoPatchSkillRequest): Map<String, Any> {
    val raw = objectMapper.convertValue(dto, object : TypeReference<Map<String, Any?>>() {})
    return raw.filterValues { it != null }.mapValues { it.value!! }
  }

  private enum class WorkflowTransformMode {
    EXPORT,
    IMPORT,
  }
}
