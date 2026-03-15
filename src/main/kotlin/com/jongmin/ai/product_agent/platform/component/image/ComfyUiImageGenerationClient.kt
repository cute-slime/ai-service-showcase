package com.jongmin.ai.product_agent.platform.component.image

import com.jongmin.ai.product_agent.platform.component.ComfyUiClient
import com.jongmin.ai.product_agent.platform.component.ComfyUiException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.*

/**
 * ComfyUI 이미지 생성 클라이언트
 *
 * ComfyUI REST API를 사용하여 이미지를 생성합니다.
 * 현재 기본 프로바이더로 사용되며, Z Image 모델을 활용합니다.
 *
 * ### 동작 흐름:
 * 1. 워크플로우 빌드 (prompt, negativePrompt, width, height, seed)
 * 2. ComfyUI에 워크플로우 제출
 * 3. 폴링으로 생성 완료 대기
 * 4. 생성된 이미지 다운로드
 *
 * @property comfyUiClient ComfyUI REST API 클라이언트
 */
@Component
class ComfyUiImageGenerationClient(
  private val comfyUiClientProvider: ObjectProvider<ComfyUiClient>,
) : ImageGenerationClient {

  private val kLogger = KotlinLogging.logger {}

  override fun getProviderType(): ImageGenerationProvider = ImageGenerationProvider.COMFYUI

  override fun isAvailable(): Boolean {
    val comfyUiClient = comfyUiClientProvider.ifAvailable
      ?: return false

    return try {
      comfyUiClient.isHealthy()
    } catch (e: Exception) {
      kLogger.warn(e) { "ComfyUI 서버 헬스체크 실패" }
      false
    }
  }

  /**
   * 이미지를 생성합니다.
   *
   * @param request 이미지 생성 요청
   * @return 이미지 생성 결과
   */
  override fun generateImage(request: ImageGenerationRequest): ImageGenerationResult {
    val startTime = System.currentTimeMillis()
    val seed = request.getEffectiveSeed()
    val clientId = "img-gen-${UUID.randomUUID()}"
    val comfyUiClient = comfyUiClientProvider.ifAvailable
      ?: return ImageGenerationResult.failure(
        provider = ImageGenerationProvider.COMFYUI,
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = seed,
        errorMessage = "ComfyUI client bean이 등록되지 않아 사용할 수 없습니다",
        generationTimeMs = System.currentTimeMillis() - startTime,
      )

    kLogger.info {
      "ComfyUI 이미지 생성 시작 - prompt: ${request.prompt.take(50)}..., " +
          "resolution: ${request.width}x${request.height}, seed: $seed"
    }

    try {
      // 1. 워크플로우 빌드
      val workflow = comfyUiClient.buildWorkflow(
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = seed,
      )

      // 2. 워크플로우 제출
      val promptResponse = comfyUiClient.submitPrompt(workflow, clientId)
      kLogger.debug { "ComfyUI 워크플로우 제출 완료 - promptId: ${promptResponse.promptId}" }

      // 3. 완료 대기
      val historyResponse = comfyUiClient.waitForCompletion(promptResponse.promptId)

      if (historyResponse.imageFilenames.isEmpty()) {
        throw ComfyUiException("이미지 생성 결과가 없습니다")
      }

      // 4. 이미지 다운로드 (첫 번째 이미지)
      val filename = historyResponse.imageFilenames.first()
      val imageBytes = comfyUiClient.downloadImage(filename)

      val generationTimeMs = System.currentTimeMillis() - startTime
      kLogger.info {
        "ComfyUI 이미지 생성 완료 - size: ${imageBytes.size} bytes, duration: ${generationTimeMs}ms"
      }

      return ImageGenerationResult.success(
        imageBytes = imageBytes,
        provider = ImageGenerationProvider.COMFYUI,
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = seed,
        generationTimeMs = generationTimeMs,
        metadata = mapOf(
          "promptId" to promptResponse.promptId,
          "filename" to filename,
        ),
      )
    } catch (e: ComfyUiException) {
      val generationTimeMs = System.currentTimeMillis() - startTime
      kLogger.error(e) { "ComfyUI 이미지 생성 실패" }
      return ImageGenerationResult.failure(
        provider = ImageGenerationProvider.COMFYUI,
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = seed,
        errorMessage = e.message ?: "ComfyUI 이미지 생성 실패",
        generationTimeMs = generationTimeMs,
      )
    } catch (e: Exception) {
      val generationTimeMs = System.currentTimeMillis() - startTime
      kLogger.error(e) { "ComfyUI 이미지 생성 중 예외 발생" }
      return ImageGenerationResult.failure(
        provider = ImageGenerationProvider.COMFYUI,
        prompt = request.prompt,
        negativePrompt = request.negativePrompt,
        width = request.width,
        height = request.height,
        seed = seed,
        errorMessage = e.message ?: "알 수 없는 오류",
        generationTimeMs = generationTimeMs,
      )
    }
  }
}
