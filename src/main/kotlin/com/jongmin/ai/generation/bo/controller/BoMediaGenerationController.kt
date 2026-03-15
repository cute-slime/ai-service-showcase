package com.jongmin.ai.generation.bo.controller

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.generation.bo.dto.*
import com.jongmin.ai.generation.bo.service.ComfyUiPromptEnhancerService
import com.jongmin.ai.generation.bo.service.MediaGenerationConcurrencyExceededException
import com.jongmin.ai.generation.bo.service.MediaGenerationService
import com.jongmin.ai.generation.bo.service.NovelAiPromptEnhancerService
import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 백오피스 미디어 생성 컨트롤러
 *
 * 도메인에 무관한 범용 미디어 생성 BO API.
 * FE에서 generationConfig를 전달하면 DTE Job을 등록하고 jobId를 반환한다.
 * SSE 스트리밍은 backbone-service 소관이므로 여기에 포함하지 않는다.
 *
 * ### API 엔드포인트:
 * - POST /v1.0/bo/media-generation/generate - 생성 요청 → jobId
 * - POST /v1.0/bo/media-generation/regenerate - 재생성 → jobId
 * - GET /v1.0/bo/media-generation/providers - 프로바이더 목록
 * - GET /v1.0/bo/media-generation/providers/{code} - 프로바이더 상태
 * - GET /v1.0/bo/media-generation/providers/by-media-type/{type} - 미디어 타입별 프로바이더
 * - GET /v1.0/bo/media-generation/active-jobs - 활성 Job 목록
 */
@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI Media Generation", description = "범용 미디어 생성 BO API")
@RestController
@RequestMapping("/v1.0")
class BoMediaGenerationController(
  private val mediaGenerationService: MediaGenerationService,
  private val novelAiPromptEnhancerService: NovelAiPromptEnhancerService,
  private val comfyUiPromptEnhancerService: ComfyUiPromptEnhancerService,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 미디어 생성 요청
   *
   * generationConfig를 파싱하여 DTE Job을 등록하고 jobId를 반환한다.
   * FE는 반환된 jobId로 backbone-service SSE 엔드포인트에 구독하여
   * 실시간 진행 상황을 수신한다.
   */
  @Operation(
    summary = "미디어 생성 요청",
    description = """
      generationConfig를 파싱하여 미디어 생성 DTE Job을 등록합니다.
      반환된 jobId로 backbone-service SSE에 구독하여 진행 상황을 수신합니다.

      ## 요청 예시
      ```json
      {
        "mediaType": "IMAGE",
        "providerCode": "COMFYUI",
        "modelCode": "FLUX_DEV",
        "generationConfig": {
          "positivePrompt": "masterpiece, best quality, fantasy landscape",
          "negativePrompt": "lowres, bad anatomy",
          "width": 1024,
          "height": 1024,
          "steps": 20,
          "cfgScale": 7.0
        }
      }
      ```

      ## 응답 예시 (성공)
      ```json
      {
        "success": true,
        "message": "미디어 생성 작업이 등록되었습니다",
        "jobId": "550e8400-e29b-41d4-a716-446655440000",
        "providerCode": "COMFYUI",
        "mediaType": "IMAGE"
      }
      ```
    """
  )
  @PostMapping("/bo/media-generation/generate")
  fun generate(
    @Valid @RequestBody request: GenerateRequest
  ): GenerateResponse {
    kLogger.debug {
      "BO Media Generate 요청 - admin: ${session!!.username}, " +
          "mediaType: ${request.mediaType}, provider: ${request.providerCode}"
    }
    return mediaGenerationService.generate(session!!, request)
  }

  /**
   * 미디어 재생성 요청
   *
   * 이전 생성 결과를 기반으로 seed/workflowJson 오버라이드를 지원한다.
   */
  @Operation(
    summary = "미디어 재생성 요청",
    description = """
      이전 생성 결과를 기반으로 재생성합니다.
      seed/workflowJson 오버라이드를 지원하여 동일한 결과 재현이 가능합니다.
    """
  )
  @PostMapping("/bo/media-generation/regenerate")
  fun regenerate(
    @Valid @RequestBody request: RegenerateRequest
  ): GenerateResponse {
    kLogger.debug {
      "BO Media Regenerate 요청 - admin: ${session!!.username}, " +
          "mediaType: ${request.mediaType}, provider: ${request.providerCode}, seed: ${request.seed}"
    }
    return mediaGenerationService.regenerate(session!!, request)
  }

  /**
   * NovelAI 프롬프트 인첸트
   *
   * 기존 캐릭터/배경 프롬프트를 기반으로 일관성 최적화된 새 프롬프트를 생성한다.
   * 생성 파라미터 권장값(샘플러/스텝/CFG/해상도/시드 정책)도 함께 반환한다.
   */
  @Operation(
    summary = "NovelAI 프롬프트 인첸트",
    description = """
      기존 프롬프트를 기반으로 NovelAI 스타일 일관성을 강화한 프롬프트를 생성합니다.

      ## 핵심 기능
      - 스타일/서브젝트/장면 블록 분리
      - 작가 태그 조합 우선 적용
      - 네거티브 프롬프트 고정 블록 정규화
      - 샘플러/스텝/CFG/해상도/시드 정책 권장값 반환

      ## 요청 예시
      ```json
      {
        "assetKind": "CHARACTER",
        "basePrompt": "1girl, long silver hair, blue eyes, white dress",
        "referencePrompts": [
          "school courtyard, cherry blossom, soft spring light"
        ],
        "baseNegativePrompt": "lowres, bad anatomy, blurry",
        "preferredArtistTags": ["hiten", "loundraw"],
        "styleKeywords": ["flat color", "thin outlines", "pastel palette"],
        "vibeKeywords": ["calm", "nostalgic", "cinematic"],
        "sceneInstruction": "standing under cherry tree, wind blowing",
        "consistencyMode": "STRICT",
        "lockedTemplate": {
          "sampler": "k_euler",
          "steps": 28,
          "cfgScale": 6.0,
          "width": 832,
          "height": 1216
        }
      }
      ```
    """
  )
  @PostMapping("/bo/media-generation/prompts/novelai/enhance")
  fun enhanceNovelAiPrompt(
    @Valid @RequestBody request: NovelAiPromptEnhanceRequest
  ): NovelAiPromptEnhanceResponse {
    kLogger.debug {
      "BO NovelAI Prompt Enhance 요청 - admin: ${session!!.username}, assetKind: ${request.assetKind}"
    }
    return novelAiPromptEnhancerService.enhance(request)
  }

  /**
   * ComfyUI 프롬프트 인첸트
   *
   * 기존 프롬프트를 기반으로 ComfyUI/Stable Diffusion 워크플로우에
   * 적합한 positive/negative prompt와 권장 설정을 생성한다.
   */
  @Operation(
    summary = "ComfyUI 프롬프트 인첸트",
    description = """
      기존 프롬프트를 기반으로 ComfyUI/Stable Diffusion 스타일 일관성을 강화한 프롬프트를 생성합니다.

      ## 핵심 기능
      - 스타일/서브젝트/장면 블록 분리
      - 선호 작가/비주얼 레퍼런스 적용
      - positive/negative prompt 동시 보정
      - width/height/steps/cfg/seed 권장값 반환
    """
  )
  @PostMapping("/bo/media-generation/prompts/comfyui/enhance")
  fun enhanceComfyUiPrompt(
    @Valid @RequestBody request: ComfyUiPromptEnhanceRequest
  ): ComfyUiPromptEnhanceResponse {
    kLogger.debug {
      "BO ComfyUI Prompt Enhance 요청 - admin: ${session!!.username}, assetKind: ${request.assetKind}"
    }
    return comfyUiPromptEnhancerService.enhance(request)
  }

  /**
   * 프로바이더 목록 조회
   */
  @Operation(
    summary = "프로바이더 목록 조회",
    description = "등록된 모든 미디어 생성 프로바이더의 상태와 지원 미디어 타입을 반환합니다."
  )
  @GetMapping("/bo/media-generation/providers")
  fun getProviders(): BoProvidersListResponse {
    kLogger.debug { "BO Media Providers 목록 조회 - admin: ${session!!.username}" }
    return mediaGenerationService.getProviders()
  }

  /**
   * 프로바이더 상태 조회
   */
  @Operation(
    summary = "프로바이더 상태 조회",
    description = "특정 프로바이더의 상태를 조회합니다."
  )
  @GetMapping("/bo/media-generation/providers/{providerCode}")
  fun getProviderStatus(
    @Parameter(description = "프로바이더 코드", example = "COMFYUI")
    @PathVariable providerCode: String
  ): BoProviderStatusResponse {
    kLogger.debug { "BO Media Provider 상태 조회 - code: $providerCode" }
    return mediaGenerationService.getProviderStatus(providerCode)
  }

  /**
   * 미디어 타입별 프로바이더 목록 조회
   */
  @Operation(
    summary = "미디어 타입별 프로바이더 목록 조회",
    description = "특정 미디어 타입을 지원하는 프로바이더 목록을 반환합니다."
  )
  @GetMapping("/bo/media-generation/providers/by-media-type/{mediaType}")
  fun getProvidersByMediaType(
    @Parameter(description = "미디어 타입", example = "IMAGE")
    @PathVariable mediaType: GenerationMediaType
  ): BoProvidersListResponse {
    kLogger.debug { "BO Media Providers by MediaType 조회 - mediaType: $mediaType" }
    return mediaGenerationService.getProvidersByMediaType(mediaType)
  }

  /**
   * 활성 Job 목록 조회
   *
   * 현재 계정의 실행 중인 미디어 생성 Job 목록을 반환한다.
   */
  @Operation(
    summary = "활성 Job 목록 조회",
    description = "현재 실행 중인 미디어 생성 Job 목록을 반환합니다."
  )
  @GetMapping("/bo/media-generation/active-jobs")
  fun getActiveJobs(): ActiveJobsListResponse {
    kLogger.debug { "BO Media Active Jobs 조회 - admin: ${session!!.username}" }
    return mediaGenerationService.getActiveJobs(session!!)
  }

  /**
   * 동시성 초과 예외 처리 → 409 Conflict
   */
  @ExceptionHandler(MediaGenerationConcurrencyExceededException::class)
  fun handleConcurrencyExceeded(
    e: MediaGenerationConcurrencyExceededException
  ): ResponseEntity<GenerateResponse> {
    kLogger.warn { "[MediaGeneration] 동시성 초과 409 응답 - ${e.message}" }
    return ResponseEntity
      .status(HttpStatus.CONFLICT)
      .body(GenerateResponse.failure(e.message ?: "동시 실행 제한 초과"))
  }
}
