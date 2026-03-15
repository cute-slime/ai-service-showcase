package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.ai.core.AiExecutionType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 오디오 생성 게이트웨이
 *
 * 모든 오디오 관련 AI 호출의 단일 진입점.
 * UnifiedAiExecutionTracker를 통해 자동으로 AiRun/AiRunStep을 생성하고 메트릭을 추적.
 *
 * 지원 기능:
 * - BGM 생성 (Suno)
 * - TTS (OpenAI, ElevenLabs)
 * - STT (OpenAI Whisper)
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class AudioGenerationGateway(
  private val tracker: UnifiedAiExecutionTracker,
  // TODO: 오디오 생성 클라이언트 추가
  // private val ttsClient: TtsClient,
  // private val sttClient: SttClient,
  // private val bgmClient: BgmClient,
) {
  private val kLogger = KotlinLogging.logger {}

  // ==================== 프로바이더/결과 타입 정의 ====================

  /**
   * BGM 생성 프로바이더
   */
  enum class BgmProvider(val code: String, val displayName: String, val costPerMinute: Double) {
    SUNO("suno", "Suno", 0.60);  // $0.60/min

    companion object {
      fun fromCode(code: String): BgmProvider {
        return entries.find { it.code.equals(code, ignoreCase = true) } ?: SUNO
      }
    }
  }

  /**
   * TTS 프로바이더
   */
  enum class TtsProvider(val code: String, val displayName: String, val costPerMillion: Double) {
    OPENAI("openai", "OpenAI TTS", 15.00),          // $15 per 1M chars
    ELEVENLABS("elevenlabs", "ElevenLabs", 30.00);  // $30 per 1M chars (예시)

    companion object {
      fun fromCode(code: String): TtsProvider {
        return entries.find { it.code.equals(code, ignoreCase = true) } ?: OPENAI
      }
    }
  }

  /**
   * STT 프로바이더
   */
  enum class SttProvider(val code: String, val displayName: String, val costPerMinute: Double) {
    WHISPER("openai/whisper", "OpenAI Whisper", 0.006);  // $0.006/min

    companion object {
      fun fromCode(code: String): SttProvider {
        return entries.find { it.code.equals(code, ignoreCase = true) } ?: WHISPER
      }
    }
  }

  /**
   * 오디오 생성 결과 (BGM/TTS 공용)
   */
  data class GeneratedAudio(
    val success: Boolean,
    val audioUrl: String? = null,
    val audioBytes: ByteArray? = null,
    val durationSec: Double,
    val sampleRate: Int? = null,
    val generationTimeMs: Long = 0,
    val errorMessage: String? = null,
    val metadata: Map<String, Any> = emptyMap()
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is GeneratedAudio) return false
      return success == other.success &&
          audioUrl == other.audioUrl &&
          audioBytes?.contentEquals(other.audioBytes) ?: (other.audioBytes == null) &&
          durationSec == other.durationSec
    }

    override fun hashCode(): Int {
      var result = success.hashCode()
      result = 31 * result + (audioUrl?.hashCode() ?: 0)
      result = 31 * result + (audioBytes?.contentHashCode() ?: 0)
      result = 31 * result + durationSec.hashCode()
      return result
    }
  }

  /**
   * STT 결과
   */
  data class TranscriptionResult(
    val success: Boolean,
    val text: String = "",
    val language: String? = null,
    val confidence: Double? = null,
    val audioDurationSec: Double = 0.0,
    val processingTimeMs: Long = 0,
    val errorMessage: String? = null
  )

  // ==================== BGM 생성 ====================

  /**
   * BGM 생성
   *
   * @param provider BGM 생성 프로바이더
   * @param prompt BGM 생성 프롬프트
   * @param durationSec 음악 길이 (초)
   * @param genre 장르 (선택)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 오디오 생성 결과
   */
  fun generateBgm(
    provider: BgmProvider = BgmProvider.SUNO,
    prompt: String,
    durationSec: Double = 30.0,
    genre: String? = null,
    callerComponent: String,
    contextId: Long? = null
  ): GeneratedAudio {
    val requestPayload = buildMap {
      put("prompt", prompt)
      put("durationSec", durationSec)
      genre?.let { put("genre", it) }
    }

    val context = tracker.startExecution(
      executionType = AiExecutionType.AUDIO_GENERATION,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 BGM 생성 클라이언트 호출
      throw AudioGenerationNotImplementedException(provider.displayName)
    } catch (e: AudioGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      GeneratedAudio(
        success = false,
        durationSec = durationSec,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  // ==================== TTS (Text-to-Speech) ====================

  /**
   * TTS - 텍스트를 음성으로 변환
   *
   * @param provider TTS 프로바이더
   * @param text 변환할 텍스트
   * @param voice 음성 유형 (예: "nova", "alloy", "echo")
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 오디오 생성 결과
   */
  fun textToSpeech(
    provider: TtsProvider = TtsProvider.OPENAI,
    text: String,
    voice: String = "nova",
    callerComponent: String,
    contextId: Long? = null
  ): GeneratedAudio {
    val requestPayload = mapOf(
      "text" to text,
      "voice" to voice,
      "charCount" to text.length
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.TTS,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 TTS 클라이언트 호출
      throw AudioGenerationNotImplementedException(provider.displayName)
    } catch (e: AudioGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      GeneratedAudio(
        success = false,
        durationSec = 0.0,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  // ==================== STT (Speech-to-Text) ====================

  /**
   * STT - 음성을 텍스트로 변환
   *
   * @param provider STT 프로바이더
   * @param audioUrl 오디오 파일 URL
   * @param language 언어 힌트 (선택)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 텍스트 변환 결과
   */
  fun speechToText(
    provider: SttProvider = SttProvider.WHISPER,
    audioUrl: String,
    language: String? = null,
    callerComponent: String,
    contextId: Long? = null
  ): TranscriptionResult {
    val requestPayload = buildMap {
      put("audioUrl", audioUrl)
      language?.let { put("language", it) }
    }

    val context = tracker.startExecution(
      executionType = AiExecutionType.STT,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 STT 클라이언트 호출
      throw AudioGenerationNotImplementedException(provider.displayName)
    } catch (e: AudioGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      TranscriptionResult(
        success = false,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * STT - 오디오 바이트 배열로부터 변환
   *
   * @param provider STT 프로바이더
   * @param audioBytes 오디오 바이트 배열
   * @param language 언어 힌트 (선택)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 텍스트 변환 결과
   */
  fun speechToTextFromBytes(
    provider: SttProvider = SttProvider.WHISPER,
    audioBytes: ByteArray,
    language: String? = null,
    callerComponent: String,
    contextId: Long? = null
  ): TranscriptionResult {
    val requestPayload = buildMap {
      put("audioSizeBytes", audioBytes.size)
      language?.let { put("language", it) }
    }

    val context = tracker.startExecution(
      executionType = AiExecutionType.STT,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 STT 클라이언트 호출
      throw AudioGenerationNotImplementedException(provider.displayName)
    } catch (e: AudioGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      TranscriptionResult(
        success = false,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  // ==================== 완료 처리 헬퍼 (내부용) ====================

  /**
   * BGM 생성 완료 처리
   */
  internal fun completeBgmGeneration(
    context: AiExecutionContext,
    result: GeneratedAudio,
    prompt: String,
    genre: String?
  ) {
    val metrics = AiExecutionMetrics.AudioGeneration(
      durationSec = result.durationSec,
      prompt = prompt,
      genre = genre,
      sampleRate = result.sampleRate,
      resultUrl = result.audioUrl,
      totalCost = calculateBgmCost(BgmProvider.SUNO, result.durationSec)
    )

    tracker.completeExecution(context, metrics, buildAudioResponsePayload(result))

    kLogger.info {
      "[AudioGenerationGateway] BGM 생성 완료 - duration: ${result.durationSec}s"
    }
  }

  /**
   * TTS 완료 처리
   */
  internal fun completeTtsGeneration(
    context: AiExecutionContext,
    result: GeneratedAudio,
    inputText: String,
    voice: String
  ) {
    val metrics = AiExecutionMetrics.TextToSpeech(
      inputCharCount = inputText.length,
      durationSec = result.durationSec,
      voice = voice,
      sampleRate = result.sampleRate,
      resultUrl = result.audioUrl,
      totalCost = calculateTtsCost(TtsProvider.OPENAI, inputText.length)
    )

    tracker.completeExecution(context, metrics, buildAudioResponsePayload(result))

    kLogger.info {
      "[AudioGenerationGateway] TTS 완료 - chars: ${inputText.length}, duration: ${result.durationSec}s"
    }
  }

  /**
   * STT 완료 처리
   */
  internal fun completeSttProcessing(
    context: AiExecutionContext,
    result: TranscriptionResult
  ) {
    val metrics = AiExecutionMetrics.SpeechToText(
      audioDurationSec = result.audioDurationSec,
      outputCharCount = result.text.length,
      language = result.language,
      confidence = result.confidence,
      totalCost = calculateSttCost(SttProvider.WHISPER, result.audioDurationSec)
    )

    val responsePayload = mapOf(
      "success" to result.success,
      "text" to result.text,
      "audioDurationSec" to result.audioDurationSec,
      "outputCharCount" to result.text.length,
      "language" to (result.language ?: "unknown"),
      "confidence" to (result.confidence ?: 0.0),
      "processingTimeMs" to result.processingTimeMs
    )

    tracker.completeExecution(context, metrics, responsePayload)

    kLogger.info {
      "[AudioGenerationGateway] STT 완료 - duration: ${result.audioDurationSec}s, " +
          "chars: ${result.text.length}"
    }
  }

  // ==================== 비용 계산 ====================

  private fun calculateBgmCost(provider: BgmProvider, durationSec: Double): Double {
    return provider.costPerMinute * (durationSec / 60.0)
  }

  private fun calculateTtsCost(provider: TtsProvider, charCount: Int): Double {
    return provider.costPerMillion * (charCount / 1_000_000.0)
  }

  private fun calculateSttCost(provider: SttProvider, durationSec: Double): Double {
    return provider.costPerMinute * (durationSec / 60.0)
  }

  private fun buildAudioResponsePayload(result: GeneratedAudio): Map<String, Any> {
    return buildMap {
      put("success", result.success)
      put("durationSec", result.durationSec)
      result.audioUrl?.let { put("audioUrl", it) }
      result.sampleRate?.let { put("sampleRate", it) }
      put("generationTimeMs", result.generationTimeMs)
      result.errorMessage?.let { put("errorMessage", it) }
    }
  }
}

/**
 * 오디오 생성 미구현 예외
 */
class AudioGenerationNotImplementedException(
  providerName: String
) : RuntimeException("$providerName 오디오 생성은 아직 구현되지 않았습니다.")
