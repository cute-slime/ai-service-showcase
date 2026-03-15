package com.jongmin.ai.core.platform.component.gateway

import com.jongmin.ai.core.AiExecutionType
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationClientRouter
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationProvider
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationRequest
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 이미지 생성 게이트웨이
 *
 * 모든 이미지 생성(텍스트→이미지) 호출의 단일 진입점.
 * UnifiedAiExecutionTracker를 통해 자동으로 AiRun/AiRunStep을 생성하고 메트릭을 추적.
 *
 * 지원 프로바이더:
 * - ComfyUI (Z Image)
 * - DALL-E 3 (미구현)
 * - Midjourney (미구현)
 * - Google Imagen (미구현)
 *
 * 주요 기능:
 * - 단일/다중 이미지 생성
 * - 프로바이더별 라우팅
 * - 자동 추적 (해상도, 생성 시간, 비용 등)
 *
 * @author Jongmin
 * @since 2026. 1. 9
 */
@Component
class ImageGenerationGateway(
  private val tracker: UnifiedAiExecutionTracker,
  private val clientRouter: ImageGenerationClientRouter
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 이미지 생성 (단일)
   *
   * @param provider 이미지 생성 프로바이더 (기본: COMFYUI)
   * @param prompt 이미지 생성 프롬프트
   * @param negativePrompt 네거티브 프롬프트 (선택)
   * @param width 이미지 너비
   * @param height 이미지 높이
   * @param seed 시드값 (선택, 재현성 확보용)
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param metadata 추가 메타데이터
   * @return 이미지 생성 결과
   */
  fun generate(
    provider: ImageGenerationProvider = ImageGenerationProvider.getDefault(),
    prompt: String,
    negativePrompt: String = ImageGenerationRequest.DEFAULT_NEGATIVE_PROMPT,
    width: Int = ImageGenerationRequest.DEFAULT_WIDTH,
    height: Int = ImageGenerationRequest.DEFAULT_HEIGHT,
    seed: Long? = null,
    callerComponent: String,
    contextId: Long? = null,
    metadata: Map<String, Any> = emptyMap()
  ): ImageGenerationResult {
    val requestPayload = buildRequestPayload(prompt, negativePrompt, width, height, seed, 1, metadata)

    // 추적 시작
    val context = tracker.startExecution(
      executionType = AiExecutionType.IMAGE_GENERATION,
      provider = provider.code(),
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      val client = clientRouter.getClient(provider)

      val request = ImageGenerationRequest(
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        seed = seed,
        imageCount = 1,
        metadata = metadata
      )

      val result = client.generateImage(request)

      // 추적 완료
      val metrics = buildMetrics(result, listOf(result))
      tracker.completeExecution(
        context = context,
        metrics = metrics,
        responsePayload = buildResponsePayload(result)
      )

      kLogger.debug {
        "[ImageGenerationGateway] 생성 완료 - caller: $callerComponent, " +
            "provider: ${provider.displayName}, size: ${width}x${height}, " +
            "timeMs: ${result.generationTimeMs}"
      }

      result
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 이미지 생성 (다중)
   *
   * @param provider 이미지 생성 프로바이더
   * @param prompt 이미지 생성 프롬프트
   * @param negativePrompt 네거티브 프롬프트
   * @param width 이미지 너비
   * @param height 이미지 높이
   * @param seed 시드값 (선택)
   * @param count 생성할 이미지 수
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param metadata 추가 메타데이터
   * @return 이미지 생성 결과 목록
   */
  fun generateMultiple(
    provider: ImageGenerationProvider = ImageGenerationProvider.getDefault(),
    prompt: String,
    negativePrompt: String = ImageGenerationRequest.DEFAULT_NEGATIVE_PROMPT,
    width: Int = ImageGenerationRequest.DEFAULT_WIDTH,
    height: Int = ImageGenerationRequest.DEFAULT_HEIGHT,
    seed: Long? = null,
    count: Int = 1,
    callerComponent: String,
    contextId: Long? = null,
    metadata: Map<String, Any> = emptyMap()
  ): List<ImageGenerationResult> {
    val requestPayload = buildRequestPayload(prompt, negativePrompt, width, height, seed, count, metadata)

    val context = tracker.startExecution(
      executionType = AiExecutionType.IMAGE_GENERATION,
      provider = provider.code(),
      modelName = provider.displayName,
      callerComponent = callerComponent,
      contextId = contextId,
      requestPayload = requestPayload
    )

    return try {
      val client = clientRouter.getClient(provider)

      val request = ImageGenerationRequest(
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        seed = seed,
        imageCount = count,
        metadata = metadata
      )

      val results = client.generateImages(request)

      // 메트릭 집계
      val successResults = results.filter { it.success }
      val totalTimeMs = results.sumOf { it.generationTimeMs }
      val firstResult = results.firstOrNull()

      val metrics = AiExecutionMetrics.ImageGeneration(
        count = results.size,
        width = width,
        height = height,
        prompt = prompt,
        negativePrompt = negativePrompt,
        seed = seed,
        generationTimeMs = totalTimeMs,
        resultUrls = successResults.mapNotNull { it.s3Key },
        totalCost = calculateCost(provider, count, width, height)
      )

      tracker.completeExecution(
        context = context,
        metrics = metrics,
        responsePayload = buildMultipleResponsePayload(results)
      )

      kLogger.info {
        "[ImageGenerationGateway] 다중 생성 완료 - caller: $callerComponent, " +
            "provider: ${provider.displayName}, count: $count, " +
            "success: ${successResults.size}/$count, totalTimeMs: $totalTimeMs"
      }

      results
    } catch (e: Exception) {
      tracker.failExecution(context, e)
      throw e
    }
  }

  /**
   * 종횡비 기반 이미지 생성
   *
   * @param provider 이미지 생성 프로바이더
   * @param prompt 이미지 생성 프롬프트
   * @param negativePrompt 네거티브 프롬프트
   * @param aspectRatio 종횡비 (예: "1:1", "16:9", "9:16")
   * @param seed 시드값
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @param metadata 추가 메타데이터
   * @return 이미지 생성 결과
   */
  fun generateWithAspectRatio(
    provider: ImageGenerationProvider = ImageGenerationProvider.getDefault(),
    prompt: String,
    negativePrompt: String = ImageGenerationRequest.DEFAULT_NEGATIVE_PROMPT,
    aspectRatio: String = "1:1",
    seed: Long? = null,
    callerComponent: String,
    contextId: Long? = null,
    metadata: Map<String, Any> = emptyMap()
  ): ImageGenerationResult {
    val (width, height) = ImageGenerationRequest.getResolutionFromAspectRatio(aspectRatio)

    return generate(
      provider = provider,
      prompt = prompt,
      negativePrompt = negativePrompt,
      width = width,
      height = height,
      seed = seed,
      callerComponent = callerComponent,
      contextId = contextId,
      metadata = metadata + mapOf("aspectRatio" to aspectRatio)
    )
  }

  /**
   * 기존 ImageGenerationRequest 기반 생성 (호환성 유지)
   *
   * @param provider 이미지 생성 프로바이더
   * @param request 이미지 생성 요청
   * @param callerComponent 호출자 컴포넌트 식별자
   * @param contextId 컨텍스트 ID
   * @return 이미지 생성 결과 목록
   */
  fun generateFromRequest(
    provider: ImageGenerationProvider = ImageGenerationProvider.getDefault(),
    request: ImageGenerationRequest,
    callerComponent: String,
    contextId: Long? = null
  ): List<ImageGenerationResult> {
    return if (request.imageCount == 1) {
      listOf(
        generate(
          provider = provider,
          prompt = request.prompt,
          negativePrompt = request.negativePrompt,
          width = request.width,
          height = request.height,
          seed = request.seed,
          callerComponent = callerComponent,
          contextId = contextId,
          metadata = request.metadata
        )
      )
    } else {
      generateMultiple(
        provider = provider,
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = request.seed,
        count = request.imageCount,
        callerComponent = callerComponent,
        contextId = contextId,
        metadata = request.metadata
      )
    }
  }

  /**
   * 사용 가능한 프로바이더 목록 반환
   */
  fun getAvailableProviders(): List<ImageGenerationProvider> {
    return clientRouter.getAvailableClients().map { it.getProviderType() }
  }

  /**
   * 특정 프로바이더 사용 가능 여부 확인
   */
  fun isProviderAvailable(provider: ImageGenerationProvider): Boolean {
    return clientRouter.isProviderAvailable(provider)
  }

  // ==================== Private Helper Methods ====================

  /**
   * 요청 페이로드 빌드
   */
  private fun buildRequestPayload(
    prompt: String,
    negativePrompt: String,
    width: Int,
    height: Int,
    seed: Long?,
    count: Int,
    metadata: Map<String, Any>
  ): Map<String, Any> {
    return buildMap {
      put("prompt", prompt)
      put("negativePrompt", negativePrompt)
      put("width", width)
      put("height", height)
      put("count", count)
      seed?.let { put("seed", it) }
      if (metadata.isNotEmpty()) put("metadata", metadata)
    }
  }

  /**
   * 메트릭 빌드
   */
  private fun buildMetrics(
    result: ImageGenerationResult,
    allResults: List<ImageGenerationResult>
  ): AiExecutionMetrics.ImageGeneration {
    return AiExecutionMetrics.ImageGeneration(
      count = allResults.size,
      width = result.width,
      height = result.height,
      prompt = result.prompt,
      negativePrompt = result.negativePrompt,
      seed = result.seed,
      generationTimeMs = result.generationTimeMs,
      resultUrls = allResults.filter { it.success }.mapNotNull { it.s3Key },
      totalCost = calculateCost(result.provider, allResults.size, result.width, result.height)
    )
  }

  /**
   * 응답 페이로드 빌드 (단일)
   */
  private fun buildResponsePayload(result: ImageGenerationResult): Map<String, Any> {
    return buildMap {
      put("success", result.success)
      put("provider", result.provider.code())
      put("width", result.width)
      put("height", result.height)
      put("seed", result.seed)
      put("generationTimeMs", result.generationTimeMs)
      result.s3Key?.let { put("s3Key", it) }
      result.errorMessage?.let { put("errorMessage", it) }
      if (result.metadata.isNotEmpty()) put("metadata", result.metadata)
    }
  }

  /**
   * 응답 페이로드 빌드 (다중)
   */
  private fun buildMultipleResponsePayload(results: List<ImageGenerationResult>): Map<String, Any> {
    val successCount = results.count { it.success }
    return mapOf(
      "totalCount" to results.size,
      "successCount" to successCount,
      "failureCount" to (results.size - successCount),
      "totalGenerationTimeMs" to results.sumOf { it.generationTimeMs },
      "results" to results.map { buildResponsePayload(it) }
    )
  }

  /**
   * 비용 계산 (프로바이더별)
   *
   * 현재 ComfyUI는 자체 호스팅으로 비용 0.
   * 향후 DALL-E, Midjourney 등 추가 시 가격 정책 반영.
   */
  private fun calculateCost(
    provider: ImageGenerationProvider,
    count: Int,
    width: Int,
    height: Int
  ): Double {
    return when (provider) {
      ImageGenerationProvider.COMFYUI -> 0.0  // 자체 호스팅
      ImageGenerationProvider.DALLE -> {
        // DALL-E 3 가격 정책 (예시)
        val sizeMultiplier = when {
          width <= 1024 && height <= 1024 -> 0.04
          width <= 1024 && height <= 1792 -> 0.08
          else -> 0.08
        }
        count * sizeMultiplier
      }

      ImageGenerationProvider.MIDJOURNEY -> count * 0.01  // 예시 가격
      ImageGenerationProvider.IMAGEN -> count * 0.02  // 예시 가격
    }
  }
}
