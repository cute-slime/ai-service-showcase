package com.jongmin.ai.generation.provider.image

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowRepository
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaWorkflow
import com.jongmin.ai.generation.dto.*
import com.jongmin.ai.generation.provider.AbstractAssetGenerationProvider
import com.jongmin.ai.generation.provider.image.ComfyUIConstants.PROVIDER_CODE
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIRuntimeConfigResolver
import com.jongmin.ai.product_agent.platform.component.ComfyUiClient
import com.jongmin.ai.product_agent.platform.component.ComfyUiException
import com.jongmin.ai.storage.StorageServiceClient
import com.jongmin.jspring.core.exception.BadRequestException
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeoutException

/**
 * ComfyUI 에셋 생성 프로바이더
 *
 * 런타임 설정(baseUrl/timeout 등)은 DB의 multimedia_provider_api_config를 사용하고,
 * 실행 워크플로우는 DB의 multimedia_workflow만 사용한다.
 */
@Component
class ComfyUIProvider(
  private val comfyUiClient: ComfyUiClient,
  private val storageServiceClient: StorageServiceClient,
  private val runtimeConfigResolver: ComfyUIRuntimeConfigResolver,
  private val workflowRepository: GenerationWorkflowRepository,
  private val objectMapper: ObjectMapper,
  private val promptExtractor: ComfyUIPromptExtractor,
  private val progressTracker: ComfyUIProgressTracker,
) : AbstractAssetGenerationProvider() {

  override fun getProviderCode(): String = PROVIDER_CODE

  override fun getSupportedMediaTypes(): List<GenerationMediaType> = listOf(GenerationMediaType.IMAGE)

  override fun getDefaultTotalSteps(): Int = 5

  override fun getDescription(): String = "ComfyUI - DB workflow 기반 이미지 생성"

  override fun doGenerate(context: GenerationContext): GenerationResult {
    val runtimeConfig = runtimeConfigResolver.resolve(context.providerId)
    val seed = (context.generationConfig["seed"] as? Number)?.toLong() ?: System.currentTimeMillis()
    val width = (context.generationConfig["width"] as? Number)?.toInt() ?: runtimeConfig.defaultWidth
    val height = (context.generationConfig["height"] as? Number)?.toInt() ?: runtimeConfig.defaultHeight

    emitProgressWithContext(
      ProgressEvent.connecting(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        progress = 5,
        message = "ComfyUI 서버 연결 중..."
      ),
      context
    )

    val resolved = try {
      emitProgressWithContext(
        ProgressEvent.processingPrompt(
          jobId = context.jobId,
          providerCode = PROVIDER_CODE,
          progress = 10,
          message = "워크플로우 구성 중..."
        ),
        context
      )

      resolveWorkflow(
        context = context,
        providerId = runtimeConfig.providerId,
        seed = seed,
        width = width,
        height = height,
        defaultSteps = runtimeConfig.defaultModelSteps,
        inputTimeoutMs = runtimeConfig.readTimeoutMs
      )
    } catch (e: Exception) {
      kLogger.error(e) { "[ComfyUI] 워크플로우 구성 실패 - jobId: ${context.jobId}" }
      return GenerationResult.failure(
        errorMessage = "워크플로우 구성 실패: ${e.message}",
        errorCode = "WORKFLOW_BUILD_FAILED"
      )
    }

    kLogger.info {
      "[ComfyUI] 이미지 생성 시작 - jobId: ${context.jobId}, seed: $seed, " +
          "workflow: ${resolved.workflowName ?: resolved.source}"
    }

    return executeWorkflowAndUpload(
      context = context,
      workflow = resolved.workflow,
      totalSteps = resolved.totalSteps,
      seed = seed,
      workflowJsonStr = resolved.workflowJson,
      originalFileName = "${context.itemId}_${System.currentTimeMillis()}.png",
      storagePathPrefix = runtimeConfig.storagePathPrefix,
      metadata = buildMap {
        put("provider", PROVIDER_CODE)
        put("width", width)
        put("height", height)
        put("workflowSource", resolved.source)
        put("workflowPipeline", resolved.pipeline.name)
        resolved.workflowId?.let { put("workflowId", it) }
        resolved.workflowName?.let { put("workflowName", it) }
        resolved.inputMedia?.let { inputMedia ->
          put("inputMediaSource", inputMedia.source)
          put("inputMediaFileName", inputMedia.fileName)
          inputMedia.contentType?.let { put("inputMediaContentType", it) }
        }
      }
    )
  }

  override fun generateWithWorkflow(
    context: GenerationContext,
    workflow: Map<String, Any>,
    seed: Long
  ): GenerationResult {
    kLogger.info { "[ComfyUI] 워크플로우 기반 재생성 시작 - jobId: ${context.jobId}, seed: $seed" }

    if (workflow.isEmpty()) {
      return GenerationResult.failure(
        errorMessage = "워크플로우가 비어있습니다",
        errorCode = "EMPTY_WORKFLOW"
      )
    }

    emitProgressWithContext(
      ProgressEvent.connecting(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        progress = 5,
        message = "ComfyUI 서버에 연결 중..."
      ),
      context
    )

    val runtimeConfig = runtimeConfigResolver.resolve(context.providerId)
    val totalSteps = extractStepsFromWorkflow(workflow) ?: runtimeConfig.defaultModelSteps
    val workflowJsonStr = serializeWorkflow(workflow)

    return executeWorkflowAndUpload(
      context = context,
      workflow = workflow,
      totalSteps = totalSteps,
      seed = seed,
      workflowJsonStr = workflowJsonStr,
      originalFileName = "regen_${seed}_${System.currentTimeMillis()}.png",
      storagePathPrefix = runtimeConfig.storagePathPrefix,
      metadata = mapOf(
        "provider" to PROVIDER_CODE,
        "regeneration" to true
      )
    )
  }

  private fun executeWorkflowAndUpload(
    context: GenerationContext,
    workflow: Map<String, Any>,
    totalSteps: Int,
    seed: Long,
    workflowJsonStr: String?,
    originalFileName: String,
    storagePathPrefix: String,
    metadata: Map<String, Any>,
  ): GenerationResult {
    val clientId = UUID.randomUUID().toString()

    val promptId: String
    try {
      emitProgressWithContext(
        ProgressEvent.processingPrompt(
          jobId = context.jobId,
          providerCode = PROVIDER_CODE,
          progress = 10,
          message = "워크플로우 제출 중..."
        ),
        context
      )

      val response = comfyUiClient.submitPrompt(workflow, clientId)
      promptId = response.promptId

      kLogger.info { "[ComfyUI] 워크플로우 제출 완료 - promptId: $promptId" }

      emitProgressWithContext(
        ProgressEvent.processingPrompt(
          jobId = context.jobId,
          providerCode = PROVIDER_CODE,
          progress = 15,
          message = "워크플로우 제출 완료 (ID: ${promptId.take(8)}...)"
        ),
        context
      )
    } catch (e: ComfyUiException) {
      kLogger.error(e) { "[ComfyUI] 워크플로우 제출 실패 - jobId: ${context.jobId}" }
      return GenerationResult.failure(
        errorMessage = "ComfyUI 워크플로우 제출 실패: ${e.message}",
        errorCode = "WORKFLOW_SUBMIT_FAILED"
      )
    }

    emitProgressWithContext(
      ProgressEvent.queued(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        queuePosition = 1,
        estimatedWaitMs = 5000,
        message = "이미지 생성 대기열 진입..."
      ),
      context
    )

    val wsCompleted = progressTracker.tryWebSocketProgress(
      promptId = promptId,
      clientId = clientId,
      context = context,
      totalSteps = totalSteps
    ) { event, ctx -> emitProgressWithContext(event, ctx) }

    val history = if (!wsCompleted) {
      kLogger.info { "[ComfyUI] HTTP 폴링으로 전환 - promptId: $promptId" }

      emitProgressWithContext(
        ProgressEvent.generating(
          jobId = context.jobId,
          providerCode = PROVIDER_CODE,
          progress = 50,
          message = "이미지 생성 중... (폴링 모드)"
        ),
        context
      )

      try {
        comfyUiClient.waitForCompletion(promptId)
      } catch (e: TimeoutException) {
        kLogger.error(e) { "[ComfyUI] 작업 타임아웃 - promptId: $promptId" }
        return GenerationResult.failure(
          errorMessage = "이미지 생성 타임아웃",
          errorCode = "TIMEOUT"
        )
      } catch (e: Exception) {
        kLogger.error(e) { "[ComfyUI] 폴링 실패 - promptId: $promptId" }
        return GenerationResult.failure(
          errorMessage = "이미지 생성 실패: ${e.message}",
          errorCode = "POLLING_FAILED"
        )
      }
    } else {
      comfyUiClient.getHistory(promptId)
        ?: return GenerationResult.failure(
          errorMessage = "히스토리 조회 실패",
          errorCode = "HISTORY_NOT_FOUND"
        )
    }

    val filename = history.imageFilenames.firstOrNull()
    if (filename == null) {
      kLogger.warn { "[ComfyUI] 생성된 이미지 없음 - promptId: $promptId" }
      return GenerationResult.failure(
        errorMessage = "생성된 이미지가 없습니다",
        errorCode = "NO_IMAGE"
      )
    }

    emitProgressWithContext(
      ProgressEvent.postProcessing(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        progress = 90,
        message = "이미지 다운로드 중..."
      ),
      context
    )

    val imageBytes: ByteArray
    try {
      imageBytes = comfyUiClient.downloadImage(filename)
      kLogger.info { "[ComfyUI] 이미지 다운로드 완료 - size: ${imageBytes.size} bytes" }
    } catch (e: ComfyUiException) {
      kLogger.error(e) { "[ComfyUI] 이미지 다운로드 실패 - filename: $filename" }
      return GenerationResult.failure(
        errorMessage = "이미지 다운로드 실패: ${e.message}",
        errorCode = "DOWNLOAD_FAILED"
      )
    }

    emitProgressWithContext(
      ProgressEvent.uploading(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        progress = 95,
        message = "이미지 업로드 중..."
      ),
      context
    )

    val uploadedAccessUrl: String
    val uploadedSourceUrl: String
    val requesterId = resolveRequesterId(context)
    val uploadPathPrefix = resolveUploadPathPrefix(
      storagePathPrefix = storagePathPrefix,
      context = context,
      requesterId = requesterId
    )
    try {
      val uploadResponse = storageServiceClient.uploadImageBytes(
        bytes = imageBytes,
        pathPrefix = uploadPathPrefix,
        accountId = requesterId,
        referenceType = "GENERATED_MEDIA",
        originalFileName = originalFileName
      )
      uploadedAccessUrl = uploadResponse.url
      uploadedSourceUrl = uploadResponse.sourceUrl.ifBlank { uploadedAccessUrl }
      kLogger.info {
        "[ComfyUI] 이미지 업로드 완료 - accessUrl: $uploadedAccessUrl, sourceUrl: $uploadedSourceUrl, isNew: ${uploadResponse.isNew}"
      }
    } catch (e: Exception) {
      kLogger.error(e) { "[ComfyUI] 이미지 업로드 실패 - jobId: ${context.jobId}" }
      return GenerationResult.failure(
        errorMessage = "이미지 업로드 실패: ${e.message}",
        errorCode = "UPLOAD_FAILED"
      )
    }

    emitProgressWithContext(
      ProgressEvent.uploading(
        jobId = context.jobId,
        providerCode = PROVIDER_CODE,
        progress = 99,
        message = "메타데이터 저장 완료"
      ),
      context
    )

    try {
      comfyUiClient.clearHistory(promptId)
    } catch (_: Exception) {
      kLogger.debug { "[ComfyUI] 히스토리 정리 실패 (무시) - promptId: $promptId" }
    }

    return GenerationResult.success(
      outputUrl = uploadedAccessUrl,
      metadata = metadata + mapOf(
        "promptId" to promptId,
        "sourceUrl" to uploadedSourceUrl,
        "accessUrl" to uploadedAccessUrl
      ),
      workflowJson = workflowJsonStr,
      seed = seed,
    )
  }

  private fun resolveRequesterId(context: GenerationContext): Long {
    return context.requesterId?.takeIf { it > 0L }
      ?: throw BadRequestException("requesterId가 필요합니다. ComfyUI 결과 업로드에 사용할 요청자 ID가 없습니다.")
  }

  private fun resolveUploadPathPrefix(
    storagePathPrefix: String,
    context: GenerationContext,
    requesterId: Long
  ): String {
    val normalizedPrefix = storagePathPrefix.trim().trim('/')
    if (normalizedPrefix.isBlank()) {
      throw BadRequestException("storagePathPrefix가 비어 있습니다.")
    }

    return if (context.groupId > 0L) {
      "$normalizedPrefix/${context.groupId}"
    } else {
      kLogger.info {
        "[ComfyUI] groupId가 비어 있어 requesterId 기반 경로를 사용합니다 - jobId: ${context.jobId}, requesterId: $requesterId"
      }
      "$normalizedPrefix/$requesterId"
    }
  }

  private fun resolveWorkflow(
    context: GenerationContext,
    providerId: Long,
    seed: Long,
    width: Int,
    height: Int,
    defaultSteps: Int,
    inputTimeoutMs: Int
  ): ResolvedWorkflow {
    val overrideWorkflowJson = context.generationConfig["workflowJson"] as? String
    if (!overrideWorkflowJson.isNullOrBlank()) {
      val pipeline = resolveRequestedPipeline(context.generationConfig)
      val preparedInput = if (pipeline == GenerationWorkflowPipeline.MEDIA_TO_MEDIA) {
        preparePipelineInput(context.generationConfig, context.jobId, inputTimeoutMs)
      } else {
        null
      }
      val overrideWorkflow = parseWorkflowMap(overrideWorkflowJson, "workflowJson override")
      val resolvedWorkflow = if (pipeline == GenerationWorkflowPipeline.MEDIA_TO_MEDIA && preparedInput != null) {
        applyInputImageToLoadImageNodes(overrideWorkflow, preparedInput.fileName)
      } else {
        overrideWorkflow
      }
      return ResolvedWorkflow(
        workflow = resolvedWorkflow,
        workflowJson = serializeWorkflow(resolvedWorkflow),
        totalSteps = extractStepsFromWorkflow(resolvedWorkflow) ?: defaultSteps,
        workflowId = null,
        workflowName = null,
        pipeline = pipeline,
        source = "OVERRIDE_JSON",
        inputMedia = preparedInput
      )
    }

    val workflowEntity = resolveWorkflowEntity(context, providerId)
    val preparedInput = if (workflowEntity.pipeline == GenerationWorkflowPipeline.MEDIA_TO_MEDIA) {
      preparePipelineInput(context.generationConfig, context.jobId, inputTimeoutMs)
    } else {
      null
    }
    val (positivePrompt, negativePrompt) = resolvePromptsForPipeline(context, workflowEntity.pipeline)
    val renderedWorkflow = renderWorkflowPayload(
      workflowEntity = workflowEntity,
      context = context,
      positivePrompt = positivePrompt,
      negativePrompt = negativePrompt,
      seed = seed,
      width = width,
      height = height,
      pipeline = workflowEntity.pipeline,
      inputImageFileName = preparedInput?.fileName
    )

    return ResolvedWorkflow(
      workflow = renderedWorkflow,
      workflowJson = serializeWorkflow(renderedWorkflow),
      totalSteps = extractStepsFromWorkflow(renderedWorkflow) ?: defaultSteps,
      workflowId = workflowEntity.id,
      workflowName = workflowEntity.name,
      pipeline = workflowEntity.pipeline,
      source = "DB_WORKFLOW",
      inputMedia = preparedInput
    )
  }

  private fun resolveWorkflowEntity(context: GenerationContext, providerId: Long): MultimediaWorkflow {
    val requestedPipeline = parsePipeline(context.generationConfig["workflowPipeline"])

    val workflowId = parseLong(context.generationConfig["workflowId"])
    if (workflowId != null) {
      val byId = workflowRepository.findById(workflowId).orElse(null)
        ?: throw BadRequestException("workflowId=$workflowId 워크플로우를 찾을 수 없습니다")
      validateWorkflow(byId, providerId, requestedPipeline)
      return byId
    }

    val targetPipeline = requestedPipeline ?: GenerationWorkflowPipeline.PROMPT_TO_MEDIA

    val workflowName = context.generationConfig["workflowName"]?.toString()?.trim()
    if (!workflowName.isNullOrBlank()) {
      val byName = workflowRepository.findByProviderIdAndMediaTypeAndNameAndPipelineAndStatusOrderByVersionDesc(
        providerId,
        GenerationMediaType.IMAGE,
        workflowName,
        targetPipeline,
        GenerationWorkflowStatus.ACTIVE
      ).firstOrNull()
        ?: throw BadRequestException(
          "workflowName='$workflowName', pipeline='${targetPipeline.name}' 워크플로우를 찾을 수 없습니다"
        )
      return byName
    }

    return workflowRepository.findByProviderIdAndMediaTypeAndPipelineAndStatusOrderByIsDefaultDescVersionDesc(
      providerId,
      GenerationMediaType.IMAGE,
      targetPipeline,
      GenerationWorkflowStatus.ACTIVE
    ).firstOrNull()
      ?: throw BadRequestException("활성화된 ComfyUI IMAGE ${targetPipeline.name} 워크플로우가 없습니다")
  }

  private fun validateWorkflow(
    workflow: MultimediaWorkflow,
    providerId: Long,
    requestedPipeline: GenerationWorkflowPipeline?
  ) {
    if (workflow.providerId != providerId) {
      throw BadRequestException("workflowId=${workflow.id}는 COMFYUI provider에 속하지 않습니다")
    }
    if (workflow.mediaType != GenerationMediaType.IMAGE) {
      throw BadRequestException("workflowId=${workflow.id}는 IMAGE 타입 워크플로우가 아닙니다")
    }
    if (workflow.status != GenerationWorkflowStatus.ACTIVE) {
      throw BadRequestException("workflowId=${workflow.id}가 ACTIVE 상태가 아닙니다")
    }
    if (requestedPipeline != null && workflow.pipeline != requestedPipeline) {
      throw BadRequestException(
        "workflowId=${workflow.id}의 pipeline(${workflow.pipeline})이 요청 pipeline(${requestedPipeline})과 다릅니다"
      )
    }
  }

  private fun resolvePromptsForPipeline(
    context: GenerationContext,
    pipeline: GenerationWorkflowPipeline
  ): Pair<String, String?> {
    return when (pipeline) {
      GenerationWorkflowPipeline.PROMPT_TO_MEDIA -> {
        val (positivePrompt, negativePrompt) = promptExtractor.extractPrompts(context)
        if (positivePrompt.isBlank()) {
          throw BadRequestException("프롬프트가 비어있습니다")
        }
        Pair(positivePrompt, negativePrompt)
      }

      GenerationWorkflowPipeline.MEDIA_TO_MEDIA -> {
        val positivePrompt = context.promptConfig["positive"]?.toString().orEmpty()
        val negativePrompt = context.promptConfig["negative"]?.toString()
        Pair(positivePrompt, negativePrompt)
      }
    }
  }

  private fun resolveRequestedPipeline(generationConfig: Map<String, Any>): GenerationWorkflowPipeline {
    return parsePipeline(generationConfig["workflowPipeline"])
      ?: GenerationWorkflowPipeline.PROMPT_TO_MEDIA
  }

  private fun parsePipeline(raw: Any?): GenerationWorkflowPipeline? {
    val value = raw?.toString()?.trim()?.uppercase() ?: return null
    return when (value) {
      "URL_TO_MEDIA", "IMAGE_TO_IMAGE" -> GenerationWorkflowPipeline.MEDIA_TO_MEDIA
      else -> runCatching { GenerationWorkflowPipeline.valueOf(value) }.getOrNull()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseWorkflowMap(workflowJson: String, sourceLabel: String): Map<String, Any> {
    return try {
      objectMapper.readValue(workflowJson, Map::class.java) as Map<String, Any>
    } catch (e: Exception) {
      throw BadRequestException("$sourceLabel JSON 파싱 실패: ${e.message}")
    }
  }

  private fun renderWorkflowPayload(
    workflowEntity: MultimediaWorkflow,
    context: GenerationContext,
    positivePrompt: String,
    negativePrompt: String?,
    seed: Long,
    width: Int,
    height: Int,
    pipeline: GenerationWorkflowPipeline,
    inputImageFileName: String? = null
  ): Map<String, Any> {
    val replacements = linkedMapOf<String, Any?>(
      "PROMPT" to positivePrompt,
      "NEGATIVE_PROMPT" to negativePrompt,
      "WIDTH" to width,
      "HEIGHT" to height,
      "SEED" to seed
    )
    if (!inputImageFileName.isNullOrBlank()) {
      replacements["INPUT_IMAGE"] = inputImageFileName
      replacements["INPUT_MEDIA"] = inputImageFileName
      replacements["INPUT_FILE"] = inputImageFileName
    }

    context.generationConfig.forEach { (key, value) ->
      replacements.putIfAbsent(key, value)
      replacements.putIfAbsent(key.uppercase(), value)
    }

    val variables = parseVariables(workflowEntity.variables)
    variables.forEach { variable ->
      val key = variable.key
      val value = replacements[key]
        ?: replacements[key.uppercase()]
        ?: findConfigValue(context.generationConfig, key)
        ?: variable.defaultValue

      if (value == null && variable.required) {
        throw BadRequestException("워크플로우 변수 '$key'가 필요합니다 (workflowId=${workflowEntity.id})")
      }

      replacements[key] = value
      replacements[key.uppercase()] = value
    }

    var rendered = workflowEntity.payload
    replacements.forEach { (key, value) ->
      if (value != null) {
        rendered = rendered.replace(
          Regex("\\{\\{\\s*${Regex.escape(key)}\\s*\\}\\}"),
          toTemplateValue(value)
        )
      }
    }

    val unresolved = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}")
      .findAll(rendered)
      .map { it.groupValues[1] }
      .toSet()

    if (unresolved.isNotEmpty()) {
      throw BadRequestException(
        "워크플로우 placeholder 치환 실패 (workflowId=${workflowEntity.id}): ${unresolved.joinToString(", ")}"
      )
    }

    val parsedWorkflow = parseWorkflowMap(rendered, "workflow payload")
    return if (pipeline == GenerationWorkflowPipeline.MEDIA_TO_MEDIA && !inputImageFileName.isNullOrBlank()) {
      applyInputImageToLoadImageNodes(parsedWorkflow, inputImageFileName)
    } else {
      parsedWorkflow
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseVariables(variablesJson: String): List<WorkflowVariableMeta> {
    return try {
      val rows = objectMapper.readValue(variablesJson, List::class.java) as? List<Map<String, Any?>>
        ?: return emptyList()

      rows.mapNotNull { row ->
        val key = row["key"]?.toString()?.trim().orEmpty()
        if (key.isBlank()) return@mapNotNull null

        WorkflowVariableMeta(
          key = key,
          required = parseBoolean(row["required"], defaultValue = true),
          defaultValue = row["defaultValue"]
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun preparePipelineInput(
    generationConfig: Map<String, Any>,
    jobId: String,
    inputTimeoutMs: Int
  ): PreparedPipelineInput {
    val inputConfig = generationConfig["input"] as? Map<String, Any>
    val fileNameHint = firstNonBlank(
      inputConfig?.get("fileName"),
      generationConfig["inputFileName"],
      generationConfig["fileName"]
    )
    val contentTypeHint = firstNonBlank(
      inputConfig?.get("contentType"),
      generationConfig["inputContentType"],
      generationConfig["contentType"]
    )
    val inputData = firstNonBlank(
      inputConfig?.get("data"),
      inputConfig?.get("imageData"),
      inputConfig?.get("base64"),
      generationConfig["inputImageData"],
      generationConfig["imageData"],
      generationConfig["inputBase64"],
      generationConfig["base64Data"]
    )

    val inputPayload = if (!inputData.isNullOrBlank()) {
      decodeInputPayload(inputData, fileNameHint, contentTypeHint)
    } else {
      val inputUrlCandidates = listOfNotNull(
        firstNonBlank(inputConfig?.get("url")),
        firstNonBlank(inputConfig?.get("sourceUrl")),
        firstNonBlank(generationConfig["inputMediaUrl"]),
        firstNonBlank(generationConfig["inputUrl"]),
        firstNonBlank(generationConfig["inputSourceUrl"]),
        firstNonBlank(generationConfig["sourceUrl"]),
        firstNonBlank(generationConfig["url"])
      ).map { it.trim() }.filter { it.isNotBlank() }

      if (inputUrlCandidates.isEmpty()) {
        throw BadRequestException(
        "MEDIA_TO_MEDIA 입력이 없습니다. input.url 또는 input.data(base64/data-uri)를 제공해주세요."
        )
      }

      val inputUrl = inputUrlCandidates.firstOrNull { isAbsoluteHttpUrl(it) }
        ?: throw BadRequestException("MEDIA_TO_MEDIA inputUrl은 http/https URL이어야 합니다: ${inputUrlCandidates.first()}")

      downloadInputPayload(inputUrl, fileNameHint, contentTypeHint, inputTimeoutMs)
    }

    val resolvedFileName = resolveInputFileName(
      fileNameHint = inputPayload.fileNameHint,
      contentType = inputPayload.contentType,
      jobId = jobId
    )
    val uploadedFileName = comfyUiClient.uploadInputImage(
      bytes = inputPayload.bytes,
      fileName = resolvedFileName,
      contentType = inputPayload.contentType
    )

    kLogger.info {
      "[ComfyUI] MEDIA_TO_MEDIA 입력 준비 완료 - source: ${inputPayload.source}, fileName: $uploadedFileName, size: ${inputPayload.bytes.size} bytes"
    }

    return PreparedPipelineInput(
      fileName = uploadedFileName,
      source = inputPayload.source,
      contentType = inputPayload.contentType
    )
  }

  private fun decodeInputPayload(
    rawInputData: String,
    fileNameHint: String?,
    contentTypeHint: String?
  ): PipelineInputPayload {
    val trimmed = rawInputData.trim()
    if (trimmed.isBlank()) {
      throw BadRequestException("MEDIA_TO_MEDIA 입력 데이터가 비어있습니다.")
    }

    return if (trimmed.startsWith("data:", ignoreCase = true)) {
      val commaIndex = trimmed.indexOf(',')
      if (commaIndex <= 0) {
        throw BadRequestException("MEDIA_TO_MEDIA data-uri 형식이 올바르지 않습니다.")
      }
      val metadata = trimmed.substring(5, commaIndex)
      if (!metadata.contains("base64", ignoreCase = true)) {
        throw BadRequestException("MEDIA_TO_MEDIA data-uri는 base64 인코딩이어야 합니다.")
      }
      val base64Data = trimmed.substring(commaIndex + 1)
      val contentType = metadata.substringBefore(';').ifBlank { contentTypeHint }
      PipelineInputPayload(
        bytes = decodeBase64(base64Data),
        source = "data-uri",
        contentType = contentType,
        fileNameHint = fileNameHint
      )
    } else {
      val normalizedBase64 = trimmed.removePrefix("base64,").replace(Regex("\\s"), "")
      PipelineInputPayload(
        bytes = decodeBase64(normalizedBase64),
        source = "base64",
        contentType = contentTypeHint,
        fileNameHint = fileNameHint
      )
    }
  }

  private fun decodeBase64(base64Value: String): ByteArray {
    return try {
      Base64.getDecoder().decode(base64Value)
    } catch (e: IllegalArgumentException) {
      throw BadRequestException("MEDIA_TO_MEDIA base64 디코딩 실패: ${e.message}")
    }
  }

  private fun downloadInputPayload(
    inputUrl: String,
    fileNameHint: String?,
    contentTypeHint: String?,
    inputTimeoutMs: Int
  ): PipelineInputPayload {
    val normalizedUrl = inputUrl.trim()
    if (!normalizedUrl.startsWith("http://", ignoreCase = true) &&
      !normalizedUrl.startsWith("https://", ignoreCase = true)
    ) {
      throw BadRequestException("MEDIA_TO_MEDIA inputUrl은 http/https URL이어야 합니다: $normalizedUrl")
    }

    kLogger.info { "[ComfyUI] MEDIA_TO_MEDIA 입력 URL 다운로드 시작 - url: $normalizedUrl" }

    val timeout = inputTimeoutMs.toLong().coerceIn(5_000L, 120_000L)
    val httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(timeout))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()
    val request = HttpRequest.newBuilder()
      .uri(URI.create(normalizedUrl))
      .timeout(Duration.ofMillis(timeout))
      .header("User-Agent", "Jongmin-AI-ComfyUI/1.0")
      .GET()
      .build()

    val response = try {
      httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
    } catch (e: Exception) {
      kLogger.warn(e) { "[ComfyUI] MEDIA_TO_MEDIA 입력 URL 다운로드 예외 - url: $normalizedUrl" }
      throw BadRequestException("MEDIA_TO_MEDIA 입력 URL 다운로드 실패 (url=$normalizedUrl): ${e.message}")
    }

    if (response.statusCode() !in 200..299) {
      val bodySnippet = response.body()
        .toString(Charsets.UTF_8)
        .replace(Regex("\\s+"), " ")
        .take(300)
      kLogger.warn {
        "[ComfyUI] MEDIA_TO_MEDIA 입력 URL 다운로드 실패 - status: ${response.statusCode()}, url: $normalizedUrl, body: $bodySnippet"
      }
      throw BadRequestException(
        "MEDIA_TO_MEDIA 입력 URL 다운로드 실패: HTTP ${response.statusCode()} (url=$normalizedUrl)"
      )
    }

    val bytes = response.body()
    if (bytes.isEmpty()) {
      throw BadRequestException("MEDIA_TO_MEDIA 입력 URL에서 빈 응답을 받았습니다.")
    }

    val responseContentType = response.headers().firstValue("content-type")
      .orElse("")
      .substringBefore(';')
      .trim()
      .ifBlank { contentTypeHint }

    return PipelineInputPayload(
      bytes = bytes,
      source = normalizedUrl,
      contentType = responseContentType,
      fileNameHint = fileNameHint ?: extractFileNameFromUrl(normalizedUrl)
    )
  }

  private fun applyInputImageToLoadImageNodes(
    workflow: Map<String, Any>,
    inputImageFileName: String
  ): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    val copiedWorkflow = objectMapper.convertValue(workflow, MutableMap::class.java) as MutableMap<String, Any>
    var updatedNodeCount = 0

    copiedWorkflow.values.forEach { nodeValue ->
      val node = nodeValue as? MutableMap<*, *> ?: return@forEach
      val classType = node["class_type"]?.toString()?.trim()?.uppercase() ?: return@forEach
      if (classType != "LOADIMAGE") {
        return@forEach
      }

      @Suppress("UNCHECKED_CAST")
      val inputs = (node["inputs"] as? MutableMap<String, Any>) ?: mutableMapOf()
      inputs["image"] = inputImageFileName

      @Suppress("UNCHECKED_CAST")
      (node as MutableMap<String, Any>)["inputs"] = inputs
      updatedNodeCount += 1
    }

    if (updatedNodeCount == 0) {
      kLogger.warn { "[ComfyUI] MEDIA_TO_MEDIA LoadImage 노드를 찾지 못했습니다. 입력 파일명 주입이 생략됩니다." }
    } else {
      kLogger.debug { "[ComfyUI] MEDIA_TO_MEDIA 입력 파일명 주입 완료 - nodes: $updatedNodeCount, fileName: $inputImageFileName" }
    }

    return copiedWorkflow
  }

  private fun resolveInputFileName(
    fileNameHint: String?,
    contentType: String?,
    jobId: String
  ): String {
    val sanitizedHint = sanitizeInputFileName(fileNameHint)
    val baseName = if (sanitizedHint.isNotBlank()) sanitizedHint else "input_${jobId.takeLast(8)}"

    if (baseName.contains('.')) {
      return baseName
    }

    val extension = resolveExtensionFromContentType(contentType) ?: "png"
    return "$baseName.$extension"
  }

  private fun sanitizeInputFileName(raw: String?): String {
    if (raw.isNullOrBlank()) {
      return ""
    }

    val candidate = raw
      .substringAfterLast('/')
      .substringAfterLast('\\')
      .trim()
      .replace(Regex("[^A-Za-z0-9._-]"), "_")

    return candidate.take(120).trim('.')
  }

  private fun resolveExtensionFromContentType(contentType: String?): String? {
    return when (contentType?.lowercase()) {
      "image/png" -> "png"
      "image/jpeg", "image/jpg" -> "jpg"
      "image/webp" -> "webp"
      "image/gif" -> "gif"
      "audio/mpeg", "audio/mp3" -> "mp3"
      "audio/wav", "audio/x-wav" -> "wav"
      "video/mp4" -> "mp4"
      else -> null
    }
  }

  private fun extractFileNameFromUrl(url: String): String? {
    return runCatching {
      val path = URI.create(url).path ?: return null
      path.substringAfterLast('/').takeIf { it.isNotBlank() }
    }.getOrNull()
  }

  private fun firstNonBlank(vararg values: Any?): String? {
    values.forEach { value ->
      val text = value?.toString()?.trim()
      if (!text.isNullOrBlank()) {
        return text
      }
    }
    return null
  }

  private fun isAbsoluteHttpUrl(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
      trimmed.startsWith("https://", ignoreCase = true)
  }

  private fun findConfigValue(config: Map<String, Any>, key: String): Any? {
    config[key]?.let { return it }
    config[key.lowercase()]?.let { return it }
    config[key.uppercase()]?.let { return it }
    return config.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
  }

  private fun parseBoolean(raw: Any?, defaultValue: Boolean): Boolean {
    return when (raw) {
      is Boolean -> raw
      is String -> raw.toBooleanStrictOrNull() ?: defaultValue
      else -> defaultValue
    }
  }

  private fun parseLong(raw: Any?): Long? {
    return when (raw) {
      is Number -> raw.toLong()
      is String -> raw.toLongOrNull()
      else -> null
    }
  }

  private fun toTemplateValue(value: Any): String {
    return when (value) {
      is String -> escapeJsonString(value)
      is Number, is Boolean -> value.toString()
      else -> objectMapper.writeValueAsString(value)
    }
  }

  private fun escapeJsonString(value: String): String {
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
  }

  override fun isAvailable(): Boolean {
    return try {
      comfyUiClient.isHealthy()
    } catch (e: Exception) {
      kLogger.warn(e) { "[ComfyUI] 헬스체크 실패" }
      false
    }
  }

  private fun serializeWorkflow(workflow: Map<String, Any>): String? {
    return try {
      objectMapper.writeValueAsString(workflow)
    } catch (e: Exception) {
      kLogger.warn(e) { "[ComfyUI] 워크플로우 JSON 직렬화 실패 (재생성 불가)" }
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun extractStepsFromWorkflow(workflow: Map<String, Any>): Int? {
    return try {
      val ksamplerNode = workflow["3"] as? Map<String, Any>
      val inputs = ksamplerNode?.get("inputs") as? Map<String, Any>
      (inputs?.get("steps") as? Number)?.toInt()
    } catch (e: Exception) {
      kLogger.debug { "[ComfyUI] 워크플로우에서 steps 추출 실패: ${e.message}" }
      null
    }
  }
}

private data class ResolvedWorkflow(
  val workflow: Map<String, Any>,
  val workflowJson: String?,
  val totalSteps: Int,
  val workflowId: Long?,
  val workflowName: String?,
  val pipeline: GenerationWorkflowPipeline,
  val source: String,
  val inputMedia: PreparedPipelineInput? = null
)

private data class PipelineInputPayload(
  val bytes: ByteArray,
  val source: String,
  val contentType: String?,
  val fileNameHint: String?
)

private data class PreparedPipelineInput(
  val fileName: String,
  val source: String,
  val contentType: String?
)

private data class WorkflowVariableMeta(
  val key: String,
  val required: Boolean,
  val defaultValue: Any?
)
