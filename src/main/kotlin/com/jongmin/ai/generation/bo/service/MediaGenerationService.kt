package com.jongmin.ai.generation.bo.service

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationModelStatus
import com.jongmin.ai.core.GenerationProviderModelRepository
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.core.GenerationProviderStatus
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowRepository
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.platform.entity.multimedia.MultimediaProvider
import com.jongmin.ai.generation.bo.component.GenerationConfigParser
import com.jongmin.ai.generation.bo.component.MediaGenerationRateLimiter
import com.jongmin.ai.generation.bo.config.MediaGenerationProperties
import com.jongmin.ai.generation.bo.dto.*
import com.jongmin.ai.generation.dto.AssetType
import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.provider.AssetGenerationProviderRegistry
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.web.entity.JSession
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.*

/**
 * 미디어 생성 서비스
 *
 * BO API에서 미디어 생성 요청을 처리한다.
 * 도메인에 무관하게 generationConfig를 파싱하여 DTE Job으로 등록하고
 * jobId를 반환한다.
 *
 * ### 처리 흐름:
 * ```
 * FE → BoMediaGenerationController → MediaGenerationService
 *   1. RateLimiter 슬롯 확보
 *   2. ConfigParser로 설정 파싱
 *   3. Provider 존재/가용 확인
 *   4. GenerationContext 구성
 *   5. DTE Job 등록 (비동기 실행)
 *   6. jobId 반환 → FE가 backbone SSE로 구독
 * ```
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
@EnableConfigurationProperties(MediaGenerationProperties::class)
class MediaGenerationService(
  private val providerRegistry: AssetGenerationProviderRegistry,
  private val distributedTaskQueue: DistributedTaskQueue,
  private val rateLimiter: MediaGenerationRateLimiter,
  private val configParser: GenerationConfigParser,
  private val properties: MediaGenerationProperties,
  private val objectMapper: ObjectMapper,
  private val providerRepository: GenerationProviderRepository,
  private val providerModelRepository: GenerationProviderModelRepository,
  private val workflowRepository: GenerationWorkflowRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val DTE_TASK_TYPE = "MEDIA_GENERATION"
  }

  /**
   * 미디어 생성 요청
   *
   * generationConfig를 파싱하고 DTE Job을 등록하여 비동기 생성을 시작한다.
   *
   * @param session 현재 세션 (BO 관리자)
   * @param request 생성 요청
   * @return 생성 응답 (jobId 포함)
   */
  fun generate(session: JSession, request: GenerateRequest): GenerateResponse {
    val accountId = session.accountId
    // FE 응답 jobId, DTE Job.id, SSE 구독 키를 동일하게 유지한다.
    val jobId = UUID.randomUUID().toString()

    kLogger.info {
      "[MediaGeneration] 생성 요청 - accountId: $accountId, " +
          "mediaType: ${request.mediaType}, provider: ${request.providerCode}, providerId: ${request.providerId}, jobId: $jobId"
    }

    // 1. 동시성 제한 확인
    if (!rateLimiter.tryAcquire(accountId, jobId)) {
      val activeCount = rateLimiter.getActiveCount(accountId)
      kLogger.warn {
        "[MediaGeneration] 동시성 제한 초과 - accountId: $accountId, " +
            "active: $activeCount, max: ${properties.maxConcurrentJobsPerAccount}"
      }
      throw MediaGenerationConcurrencyExceededException(
        activeCount = activeCount,
        maxConcurrent = properties.maxConcurrentJobsPerAccount,
      )
    }

    try {
      // 2. generationConfig 파싱
      val parsedConfig = configParser.parse(
        generationConfig = request.generationConfig,
        mediaType = request.mediaType,
        providerCode = request.providerCode,
      )

      // 3. 프로바이더 존재/가용 확인
      val mediaType = parseMediaType(request.mediaType)
      val provider = providerRegistry.getProviderOrNull(request.providerCode)
        ?: throw ObjectNotFoundException("프로바이더를 찾을 수 없음: ${request.providerCode}")

      if (!provider.isAvailable()) {
        rateLimiter.release(accountId, jobId)
        throw BadRequestException("프로바이더 ${request.providerCode}가 현재 사용 불가")
      }

      if (!provider.getSupportedMediaTypes().contains(mediaType)) {
        rateLimiter.release(accountId, jobId)
        throw BadRequestException(
          "프로바이더 ${request.providerCode}가 ${request.mediaType}을 지원하지 않음"
        )
      }

      // 4. GenerationContext 구성
      val modelCode = request.modelCode ?: parsedConfig.modelCode
      val parsedGenerationConfig = parsedConfig.toGenerationConfigMap()
      val providerId = resolveProviderId(
        providerCode = request.providerCode,
        modelCode = modelCode,
        explicitProviderId = request.providerId,
        mediaType = mediaType,
        generationConfig = parsedGenerationConfig
      )
      val modelId = if (providerId != null && !modelCode.isNullOrBlank()) {
        providerModelRepository.findByProviderIdAndCode(providerId, modelCode)?.id
      } else {
        null
      }

      val context = GenerationContext(
        jobId = jobId,
        groupId = 0L,
        itemId = 0L,
        assetType = AssetType.BACKGROUND,
        mediaType = mediaType,
        providerCode = request.providerCode,
        providerId = providerId,
        modelCode = modelCode,
        modelId = modelId,
        promptConfig = parsedConfig.toPromptConfigMap(),
        generationConfig = parsedGenerationConfig,
        requesterId = accountId,
        correlationId = jobId,
      )

      // 5. DTE Job 등록
      val payload = objectMapper.convertValue(
        context,
        object : tools.jackson.core.type.TypeReference<Map<String, Any>>() {}
      )

      val job = DistributedJob(
        id = jobId,
        type = DTE_TASK_TYPE,
        payload = payload,
        requesterId = accountId,
        correlationId = jobId,
      )

      val enqueuedJob = distributedTaskQueue.enqueue(DTE_TASK_TYPE, job)

      kLogger.info {
        "[MediaGeneration] DTE Job 등록 완료 - jobId: ${enqueuedJob.id}, " +
            "provider: ${request.providerCode}, mediaType: ${request.mediaType}"
      }

      return GenerateResponse.success(
        jobId = enqueuedJob.id,
        providerCode = request.providerCode,
        mediaType = request.mediaType,
      )
    } catch (e: Exception) {
      // 실패 시 슬롯 반환
      rateLimiter.release(accountId, jobId)
      kLogger.error(e) {
        "[MediaGeneration] 생성 요청 실패 - accountId: $accountId, jobId: $jobId"
      }
      throw e
    }
  }

  /**
   * 미디어 재생성 요청
   *
   * generate와 동일하되 seed/workflowJson 오버라이드를 지원한다.
   *
   * @param session 현재 세션
   * @param request 재생성 요청
   * @return 생성 응답 (jobId 포함)
   */
  fun regenerate(session: JSession, request: RegenerateRequest): GenerateResponse {
    // 재생성용 generationConfig에 seed/workflowJson 오버라이드 병합
    val mergedConfig = request.generationConfig.toMutableMap()
    request.seed?.let { mergedConfig["seed"] = it }
    request.workflowJson?.let { mergedConfig["workflowJson"] = it }

    val generateRequest = GenerateRequest(
      mediaType = request.mediaType,
      providerCode = request.providerCode,
      providerId = request.providerId,
      modelCode = request.modelCode,
      generationConfig = mergedConfig,
    )

    return generate(session, generateRequest)
  }

  /**
   * 프로바이더 목록 조회
   *
   * @return 프로바이더 목록 응답
   */
  fun getProviders(): BoProvidersListResponse {
    val allProviders = providerRegistry.getAllProviders()
    val providerStatuses = allProviders.map { provider ->
      BoProviderStatusResponse(
        providerCode = provider.getProviderCode(),
        description = provider.getDescription(),
        supportedMediaTypes = provider.getSupportedMediaTypes(),
        available = provider.isAvailable(),
      )
    }
    return BoProvidersListResponse(providers = providerStatuses)
  }

  /**
   * 특정 프로바이더 상태 조회
   *
   * @param providerCode 프로바이더 코드
   * @return 프로바이더 상태 응답
   */
  fun getProviderStatus(providerCode: String): BoProviderStatusResponse {
    val provider = providerRegistry.getProviderOrNull(providerCode)
      ?: throw ObjectNotFoundException("프로바이더를 찾을 수 없음: $providerCode")

    return BoProviderStatusResponse(
      providerCode = provider.getProviderCode(),
      description = provider.getDescription(),
      supportedMediaTypes = provider.getSupportedMediaTypes(),
      available = provider.isAvailable(),
    )
  }

  /**
   * 미디어 타입별 프로바이더 목록 조회
   *
   * @param mediaType 미디어 타입
   * @return 해당 미디어 타입을 지원하는 프로바이더 목록
   */
  fun getProvidersByMediaType(mediaType: GenerationMediaType): BoProvidersListResponse {
    val providers = providerRegistry.getProvidersByMediaType(mediaType)
    val providerStatuses = providers.map { provider ->
      BoProviderStatusResponse(
        providerCode = provider.getProviderCode(),
        description = provider.getDescription(),
        supportedMediaTypes = provider.getSupportedMediaTypes(),
        available = provider.isAvailable(),
      )
    }
    return BoProvidersListResponse(providers = providerStatuses)
  }

  /**
   * 활성 Job 목록 조회
   *
   * @param session 현재 세션
   * @return 활성 Job 목록 응답
   */
  fun getActiveJobs(session: JSession): ActiveJobsListResponse {
    val accountId = session.accountId
    val activeJobIds = rateLimiter.getActiveJobIds(accountId)

    val jobs = activeJobIds.map { jobId ->
      ActiveJobResponse(
        jobId = jobId,
        mediaType = "UNKNOWN",
        providerCode = "UNKNOWN",
        status = "RUNNING",
        createdAt = System.currentTimeMillis(),
      )
    }

    return ActiveJobsListResponse(
      jobs = jobs,
      maxConcurrentJobs = properties.maxConcurrentJobsPerAccount,
    )
  }

  /**
   * 미디어 타입 문자열 → enum 변환
   */
  private fun parseMediaType(mediaType: String): GenerationMediaType {
    return try {
      GenerationMediaType.valueOf(mediaType.uppercase())
    } catch (e: IllegalArgumentException) {
      throw BadRequestException("지원하지 않는 미디어 타입: $mediaType (지원: ${GenerationMediaType.entries.joinToString()})")
    }
  }

  /**
   * providerCode 기준으로 우선순위 서버를 선택한다.
   *
   * - ACTIVE 상태 서버 중 sortOrder 오름차순 우선
   * - modelCode가 있으면 해당 모델을 가진 서버를 우선 선택
   */
  private fun resolveProviderId(providerCode: String, modelCode: String?): Long? {
    return resolveProviderId(
      providerCode = providerCode,
      modelCode = modelCode,
      explicitProviderId = null,
      mediaType = null,
      generationConfig = emptyMap()
    )
  }

  private fun resolveProviderId(
    providerCode: String,
    modelCode: String?,
    explicitProviderId: Long?,
    mediaType: GenerationMediaType?,
    generationConfig: Map<String, Any>
  ): Long? {
    if (explicitProviderId != null) {
      val explicitProvider = providerRepository.findById(explicitProviderId)
        .orElseThrow { ObjectNotFoundException("프로바이더를 찾을 수 없음: providerId=$explicitProviderId") }

      if (!explicitProvider.code.equals(providerCode, ignoreCase = true)) {
        throw BadRequestException(
          "providerId=${explicitProviderId}는 providerCode=${providerCode}와 일치하지 않습니다 (actual=${explicitProvider.code})"
        )
      }

      if (explicitProvider.status != GenerationProviderStatus.ACTIVE) {
        throw BadRequestException("providerId=${explicitProviderId}가 ACTIVE 상태가 아닙니다")
      }

      if (!modelCode.isNullOrBlank()) {
        val model = providerModelRepository.findByProviderIdAndCode(explicitProviderId, modelCode)
        if (model == null || model.status != GenerationModelStatus.ACTIVE) {
          throw BadRequestException(
            "modelCode=${modelCode}는 providerId=${explicitProviderId}에서 ACTIVE 모델이 아닙니다"
          )
        }
      }

      return explicitProviderId
    }

    val activeProviders = providerRepository.findByCodeAndStatusOrderBySortOrderAscIdAsc(
      providerCode,
      GenerationProviderStatus.ACTIVE
    )

    if (activeProviders.isEmpty()) {
      return providerRepository.findFirstByCodeOrderBySortOrderAscIdAsc(providerCode)?.id
    }

    var candidateProviders = activeProviders

    if (!modelCode.isNullOrBlank()) {
      val providerIds = candidateProviders.map { it.id }
      val activeModels = providerModelRepository.findByProviderIdInAndStatus(providerIds, GenerationModelStatus.ACTIVE)
        .filter { it.code.equals(modelCode, ignoreCase = true) }

      if (activeModels.isNotEmpty()) {
        val modelProviderIds = activeModels.map { it.providerId }.toSet()
        candidateProviders = candidateProviders.filter { it.id in modelProviderIds }
      }
    }

    val workflowMatchedProviderId = resolveProviderIdByWorkflowHint(
      activeProviders = candidateProviders,
      mediaType = mediaType,
      generationConfig = generationConfig
    )
    if (workflowMatchedProviderId != null) {
      return workflowMatchedProviderId
    }

    return candidateProviders.first().id
  }

  private fun resolveProviderIdByWorkflowHint(
    activeProviders: List<MultimediaProvider>,
    mediaType: GenerationMediaType?,
    generationConfig: Map<String, Any>
  ): Long? {
    if (mediaType == null || activeProviders.size <= 1) {
      return null
    }

    val workflowJsonOverride = generationConfig["workflowJson"] as? String
    if (!workflowJsonOverride.isNullOrBlank()) {
      return null
    }

    val workflowId = parseLong(generationConfig["workflowId"])
    if (workflowId != null) {
      val workflow = workflowRepository.findById(workflowId).orElse(null)
      if (
        workflow != null &&
        workflow.status == GenerationWorkflowStatus.ACTIVE &&
        workflow.mediaType == mediaType &&
        activeProviders.any { it.id == workflow.providerId }
      ) {
        return workflow.providerId
      }
    }

    val requestedPipeline = resolveRequestedPipeline(generationConfig)
    val matchedByPipeline = activeProviders.filter { provider ->
      workflowRepository.existsByProviderIdAndMediaTypeAndPipelineAndStatus(
        provider.id,
        mediaType,
        requestedPipeline,
        GenerationWorkflowStatus.ACTIVE
      )
    }

    return matchedByPipeline.firstOrNull()?.id
  }

  private fun resolveRequestedPipeline(generationConfig: Map<String, Any>): GenerationWorkflowPipeline {
    val raw = generationConfig["workflowPipeline"]?.toString()?.trim()?.uppercase()
      ?: return GenerationWorkflowPipeline.PROMPT_TO_MEDIA

    return when (raw) {
      "URL_TO_MEDIA", "IMAGE_TO_IMAGE" -> GenerationWorkflowPipeline.MEDIA_TO_MEDIA
      else -> runCatching { GenerationWorkflowPipeline.valueOf(raw) }
        .getOrDefault(GenerationWorkflowPipeline.PROMPT_TO_MEDIA)
    }
  }

  private fun parseLong(raw: Any?): Long? {
    return when (raw) {
      is Number -> raw.toLong()
      is String -> raw.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
      else -> null
    }
  }
}

/**
 * 미디어 생성 동시성 초과 예외
 *
 * 계정당 최대 동시 실행 수를 초과했을 때 발생한다.
 * 컨트롤러에서 409 Conflict 응답으로 변환된다.
 */
class MediaGenerationConcurrencyExceededException(
  val activeCount: Long,
  val maxConcurrent: Int,
) : RuntimeException(
  "동시 실행 제한 초과: 현재 ${activeCount}개 실행 중 (최대 ${maxConcurrent}개)"
)
