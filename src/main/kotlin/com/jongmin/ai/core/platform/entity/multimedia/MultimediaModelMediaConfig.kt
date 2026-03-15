package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationPromptFormat
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer
import java.math.BigDecimal

/**
 * 모델별 미디어 타입 설정 엔티티
 *
 * 각 모델의 파라미터 옵션, 프롬프트 규격, 비용, 해상도 제한 등을 관리합니다.
 * 백오피스/플랫폼 UI에서 사용할 옵션 정보를 제공합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_model_media_config",
  indexes = [
    Index(name = "unq_multimediaModelMediaConfig_modelMedia", columnList = "modelId, mediaType", unique = true),
    Index(name = "idx_multimediaModelMediaConfig_modelId", columnList = "modelId"),
  ]
)
data class MultimediaModelMediaConfig(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider_model.id")
  val modelId: Long,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "미디어 타입: IMAGE, VIDEO, BGM 등")
  var mediaType: GenerationMediaType,

  // ========== 파라미터 옵션 ==========

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "기본 파라미터")
  var defaultParams: String = "{}",

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "선택 가능한 파라미터 스키마")
  var availableParams: String = "{}",

  // ========== 프롬프트 규격 ==========

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "프롬프트 포맷: NATURAL, TAG_BASED, STRUCTURED")
  var promptFormat: GenerationPromptFormat = GenerationPromptFormat.NATURAL,

  @Column(nullable = false, comment = "최대 프롬프트 길이")
  var maxPromptLength: Int = 2000,

  @Column(nullable = false, comment = "네거티브 프롬프트 지원 여부")
  var supportsNegativePrompt: Boolean = true,

  @Column(columnDefinition = "TEXT", comment = "프롬프트 템플릿")
  var promptTemplate: String? = null,

  @Column(columnDefinition = "TEXT", comment = "네거티브 프롬프트 템플릿")
  var negativePromptTemplate: String? = null,

  // ========== 비용 ==========

  @Column(precision = 10, scale = 4, comment = "단위당 비용")
  var costPerUnit: BigDecimal? = null,

  @Enumerated(EnumType.STRING)
  @Column(length = 30, comment = "비용 단위: PER_IMAGE, PER_SECOND, PER_MINUTE 등")
  var costUnitType: GenerationCostUnitType? = null,

  @Column(length = 10, comment = "통화 (USD, KRW 등)")
  var costCurrency: String = "USD",

  // ========== 해상도 제한 (IMAGE, VIDEO용) ==========

  @Column(comment = "최소 너비")
  var minWidth: Int? = null,

  @Column(comment = "최대 너비")
  var maxWidth: Int? = null,

  @Column(comment = "최소 높이")
  var minHeight: Int? = null,

  @Column(comment = "최대 높이")
  var maxHeight: Int? = null,

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", comment = "지원 종횡비 [\"1:1\", \"16:9\"]")
  var supportedAspectRatios: String? = null,

  // ========== 길이 제한 (VIDEO, BGM용) ==========

  @Column(comment = "최소 길이 (초)")
  var minDurationSec: Int? = null,

  @Column(comment = "최대 길이 (초)")
  var maxDurationSec: Int? = null,

  ) : BaseTimeEntity() {

  // ========== 헬퍼 프로퍼티 ==========

  /**
   * 자연어 프롬프트 지원 여부
   *
   * promptFormat이 NATURAL인 경우 자연어 문장 형태의 프롬프트를 지원합니다.
   * TAG_BASED나 STRUCTURED인 경우 false를 반환합니다.
   */
  val supportsNaturalLanguage: Boolean
    get() = promptFormat == GenerationPromptFormat.NATURAL
}
