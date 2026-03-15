package com.jongmin.ai.core.backoffice.dto

import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.AiModelType
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoAssetRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoCompatibilityRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoLlmTriggerConfigRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoReferenceRequest
import com.jongmin.ai.multiagent.skill.backoffice.dto.BoScriptRequest
import com.jongmin.ai.multiagent.skill.model.NetworkPolicy
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import com.jongmin.jspring.data.entity.StatusType
import java.math.BigDecimal

enum class PortableImportAction {
  CREATED,
  UPDATED,
}

data class PortableImportResponse(
  val id: Long,
  val name: String,
  val action: PortableImportAction,
)

data class PortableBatchImportResponse(
  val importedCount: Int,
  val results: List<PortableImportResponse>,
)

data class PortableAiApiKeyData(
  val key: String,
)

data class PortableAiProviderData(
  val name: String,
  val description: String? = null,
  val baseUrl: String? = null,
  val status: StatusType? = null,
  val apiKeys: List<PortableAiApiKeyData> = emptyList(),
)

data class PortableAiProviderRef(
  val name: String,
)

data class PortableAiModelRef(
  val providerName: String,
  val name: String,
  val reasoningEffort: ReasoningEffort? = null,
)

data class PortableAiApiKeyRef(
  val providerName: String,
  val key: String,
)

data class PortableAiAssistantRef(
  val name: String,
)

data class PortableAiModelData(
  val providerName: String,
  val name: String,
  val description: String? = null,
  val status: StatusType? = null,
  val supportsReasoning: Boolean? = false,
  val reasoningEffort: ReasoningEffort? = null,
  val type: AiModelType,
  val maxTokens: Int? = null,
  val inputTokenPrice: BigDecimal,
  val outputTokenPrice: BigDecimal,
  val inputTokenPriceInService: BigDecimal,
  val outputTokenPriceInService: BigDecimal,
)

data class PortableAiAssistantData(
  val name: String,
  val type: AiAssistantType,
  val description: String? = null,
  val status: StatusType? = null,
  val primaryModel: PortableAiModelRef,
  val primaryApiKey: PortableAiApiKeyRef,
  val instructions: String? = null,
  val temperature: Double? = null,
  val topP: Double? = null,
  val responseFormat: String? = null,
  val maxTokens: Int? = null,
)

data class PortableAiAgentData(
  val name: String,
  val description: String? = null,
  val status: StatusType? = null,
  val workflow: Map<String, Any> = emptyMap(),
)

data class PortableSkillData(
  val name: String,
  val description: String,
  val license: String? = null,
  val compatibility: BoCompatibilityRequest? = null,
  val metadata: Map<String, Any>? = null,
  val allowedTools: List<String>? = null,
  val body: String,
  val scripts: List<BoScriptRequest> = emptyList(),
  val references: List<BoReferenceRequest> = emptyList(),
  val assets: List<BoAssetRequest> = emptyList(),
  val networkPolicy: NetworkPolicy = NetworkPolicy.ALLOW_SPECIFIC,
  val allowedDomains: List<String>? = null,
  val triggerStrategy: TriggerStrategy = TriggerStrategy.RULE_BASED,
  val llmTriggerConfig: BoLlmTriggerConfigRequest? = null,
  val status: Int? = null,
)
