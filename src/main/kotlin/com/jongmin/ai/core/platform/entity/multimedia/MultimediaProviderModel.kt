package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.ai.core.GenerationModelStatus
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer
import java.time.LocalDate

/**
 * 프로바이더별 모델 엔티티
 *
 * 각 프로바이더에서 제공하는 AI 모델(Flux.1 Dev, NAI V3, Midjourney v6 등)을 관리합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_provider_model",
  indexes = [
    Index(name = "unq_multimediaProviderModel_providerCode", columnList = "providerId, code", unique = true),
    Index(name = "idx_multimediaProviderModel_providerId", columnList = "providerId"),
    Index(name = "idx_multimediaProviderModel_status", columnList = "status"),
    Index(name = "idx_multimediaProviderModel_isDefault", columnList = "providerId, isDefault"),
  ]
)
data class MultimediaProviderModel(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider.id")
  val providerId: Long,

  @Column(length = 50, nullable = false, comment = "모델 코드 (FLUX_DEV, NAI_V3 등)")
  var code: String,

  @Column(length = 100, nullable = false, comment = "모델 표시명")
  var name: String,

  @Column(columnDefinition = "TEXT", comment = "모델 설명")
  var description: String? = null,

  @Column(length = 20, comment = "모델 버전")
  var version: String? = null,

  // ========== 상태 ==========

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "상태: ACTIVE, BETA, DEPRECATED, INACTIVE")
  var status: GenerationModelStatus = GenerationModelStatus.ACTIVE,

  @Column(nullable = false, comment = "프로바이더 기본 모델 여부")
  var isDefault: Boolean = false,

  // ========== 지원 미디어 타입 ==========

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "지원 미디어 타입 [\"IMAGE\", \"VIDEO\"]")
  var supportedMediaTypes: String = "[\"IMAGE\"]",

  // ========== API 설정 ==========

  /**
   * 워크플로우/API 설정 JSON
   *
   * 프로바이더별 API 호출에 필요한 설정을 저장합니다.
   * - ComfyUI: 전체 워크플로우 JSON (플레이스홀더 포함)
   * - NovelAI: API 파라미터 설정 {"sampler": "k_euler", ...}
   * - Midjourney: 파라미터 설정 {"version": "6", "style": "raw", ...}
   *
   * 플레이스홀더 예시: {{PROMPT}}, {{NEGATIVE_PROMPT}}, {{SEED}}, {{WIDTH}}, {{HEIGHT}}
   */
  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", comment = "워크플로우/API 설정 JSON")
  var configJson: String? = null,

  // ========== 일정 ==========

  @Column(comment = "출시일")
  var releaseDate: LocalDate? = null,

  @Column(comment = "지원 종료 예정일")
  var deprecationDate: LocalDate? = null,

  @Column(nullable = false, comment = "정렬 순서")
  var sortOrder: Int = 0,

  ) : BaseTimeEntity()
