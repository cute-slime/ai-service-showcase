package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.GenerationProviderStatus
import com.jongmin.ai.core.backoffice.dto.request.*
import com.jongmin.ai.core.backoffice.dto.response.*
import com.jongmin.ai.core.backoffice.service.BoGenerationModelPresetService
import com.jongmin.ai.core.backoffice.service.BoGenerationProviderModelService
import com.jongmin.ai.core.backoffice.service.BoGenerationProviderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * (백오피스) AI 미디어 생성 프로바이더 관리 컨트롤러
 *
 * @author Claude Code
 * @since 2026.01.10
 * @modified 2026.02.19 서비스 분리에 따른 DI 변경
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoGenerationProviderController(
  private val objectMapper: ObjectMapper,
  private val boGenerationProviderService: BoGenerationProviderService,
  private val boGenerationProviderModelService: BoGenerationProviderModelService,
  private val boGenerationModelPresetService: BoGenerationModelPresetService,
) : JController() {

  // ========== Provider ==========

  @Operation(
    summary = "(백오피스) 미디어 생성 프로바이더를 생성한다.",
    description = """
    권한: ai("admin")
    새로운 미디어 생성 프로바이더(ComfyUI, NovelAI, Midjourney 등)를 등록한다.
    API 설정도 함께 등록할 수 있다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/generation-providers")
  fun createProvider(
    @Validated @RequestBody dto: CreateGenerationProvider
  ): CreateGenerationProviderResult {
    return boGenerationProviderService.createProvider(session!!, dto)
  }

  @Operation(
    summary = "(백오피스) 미디어 생성 프로바이더 상세를 조회한다.",
    description = """
    권한: ai("admin")

    프로바이더 상세 정보와 함께 API 설정, 등록된 모델 목록을 조회한다.

    ### includePresets=true
    - 각 모델의 presets 필드에 프리셋 목록 포함
    - API 호출 횟수 최적화: 1 + N회 → 1회
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-providers/{id}")
  fun findProviderById(
    @PathVariable(required = true) id: Long,

    @Parameter(name = "includePresets", description = "프리셋 목록 포함 여부 (기본값: false)")
    @RequestParam(required = false) includePresets: Boolean = false,
  ): BoGenerationProviderDetail {
    return boGenerationProviderService.findProviderById(id, includePresets)
  }

  @Operation(
    summary = "(백오피스) 미디어 생성 프로바이더 목록을 조회한다.",
    description = """
    권한: ai("admin")

    프로바이더 목록을 페이징하여 조회한다.

    ### 기본 응답
    - 프로바이더 기본 정보 + modelCount (모델 수)

    ### includeModels=true
    - models 필드에 프로바이더별 모델 목록 포함
    - API 호출 횟수 최적화: 20~40회 → 1회

    ### includePresets=true (includeModels=true 필요)
    - 각 모델의 presets 필드에 프리셋 목록 포함
    - includeModels=false 인 경우 무시됨
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-providers")
  fun findAllProviders(
    @Parameter(name = "statuses", description = "상태 필터")
    @RequestParam(required = false) statuses: Set<GenerationProviderStatus>? = null,

    @Parameter(name = "q", description = "검색어 (코드, 이름)")
    @RequestParam(required = false) q: String? = null,

    @Parameter(name = "includeModels", description = "모델 목록 포함 여부 (기본값: false)")
    @RequestParam(required = false) includeModels: Boolean = false,

    @Parameter(name = "includePresets", description = "프리셋 목록 포함 여부 (includeModels=true 필요, 기본값: false)")
    @RequestParam(required = false) includePresets: Boolean = false,

    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoGenerationProviderItem> {
    return boGenerationProviderService.findAllProviders(statuses, q, includeModels, includePresets, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) 미디어 생성 프로바이더를 수정한다.",
    description = """
    권한: ai("admin")
    프로바이더 정보를 수정한다. code는 수정 불가.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/generation-providers/{id}")
  fun patchProvider(
    @PathVariable(required = true) id: Long,
    @Validated @RequestBody dto: PatchGenerationProvider
  ): Map<String, Any?> {
    dto.id = id
    return boGenerationProviderService.patchProvider(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  @Operation(
    summary = "(백오피스) 미디어 생성 프로바이더를 삭제한다.",
    description = """
    권한: ai("admin")
    프로바이더와 관련된 모든 데이터(모델, API 규격, 프리셋 등)가 함께 삭제된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/generation-providers/{id}")
  fun deleteProvider(
    @PathVariable(required = true) id: Long
  ): CommonDto.JApiResponse<Boolean> {
    boGenerationProviderService.deleteProvider(session!!, id)
    return CommonDto.JApiResponse(data = true)
  }

  // ========== Provider API Config ==========

  @Operation(
    summary = "(백오피스) 프로바이더 API 설정을 생성/수정한다.",
    description = """
    권한: ai("admin")
    프로바이더의 API 연동 설정(인증, 엔드포인트, 타임아웃 등)을 설정한다.
    이미 설정이 있으면 수정하고, 없으면 새로 생성한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PutMapping("/bo/generation-providers/{providerId}/api-config")
  fun createOrUpdateApiConfig(
    @PathVariable(required = true) providerId: Long,
    @Validated @RequestBody dto: CreateGenerationProviderApiConfig
  ): BoGenerationProviderApiConfigDto {
    return boGenerationProviderService.createOrUpdateApiConfig(session!!, providerId, dto)
  }

  // ========== Model ==========

  @Operation(
    summary = "(백오피스) 프로바이더 모델을 생성한다.",
    description = """
    권한: ai("admin")
    프로바이더에 새로운 모델(Flux, SDXL, NovelAI v3 등)을 등록한다.
    API 규격, 미디어 설정, 프리셋도 함께 등록할 수 있다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/generation-providers/{providerId}/models")
  fun createModel(
    @PathVariable(required = true) providerId: Long,
    @Validated @RequestBody dto: CreateGenerationProviderModel
  ): CreateGenerationProviderModelResult {
    return boGenerationProviderModelService.createModel(session!!, providerId, dto)
  }

  @Operation(
    summary = "(백오피스) 프로바이더 모델 상세를 조회한다.",
    description = """
    권한: ai("admin")
    모델 상세 정보와 함께 API 규격, 미디어 설정, 프리셋 목록을 조회한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-models/{id}")
  fun findModelById(
    @PathVariable(required = true) id: Long
  ): BoGenerationProviderModelDetail {
    return boGenerationProviderModelService.findModelById(id)
  }

  @Operation(
    summary = "(백오피스) 프로바이더의 모델 목록을 조회한다.",
    description = """
    권한: ai("admin")
    특정 프로바이더에 등록된 모델 목록을 조회한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-providers/{providerId}/models")
  fun findModelsByProviderId(
    @PathVariable(required = true) providerId: Long
  ): List<BoGenerationProviderModelItem> {
    return boGenerationProviderModelService.findModelsByProviderId(providerId)
  }

  @Operation(
    summary = "(백오피스) 프로바이더 모델을 수정한다.",
    description = """
    권한: ai("admin")
    모델 정보를 수정한다. providerId, code는 수정 불가.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/generation-models/{id}")
  fun patchModel(
    @PathVariable(required = true) id: Long,
    @Validated @RequestBody dto: PatchGenerationProviderModel
  ): Map<String, Any?> {
    dto.id = id
    return boGenerationProviderModelService.patchModel(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  @Operation(
    summary = "(백오피스) 프로바이더 모델을 삭제한다.",
    description = """
    권한: ai("admin")
    모델과 관련된 모든 데이터(API 규격, 미디어 설정, 프리셋)가 함께 삭제된다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/generation-models/{id}")
  fun deleteModel(
    @PathVariable(required = true) id: Long
  ): CommonDto.JApiResponse<Boolean> {
    boGenerationProviderModelService.deleteModel(session!!, id)
    return CommonDto.JApiResponse(data = true)
  }

  // ========== Preset ==========

  @Operation(
    summary = "(백오피스) 모델 프리셋을 생성한다.",
    description = """
    권한: ai("admin")
    모델에 새로운 프리셋(해상도, 스타일, 품질 등)을 등록한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/generation-models/{modelId}/presets")
  fun createPreset(
    @PathVariable(required = true) modelId: Long,
    @Validated @RequestBody dto: CreateGenerationModelPreset
  ): CreateGenerationModelPresetResult {
    return boGenerationModelPresetService.createPreset(session!!, modelId, dto)
  }

  @Operation(
    summary = "(백오피스) 모델의 프리셋 목록을 조회한다.",
    description = """
    권한: ai("admin")
    특정 모델에 등록된 프리셋 목록을 조회한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/generation-models/{modelId}/presets")
  fun findPresetsByModelId(
    @PathVariable(required = true) modelId: Long
  ): List<BoGenerationModelPresetDto> {
    return boGenerationModelPresetService.findPresetsByModelId(modelId)
  }

  @Operation(
    summary = "(백오피스) 모델 프리셋을 수정한다.",
    description = """
    권한: ai("admin")
    프리셋 정보를 수정한다. modelId, mediaType, presetType, code는 수정 불가.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PatchMapping("/bo/generation-presets/{id}")
  fun patchPreset(
    @PathVariable(required = true) id: Long,
    @Validated @RequestBody dto: PatchGenerationModelPreset
  ): Map<String, Any?> {
    dto.id = id
    return boGenerationModelPresetService.patchPreset(
      session!!,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  @Operation(
    summary = "(백오피스) 모델 프리셋을 삭제한다.",
    description = """
    권한: ai("admin")
    프리셋을 삭제한다.
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/generation-presets/{id}")
  fun deletePreset(
    @PathVariable(required = true) id: Long
  ): CommonDto.JApiResponse<Boolean> {
    boGenerationModelPresetService.deletePreset(session!!, id)
    return CommonDto.JApiResponse(data = true)
  }
}
