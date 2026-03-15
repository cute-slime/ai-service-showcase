package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.aspect.LoginRequired
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.platform.dto.response.GenerationModelConfig
import com.jongmin.ai.core.platform.dto.response.GenerationModelSimple
import com.jongmin.ai.core.platform.dto.response.GenerationProviderSimple
import com.jongmin.ai.core.platform.service.GenerationProviderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * (플랫폼) AI 미디어 생성 프로바이더 조회 컨트롤러
 *
 * FE에서 프로바이더/모델 선택에 필요한 정보를 제공한다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@LoginRequired
@Tag(name = "200. AI - Generation Provider")
@RestController
@RequestMapping("/v1.0")
class GenerationProviderController(
  private val generationProviderService: GenerationProviderService,
) : JController() {

  @Operation(
    summary = "활성 미디어 생성 프로바이더 목록을 조회한다.",
    description = """
    로그인 필요.
    현재 활성화된 미디어 생성 프로바이더(ComfyUI, NovelAI, Midjourney 등) 목록을 조회한다.
    각 프로바이더의 활성 모델 수도 함께 제공된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/generation-providers")
  fun findActiveProviders(): List<GenerationProviderSimple> {
    return generationProviderService.findActiveProviders()
  }

  @Operation(
    summary = "프로바이더별 활성 모델 목록을 조회한다.",
    description = """
    로그인 필요.
    특정 프로바이더에 등록된 활성 모델(Flux, SDXL, NAI v3 등) 목록을 조회한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/generation-providers/{providerCode}/models")
  fun findActiveModels(
    @PathVariable(required = true) providerCode: String
  ): List<GenerationModelSimple> {
    return generationProviderService.findActiveModelsByProviderCode(providerCode)
  }

  @Operation(
    summary = "모델 설정을 조회한다.",
    description = """
    로그인 필요.
    특정 모델의 상세 설정 정보를 조회한다.
    미디어 설정(파라미터, 프롬프트 규격, 해상도 제한 등)과 프리셋(해상도, 스타일, 품질 등)이 포함된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/generation-providers/{providerCode}/models/{modelCode}/config")
  fun findModelConfig(
    @PathVariable(required = true) providerCode: String,
    @PathVariable(required = true) modelCode: String
  ): GenerationModelConfig {
    return generationProviderService.findModelConfig(providerCode, modelCode)
  }
}
