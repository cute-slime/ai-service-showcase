package com.jongmin.ai.generation.provider.image

import com.jongmin.ai.core.GenerationProviderModelRepository
import com.jongmin.ai.generation.dto.*
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIPromptBuilder
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIRuntimeConfigResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * ComfyUI 프롬프트 추출기
 *
 * promptConfig에서 프롬프트를 추출하는 로직을 담당한다.
 *
 * ### 지원 모드 (우선순위 순):
 * 1. Phase 6: presetCode + LLM 생성 프롬프트 (3가지 하위 방식)
 * 2. Phase 5: presetCode + DB 스타일 프리셋 기반 (레거시)
 * 3. Variant 기반: variant + location 정보로 PromptBuilder 사용 (레거시)
 * 4. 직접 지정: promptConfig에 positive/negative가 직접 있으면 그대로 사용
 *
 * @author Claude Code
 * @since 2026.02.19 (ComfyUIProvider에서 분리)
 */
@Component
class ComfyUIPromptExtractor(
  private val promptBuilder: ComfyUIPromptBuilder,
  private val providerModelRepository: GenerationProviderModelRepository,
  private val runtimeConfigResolver: ComfyUIRuntimeConfigResolver,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * promptConfig에서 프롬프트 추출
   *
   * @param context 생성 요청 컨텍스트
   * @return (positivePrompt, negativePrompt) 쌍
   */
  @Suppress("UNCHECKED_CAST")
  fun extractPrompts(context: GenerationContext): Pair<String, String> {
    val runtimeConfig = runtimeConfigResolver.resolve()
    val config = context.promptConfig

    // 1. Phase 5/6: presetCode 기반 프롬프트 생성 시도 (권장)
    val presetPrompts = tryBuildPresetCodePrompts(config, context.modelId)
    if (presetPrompts != null) {
      kLogger.info {
        "[ComfyUI] presetCode 기반 프롬프트 생성 - presetCode: ${presetPrompts.presetCode ?: presetPrompts.stylePreset}"
      }
      return Pair(presetPrompts.prompt, presetPrompts.negativePrompt)
    }

    // 2. 레거시: Variant 기반 프롬프트 생성 시도
    val variantPrompts = tryBuildVariantPrompts(config)
    if (variantPrompts != null) {
      kLogger.info {
        "[ComfyUI] Variant 기반 프롬프트 생성 - variant: ${variantPrompts.variant}, " +
            "preset: ${variantPrompts.stylePreset}"
      }
      return Pair(variantPrompts.prompt, variantPrompts.negativePrompt)
    }

    // 3. 기존 방식: 직접 지정된 프롬프트 추출
    val imageConfig = config["image"] as? Map<String, Any>
    val comfyConfig = imageConfig?.get("COMFYUI") as? Map<String, Any>

    val positive = comfyConfig?.get("positive") as? String
      ?: config["positive"] as? String
      ?: config["prompt"] as? String
      ?: ""

    val negative = comfyConfig?.get("negative") as? String
      ?: config["negative"] as? String
      ?: runtimeConfig.defaultNegativePrompt

    return Pair(positive, negative)
  }

  /**
   * Phase 5/6: presetCode 기반 프롬프트 생성 시도
   *
   * ### Phase 6 우선 (3가지 방식):
   * 1. `phase6: true` + `llmGenerated: true` + `image.COMFYUI.positive` -> 직접 사용
   * 2. `llm.generated` 구조 (LlmPromptGenerationTaskHandler 생성)
   * 3. `phase6.llmGeneratedPrompt` 구조 (레거시 호환)
   *
   * ### Phase 5 Fallback:
   * Phase 6 데이터가 없으면 DB 스타일 프리셋 기반으로 프롬프트 생성.
   *
   * @param config promptConfig (presetCode, location, metadata, phase6 포함)
   * @param modelId 모델 ID (DB 조회용)
   * @return PromptOutput 또는 null (presetCode가 없거나 DB 조회 실패 시)
   */
  @Suppress("UNCHECKED_CAST")
  private fun tryBuildPresetCodePrompts(config: Map<String, Any>, modelId: Long?): PromptOutput? {
    val defaultNegativePrompt = runtimeConfigResolver.resolve().defaultNegativePrompt
    // presetCode가 없으면 null 반환 (variant 방식으로 fallback)
    val presetCode = config["presetCode"] as? String ?: return null

    // ===== Phase 6: LLM 생성 프롬프트 우선 사용 =====

    // 방법 1: phase6: true (Boolean) + llmGenerated: true + image.COMFYUI.positive
    val isPhase6 = config["phase6"] as? Boolean ?: false
    val isLlmGenerated = config["llmGenerated"] as? Boolean ?: false

    if (isPhase6 && isLlmGenerated) {
      val imageConfig = config["image"] as? Map<String, Any>
      val comfyConfig = imageConfig?.get("COMFYUI") as? Map<String, Any>

      val positive = comfyConfig?.get("positive") as? String
      val negative = comfyConfig?.get("negative") as? String

      // Phase 6 플래그가 있는데 positive가 없으면 에러 (fallback 방지)
      if (positive.isNullOrBlank()) {
        throw IllegalStateException(
          "[ComfyUI Phase6] phase6=true, llmGenerated=true인데 image.COMFYUI.positive가 없습니다! " +
              "presetCode: $presetCode, llmAssistantId: ${config["llmAssistantId"]}"
        )
      }

      kLogger.info {
        "[ComfyUI Phase6] LLM 생성 프롬프트 직접 사용 - " +
            "presetCode: $presetCode, " +
            "llmAssistant: ${config["llmAssistantName"]}, " +
            "llmDurationMs: ${config["llmDurationMs"]}"
      }
      kLogger.debug {
        "[ComfyUI Phase6] positive 프롬프트: ${positive.take(100)}..."
      }

      return PromptOutput(
        prompt = positive,
        negativePrompt = negative ?: defaultNegativePrompt,
        naturalPrompt = null,
        naturalPromptKo = null,
        stylePreset = presetCode,
        artistTag = null,
        presetCode = presetCode
      )
    }

    // 방법 2: llm.generated 구조 (LlmPromptGenerationTaskHandler가 생성)
    val llmData = config["llm"] as? Map<String, Any>
    val useLlm = config["useLlm"] as? Boolean ?: false
    if (useLlm && llmData != null) {
      val llmGenerated = llmData["generated"] as? String
      if (!llmGenerated.isNullOrBlank()) {
        kLogger.info { "[ComfyUI Phase6] LLM 생성 프롬프트 사용 (llm.generated) - presetCode: $presetCode" }
        return buildPhase6PromptOutputFromLlm(config, llmData, presetCode)
      }
    }

    // 방법 3: phase6.llmGeneratedPrompt 구조 (레거시 호환 - phase6가 Map인 경우)
    val phase6Data = config["phase6"] as? Map<String, Any>
    if (phase6Data != null) {
      val llmPrompt = phase6Data["llmGeneratedPrompt"] as? String
      if (!llmPrompt.isNullOrBlank()) {
        kLogger.info { "[ComfyUI Phase6] LLM 생성 프롬프트 사용 (phase6 Map) - presetCode: $presetCode" }
        return buildPhase6PromptOutput(config, phase6Data, presetCode)
      }
    }

    // ===== Phase 5 Fallback은 개발 단계에서 에러 처리 =====
    if (isPhase6 || isLlmGenerated) {
      throw IllegalStateException(
        "[ComfyUI] Phase 6 플래그가 설정되었으나 LLM 프롬프트 추출 실패! " +
            "Fallback 금지. presetCode: $presetCode, isPhase6: $isPhase6, isLlmGenerated: $isLlmGenerated, " +
            "config keys: ${config.keys}"
      )
    }

    // Phase 6 플래그가 없는 경우에만 Phase 5 허용 (레거시 호환)
    kLogger.debug {
      "[ComfyUI] Phase 6 플래그 없음, Phase 5 프리셋 기반 처리 - presetCode: $presetCode"
    }

    // modelId가 없으면 null 반환 (DB 조회 불가)
    if (modelId == null) {
      kLogger.debug { "[ComfyUI] modelId가 없어 presetCode 조회 불가, variant 방식 시도 - presetCode: $presetCode" }
      return null
    }

    // location 정보 추출
    val locationMap = config["location"] as? Map<String, Any>
    val metadataMap = config["metadata"] as? Map<String, Any>

    // location이 있으면 전체 프롬프트 빌드
    if (locationMap != null) {
      val location = parseLocationData(locationMap)
      val metadata = parseScenarioMetadata(metadataMap)

      kLogger.info { "[ComfyUI Phase5] 프리셋 기반 프롬프트 생성 (레거시) - presetCode: $presetCode" }
      return promptBuilder.buildBackgroundPromptByPresetCode(location, presetCode, modelId, metadata)
    }

    // location 없으면 null 반환 (단순 프롬프트에서는 presetCode 미지원)
    kLogger.debug { "[ComfyUI] location 없이 presetCode 사용 불가 - presetCode: $presetCode" }
    return null
  }

  /**
   * Phase 6 LLM 생성 프롬프트로 PromptOutput 구성 (llm.generated 구조)
   */
  @Suppress("UNCHECKED_CAST")
  private fun buildPhase6PromptOutputFromLlm(
    config: Map<String, Any>,
    llmData: Map<String, Any>,
    presetCode: String
  ): PromptOutput {
    val defaultNegativePrompt = runtimeConfigResolver.resolve().defaultNegativePrompt
    val imageData = config["image"] as? Map<String, Any>
    val comfyuiData = imageData?.get("COMFYUI") as? Map<String, Any>

    val prompt = comfyuiData?.get("positive") as? String
      ?: throw IllegalStateException("[ComfyUI Phase6] image.COMFYUI.positive가 없습니다")
    val negativePrompt = comfyuiData["negative"] as? String
      ?: defaultNegativePrompt

    kLogger.debug {
      "[ComfyUI Phase6] LLM 프롬프트 로드 완료 - " +
          "llmGenerated: ${(llmData["generated"] as? String)?.take(50)}..., " +
          "assistantId: ${llmData["assistantId"]}, " +
          "durationMs: ${llmData["durationMs"]}"
    }

    return PromptOutput(
      prompt = prompt,
      negativePrompt = negativePrompt,
      naturalPrompt = null,
      naturalPromptKo = null,
      stylePreset = presetCode,
      artistTag = null,
      presetCode = presetCode
    )
  }

  /**
   * Phase 6 LLM 생성 프롬프트로 PromptOutput 구성 (phase6 구조 - 레거시 호환)
   */
  @Suppress("UNCHECKED_CAST")
  private fun buildPhase6PromptOutput(
    config: Map<String, Any>,
    phase6Data: Map<String, Any>,
    presetCode: String
  ): PromptOutput {
    val defaultNegativePrompt = runtimeConfigResolver.resolve().defaultNegativePrompt
    val prompt = config["prompt"] as? String
      ?: throw IllegalStateException("[ComfyUI Phase6] prompt가 없습니다")
    val negativePrompt = config["negativePrompt"] as? String
      ?: defaultNegativePrompt

    return PromptOutput(
      prompt = prompt,
      negativePrompt = negativePrompt,
      naturalPrompt = config["naturalPrompt"] as? String,
      naturalPromptKo = config["naturalPromptKo"] as? String,
      stylePreset = config["stylePreset"] as? String ?: presetCode,
      artistTag = config["artistTag"] as? String,
      presetCode = presetCode
    )
  }

  /**
   * Variant 기반 프롬프트 생성 시도 (레거시 호환)
   *
   * @param config promptConfig
   * @return PromptOutput 또는 null (해당 정보가 없으면)
   */
  @Suppress("UNCHECKED_CAST")
  private fun tryBuildVariantPrompts(config: Map<String, Any>): PromptOutput? {
    val variantStr = config["variant"] as? String ?: return null
    val variant = try {
      BackgroundVariant.fromString(variantStr)
    } catch (e: Exception) {
      kLogger.warn { "[ComfyUI] Unknown variant: $variantStr" }
      return null
    }

    val locationMap = config["location"] as? Map<String, Any>
    val metadataMap = config["metadata"] as? Map<String, Any>

    // location이 있으면 전체 프롬프트 빌드
    if (locationMap != null) {
      val location = parseLocationData(locationMap)
      val metadata = parseScenarioMetadata(metadataMap)
      return promptBuilder.buildBackgroundPrompt(location, variant, metadata)
    }

    // location 없으면 단순 프롬프트 빌드
    val description = config["description"] as? String ?: return null
    val era = metadataMap?.get("era") as? String ?: ""
    val region = metadataMap?.get("region") as? String ?: ""

    return promptBuilder.buildSimplePrompt(description, variant, era, region)
  }

  // ========== Private Helper ==========

  @Suppress("UNCHECKED_CAST")
  private fun parseLocationData(locationMap: Map<String, Any>): LocationData {
    return LocationData(
      locationId = locationMap["locationId"] as? String ?: "unknown",
      name = locationMap["name"] as? String ?: "",
      nameKo = locationMap["nameKo"] as? String,
      locationType = locationMap["locationType"] as? String ?: "scenery",
      description = locationMap["description"] as? String ?: "",
      descriptionKo = locationMap["descriptionKo"] as? String,
      detailElements = (locationMap["detailElements"] as? List<String>) ?: emptyList()
    )
  }

  private fun parseScenarioMetadata(metadataMap: Map<String, Any>?): ScenarioMetadata {
    return ScenarioMetadata(
      era = metadataMap?.get("era") as? String ?: "",
      region = metadataMap?.get("region") as? String ?: "",
      atmosphere = metadataMap?.get("atmosphere") as? String,
      weather = metadataMap?.get("weather") as? String,
      projectTitle = metadataMap?.get("projectTitle") as? String
    )
  }
}
