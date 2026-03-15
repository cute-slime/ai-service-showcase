package com.jongmin.ai.generation.system.service

import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.dto.GenerationResult
import com.jongmin.ai.generation.provider.AssetGenerationProviderRegistry
import com.jongmin.ai.generation.system.dto.ProviderStatusResponse
import com.jongmin.ai.generation.system.dto.ProvidersListResponse
import com.jongmin.ai.generation.system.dto.SystemMediaGenerationRequest
import com.jongmin.ai.generation.system.dto.SystemMediaGenerationResponse
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 시스템 미디어 생성 서비스
 *
 * 다른 마이크로서비스(game-service 등)에서 미디어 생성을 요청할 때 사용되는 내부 서비스.
 * AssetGenerationProviderRegistry를 통해 적절한 프로바이더를 선택하고 생성을 수행한다.
 *
 * ### 사용 예시 (game-service에서):
 * ```kotlin
 * // AiServiceClient를 통한 HTTP 호출
 * val response = aiServiceClient.generateMedia(request)
 * ```
 *
 * ### 처리 흐름:
 * ```
 * game-service → HTTP API → SystemMediaGenerationController
 *                               ↓
 *                         SystemMediaGenerationService
 *                               ↓
 *                         AssetGenerationProviderRegistry
 *                               ↓
 *                         ComfyUIProvider / NovelAIProvider
 *                               ↓
 *                         외부 AI 서버 (ComfyUI, NovelAI 등)
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class SystemMediaGenerationService(
  private val providerRegistry: AssetGenerationProviderRegistry,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 미디어 생성 수행
   *
   * 지정된 프로바이더를 사용하여 미디어(이미지, 영상, BGM)를 생성한다.
   *
   * @param request 생성 요청
   * @return 생성 응답
   * @throws ObjectNotFoundException 프로바이더를 찾을 수 없는 경우
   */
  fun generate(request: SystemMediaGenerationRequest): SystemMediaGenerationResponse {
    kLogger.info {
      "[MediaGeneration] 생성 요청 - jobId: ${request.jobId}, " +
          "providerCode: ${request.providerCode}, mediaType: ${request.mediaType}, " +
          "itemId: ${request.itemId}, item: ${request.itemIndex + 1}/${request.totalItems}"
    }

    // 1. 프로바이더 조회
    val provider = providerRegistry.getProvider(request.providerCode)
      ?: throw ObjectNotFoundException("프로바이더를 찾을 수 없음: ${request.providerCode}")

    // 2. 프로바이더가 요청한 mediaType을 지원하는지 확인
    if (!provider.getSupportedMediaTypes().contains(request.mediaType)) {
      val errorMessage = "프로바이더 ${request.providerCode}가 ${request.mediaType}을 지원하지 않음"
      kLogger.warn { "[MediaGeneration] $errorMessage" }
      return SystemMediaGenerationResponse(
        success = false,
        errorCode = "UNSUPPORTED_MEDIA_TYPE",
        errorMessage = errorMessage
      )
    }

    // 3. 프로바이더 사용 가능 여부 확인
    if (!provider.isAvailable()) {
      val errorMessage = "프로바이더 ${request.providerCode}가 현재 사용 불가"
      kLogger.warn { "[MediaGeneration] $errorMessage" }
      return SystemMediaGenerationResponse(
        success = false,
        errorCode = "PROVIDER_UNAVAILABLE",
        errorMessage = errorMessage
      )
    }

    // 4. Request → GenerationContext 변환
    val context = toGenerationContext(request)

    // 5. 생성 수행
    val startTime = System.currentTimeMillis()
    val result = try {
      provider.generate(context)
    } catch (e: Exception) {
      kLogger.error(e) {
        "[MediaGeneration] 생성 예외 - jobId: ${request.jobId}, provider: ${request.providerCode}"
      }
      GenerationResult.failure(
        errorMessage = e.message ?: "생성 중 예외 발생",
        errorCode = "GENERATION_EXCEPTION"
      )
    }
    val totalDuration = System.currentTimeMillis() - startTime

    kLogger.info {
      "[MediaGeneration] 생성 완료 - jobId: ${request.jobId}, " +
          "success: ${result.success}, duration: ${totalDuration}ms, " +
          "outputUrl: ${result.outputUrl}"
    }

    // 6. GenerationResult → Response 변환
    return SystemMediaGenerationResponse.from(result)
  }

  /**
   * 프로바이더 목록 조회
   *
   * 등록된 모든 프로바이더의 상태와 지원 미디어 타입을 반환한다.
   *
   * @return 프로바이더 목록 응답
   */
  fun getProviders(): ProvidersListResponse {
    val allProviders = providerRegistry.getAllProviders()
    val providerStatuses = allProviders.map { provider ->
      ProviderStatusResponse(
        providerCode = provider.getProviderCode(),
        description = provider.getDescription(),
        supportedMediaTypes = provider.getSupportedMediaTypes(),
        available = provider.isAvailable()
      )
    }
    return ProvidersListResponse(providers = providerStatuses)
  }

  /**
   * 특정 프로바이더 상태 조회
   *
   * @param providerCode 프로바이더 코드
   * @return 프로바이더 상태 응답
   * @throws ObjectNotFoundException 프로바이더를 찾을 수 없는 경우
   */
  fun getProviderStatus(providerCode: String): ProviderStatusResponse {
    val provider = providerRegistry.getProvider(providerCode)
      ?: throw ObjectNotFoundException("프로바이더를 찾을 수 없음: $providerCode")

    return ProviderStatusResponse(
      providerCode = provider.getProviderCode(),
      description = provider.getDescription(),
      supportedMediaTypes = provider.getSupportedMediaTypes(),
      available = provider.isAvailable()
    )
  }

  /**
   * 미디어 타입별 프로바이더 목록 조회
   *
   * @param mediaType 미디어 타입
   * @return 해당 미디어 타입을 지원하는 프로바이더 목록
   */
  fun getProvidersByMediaType(
    mediaType: com.jongmin.ai.core.GenerationMediaType
  ): ProvidersListResponse {
    val providers = providerRegistry.getProvidersByMediaType(mediaType)
    val providerStatuses = providers.map { provider ->
      ProviderStatusResponse(
        providerCode = provider.getProviderCode(),
        description = provider.getDescription(),
        supportedMediaTypes = provider.getSupportedMediaTypes(),
        available = provider.isAvailable()
      )
    }
    return ProvidersListResponse(providers = providerStatuses)
  }

  /**
   * SystemMediaGenerationRequest → GenerationContext 변환
   */
  private fun toGenerationContext(request: SystemMediaGenerationRequest): GenerationContext {
    return GenerationContext(
      jobId = request.jobId,
      groupId = request.groupId,
      itemId = request.itemId,
      assetType = request.assetType,
      mediaType = request.mediaType,
      providerCode = request.providerCode,
      providerId = request.providerId,
      modelCode = request.modelCode,
      modelId = request.modelId,
      promptConfig = request.promptConfig,
      generationConfig = request.generationConfig,
      metadata = request.metadata,
      requesterId = request.requesterId,
      correlationId = request.correlationId,
      itemIndex = request.itemIndex,
      totalItems = request.totalItems
    )
  }
}
