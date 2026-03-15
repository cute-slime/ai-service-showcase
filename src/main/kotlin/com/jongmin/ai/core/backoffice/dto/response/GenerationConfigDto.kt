package com.jongmin.ai.core.backoffice.dto.response

/**
 * 미디어 생성 설정 DTO
 *
 * 4단계 계층 구조: Provider -> Model -> Preset -> Count
 * Request/Response 모두에서 사용
 *
 * @author Claude Code
 * @since 2026.01.11
 */
data class GenerationConfig(
  /** 이미지 생성 설정 */
  val image: List<MediaGenerationConfigItem>? = null,

  /** 비디오 생성 설정 */
  val video: List<MediaGenerationConfigItem>? = null,

  /** BGM 생성 설정 */
  val bgm: List<MediaGenerationConfigItem>? = null,

  /** OST 생성 설정 */
  val ost: List<MediaGenerationConfigItem>? = null,

  /** 효과음(SFX) 생성 설정 */
  val sfx: List<MediaGenerationConfigItem>? = null,
) {
  /**
   * 전체 생성 개수 합계 계산
   */
  fun totalCount(): Int {
    return (image?.sumOf { it.count } ?: 0) +
        (video?.sumOf { it.count } ?: 0) +
        (bgm?.sumOf { it.count } ?: 0) +
        (ost?.sumOf { it.count } ?: 0) +
        (sfx?.sumOf { it.count } ?: 0)
  }

  /**
   * 비어있는지 확인
   */
  fun isEmpty(): Boolean = totalCount() == 0
}

/**
 * 미디어 생성 설정 항목 DTO
 *
 * Provider -> Model -> Preset 계층에서 실제 생성 요청 단위
 *
 * @author Claude Code
 * @since 2026.01.11
 */
data class MediaGenerationConfigItem(
  /** Generation Provider ID (FK: multimedia_provider.id) */
  val providerId: Long,

  /** 프로바이더 이름 (표시용, 응답에서만 사용) */
  val providerName: String? = null,

  /** Generation Provider Model ID (FK: multimedia_provider_model.id) */
  val modelId: Long,

  /** 모델 이름 (표시용, 응답에서만 사용) */
  val modelName: String? = null,

  /** Generation Model Preset ID (FK: multimedia_model_preset.id) */
  val presetId: Long,

  /** 프리셋 이름 (표시용, 응답에서만 사용) */
  val presetName: String? = null,

  /** 생성 개수 */
  val count: Int,
)
