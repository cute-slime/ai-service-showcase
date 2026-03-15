package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.AssetPresetType
import com.jongmin.ai.core.backoffice.dto.request.PatchAssetGenerationPreset
import com.jongmin.ai.core.backoffice.dto.response.AssetGenerationPresetListResponse
import com.jongmin.ai.core.backoffice.dto.response.AssetGenerationPresetResponse
import com.jongmin.ai.core.backoffice.dto.response.AssetPresetPatchResult
import com.jongmin.ai.core.backoffice.service.BoAssetPresetService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/**
 * (백오피스) Asset Generation Preset 관리 컨트롤러
 *
 * 플랫폼 설정 > AI > 에셋 프리셋 관리
 * 배경/캐릭터 에셋 그룹 생성 시 사용할 기본 미디어 생성 설정 관리
 *
 * API 경로: /bo/v1.0/platform-settings/ai/asset-presets
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-8. BackOffice - Asset Preset", description = "에셋 생성 프리셋 백오피스 API")
@Validated
@RestController
@RequestMapping("/v1.0")
class BoAssetPresetController(
  private val jsonMapper: JsonMapper,
  private val boAssetPresetService: BoAssetPresetService,
) : JController() {

  // ========== 조회 API ==========

  /**
   * (백오피스) Asset Generation Preset 목록 조회
   *
   * 모든 프리셋(BACKGROUND, CHARACTER) 목록 반환
   * 타입별 1개씩만 존재하므로 최대 2개
   */
  @Operation(
    summary = "에셋 생성 프리셋 목록 조회",
    description = "모든 에셋 생성 프리셋 목록을 조회합니다. 타입별 1개씩 존재하므로 최대 2개가 반환됩니다."
  )
  @GetMapping("/bo/platform-settings/ai/asset-presets")
  fun findAll(): AssetGenerationPresetListResponse {
    return boAssetPresetService.findAll()
  }

  /**
   * (백오피스) Asset Generation Preset 상세 조회
   *
   * 타입(BACKGROUND, CHARACTER)별 프리셋 상세 정보 조회
   * 프리셋이 없으면 기본값으로 자동 생성
   */
  @Operation(
    summary = "에셋 생성 프리셋 상세 조회",
    description = "타입별 에셋 생성 프리셋 상세 정보를 조회합니다. 프리셋이 없으면 기본값으로 자동 생성됩니다."
  )
  @GetMapping("/bo/platform-settings/ai/asset-presets/{type}")
  fun findByType(
    @Parameter(description = "프리셋 타입 (BACKGROUND, CHARACTER)")
    @PathVariable type: AssetPresetType
  ): AssetGenerationPresetResponse {
    return boAssetPresetService.findByType(type)
  }

  // ========== 수정 API ==========

  /**
   * (백오피스) Asset Generation Preset 수정
   *
   * PATCH 패턴으로 부분 수정 지원
   * null인 필드는 수정하지 않음
   */
  @Operation(
    summary = "에셋 생성 프리셋 수정",
    description = """
      에셋 생성 프리셋을 수정합니다.

      ## generationConfig 형식
      ```json
      {
        "image": [{"providerId": 1, "modelId": 1, "presetId": 1, "count": 3}],
        "video": [{"providerId": 2, "modelId": 2, "presetId": 2, "count": 1}],
        "bgm": [{"providerId": 3, "modelId": 3, "presetId": 3, "count": 2}]
      }
      ```

      ## 프로바이더-미디어 타입 호환성
      - IMAGE: MIDJOURNEY, DALLE, STABLE_DIFFUSION, COMFYUI, RUNWAY
      - VIDEO: COMFYUI, RUNWAY, PIKA, KLING
      - BGM/OST/SFX: SUNO, COMFYUI
    """
  )
  @PatchMapping("/bo/platform-settings/ai/asset-presets/{type}")
  fun patch(
    @Parameter(description = "프리셋 타입 (BACKGROUND, CHARACTER)")
    @PathVariable type: AssetPresetType,

    @Valid @RequestBody dto: PatchAssetGenerationPreset
  ): AssetPresetPatchResult {
    // PathVariable을 DTO에 설정
    dto.type = type

    // DTO를 Map으로 변환 (PATCH 패턴)
    val data = jsonMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})

    val changedFields = boAssetPresetService.patch(session!!, data)

    return AssetPresetPatchResult(
      success = true,
      type = type,
      changedFields = changedFields,
      message = "프리셋이 성공적으로 수정되었습니다."
    )
  }
}
