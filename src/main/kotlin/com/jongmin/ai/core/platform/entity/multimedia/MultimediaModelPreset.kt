package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationPresetType
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer

/**
 * 모델별 프리셋 엔티티
 *
 * 해상도, 스타일, 품질 등의 프리셋을 관리합니다.
 * 사용자가 쉽게 선택할 수 있는 미리 정의된 설정 조합을 제공합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_model_preset",
  indexes = [
    Index(
      name = "unq_multimediaModelPreset_modelPreset",
      columnList = "modelId, mediaType, presetType, code",
      unique = true
    ),
    Index(name = "idx_multimediaModelPreset_modelId", columnList = "modelId"),
    Index(name = "idx_multimediaModelPreset_presetType", columnList = "modelId, mediaType, presetType"),
    Index(name = "idx_multimediaModelPreset_status", columnList = "status"),
  ]
)
data class MultimediaModelPreset(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider_model.id")
  val modelId: Long,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "미디어 타입: IMAGE, VIDEO, BGM 등")
  var mediaType: GenerationMediaType,

  // ========== 프리셋 정보 ==========

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "프리셋 타입: RESOLUTION, STYLE, QUALITY, SAMPLER, DURATION")
  var presetType: GenerationPresetType,

  @Column(length = 50, nullable = false, comment = "프리셋 코드")
  var code: String,

  @Column(length = 100, nullable = false, comment = "프리셋 표시명")
  var name: String,

  @Column(columnDefinition = "TEXT", comment = "프리셋 설명")
  var description: String? = null,

  // ========== 프리셋 값 ==========

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "프리셋 파라미터")
  var params: String = "{}",

  // ========== 상태 ==========

  @Column(nullable = false, comment = "기본 프리셋 여부")
  var isDefault: Boolean = false,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "상태: ACTIVE, INACTIVE")
  var status: StatusType = StatusType.ACTIVE,

  @Column(nullable = false, comment = "정렬 순서")
  var sortOrder: Int = 0,

  ) : BaseTimeEntity()
