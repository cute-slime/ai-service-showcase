package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.core.platform.component.gateway.ImageGenerationGateway
import com.jongmin.ai.product_agent.platform.component.ComfyUiException
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationRequest
import com.jongmin.ai.product_agent.platform.component.prompt.ImagePromptEvaluator
import com.jongmin.ai.product_agent.platform.component.prompt.ImagePromptGenerator
import com.jongmin.ai.product_agent.platform.dto.request.ProductImageGenerateData
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.ai.storage.S3Service
import com.jongmin.ai.storage.StorageServiceClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeoutException

/**
 * 이미지 생성 작업 실행기
 *
 * Text-to-Image 방식의 상품 이미지 생성 작업을 담당합니다.
 *
 * ### 파이프라인 흐름:
 * 1. EventBridgeFluxSink 생성 (jobId, jobType 연결)
 * 2. 프롬프트 평가 (LLM 기반 가드레일)
 * 3. 프롬프트 생성 (LLM 기반 최적화)
 * 4. ImageGenerationClient를 통한 이미지 생성
 * 5. S3에 이미지 업로드
 * 6. ProductAgentOutput에 결과 저장
 * 7. 완료 이벤트 발행
 *
 * @property objectMapper JSON 변환
 * @property sseHelper SSE 이벤트 헬퍼
 * @property imageOutputHelper 이미지 출력 저장 헬퍼
 * @property imagePromptEvaluator 프롬프트 평가기
 * @property imagePromptGenerator 프롬프트 생성기
 * @property imageGenerationGateway 이미지 생성 게이트웨이 (추적 자동 적용)
 * @property s3Service S3 서비스
 */
@Component
class ImageGenerationExecutor(
  private val objectMapper: ObjectMapper,
  private val sseHelper: ProductAgentSseHelper,
  private val imageOutputHelper: ImageOutputHelper,
  private val imagePromptEvaluator: ImagePromptEvaluator,
  private val imagePromptGenerator: ImagePromptGenerator,
  private val imageGenerationGateway: ImageGenerationGateway,
  private val s3Service: S3Service,
  private val storageServiceClient: StorageServiceClient,
) : ProductAgentExecutor {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val SUB_TYPE = "IMAGE_GENERATE"
  }

  override fun getSubType(): String = SUB_TYPE

  override fun execute(job: DistributedJob, payload: Map<*, *>) {
    // data JSON 문자열 추출 및 파싱
    val dataJson = payload["data"] as String
    val generateData = objectMapper.readValue(dataJson, ProductImageGenerateData::class.java)

    val productName = generateData.productName
      ?: throw IllegalArgumentException("상품명은 필수입니다")
    val userPrompt = generateData.prompt
      ?: throw IllegalArgumentException("프롬프트는 필수입니다")
    val imageStyle = generateData.imageStyle?.code()
    val aspectRatio = generateData.aspectRatio.code()
    val imageCount = generateData.imageCount

    // aspectRatio를 width/height로 변환
    val (width, height) = ImageGenerationRequest.getResolutionFromAspectRatio(aspectRatio)

    kLogger.info {
      "이미지 생성 요청 - jobId: ${job.id}, productName: $productName, " +
          "prompt: ${userPrompt.take(50)}..., resolution: ${width}x${height}, imageCount: $imageCount"
    }

    val emitter = sseHelper.createEmitter(job)
    val startTime = System.currentTimeMillis()
    val generatedImageKeys = mutableListOf<String>()

    try {
      // 1. PROCESSING 상태 전송
      sseHelper.emitStatus(emitter, "PROCESSING", "이미지 생성을 시작합니다...")

      // ========== 2. 프롬프트 평가 (LLM 기반 가드레일) ==========
      sseHelper.emitStatus(emitter, "EVALUATING", "프롬프트를 평가하고 있습니다...")

      val evaluationResult = imagePromptEvaluator.evaluate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = imageStyle
      )

      // 평가 거부 시 즉시 종료
      if (evaluationResult.isRejected) {
        kLogger.warn { "프롬프트 평가 거부 - jobId: ${job.id}, reason: ${evaluationResult.rejectionReason}" }
        sseHelper.emitEvaluationRejected(emitter, evaluationResult)
        emitter.complete()
        return
      }

      sseHelper.emitStatus(emitter, "EVALUATION_PASSED", "프롬프트 평가를 통과했습니다.")
      kLogger.info { "프롬프트 평가 통과 - jobId: ${job.id}" }

      // ========== 3. 프롬프트 생성 (LLM 기반 최적화) ==========
      sseHelper.emitStatus(emitter, "GENERATING_PROMPT", "프롬프트를 이해하고 있습니다...")

      val promptResult = imagePromptGenerator.generate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = imageStyle,
        aspectRatio = aspectRatio
      )

      sseHelper.emitPromptGenerated(emitter)
      kLogger.info { "프롬프트 생성 완료" }

      // ========== 4. 이미지 생성 (ImageGenerationGateway 경유 - 추적 자동 적용) ==========
      for (imageIndex in 1..imageCount) {
        kLogger.info { "이미지 생성 중 - jobId: ${job.id}, index: $imageIndex/$imageCount" }
        sseHelper.emitStatus(emitter, "GENERATING", "이미지를 생성하고 있습니다... ($imageIndex/$imageCount)")

        // ImageGenerationGateway 경유 이미지 생성 (자동 추적)
        val result = imageGenerationGateway.generate(
          prompt = promptResult.positivePrompt,
          negativePrompt = promptResult.negativePrompt,
          width = width,
          height = height,
          seed = System.currentTimeMillis() + imageIndex,
          callerComponent = "ImageGenerationExecutor",
          metadata = mapOf(
            "jobId" to job.id,
            "productName" to productName,
            "imageIndex" to imageIndex,
            "imageCount" to imageCount,
            "imageStyle" to (imageStyle ?: "default")
          )
        )

        if (!result.success || !result.hasImage) {
          throw ComfyUiException(result.errorMessage ?: "이미지 생성 결과가 없습니다")
        }

        kLogger.info { "이미지 생성 완료 - size: ${result.imageSizeBytes} bytes, duration: ${result.generationTimeMs}ms" }

        // ========== 5. S3 업로드 ==========
        sseHelper.emitStatus(emitter, "UPLOADING", "이미지생성이 완료 되었습니다... ($imageIndex/$imageCount)")
        val s3Key = s3Service.uploadImageToTempAndGetKey(
          bytes = result.imageBytes!!,
          pathPrefix = ProductAgentSseHelper.S3_IMAGE_PATH_PREFIX,
          contentType = "image/png"
        )
        generatedImageKeys.add(s3Key)

        kLogger.info { "S3 업로드 완료 - imageIndex: $imageIndex, s3Key: $s3Key" }
      }

      // ========== 6. ProductAgentOutput에 결과 저장 ==========
      val savedResult = imageOutputHelper.saveImageOutput(
        job = job,
        productName = productName,
        prompt = promptResult.positivePrompt,
        imageStyle = imageStyle,
        aspectRatio = aspectRatio,
        imageCount = imageCount,
        generatedImageKeys = generatedImageKeys
      )

      // ========== 7. 완료 이벤트 발행 ========== 
      val presignedUrls = savedResult.committedImageKeys.map { key ->
        s3Service.generateGetPresignedUrl(
          key,
          expirationMinutes = ProductAgentSseHelper.PRESIGNED_URL_EXPIRATION_MINUTES
        )
      }

      sseHelper.emitImageCompleted(emitter, savedResult.output.id, presignedUrls)
      emitter.complete()

      val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
      kLogger.info {
        "이미지 생성 작업 완료 - jobId: ${job.id}, outputId: ${savedResult.output.id}, " +
            "images: ${presignedUrls.size}개, 소요시간: ${String.format("%.2f", elapsedSeconds)}초"
      }

    } catch (e: TimeoutException) {
      cancelStagedKeys(generatedImageKeys)
      kLogger.error(e) { "이미지 생성 타임아웃 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    } catch (e: ComfyUiException) {
      cancelStagedKeys(generatedImageKeys)
      kLogger.error(e) { "ComfyUI 에러 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    } catch (e: Exception) {
      cancelStagedKeys(generatedImageKeys)
      kLogger.error(e) { "이미지 생성 작업 실패 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    }
  }

  private fun cancelStagedKeys(keys: List<String>) {
    val stagedKeys = keys.filter { it.startsWith("_tmp/") }
    if (stagedKeys.isEmpty()) {
      return
    }

    runCatching { storageServiceClient.cancel(stagedKeys) }
      .onFailure { kLogger.warn(it) { "이미지 생성 temp 정리 실패 - keys: ${stagedKeys.joinToString()}" } }
  }
}
