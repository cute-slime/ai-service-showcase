package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.AssetPresetType
import com.jongmin.ai.core.backoffice.dto.response.GenerationConfig
import jakarta.validation.constraints.Size

/**
 * (백오피스) Asset Generation Preset 수정 요청 DTO
 *
 * 플랫폼 설정 > AI > 에셋 프리셋 관리
 * 타입(BACKGROUND, CHARACTER)별로 1개의 프리셋만 존재
 * null인 필드는 수정하지 않음 (Patch 패턴)
 *
 * @author Claude Code
 * @since 2026.01.10
 */
data class PatchAssetGenerationPreset(
  /**
   * 프리셋 타입 (BACKGROUND, CHARACTER)
   * PathVariable로 전달받아 컨트롤러에서 설정
   */
  var type: AssetPresetType? = null,

  /**
   * 프리셋 이름 (선택)
   * 예: "기본 배경 프리셋", "고퀄리티 캐릭터 프리셋"
   */
  @field:Size(max = 200, message = "프리셋 이름은 200자를 초과할 수 없습니다")
  var name: String? = null,

  /**
   * 미디어 생성 설정 (4단계 계층 구조)
   *
   * Provider -> Model -> Preset -> Count 구조
   * 예시: {"image":[{"providerId":1,"modelId":1,"presetId":1,"count":3}]}
   */
  var generationConfig: GenerationConfig? = null,
)
