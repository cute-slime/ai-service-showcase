package com.jongmin.ai.generation.bo.component

import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.jspring.core.exception.BadRequestException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * generationConfig 파싱 유틸리티
 *
 * FE에서 전달받은 generationConfig를 파싱하여 프로바이더에 전달할
 * 프롬프트와 파라미터를 추출한다.
 *
 * ### 파싱 우선순위:
 * 1. `generationConfig.media[mediaType].providers[providerCode]` (최우선)
 * 2. `generationConfig.default` (fallback)
 * 3. `generationConfig` 루트 레벨 (최종 fallback)
 */
@Component
class GenerationConfigParser {
  private val kLogger = KotlinLogging.logger {}

  /**
   * generationConfig를 파싱하여 ParsedGenerationConfig를 반환한다.
   *
   * @param generationConfig FE에서 전달받은 생성 설정
   * @param mediaType 미디어 타입 (IMAGE, VIDEO, BGM)
   * @param providerCode 프로바이더 코드 (COMFYUI, NOVELAI 등)
   * @return 파싱된 생성 설정
   */
  @Suppress("UNCHECKED_CAST")
  fun parse(
    generationConfig: Map<String, Any>,
    mediaType: String,
    providerCode: String,
  ): ParsedGenerationConfig {
    kLogger.debug { "[ConfigParser] 파싱 시작 - mediaType: $mediaType, provider: $providerCode" }

    // 1순위: media[mediaType].providers[providerCode]
    val mediaConfig = generationConfig["media"] as? Map<String, Any>
    val mediaTypeConfig = mediaConfig?.get(mediaType.lowercase()) as? Map<String, Any>
    val providersConfig = mediaTypeConfig?.get("providers") as? Map<String, Any>
    val providerSpecificConfig = providersConfig?.get(providerCode.uppercase()) as? Map<String, Any>

    // 2순위: default
    val defaultConfig = generationConfig["default"] as? Map<String, Any>

    // 설정 병합 (providerSpecific > default > root)
    val effectiveConfig = mutableMapOf<String, Any>()

    // 루트 레벨 값 먼저 적용
    generationConfig.forEach { (key, value) ->
      if (key != "media" && key != "default") {
        effectiveConfig[key] = value
      }
    }

    // default 값 오버라이드
    defaultConfig?.let { effectiveConfig.putAll(it) }

    // provider-specific 값 최종 오버라이드
    providerSpecificConfig?.let { effectiveConfig.putAll(it) }

    // 루트에 실어 보낸 실행 시점(runtime) 오버라이드는 최종 우선한다.
    // 파이프라인 입력값(input/url/base64 등)이 default/provider 설정에 의해 덮이지 않도록 보호한다.
    applyRuntimeOverrides(generationConfig, effectiveConfig)

    val workflowPipeline = parseWorkflowPipeline(effectiveConfig["workflowPipeline"])
    val promptRequired = workflowPipeline != GenerationWorkflowPipeline.MEDIA_TO_MEDIA

    // 프롬프트 추출
    val positivePrompt = effectiveConfig["positivePrompt"] as? String
      ?: effectiveConfig["positive"] as? String
      ?: effectiveConfig["prompt"] as? String

    // positivePrompt 필수 검증
    if (promptRequired && positivePrompt.isNullOrBlank()) {
      throw BadRequestException(
        "positivePrompt는 필수입니다. generationConfig에 'positivePrompt', 'positive', 또는 'prompt' 키로 제공해주세요."
      )
    }

    val negativePrompt = effectiveConfig["negativePrompt"] as? String
      ?: effectiveConfig["negative"] as? String

    // 파라미터 추출 (프롬프트 키 제외)
    val promptKeys = setOf("positivePrompt", "positive", "prompt", "negativePrompt", "negative")
    val params = effectiveConfig.filterKeys { it !in promptKeys }.toMutableMap()

    // seed 처리: -1 또는 미지정 → 랜덤 시드 생성
    val seedValue = params["seed"]
    if (seedValue == null || seedValue == -1 || seedValue == -1L) {
      params["seed"] = Random.nextLong(0, Long.MAX_VALUE)
      kLogger.debug { "[ConfigParser] 시드 자동 생성 - seed: ${params["seed"]}" }
    }

    val result = ParsedGenerationConfig(
      positivePrompt = positivePrompt.orEmpty(),
      negativePrompt = negativePrompt,
      params = params,
      stylePreset = effectiveConfig["stylePreset"] as? String,
      modelCode = effectiveConfig["modelCode"] as? String,
    )

    kLogger.debug {
      "[ConfigParser] 파싱 완료 - positivePrompt: ${positivePrompt.orEmpty().take(50)}..., " +
          "negativePrompt: ${negativePrompt?.take(30) ?: "null"}, " +
          "params: ${params.keys}"
    }

    return result
  }

  private fun parseWorkflowPipeline(raw: Any?): GenerationWorkflowPipeline? {
    val value = raw?.toString()?.trim()?.uppercase() ?: return null
    return when (value) {
      "URL_TO_MEDIA", "IMAGE_TO_IMAGE" -> GenerationWorkflowPipeline.MEDIA_TO_MEDIA
      else -> runCatching { GenerationWorkflowPipeline.valueOf(value) }.getOrNull()
    }
  }

  private fun applyRuntimeOverrides(
    rootConfig: Map<String, Any>,
    effectiveConfig: MutableMap<String, Any>
  ) {
    val runtimeOverrideKeys = setOf(
      "workflowId",
      "workflowPipeline",
      "workflowJson",
      "seed",
      "width",
      "height",
      "input",
      "inputUrl",
      "inputMediaUrl",
      "inputSourceUrl",
      "inputFileName",
      "inputContentType",
      "inputImageData",
      "inputBase64",
      "imageData",
      "base64Data",
      "url",
      "sourceUrl"
    )

    runtimeOverrideKeys.forEach { key ->
      rootConfig[key]?.let { effectiveConfig[key] = it }
    }
  }
}

/**
 * 파싱된 생성 설정
 *
 * generationConfig에서 추출한 프롬프트와 파라미터를 담는 데이터 클래스.
 * GenerationContext의 promptConfig/generationConfig 맵으로 변환하여 사용한다.
 */
data class ParsedGenerationConfig(
  /** 긍정 프롬프트 (필수) */
  val positivePrompt: String,

  /** 부정 프롬프트 (선택) */
  val negativePrompt: String? = null,

  /** 생성 파라미터 (width, height, steps, cfgScale, seed, samplerName 등) */
  val params: Map<String, Any> = emptyMap(),

  /** 스타일 프리셋 (선택) */
  val stylePreset: String? = null,

  /** 모델 코드 오버라이드 (선택) */
  val modelCode: String? = null,
) {
  /**
   * GenerationContext.promptConfig에 전달할 Map 변환
   */
  fun toPromptConfigMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    if (positivePrompt.isNotBlank()) {
      map["positive"] = positivePrompt
    }
    negativePrompt?.let { map["negative"] = it }
    return map
  }

  /**
   * GenerationContext.generationConfig에 전달할 Map 변환
   */
  fun toGenerationConfigMap(): Map<String, Any> {
    val map = params.toMutableMap()
    stylePreset?.let { map["stylePreset"] = it }
    modelCode?.let { map["modelCode"] = it }
    return map
  }
}
