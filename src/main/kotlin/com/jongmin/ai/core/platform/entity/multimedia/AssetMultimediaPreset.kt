package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AssetPresetType
import jakarta.persistence.*
import java.time.ZonedDateTime

/**
 * Asset Generation Preset 엔티티
 * 플랫폼 설정에서 관리되는 에셋 생성 기본 프리셋
 *
 * - 배경/캐릭터 에셋 그룹 생성 시 사용할 기본 설정
 * - 타입별로 하나의 프리셋만 존재 가능 (BACKGROUND 1개, CHARACTER 1개)
 * - generationConfig JSON으로 프로바이더별 생성 개수 저장
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "asset_multimedia_preset",
  indexes = [
    Index(name = "idx_asset_multimedia_preset_type", columnList = "type", unique = true),
  ]
)
data class AssetMultimediaPreset(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  /**
   * 프리셋 타입
   * - BACKGROUND: 배경 에셋 프리셋
   * - CHARACTER: 캐릭터 에셋 프리셋
   */
  @Column(length = 20, nullable = false, unique = true, comment = "프리셋 타입 (BACKGROUND, CHARACTER)")
  @Enumerated(EnumType.STRING)
  val type: AssetPresetType,

  /**
   * 프리셋 이름 (선택)
   */
  @Column(length = 200, comment = "프리셋 이름")
  var name: String? = null,

  /**
   * 미디어 생성 설정 (JSON) - 4단계 계층 구조
   *
   * Provider -> Model -> Preset -> Count 구조
   * 예시: {"image":[{"providerId":1,"modelId":1,"presetId":1,"count":3}]}
   */
  @Column(columnDefinition = "TEXT", nullable = false, comment = "미디어 생성 설정 (JSON)")
  var generationConfig: String = "{}",

  // ========== 메타 정보 ==========

  @Column(nullable = false, columnDefinition = "TIMESTAMP", updatable = false, comment = "생성일")
  val createdAt: ZonedDateTime = now(),

  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "수정일")
  var updatedAt: ZonedDateTime = now(),
)

