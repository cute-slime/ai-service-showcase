package com.jongmin.ai.core.system.controller

import com.jongmin.jspring.web.aspect.SystemCall
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.GenerationModelPresetRepository
import com.jongmin.ai.core.GenerationProviderModelRepository
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.core.platform.dto.response.GenerationProviderSimple
import com.jongmin.ai.core.platform.service.GenerationProviderService
import com.jongmin.ai.core.system.dto.GenerationNameBatchRequest
import com.jongmin.ai.core.system.dto.GenerationNameBatchResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 시스템 미디어 생성 프로바이더 컨트롤러
 *
 * 다른 마이크로서비스(game-service 등)에서 Provider/Model/Preset 이름을 조회할 때 사용하는 내부 API.
 * @SystemCall 어노테이션으로 시스템 토큰 인증 필수.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Validated
@RestController
@RequestMapping("/v1.0")
@Tag(name = "System - Generation Provider", description = "미디어 생성 프로바이더 시스템 API (내부용)")
class SystemGenerationProviderController(
  private val generationProviderRepository: GenerationProviderRepository,
  private val generationProviderModelRepository: GenerationProviderModelRepository,
  private val generationModelPresetRepository: GenerationModelPresetRepository,
  private val generationProviderService: GenerationProviderService,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 활성 프로바이더 목록 조회 (시스템 내부용)
   *
   * game-service 등 내부 서비스에서 에셋 생성 옵션을 구성할 때 사용한다.
   */
  @Operation(
    summary = "활성 프로바이더 목록 조회 (시스템)",
    description = "시스템 토큰 인증으로 활성 미디어 생성 프로바이더 목록을 조회합니다."
  )
  @SystemCall
  @GetMapping("/system/generation-providers")
  fun findActiveProviders(): List<GenerationProviderSimple> {
    kLogger.debug { "시스템 호출 - 활성 프로바이더 목록 조회" }
    return generationProviderService.findActiveProviders()
  }

  /**
   * Provider/Model/Preset 이름 배치 조회
   *
   * 여러 ID를 한 번에 조회하여 이름을 반환한다.
   * game-service의 enrichGenerationConfig()에서 사용.
   *
   * @param request 조회할 Provider/Model/Preset ID 목록
   * @return ID별 이름 Map
   */
  @Operation(
    summary = "Provider/Model/Preset 이름 배치 조회",
    description = """
      여러 ID를 한 번에 조회하여 이름을 반환합니다.

      ## 요청 예시
      ```json
      {
        "providerIds": [1, 2, 3],
        "modelIds": [10, 11],
        "presetIds": [100, 101, 102]
      }
      ```

      ## 응답 예시
      ```json
      {
        "providerNames": {1: "ComfyUI", 2: "NovelAI", 3: "Midjourney"},
        "modelNames": {10: "SDXL 1.0", 11: "SD 1.5"},
        "presetNames": {100: "Default", 101: "Anime", 102: "Realistic"}
      }
      ```
    """
  )
  @SystemCall
  @PostMapping("/system/generation-providers/batch-names")
  fun batchGetNames(
    @RequestBody request: GenerationNameBatchRequest
  ): GenerationNameBatchResponse {
    kLogger.debug {
      "Generation 이름 배치 조회 - providers: ${request.providerIds?.size ?: 0}, " +
          "models: ${request.modelIds?.size ?: 0}, presets: ${request.presetIds?.size ?: 0}"
    }

    // Provider 이름 조회
    val providerNames = request.providerIds?.takeIf { it.isNotEmpty() }?.let { ids ->
      generationProviderRepository.findAllById(ids).associate { it.id to it.name }
    } ?: emptyMap()

    // Model 이름 조회
    val modelNames = request.modelIds?.takeIf { it.isNotEmpty() }?.let { ids ->
      generationProviderModelRepository.findAllById(ids).associate { it.id to it.name }
    } ?: emptyMap()

    // Preset 이름 조회
    val presetNames = request.presetIds?.takeIf { it.isNotEmpty() }?.let { ids ->
      generationModelPresetRepository.findAllById(ids).associate { it.id to it.name }
    } ?: emptyMap()

    kLogger.debug {
      "Generation 이름 배치 조회 완료 - providers: ${providerNames.size}, " +
          "models: ${modelNames.size}, presets: ${presetNames.size}"
    }

    return GenerationNameBatchResponse(
      providerNames = providerNames,
      modelNames = modelNames,
      presetNames = presetNames,
    )
  }
}
