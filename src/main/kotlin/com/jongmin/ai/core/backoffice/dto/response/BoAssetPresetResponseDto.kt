package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.ai.core.AssetPresetType
import java.time.ZonedDateTime

/**
 * (백오피스) Asset Generation Preset 상세 응답 DTO
 *
 * 플랫폼 설정 > AI > 에셋 프리셋 관리
 * 타입(BACKGROUND, CHARACTER)별로 1개의 프리셋만 존재
 *
 * @author Claude Code
 * @since 2026.01.10
 */
data class AssetGenerationPresetResponse(
  // ========== 기본 정보 ==========

  /** 프리셋 ID */
  val id: Long,

  /**
   * 프리셋 타입
   * - BACKGROUND: 배경 에셋 프리셋
   * - CHARACTER: 캐릭터 에셋 프리셋
   */
  val type: AssetPresetType,

  /**
   * 프리셋 이름 (선택)
   */
  val name: String?,

  // ========== 미디어 생성 설정 ==========

  /**
   * 미디어 생성 설정 (4단계 계층 구조)
   *
   * Provider -> Model -> Preset -> Count 구조
   * 예시: {"image":[{"providerId":1,"modelId":1,"presetId":1,"count":3}]}
   */
  val generationConfig: GenerationConfig,

  /**
   * 전체 미디어 생성 개수 합계
   */
  val totalMediaCount: Int,

  // ========== 타임스탬프 ==========

  /** 생성일시 */
  val createdAt: ZonedDateTime,

  /** 수정일시 */
  val updatedAt: ZonedDateTime,
)

/**
 * (백오피스) Asset Generation Preset 목록 항목 DTO
 *
 * 목록 조회용 간소화된 응답
 *
 * @author Claude Code
 * @since 2026.01.10
 */
data class AssetGenerationPresetListItem(
  /** 프리셋 ID */
  val id: Long,

  /** 프리셋 타입 */
  val type: AssetPresetType,

  /** 프리셋 이름 */
  val name: String?,

  /** 전체 미디어 생성 개수 합계 */
  val totalMediaCount: Int,

  /** 수정일시 */
  val updatedAt: ZonedDateTime,
)

/**
 * (백오피스) Asset Generation Preset 목록 응답 DTO
 *
 * 모든 프리셋(BACKGROUND, CHARACTER) 목록 반환
 *
 * @author Claude Code
 * @since 2026.01.10
 */
data class AssetGenerationPresetListResponse(
  /** 프리셋 목록 */
  val presets: List<AssetGenerationPresetListItem>,

  /** 총 개수 (최대 2개: BACKGROUND, CHARACTER) */
  val total: Int,
)

/**
 * (백오피스) Asset Generation Preset 수정 결과 DTO
 *
 * @author Claude Code
 * @since 2026.01.10
 */
data class AssetPresetPatchResult(
  /** 성공 여부 */
  val success: Boolean,

  /** 프리셋 타입 */
  val type: AssetPresetType,

  /** 변경된 필드들 */
  val changedFields: Map<String, Any?>,

  /** 메시지 */
  val message: String,
)
