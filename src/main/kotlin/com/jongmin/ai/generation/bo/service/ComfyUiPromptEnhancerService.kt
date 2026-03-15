package com.jongmin.ai.generation.bo.service

import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.system.dto.SystemChatMessage
import com.jongmin.ai.core.system.dto.SystemChatRequest
import com.jongmin.ai.core.system.dto.SystemChatResponse
import com.jongmin.ai.core.system.service.SystemAiChatService
import com.jongmin.ai.generation.bo.dto.ComfyUiGenerationSettingsResponse
import com.jongmin.ai.generation.bo.dto.ComfyUiPromptEnhanceRequest
import com.jongmin.ai.generation.bo.dto.ComfyUiPromptEnhanceResponse
import com.jongmin.ai.generation.bo.dto.ComfyUiPromptTemplateBlockResponse
import com.jongmin.ai.generation.bo.dto.NovelAiAssetKind
import com.jongmin.ai.generation.bo.dto.NovelAiConsistencyMode
import com.jongmin.jspring.core.util.cleanJsonString
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.Locale

/**
 * ComfyUI 프롬프트 인첸터 서비스
 *
 * 기존 프롬프트를 기반으로 ComfyUI/Stable Diffusion 계열 워크플로우에
 * 적합한 positive/negative prompt를 생성한다.
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class ComfyUiPromptEnhancerService(
  private val systemAiChatService: SystemAiChatService,
  private val objectMapper: ObjectMapper,
) {
  private val weightedTagPattern = Regex("""^[\(\[]\s*(.+?)\s*:\s*-?\d+(?:\.\d+)?\s*[\)\]]$""")

  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val BACKGROUND_ASSISTANT_CATEGORY = "BACKGROUND_PROMPT_GENERATOR"
    private const val CHARACTER_ASSISTANT_CATEGORY = "CHARACTER_PROMPT_GENERATOR"

    private const val DEFAULT_STYLE_BLOCK =
      "masterpiece, best quality, highly detailed, cinematic lighting, atmospheric depth"

    private const val DEFAULT_NEGATIVE =
      "lowres, blurry, text, watermark, logo, jpeg artifacts, deformed, extra digits"

    private const val DEFAULT_BACKGROUND_BLOCK = "environment, scenery, background focus, no humans"

    private val BACKGROUND_REQUIRED_NEGATIVE = listOf(
      "text", "watermark", "logo", "lowres"
    )

    private val CHARACTER_REQUIRED_NEGATIVE = listOf(
      "text", "watermark", "logo", "extra digits", "deformed hands"
    )

    private val SYSTEM_PROMPT = """
      |# Role
      |You are a ComfyUI / Stable Diffusion prompt enhancement specialist for production pipelines.
      |
      |# Objective
      |Improve the given prompt while preserving asset identity and project-level visual consistency.
      |
      |# Rules
      |1. Keep the original prompt style. If the input is comma-separated tags, return comma-separated tags.
      |2. If the input is descriptive prose, return concise production-ready prose instead of turning it into markdown or lists.
      |3. Separate the output into:
      |   - styleBlock
      |   - subjectBlock
      |   - variableSceneBlock
      |4. Preserve preferredArtistTags, styleKeywords, and vibeKeywords when provided.
      |5. Respect locked template intent and reuse locked values in settings when possible.
      |6. If assetKind is BACKGROUND, avoid person/human related positive tags unless explicitly required.
      |7. Keep negative prompt concise and practical for image generation.
      |8. Return JSON only. Do not output markdown.
      |
      |# Output format (JSON only)
      |{
      |  "enhancedPrompt": "string",
      |  "enhancedNegativePrompt": "string",
      |  "styleBlock": "string",
      |  "subjectBlock": "string",
      |  "variableSceneBlock": "string",
      |  "appliedStrategies": ["string"],
      |  "warnings": ["string"]
      |}
    """.trimMargin()
  }

  fun enhance(request: ComfyUiPromptEnhanceRequest): ComfyUiPromptEnhanceResponse {
    val normalizedRequest = request.copy(
      preferredArtistTags = normalizePromptReferences(request.preferredArtistTags),
      styleKeywords = normalizePromptReferences(request.styleKeywords),
      vibeKeywords = normalizePromptReferences(request.vibeKeywords),
    )
    val startedAt = System.currentTimeMillis()
    val userPrompt = buildUserPrompt(normalizedRequest)
    val llmResponse = callLlmWithFallback(normalizedRequest, userPrompt)
    val parsed = parseLlmResult(llmResponse)
    val normalized = normalizeResult(normalizedRequest, parsed, llmResponse == null)
    val durationMs = System.currentTimeMillis() - startedAt

    return ComfyUiPromptEnhanceResponse(
      success = true,
      fallbackApplied = normalized.fallbackApplied,
      enhancedPrompt = normalized.enhancedPrompt,
      enhancedNegativePrompt = normalized.enhancedNegativePrompt,
      promptTemplate = normalized.promptTemplate,
      settings = normalized.settings,
      appliedStrategies = normalized.appliedStrategies,
      warnings = normalized.warnings,
      llmAssistantId = llmResponse?.assistantId,
      llmAssistantName = llmResponse?.assistantName,
      durationMs = durationMs,
    )
  }

  private fun normalizePromptReferences(values: List<String>): List<String> {
    val dedup = LinkedHashMap<String, String>()
    values.forEach { raw ->
      val normalized = normalizeTag(raw)
      if (normalized.isBlank()) return@forEach
      dedup.putIfAbsent(normalized.lowercase(Locale.ROOT), normalized)
    }
    return dedup.values.toList()
  }

  private fun buildUserPrompt(request: ComfyUiPromptEnhanceRequest): String {
    return buildString {
      appendLine("## Input")
      appendLine("- assetKind: ${request.assetKind}")
      appendLine("- consistencyMode: ${request.consistencyMode}")
      appendLine("- basePrompt: ${request.basePrompt}")
      request.baseNegativePrompt?.let { appendLine("- baseNegativePrompt: $it") }
      request.sceneInstruction?.let { appendLine("- sceneInstruction: $it") }

      if (request.referencePrompts.isNotEmpty()) {
        appendLine("- referencePrompts:")
        request.referencePrompts.forEachIndexed { index, prompt ->
          appendLine("  ${index + 1}. $prompt")
        }
      }
      if (request.preferredArtistTags.isNotEmpty()) {
        appendLine("- preferredArtistTags: ${request.preferredArtistTags.joinToString(", ")}")
      }
      if (request.styleKeywords.isNotEmpty()) {
        appendLine("- styleKeywords: ${request.styleKeywords.joinToString(", ")}")
      }
      if (request.vibeKeywords.isNotEmpty()) {
        appendLine("- vibeKeywords: ${request.vibeKeywords.joinToString(", ")}")
      }

      request.lockedTemplate?.let { locked ->
        appendLine()
        appendLine("## LockedTemplate")
        locked.styleBlock?.let { appendLine("- styleBlock: $it") }
        locked.characterBlock?.let { appendLine("- characterBlock: $it") }
        locked.backgroundBlock?.let { appendLine("- backgroundBlock: $it") }
        locked.sampler?.let { appendLine("- sampler: $it") }
        locked.steps?.let { appendLine("- steps: $it") }
        locked.cfgScale?.let { appendLine("- cfgScale: $it") }
        locked.width?.let { appendLine("- width: $it") }
        locked.height?.let { appendLine("- height: $it") }
        locked.seed?.let { appendLine("- seed: $it") }
      }
    }
  }

  private fun callLlmWithFallback(
    request: ComfyUiPromptEnhanceRequest,
    userPrompt: String,
  ): SystemChatResponse? {
    if (request.assistantId != null) {
      return runCatching {
        chatWithAssistantId(
          assistantId = request.assistantId,
          userPrompt = userPrompt,
          consistencyMode = request.consistencyMode,
        )
      }.onFailure { e ->
        kLogger.warn { "[ComfyUI Enhancer] assistantId=${request.assistantId} 호출 실패: ${e.message}" }
      }.getOrNull()
    }

    val candidates = assistantCandidates(request.assetKind)
    candidates.forEach { candidate ->
      val response = runCatching {
        chatWithAssistantType(
          assistantType = candidate.type,
          category = candidate.category,
          userPrompt = userPrompt,
          consistencyMode = request.consistencyMode,
        )
      }.onFailure { e ->
        kLogger.warn {
          "[ComfyUI Enhancer] assistantType=${candidate.type}, category=${candidate.category} 호출 실패: ${e.message}"
        }
      }.getOrNull()

      if (response != null) {
        return response
      }
    }

    kLogger.warn { "[ComfyUI Enhancer] LLM 호출 실패, 규칙 기반 fallback 적용" }
    return null
  }

  private fun chatWithAssistantId(
    assistantId: Long,
    userPrompt: String,
    consistencyMode: NovelAiConsistencyMode,
  ): SystemChatResponse {
    val chatRequest = SystemChatRequest(
      assistantId = assistantId,
      temperature = getTemperature(consistencyMode),
      maxTokens = 1200,
      messages = listOf(
        SystemChatMessage(role = "system", content = SYSTEM_PROMPT),
        SystemChatMessage(role = "user", content = userPrompt),
      )
    )
    return systemAiChatService.chat(chatRequest)
  }

  private fun chatWithAssistantType(
    assistantType: AiAssistantType,
    category: String?,
    userPrompt: String,
    consistencyMode: NovelAiConsistencyMode,
  ): SystemChatResponse {
    val chatRequest = SystemChatRequest(
      assistantType = assistantType,
      assistantCategory = category,
      temperature = getTemperature(consistencyMode),
      maxTokens = 1200,
      messages = listOf(
        SystemChatMessage(role = "system", content = SYSTEM_PROMPT),
        SystemChatMessage(role = "user", content = userPrompt),
      )
    )
    return systemAiChatService.chat(chatRequest)
  }

  private fun assistantCandidates(assetKind: NovelAiAssetKind): List<ComfyUiAssistantCandidate> {
    return when (assetKind) {
      NovelAiAssetKind.BACKGROUND -> listOf(
        ComfyUiAssistantCandidate(AiAssistantType.BACKGROUND_PROMPT, BACKGROUND_ASSISTANT_CATEGORY),
        ComfyUiAssistantCandidate(AiAssistantType.IMAGE_PROMPT_GENERATOR, null),
      )

      NovelAiAssetKind.CHARACTER -> listOf(
        ComfyUiAssistantCandidate(AiAssistantType.CHARACTER_PROMPT, CHARACTER_ASSISTANT_CATEGORY),
        ComfyUiAssistantCandidate(AiAssistantType.IMAGE_PROMPT_GENERATOR, null),
      )
    }
  }

  private fun getTemperature(mode: NovelAiConsistencyMode): Double {
    return when (mode) {
      NovelAiConsistencyMode.STRICT -> 0.2
      NovelAiConsistencyMode.BALANCED -> 0.35
      NovelAiConsistencyMode.FLEXIBLE -> 0.5
    }
  }

  private fun parseLlmResult(response: SystemChatResponse?): ComfyUiParsedLlmResult {
    val content = response?.content
    val assistantLabel = buildAssistantLogLabel(response)

    if (content.isNullOrBlank()) {
      kLogger.warn { "[ComfyUI Enhancer] LLM 응답이 비어 있어 fallback 적용 - assistant=$assistantLabel" }
      return ComfyUiParsedLlmResult(fallbackUsed = true)
    }

    val cleaned = content.cleanJsonString()
    val json = extractJson(cleaned)
    if (json == null) {
      kLogger.warn {
        "[ComfyUI Enhancer] LLM 응답에서 JSON 추출 실패, fallback 적용 - " +
            "assistant=$assistantLabel, rawPreview=${previewForLog(content)}"
      }
      return ComfyUiParsedLlmResult(fallbackUsed = true)
    }

    return try {
      val node = objectMapper.readTree(json)
      ComfyUiParsedLlmResult(
        enhancedPrompt = node.text("enhancedPrompt"),
        enhancedNegativePrompt = node.text("enhancedNegativePrompt"),
        styleBlock = node.text("styleBlock"),
        subjectBlock = node.text("subjectBlock"),
        variableSceneBlock = node.text("variableSceneBlock"),
        appliedStrategies = node.stringArray("appliedStrategies"),
        warnings = node.stringArray("warnings"),
        fallbackUsed = false,
      )
    } catch (e: Exception) {
      kLogger.warn {
        "[ComfyUI Enhancer] LLM JSON 파싱 실패, fallback 적용 - " +
            "assistant=$assistantLabel, error=${e.message}, " +
            "rawPreview=${previewForLog(content)}, jsonPreview=${previewForLog(json)}"
      }
      ComfyUiParsedLlmResult(fallbackUsed = true)
    }
  }

  private fun normalizeResult(
    request: ComfyUiPromptEnhanceRequest,
    parsed: ComfyUiParsedLlmResult,
    llmUnavailable: Boolean,
  ): ComfyUiNormalizedEnhancerResult {
    val styleSeed = firstNonBlank(
      request.lockedTemplate?.styleBlock,
      parsed.styleBlock,
      DEFAULT_STYLE_BLOCK,
    )
    val styleTags = mergeTags(
      splitTags(styleSeed),
      request.preferredArtistTags.map { normalizeTag(it) },
      request.styleKeywords.map { normalizeTag(it) },
      request.vibeKeywords.map { normalizeTag(it) },
    )

    val subjectSeed = when (request.assetKind) {
      NovelAiAssetKind.CHARACTER -> firstNonBlank(
        request.lockedTemplate?.characterBlock,
        parsed.subjectBlock,
        "",
      )

      NovelAiAssetKind.BACKGROUND -> firstNonBlank(
        request.lockedTemplate?.backgroundBlock,
        parsed.subjectBlock,
        DEFAULT_BACKGROUND_BLOCK,
      )
    }

    val referenceTags = request.referencePrompts
      .flatMap { splitTags(it) }
      .take(24)

    val subjectTags = mergeTags(
      if (request.assetKind == NovelAiAssetKind.BACKGROUND) splitTags(DEFAULT_BACKGROUND_BLOCK) else emptyList(),
      splitTags(subjectSeed),
      splitTags(request.basePrompt),
      splitTags(parsed.enhancedPrompt),
      referenceTags,
    )

    val variableTags = mergeTags(
      splitTags(request.sceneInstruction),
      splitTags(parsed.variableSceneBlock),
    )

    val finalPrompt = mergeTags(styleTags, subjectTags, variableTags).joinToString(", ")

    val baseNegative = firstNonBlank(
      request.baseNegativePrompt,
      parsed.enhancedNegativePrompt,
      DEFAULT_NEGATIVE,
    )
    val requiredNegative = when (request.assetKind) {
      NovelAiAssetKind.CHARACTER -> CHARACTER_REQUIRED_NEGATIVE
      NovelAiAssetKind.BACKGROUND -> BACKGROUND_REQUIRED_NEGATIVE
    }
    val finalNegative = mergeTags(
      splitTags(baseNegative),
      requiredNegative.map { normalizeTag(it) },
    ).joinToString(", ")

    val settings = buildSettings(request)
    val appliedStrategies = buildAppliedStrategies(request, parsed.appliedStrategies)
    val warnings = buildWarnings(request, parsed, llmUnavailable)

    return ComfyUiNormalizedEnhancerResult(
      fallbackApplied = llmUnavailable || parsed.fallbackUsed,
      enhancedPrompt = finalPrompt,
      enhancedNegativePrompt = finalNegative,
      promptTemplate = ComfyUiPromptTemplateBlockResponse(
        styleBlock = styleTags.joinToString(", "),
        subjectBlock = subjectTags.joinToString(", "),
        variableSceneBlock = variableTags.joinToString(", "),
      ),
      settings = settings,
      appliedStrategies = appliedStrategies,
      warnings = warnings,
    )
  }

  private fun buildSettings(request: ComfyUiPromptEnhanceRequest): ComfyUiGenerationSettingsResponse {
    val locked = request.lockedTemplate
    val defaultSize = when (request.assetKind) {
      NovelAiAssetKind.CHARACTER -> Pair(832, 1216)
      NovelAiAssetKind.BACKGROUND -> Pair(1024, 1024)
    }
    val seedPolicy = when {
      locked?.seed != null -> "FIXED_SEED"
      request.consistencyMode == NovelAiConsistencyMode.STRICT -> "FIXED_SEED_POOL"
      request.consistencyMode == NovelAiConsistencyMode.BALANCED -> "ANCHOR_SEED_POOL"
      else -> "SCENE_RANDOM_WITH_STYLE_LOCK"
    }

    return ComfyUiGenerationSettingsResponse(
      sampler = locked?.sampler ?: "euler",
      steps = (locked?.steps ?: 28).coerceIn(8, 80),
      cfgScale = (locked?.cfgScale ?: 7.0).coerceIn(1.0, 20.0),
      width = locked?.width ?: defaultSize.first,
      height = locked?.height ?: defaultSize.second,
      seed = locked?.seed,
      seedPolicy = seedPolicy,
    )
  }

  private fun buildAppliedStrategies(
    request: ComfyUiPromptEnhanceRequest,
    llmStrategies: List<String>,
  ): List<String> {
    val base = mutableListOf(
      "프롬프트 템플릿 분리(style/subject/scene)",
      "positive/negative prompt 일관성 정리",
      "steps/cfg/size/seed 권장값 정리",
    )

    if (request.referencePrompts.isNotEmpty()) {
      base += "레퍼런스 프롬프트 기반 비주얼 앵커링"
    }
    if (request.preferredArtistTags.isNotEmpty()) {
      base += "선호 작가/비주얼 레퍼런스 우선 적용"
    }
    if (request.sceneInstruction != null) {
      base += "장면 가변 블록 분리 적용"
    }

    return mergeTags(base, llmStrategies)
  }

  private fun buildWarnings(
    request: ComfyUiPromptEnhanceRequest,
    parsed: ComfyUiParsedLlmResult,
    llmUnavailable: Boolean,
  ): List<String> {
    val warnings = mutableListOf<String>()
    warnings += parsed.warnings

    if (llmUnavailable) {
      warnings += "LLM 호출 실패로 규칙 기반 fallback이 적용되었습니다."
    }
    if (request.referencePrompts.isEmpty()) {
      warnings += "레퍼런스 프롬프트가 없어 시리즈 일관성이 약해질 수 있습니다."
    }
    if (request.lockedTemplate?.seed == null && request.consistencyMode == NovelAiConsistencyMode.STRICT) {
      warnings += "STRICT 모드에서는 seed 고정(또는 고정 seed pool) 운영을 권장합니다."
    }

    return mergeTags(warnings)
  }

  private fun splitTags(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",", "\n", ";")
      .map { normalizeTag(it) }
      .filter { it.isNotBlank() }
  }

  private fun normalizeTag(raw: String): String {
    val normalized = raw.trim()
      .replace(Regex("\\s+"), " ")
      .trim(',', ' ')

    return weightedTagPattern.matchEntire(normalized)?.groupValues?.get(1)?.trim() ?: normalized
  }

  private fun mergeTags(vararg groups: List<String>): List<String> {
    val dedup = LinkedHashMap<String, String>()
    groups.asList().flatten().forEach { tag ->
      val normalized = normalizeTag(tag)
      if (normalized.isBlank()) return@forEach
      val key = normalized.removeSurrounding("{", "}").lowercase(Locale.ROOT)
      dedup.putIfAbsent(key, normalized)
    }
    return dedup.values.toList()
  }

  private fun firstNonBlank(vararg values: String?): String {
    values.forEach { value ->
      if (!value.isNullOrBlank()) {
        return value
      }
    }
    return ""
  }

  private fun extractJson(raw: String): String? {
    val start = raw.indexOf('{')
    if (start < 0) return null

    var depth = 0
    for (i in start until raw.length) {
      when (raw[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            return raw.substring(start, i + 1)
          }
        }
      }
    }
    return null
  }

  private fun buildAssistantLogLabel(response: SystemChatResponse?): String {
    if (response == null) {
      return "none"
    }

    val name = response.assistantName?.takeIf { it.isNotBlank() } ?: "unknown"
    val id = response.assistantId?.toString() ?: "unknown"
    return "$name($id)"
  }

  private fun previewForLog(value: String, maxLength: Int = 800): String {
    val compact = value.replace(Regex("\\s+"), " ").trim()
    if (compact.isBlank()) {
      return "<blank>"
    }
    return if (compact.length <= maxLength) compact else "${compact.take(maxLength)}..."
  }

  private fun JsonNode.text(field: String): String? {
    return this.get(field)?.asString()?.takeIf { it.isNotBlank() }
  }

  private fun JsonNode.stringArray(field: String): List<String> {
    val node = this.get(field) ?: return emptyList()
    if (!node.isArray) return emptyList()
    return node.mapNotNull { child ->
      child.asString().takeIf { it.isNotBlank() }?.let { normalizeTag(it) }
    }
  }
}

private data class ComfyUiAssistantCandidate(
  val type: AiAssistantType,
  val category: String?,
)

private data class ComfyUiParsedLlmResult(
  val enhancedPrompt: String? = null,
  val enhancedNegativePrompt: String? = null,
  val styleBlock: String? = null,
  val subjectBlock: String? = null,
  val variableSceneBlock: String? = null,
  val appliedStrategies: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
  val fallbackUsed: Boolean = false,
)

private data class ComfyUiNormalizedEnhancerResult(
  val fallbackApplied: Boolean,
  val enhancedPrompt: String,
  val enhancedNegativePrompt: String,
  val promptTemplate: ComfyUiPromptTemplateBlockResponse,
  val settings: ComfyUiGenerationSettingsResponse,
  val appliedStrategies: List<String>,
  val warnings: List<String>,
)
