package com.jongmin.ai.generation.system.controller

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.generation.system.dto.ProviderStatusResponse
import com.jongmin.ai.generation.system.dto.ProvidersListResponse
import com.jongmin.ai.generation.system.dto.SystemMediaGenerationRequest
import com.jongmin.ai.generation.system.dto.SystemMediaGenerationResponse
import com.jongmin.ai.generation.system.service.SystemMediaGenerationService
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.web.aspect.SystemCall
import com.jongmin.jspring.web.controller.JController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 시스템 미디어 생성 컨트롤러
 *
 * 다른 마이크로서비스(game-service 등)에서 미디어 생성을 요청할 때 사용하는 내부 API.
 * @SystemCall 어노테이션으로 시스템 토큰 인증 필수.
 *
 * ### 사용 예시 (game-service에서):
 * ```kotlin
 * // AiServiceClient를 통한 HTTP 호출
 * val request = SystemMediaGenerationRequest(
 *   jobId = "job-123",
 *   groupId = 1L,
 *   itemId = 10L,
 *   assetType = AssetType.BACKGROUND,
 *   mediaType = GenerationMediaType.IMAGE,
 *   providerCode = "COMFYUI",
 *   promptConfig = mapOf("positive" to "...", "negative" to "...")
 * )
 * val response = aiServiceClient.generateMedia(request)
 * ```
 *
 * ### API 엔드포인트:
 * - POST /v1.0/system/media/generate - 미디어 생성
 * - GET /v1.0/system/media/providers - 프로바이더 목록 조회
 * - GET /v1.0/system/media/providers/{providerCode} - 프로바이더 상태 조회
 * - GET /v1.0/system/media/providers/by-media-type/{mediaType} - 미디어 타입별 프로바이더 조회
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Validated
@RestController
@RequestMapping("/v1.0")
@Tag(name = "System - Media Generation", description = "미디어 생성 시스템 API (내부용)")
class SystemMediaGenerationController(
  private val systemMediaGenerationService: SystemMediaGenerationService,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 미디어 생성
   *
   * 지정된 프로바이더를 사용하여 미디어(이미지, 영상, BGM)를 생성한다.
   * 동기 방식으로 생성 완료까지 대기 후 결과를 반환한다.
   *
   * @param request 생성 요청
   * @return 생성 응답
   */
  @Operation(
    summary = "미디어 생성",
    description = """
      지정된 프로바이더를 사용하여 미디어(이미지, 영상, BGM)를 생성합니다.
      동기 방식으로 생성 완료까지 대기 후 결과를 반환합니다.

      ## 요청 예시
      ```json
      {
        "jobId": "job-abc123",
        "groupId": 1,
        "itemId": 10,
        "assetType": "BACKGROUND",
        "mediaType": "IMAGE",
        "providerCode": "COMFYUI",
        "modelCode": "FLUX_DEV",
        "promptConfig": {
          "positive": "masterpiece, best quality, 1girl, solo",
          "negative": "lowres, bad anatomy"
        },
        "generationConfig": {
          "width": 1024,
          "height": 1024,
          "steps": 20,
          "cfgScale": 7.0
        },
        "itemIndex": 0,
        "totalItems": 5
      }
      ```

      ## 응답 예시 (성공)
      ```json
      {
        "success": true,
        "outputUrl": "https://s3.../generated-image.png",
        "outputUrls": ["https://s3.../generated-image.png"],
        "durationMs": 15230,
        "estimatedCost": 0.02,
        "costCurrency": "USD"
      }
      ```

      ## 응답 예시 (실패)
      ```json
      {
        "success": false,
        "errorCode": "TIMEOUT",
        "errorMessage": "생성 시간 초과: 300000ms"
      }
      ```
    """
  )
  @SystemCall
  @PostMapping("/system/media/generate")
  fun generate(
    @Valid @RequestBody request: SystemMediaGenerationRequest
  ): SystemMediaGenerationResponse {
    val requesterId = request.requesterId ?: systemCallAccountId
      ?: throw BadRequestException(
        "requesterId",
        "requesterId가 필요합니다. 시스템 호출 시 requesterId 또는 X-Account-Id를 전달해야 합니다."
      )

    kLogger.debug {
      "System Media Generate 요청 - jobId: ${request.jobId}, " +
          "provider: ${request.providerCode}, mediaType: ${request.mediaType}, requesterId: $requesterId"
    }
    return systemMediaGenerationService.generate(
      request.copy(requesterId = requesterId)
    )
  }

  /**
   * 프로바이더 목록 조회
   *
   * 등록된 모든 프로바이더의 상태와 지원 미디어 타입을 반환한다.
   *
   * @return 프로바이더 목록 응답
   */
  @Operation(
    summary = "프로바이더 목록 조회",
    description = """
      등록된 모든 미디어 생성 프로바이더의 목록을 반환합니다.

      ## 응답 예시
      ```json
      {
        "providers": [
          {
            "providerCode": "COMFYUI",
            "description": "ComfyUI Provider (IMAGE)",
            "supportedMediaTypes": ["IMAGE"],
            "available": true
          },
          {
            "providerCode": "NOVELAI",
            "description": "NovelAI Provider (IMAGE)",
            "supportedMediaTypes": ["IMAGE"],
            "available": true
          }
        ],
        "totalCount": 2
      }
      ```
    """
  )
  @SystemCall
  @GetMapping("/system/media/providers")
  fun getProviders(): ProvidersListResponse {
    kLogger.debug { "System Media Providers 목록 조회" }
    return systemMediaGenerationService.getProviders()
  }

  /**
   * 프로바이더 상태 조회
   *
   * @param providerCode 프로바이더 코드
   * @return 프로바이더 상태 응답
   */
  @Operation(
    summary = "프로바이더 상태 조회",
    description = """
      특정 프로바이더의 상태를 조회합니다.

      ## 응답 예시
      ```json
      {
        "providerCode": "COMFYUI",
        "description": "ComfyUI Provider (IMAGE)",
        "supportedMediaTypes": ["IMAGE"],
        "available": true
      }
      ```
    """
  )
  @SystemCall
  @GetMapping("/system/media/providers/{providerCode}")
  fun getProviderStatus(
    @Parameter(description = "프로바이더 코드", example = "COMFYUI")
    @PathVariable providerCode: String
  ): ProviderStatusResponse {
    kLogger.debug { "System Media Provider 상태 조회 - code: $providerCode" }
    return systemMediaGenerationService.getProviderStatus(providerCode)
  }

  /**
   * 미디어 타입별 프로바이더 목록 조회
   *
   * @param mediaType 미디어 타입
   * @return 해당 미디어 타입을 지원하는 프로바이더 목록
   */
  @Operation(
    summary = "미디어 타입별 프로바이더 목록 조회",
    description = """
      특정 미디어 타입을 지원하는 프로바이더 목록을 반환합니다.

      ## 미디어 타입
      - IMAGE: 이미지
      - VIDEO: 영상
      - BGM: 배경 음악

      ## 응답 예시
      ```json
      {
        "providers": [
          {
            "providerCode": "COMFYUI",
            "description": "ComfyUI Provider (IMAGE)",
            "supportedMediaTypes": ["IMAGE"],
            "available": true
          }
        ],
        "totalCount": 1
      }
      ```
    """
  )
  @SystemCall
  @GetMapping("/system/media/providers/by-media-type/{mediaType}")
  fun getProvidersByMediaType(
    @Parameter(description = "미디어 타입", example = "IMAGE")
    @PathVariable mediaType: GenerationMediaType
  ): ProvidersListResponse {
    kLogger.debug { "System Media Providers by MediaType 조회 - mediaType: $mediaType" }
    return systemMediaGenerationService.getProvidersByMediaType(mediaType)
  }
}
