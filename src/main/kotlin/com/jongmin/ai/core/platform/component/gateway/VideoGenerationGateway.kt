package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.ai.core.AiExecutionType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 비디오 생성 게이트웨이
 *
 * 모든 비디오 생성(텍스트/이미지→비디오) 호출의 단일 진입점.
 * UnifiedAiExecutionTracker를 통해 자동으로 AiRun/AiRunStep을 생성하고 메트릭을 추적.
 *
 * 지원 프로바이더:
 * - Runway (Gen-3)
 * - Pika
 * - Kling
 *
 * 주요 기능:
 * - 텍스트→비디오 생성
 * - 이미지→비디오 변환
 * - 자동 추적 (길이, 해상도, 비용 등)
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class VideoGenerationGateway(
  private val tracker: UnifiedAiExecutionTracker,
  // TODO: 비디오 생성 클라이언트 라우터 추가
  // private val clientRouter: VideoGenerationClientRouter
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 비디오 생성 프로바이더
   */
  enum class VideoProvider(val code: String, val displayName: String, val costPerSecond: Double) {
    RUNWAY("runway", "Runway Gen-3", 0.05),      // $3/min = $0.05/sec
    PIKA("pika", "Pika", 0.02),
    KLING("kling", "Kling", 0.03);

    companion object {
      fun fromCode(code: String): VideoProvider {
        return entries.find { it.code.equals(code, ignoreCase = true) } ?: RUNWAY
      }
    }
  }

  /**
   * 비디오 생성 결과
   */
  data class GeneratedVideo(
    val success: Boolean,
    val videoUrl: String? = null,
    val provider: VideoProvider,
    val prompt: String,
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val fps: Int? = null,
    val fileSizeBytes: Long? = null,
    val generationTimeMs: Long = 0,
    val errorMessage: String? = null,
    val metadata: Map<String, Any> = emptyMap()
  )

  /**
   * 텍스트로부터 비디오 생성
   *
   * @param provider 비디오 생성 프로바이더
   * @param prompt 비디오 생성 프롬프트
   * @param durationSec 비디오 길이 (초)
   * @param width 비디오 너비
   * @param height 비디오 높이
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 비디오 생성 결과
   */
  fun generateFromText(
    provider: VideoProvider = VideoProvider.RUNWAY,
    prompt: String,
    durationSec: Double = 5.0,
    width: Int = 1920,
    height: Int = 1080,
    callerComponent: String,
    contextId: Long? = null
  ): GeneratedVideo {
    val requestPayload = mapOf(
      "prompt" to prompt,
      "durationSec" to durationSec,
      "width" to width,
      "height" to height
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.VIDEO_GENERATION,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 비디오 생성 클라이언트 호출
      // val result = clientRouter.getClient(provider).generateFromText(...)

      // 현재는 미구현 - placeholder 응답
      throw VideoGenerationNotImplementedException(provider)
    } catch (e: VideoGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      GeneratedVideo(
        success = false,
        provider = provider,
        prompt = prompt,
        durationSec = durationSec,
        width = width,
        height = height,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 이미지로부터 비디오 생성 (Image-to-Video)
   *
   * @param provider 비디오 생성 프로바이더
   * @param imageUrl 소스 이미지 URL
   * @param prompt 모션 프롬프트
   * @param durationSec 비디오 길이 (초)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 비디오 생성 결과
   */
  fun generateFromImage(
    provider: VideoProvider = VideoProvider.RUNWAY,
    imageUrl: String,
    prompt: String,
    durationSec: Double = 5.0,
    callerComponent: String,
    contextId: Long? = null
  ): GeneratedVideo {
    val requestPayload = mapOf(
      "imageUrl" to imageUrl,
      "prompt" to prompt,
      "durationSec" to durationSec
    )

    val context = tracker.startExecution(
      executionType = AiExecutionType.VIDEO_GENERATION,
      provider = provider.code,
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      // TODO: 실제 비디오 생성 클라이언트 호출
      throw VideoGenerationNotImplementedException(provider)
    } catch (e: VideoGenerationNotImplementedException) {
      tracker.failExecution(context, e)
      GeneratedVideo(
        success = false,
        provider = provider,
        prompt = prompt,
        durationSec = durationSec,
        width = 0,
        height = 0,
        errorMessage = e.message
      )
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 비디오 생성 완료 처리 (내부용)
   *
   * 비동기 생성 작업 완료 시 호출하여 추적을 완료합니다.
   * BackgroundAsset 시스템의 콜백에서 사용됩니다.
   */
  internal fun completeVideoGeneration(
    context: AiExecutionContext,
    result: GeneratedVideo
  ) {
    val metrics = AiExecutionMetrics.VideoGeneration(
      durationSec = result.durationSec,
      width = result.width,
      height = result.height,
      fps = result.fps,
      prompt = result.prompt,
      fileSizeBytes = result.fileSizeBytes,
      resultUrl = result.videoUrl,
      totalCost = calculateCost(result.provider, result.durationSec)
    )

    val responsePayload = buildMap {
      put("success", result.success)
      put("provider", result.provider.code)
      put("durationSec", result.durationSec)
      put("width", result.width)
      put("height", result.height)
      result.fps?.let { put("fps", it) }
      result.videoUrl?.let { put("videoUrl", it) }
      result.fileSizeBytes?.let { put("fileSizeBytes", it) }
      put("generationTimeMs", result.generationTimeMs)
      result.errorMessage?.let { put("errorMessage", it) }
    }

    tracker.completeExecution(context, metrics, responsePayload)

    kLogger.info {
      "[VideoGenerationGateway] 생성 완료 - provider: ${result.provider.displayName}, " +
          "duration: ${result.durationSec}s, size: ${result.width}x${result.height}"
    }
  }

  /**
   * 비용 계산 (프로바이더별)
   */
  private fun calculateCost(provider: VideoProvider, durationSec: Double): Double {
    return provider.costPerSecond * durationSec
  }
}

/**
 * 비디오 생성 미구현 예외
 */
class VideoGenerationNotImplementedException(
  provider: VideoGenerationGateway.VideoProvider
) : RuntimeException("${provider.displayName} 비디오 생성은 아직 구현되지 않았습니다.")
