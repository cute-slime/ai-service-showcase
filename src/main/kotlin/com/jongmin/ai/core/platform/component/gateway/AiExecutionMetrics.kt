package com.jongmin.ai.core.platform.component.gateway

/**
 * AI 실행 메트릭
 *
 * AI 유형별 상세 메트릭을 표현하는 sealed class.
 * 게이트웨이가 실행 완료 시 이 객체를 생성하여 AiRunStep.executionMetrics에 저장.
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
sealed class AiExecutionMetrics {

  /**
   * 메트릭을 Map으로 변환 (JSON 저장용)
   */
  abstract fun toMap(): Map<String, Any>

  /**
   * 총 비용 (USD)
   */
  abstract val totalCost: Double

  // ========== LLM/VLM 메트릭 ==========

  /**
   * 토큰 기반 메트릭 (LLM, VLM)
   */
  data class TokenBased(
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long = 0,
    val cacheCreationTokens: Long = 0,
    val inputCost: Double = 0.0,
    val outputCost: Double = 0.0,
    val cachedCost: Double = 0.0,
    override val totalCost: Double = inputCost + outputCost + cachedCost,
    val imageTokens: Long = 0,  // VLM 전용
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "TOKEN_BASED")
      put("inputTokens", inputTokens)
      put("outputTokens", outputTokens)
      if (cachedTokens > 0) put("cachedTokens", cachedTokens)
      if (cacheCreationTokens > 0) put("cacheCreationTokens", cacheCreationTokens)
      if (imageTokens > 0) put("imageTokens", imageTokens)
      put("inputCost", inputCost)
      put("outputCost", outputCost)
      if (cachedCost > 0) put("cachedCost", cachedCost)
      put("totalCost", totalCost)
    }
  }

  // ========== 이미지 생성 메트릭 ==========

  /**
   * 이미지 생성 메트릭
   */
  data class ImageGeneration(
    val count: Int,
    val width: Int,
    val height: Int,
    val prompt: String,
    val negativePrompt: String? = null,
    val seed: Long? = null,
    val generationTimeMs: Long = 0,
    val resultUrls: List<String> = emptyList(),
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "IMAGE_GENERATION")
      put("count", count)
      put("width", width)
      put("height", height)
      put("prompt", prompt)
      negativePrompt?.let { put("negativePrompt", it) }
      seed?.let { put("seed", it) }
      if (generationTimeMs > 0) put("generationTimeMs", generationTimeMs)
      if (resultUrls.isNotEmpty()) put("resultUrls", resultUrls)
      put("totalCost", totalCost)
    }
  }

  // ========== 비디오 생성 메트릭 ==========

  /**
   * 비디오 생성 메트릭
   */
  data class VideoGeneration(
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val fps: Int? = null,
    val prompt: String,
    val fileSizeBytes: Long? = null,
    val resultUrl: String? = null,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "VIDEO_GENERATION")
      put("durationSec", durationSec)
      put("width", width)
      put("height", height)
      fps?.let { put("fps", it) }
      put("prompt", prompt)
      fileSizeBytes?.let { put("fileSizeBytes", it) }
      resultUrl?.let { put("resultUrl", it) }
      put("totalCost", totalCost)
    }
  }

  // ========== 오디오 생성 메트릭 ==========

  /**
   * 오디오/BGM 생성 메트릭
   */
  data class AudioGeneration(
    val durationSec: Double,
    val prompt: String? = null,
    val genre: String? = null,
    val sampleRate: Int? = null,
    val resultUrl: String? = null,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "AUDIO_GENERATION")
      put("durationSec", durationSec)
      prompt?.let { put("prompt", it) }
      genre?.let { put("genre", it) }
      sampleRate?.let { put("sampleRate", it) }
      resultUrl?.let { put("resultUrl", it) }
      put("totalCost", totalCost)
    }
  }

  // ========== TTS 메트릭 ==========

  /**
   * Text-to-Speech 메트릭
   */
  data class TextToSpeech(
    val inputCharCount: Int,
    val durationSec: Double,
    val voice: String,
    val sampleRate: Int? = null,
    val resultUrl: String? = null,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "TTS")
      put("inputCharCount", inputCharCount)
      put("durationSec", durationSec)
      put("voice", voice)
      sampleRate?.let { put("sampleRate", it) }
      resultUrl?.let { put("resultUrl", it) }
      put("totalCost", totalCost)
    }
  }

  // ========== STT 메트릭 ==========

  /**
   * Speech-to-Text 메트릭
   */
  data class SpeechToText(
    val audioDurationSec: Double,
    val outputCharCount: Int,
    val language: String? = null,
    val confidence: Double? = null,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "STT")
      put("audioDurationSec", audioDurationSec)
      put("outputCharCount", outputCharCount)
      language?.let { put("language", it) }
      confidence?.let { put("confidence", it) }
      put("totalCost", totalCost)
    }
  }

  // ========== 임베딩 메트릭 ==========

  /**
   * 임베딩/벡터 생성 메트릭
   */
  data class Embedding(
    val inputTokens: Long,
    val dimensions: Int,
    val chunkCount: Int = 1,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "EMBEDDING")
      put("inputTokens", inputTokens)
      put("dimensions", dimensions)
      put("chunkCount", chunkCount)
      put("totalCost", totalCost)
    }
  }

  // ========== OCR 메트릭 ==========

  /**
   * 광학 문자 인식 메트릭
   */
  data class Ocr(
    val imageCount: Int,
    val outputCharCount: Int,
    val language: String? = null,
    val confidence: Double? = null,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "OCR")
      put("imageCount", imageCount)
      put("outputCharCount", outputCharCount)
      language?.let { put("language", it) }
      confidence?.let { put("confidence", it) }
      put("totalCost", totalCost)
    }
  }

  // ========== 웹 검색 메트릭 ==========

  /**
   * 웹 검색 메트릭
   */
  data class WebSearch(
    val query: String,
    val resultCount: Int,
    val searchTimeMs: Long = 0,
    override val totalCost: Double = 0.0,
  ) : AiExecutionMetrics() {
    override fun toMap(): Map<String, Any> = buildMap {
      put("type", "WEB_SEARCH")
      put("query", query)
      put("resultCount", resultCount)
      if (searchTimeMs > 0) put("searchTimeMs", searchTimeMs)
      put("totalCost", totalCost)
    }
  }
}
