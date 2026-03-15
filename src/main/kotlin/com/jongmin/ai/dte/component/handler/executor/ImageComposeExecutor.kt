package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.core.platform.component.gateway.ImageGenerationGateway
import com.jongmin.ai.product_agent.platform.component.ComfyUiException
import com.jongmin.ai.product_agent.platform.component.image.ImageGenerationRequest
import com.jongmin.ai.product_agent.platform.component.prompt.ImagePromptEvaluator
import com.jongmin.ai.product_agent.platform.component.prompt.ImagePromptGenerator
import com.jongmin.ai.product_agent.platform.component.prompt.PromptGenerationResult
import com.jongmin.ai.product_agent.platform.dto.request.ProductImageComposeData
import com.jongmin.ai.product_agent.platform.dto.request.ReferenceImageRoleInfo
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.ai.storage.S3Service
import com.jongmin.ai.storage.StorageServiceClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeoutException

/**
 * 이미지 합성 작업 실행기
 *
 * 여러 참조 이미지를 합성하여 새로운 마케팅 이미지를 생성합니다.
 * 테스트 모드와 실제 모드를 지원합니다.
 *
 * ### 파이프라인 흐름 (실제 모드):
 * 1. EventBridgeFluxSink 생성 (jobId, jobType 연결)
 * 2. 참조 이미지 정보 검증
 * 3. 프롬프트 평가 (LLM 기반 가드레일)
 * 4. 프롬프트 생성 (LLM 기반 최적화)
 * 5. ComfyUI 워크플로우 실행 (IP-Adapter / ControlNet 등)
 * 6. S3에 결과 이미지 업로드
 * 7. ProductAgentOutput에 결과 저장
 * 8. 완료 이벤트 발행
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
class ImageComposeExecutor(
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
    const val SUB_TYPE = "IMAGE_COMPOSE"
    private const val SYSTEM_ACCOUNT_ID = 0L
    private const val DUMMY_OUTPUT_PATH_PREFIX = "product-images/dummy-compose"

    /**
     * 이미지 합성 테스트 모드 플래그
     *
     * true: 전체 과정을 더미화 (LLM 평가/생성 스킵, 더미 이미지 반환)
     * false: 실제 프로세스 진행 (프롬프트 평가 → 프롬프트 생성 → 이미지 생성)
     *
     * TODO: 워크플로우 구현 완료 시 false로 변경
     */
    private const val TEST_MODE = true
  }

  override fun getSubType(): String = SUB_TYPE

  @Suppress("UNCHECKED_CAST")
  override fun execute(job: DistributedJob, payload: Map<*, *>) {
    // data JSON 문자열 추출 및 파싱
    val dataJson = payload["data"] as String
    val composeData = objectMapper.readValue(dataJson, ProductImageComposeData::class.java)

    // 참조 이미지 S3 키 목록 추출
    val referenceImageKeys = (payload["referenceImageKeys"] as? List<*>)
      ?.filterIsInstance<String>()
      ?: emptyList()

    val productName = composeData.productName
      ?: throw IllegalArgumentException("상품명은 필수입니다")
    val userPrompt = composeData.prompt
      ?: throw IllegalArgumentException("프롬프트는 필수입니다")
    val referenceImageRoles = composeData.referenceImageRoles
      ?: throw IllegalArgumentException("참조 이미지 역할 정보는 필수입니다")
    val imageStyle = composeData.imageStyle?.code()
    val aspectRatio = composeData.aspectRatio.code()
    val imageCount = composeData.imageCount

    // aspectRatio를 width/height로 변환
    val (width, height) = ImageGenerationRequest.getResolutionFromAspectRatio(aspectRatio)

    kLogger.info {
      """
            |========== 이미지 합성 작업 시작 ==========
            |[작업 정보]
            |  - jobId: ${job.id}
            |  - testMode: $TEST_MODE
            |  - productName: $productName
            |  - prompt: ${userPrompt.take(100)}...
            |  - imageStyle: ${imageStyle ?: "기본"}
            |  - aspectRatio: $aspectRatio (${width}x${height})
            |  - imageCount: $imageCount
            |
            |[참조 이미지 정보] (${referenceImageKeys.size}개)
            |${
        referenceImageRoles.mapIndexed { idx, role ->
          "|  [$idx] preset: ${role.preset?.code() ?: "없음"}, description: ${role.description}"
        }.joinToString("\n")
      }
            |
            |[S3 키]
            |${
        referenceImageKeys.mapIndexed { idx, key ->
          "|  [$idx] $key"
        }.joinToString("\n")
      }
            |================================================
            """.trimMargin()
    }

    // 테스트 모드 분기
    if (TEST_MODE) {
      executeTestMode(job, composeData, referenceImageKeys)
    } else {
      executeRealMode(job, composeData, referenceImageKeys, width, height)
    }
  }

  /**
   * 이미지 합성 - 테스트 모드 (전체 더미화)
   *
   * LLM 호출, ComfyUI 호출 없이 빠르게 더미 결과를 반환합니다.
   */
  private fun executeTestMode(
    job: DistributedJob,
    composeData: ProductImageComposeData,
    referenceImageKeys: List<String>
  ) {
    val productName = composeData.productName!!
    val userPrompt = composeData.prompt!!
    val imageStyle = composeData.imageStyle?.code()
    val aspectRatio = composeData.aspectRatio.code()
    val imageCount = composeData.imageCount

    kLogger.warn { "🧪 테스트 모드 - 전체 과정 더미화 (jobId: ${job.id})" }

    val emitter = sseHelper.createEmitter(job)
    val startTime = System.currentTimeMillis()
    val generatedImageKeys = mutableListOf<String>()

    try {
      // 1. PROCESSING 상태 전송
      sseHelper.emitStatus(emitter, "PROCESSING", "이미지 합성을 시작합니다...")
      Thread.sleep(300)

      // 2. EVALUATING 상태 (더미)
      sseHelper.emitStatus(emitter, "EVALUATING", "프롬프트를 평가하고 있습니다...")
      Thread.sleep(300)

      // 3. EVALUATION_PASSED 상태 (더미)
      sseHelper.emitStatus(emitter, "EVALUATION_PASSED", "프롬프트 평가를 통과했습니다.")
      Thread.sleep(200)

      // 4. GENERATING_PROMPT 상태 (더미)
      sseHelper.emitStatus(emitter, "GENERATING_PROMPT", "프롬프트를 이해하고 있습니다...")
      Thread.sleep(300)

      // 5. PROMPT_GENERATED 이벤트 (더미)
      sseHelper.emitPromptGenerated(emitter)
      Thread.sleep(200)

      // 6. 더미 이미지 URL 생성
      generatedImageKeys += createDummyImageKeys(job, emitter, imageCount)

      // 7. ProductAgentOutput에 더미 결과 저장
      val savedResult = imageOutputHelper.saveImageOutput(
        job = job,
        productName = productName,
        prompt = "[TEST MODE] $userPrompt",
        imageStyle = imageStyle,
        aspectRatio = aspectRatio,
        imageCount = imageCount,
        generatedImageKeys = generatedImageKeys,
        outputType = ProductAgentOutputType.PRODUCT_IMAGE_COMPOSE
      )
      val dummyImageUrls = savedResult.committedImageKeys.map { key ->
        storageServiceClient.issueAccessUrl(
          key,
          ProductAgentSseHelper.PRESIGNED_URL_EXPIRATION_MINUTES.toLong()
        )
      }

      // 8. 완료 이벤트 발행
      sseHelper.emitImageCompleted(emitter, savedResult.output.id, dummyImageUrls)
      emitter.complete()

      val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
      kLogger.info {
        "🧪 테스트 모드 완료 - jobId: ${job.id}, outputId: ${savedResult.output.id}, " +
            "소요시간: ${String.format("%.2f", elapsedSeconds)}초"
      }

    } catch (e: Exception) {
      cancelStagedKeys(generatedImageKeys)
      kLogger.error(e) { "테스트 모드 실패 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    } finally {
      cancelStagedKeys(referenceImageKeys)
    }
  }

  /**
   * 테스트 모드용 더미 이미지를 storage-service에 staged 업로드한다.
   */
  private fun createDummyImageKeys(
    job: DistributedJob,
    emitter: EventBridgeFluxSink,
    imageCount: Int,
  ): List<String> {
    val dummyImageKeys = mutableListOf<String>()
    val requesterId = job.requesterId ?: SYSTEM_ACCOUNT_ID

    for (imageIndex in 1..imageCount) {
      sseHelper.emitStatus(emitter, "GENERATING", "이미지를 합성하고 있습니다... ($imageIndex/$imageCount)")
      Thread.sleep(500)

      sseHelper.emitStatus(emitter, "UPLOADING", "이미지 합성이 완료되었습니다... ($imageIndex/$imageCount)")
      Thread.sleep(200)

      val dummyUpload = storageServiceClient.uploadExternalImage(
        externalUrl = "https://placehold.co/1024x1024/png?text=Dummy+Compose+$imageIndex",
        pathPrefix = DUMMY_OUTPUT_PATH_PREFIX,
        accountId = requesterId,
        referenceType = ProductAgentOutputType.PRODUCT_IMAGE_COMPOSE.name,
        originalFileName = "dummy-compose-$imageIndex.png",
      )
      dummyImageKeys.add(dummyUpload.sourceUrl)
    }

    return dummyImageKeys
  }

  /**
   * 이미지 합성 - 실제 모드
   */
  private fun executeRealMode(
    job: DistributedJob,
    composeData: ProductImageComposeData,
    referenceImageKeys: List<String>,
    width: Int,
    height: Int
  ) {
    val productName = composeData.productName!!
    val userPrompt = composeData.prompt!!
    val referenceImageRoles = composeData.referenceImageRoles!!
    val imageStyle = composeData.imageStyle?.code()
    val aspectRatio = composeData.aspectRatio.code()
    val imageCount = composeData.imageCount

    val emitter = sseHelper.createEmitter(job)
    val startTime = System.currentTimeMillis()
    val generatedImageKeys = mutableListOf<String>()

    try {
      // 1. PROCESSING 상태 전송
      sseHelper.emitStatus(emitter, "PROCESSING", "이미지 합성을 시작합니다...")

      // 2. 참조 이미지 정보 검증
      if (referenceImageKeys.size != referenceImageRoles.size) {
        throw IllegalArgumentException(
          "참조 이미지 수(${referenceImageKeys.size})와 역할 정보 수(${referenceImageRoles.size})가 일치하지 않습니다."
        )
      }

      // ========== 3. 프롬프트 평가 (LLM 기반 가드레일) ==========
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

      // ========== 4. 프롬프트 생성 (LLM 기반 최적화) ==========
      sseHelper.emitStatus(emitter, "GENERATING_PROMPT", "프롬프트를 이해하고 있습니다...")

      val promptResult = imagePromptGenerator.generate(
        productName = productName,
        userPrompt = userPrompt,
        imageStyle = imageStyle,
        aspectRatio = aspectRatio
      )

      sseHelper.emitPromptGenerated(emitter)
      kLogger.info { "프롬프트 생성 완료 - jobId: ${job.id}" }

      // ========== 5. 이미지 합성 워크플로우 실행 ==========
      logWorkflowParameters(job, promptResult, width, height, imageCount, referenceImageRoles, referenceImageKeys)

      // TODO: 실제 ComfyUI 워크플로우 호출로 교체 필요
      kLogger.warn { "⚠️ 이미지 합성 워크플로우 미구현 - 기존 Text-to-Image로 대체 생성 (jobId: ${job.id})" }

      generatedImageKeys += generateImagesWithTextToImage(
        job, emitter, promptResult, width, height, imageCount
      )

      // ProductAgentOutput에 결과 저장
      val savedResult = imageOutputHelper.saveImageOutput(
        job = job,
        productName = productName,
        prompt = promptResult.positivePrompt,
        imageStyle = imageStyle,
        aspectRatio = aspectRatio,
        imageCount = imageCount,
        generatedImageKeys = generatedImageKeys,
        outputType = ProductAgentOutputType.PRODUCT_IMAGE_COMPOSE
      )

      // 완료 이벤트 발행
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
        "이미지 합성 작업 완료 - jobId: ${job.id}, outputId: ${savedResult.output.id}, " +
            "images: ${presignedUrls.size}개, 소요시간: ${String.format("%.2f", elapsedSeconds)}초"
      }

    } catch (e: TimeoutException) {
      cancelStagedKeys(generatedImageKeys)
      kLogger.error(e) { "이미지 합성 타임아웃 - jobId: ${job.id}" }
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
      kLogger.error(e) { "이미지 합성 작업 실패 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    } finally {
      cancelStagedKeys(referenceImageKeys)
    }
  }

  /**
   * 워크플로우 연동을 위한 파라미터 상세 로깅
   */
  private fun logWorkflowParameters(
    job: DistributedJob,
    promptResult: PromptGenerationResult,
    width: Int,
    height: Int,
    imageCount: Int,
    referenceImageRoles: List<ReferenceImageRoleInfo>,
    referenceImageKeys: List<String>
  ) {
    kLogger.info {
      """
            |========== ComfyUI 워크플로우 연동 파라미터 ==========
            |[생성된 프롬프트]
            |  - positivePrompt: ${promptResult.positivePrompt.take(200)}...
            |  - negativePrompt: ${promptResult.negativePrompt}
            |
            |[이미지 설정]
            |  - width: $width
            |  - height: $height
            |  - seed: ${System.currentTimeMillis()}
            |  - imageCount: $imageCount
            |
            |[참조 이미지 역할별 분류]
            |${
        referenceImageRoles.mapIndexed { idx, role ->
          val preset = role.preset?.code() ?: "custom"
          "|  [$preset] ${referenceImageKeys.getOrNull(idx) ?: "없음"} - ${role.description}"
        }.joinToString("\n")
      }
            |
            |[워크플로우 구현 시 필요한 노드]
            |  - LoadImage: 각 참조 이미지 로드
            |  - IP-Adapter: 스타일/컨셉 참조 적용
            |  - ControlNet: 포즈/구조 제어 (모델 이미지 사용 시)
            |  - Background Removal: 제품 이미지 배경 제거
            |  - Composite: 이미지 합성
            |=====================================================
            """.trimMargin()
    }
  }

  /**
   * Text-to-Image 워크플로우로 이미지 생성 (임시)
   * ImageGenerationGateway 경유 - 자동 추적 적용
   */
  private fun generateImagesWithTextToImage(
    job: DistributedJob,
    emitter: EventBridgeFluxSink,
    promptResult: PromptGenerationResult,
    width: Int,
    height: Int,
    imageCount: Int
  ): List<String> {
    val generatedImageKeys = mutableListOf<String>()

    for (imageIndex in 1..imageCount) {
      kLogger.info { "이미지 생성 중 - jobId: ${job.id}, index: $imageIndex/$imageCount" }
      sseHelper.emitStatus(emitter, "GENERATING", "이미지를 합성하고 있습니다... ($imageIndex/$imageCount)")

      // ImageGenerationGateway 경유 이미지 생성 (자동 추적)
      val result = imageGenerationGateway.generate(
        prompt = promptResult.positivePrompt,
        negativePrompt = promptResult.negativePrompt,
        width = width,
        height = height,
        seed = System.currentTimeMillis() + imageIndex,
        callerComponent = "ImageComposeExecutor",
        metadata = mapOf(
          "jobId" to job.id,
          "imageIndex" to imageIndex,
          "imageCount" to imageCount,
          "workflowType" to "text-to-image-fallback"
        )
      )

      if (!result.success || !result.hasImage) {
        throw ComfyUiException(result.errorMessage ?: "이미지 생성 결과가 없습니다")
      }

      kLogger.info { "이미지 생성 완료 - size: ${result.imageSizeBytes} bytes, duration: ${result.generationTimeMs}ms" }

      // S3 업로드
      sseHelper.emitStatus(emitter, "UPLOADING", "이미지 합성이 완료되었습니다... ($imageIndex/$imageCount)")
      val s3Key = s3Service.uploadImageToTempAndGetKey(
        bytes = result.imageBytes!!,
        pathPrefix = ProductAgentSseHelper.S3_IMAGE_PATH_PREFIX,
        contentType = "image/png"
      )
      generatedImageKeys.add(s3Key)

      kLogger.info { "S3 업로드 완료 - imageIndex: $imageIndex, s3Key: $s3Key" }
    }

    return generatedImageKeys
  }

  private fun cancelStagedKeys(keys: List<String>) {
    val stagedKeys = keys.filter { it.startsWith("_tmp/") }
    if (stagedKeys.isEmpty()) {
      return
    }

    runCatching { storageServiceClient.cancel(stagedKeys) }
      .onFailure { kLogger.warn(it) { "이미지 합성 temp 정리 실패 - keys: ${stagedKeys.joinToString()}" } }
  }
}
