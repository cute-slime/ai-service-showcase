package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * 미디어 생성 비용 규칙 엔티티
 *
 * 프로바이더/모델/조건별 세분화된 비용 규칙을 관리합니다.
 * 해상도, 품질, 길이 등 다양한 조건에 따른 비용 차이를 표현할 수 있습니다.
 *
 * ### 비용 계산 우선순위
 * 1. 가장 구체적인 조건 매칭 (priority 낮을수록 우선)
 * 2. 조건이 NULL인 규칙은 기본값(fallback)으로 사용
 *
 * ### 사용 예시
 * ```
 * DALL-E 3:
 * - 1024x1024 standard → $0.040/image
 * - 1024x1024 hd → $0.080/image
 * - 1792x1024 hd → $0.120/image
 *
 * Runway Gen-4:
 * - 5초 이하 → $0.35/clip
 * - 10초 이하 → $0.70/clip
 *
 * ComfyUI (관리자 설정):
 * - NovelAI 노드 사용 워크플로우 → $0.05/image (추정)
 * ```
 *
 * ### 주의사항
 * - ComfyUI 같은 워크플로우 기반 프로바이더는 내부 API 호출 비용을 자동 추적할 수 없음
 * - 관리자가 예상 비용을 명시적으로 설정해야 함
 *
 * @author Claude Code
 * @since 2026.01.12
 */
@Entity
@Table(
  name = "multimedia_cost_rule",
  indexes = [
    Index(name = "idx_multimediaCostRule_modelId", columnList = "modelId"),
    Index(name = "idx_multimediaCostRule_modelMedia", columnList = "modelId, mediaType"),
    Index(name = "idx_multimediaCostRule_priority", columnList = "modelId, mediaType, priority"),
    Index(name = "idx_multimediaCostRule_status", columnList = "status"),
  ]
)
data class MultimediaCostRule(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  // ========== 적용 대상 ==========

  /**
   * 대상 모델 ID (FK: multimedia_provider_model.id)
   * NULL이면 프로바이더의 모든 모델에 적용
   */
  @Column(comment = "FK: multimedia_provider_model.id (NULL=모든 모델)")
  val modelId: Long? = null,

  /**
   * 대상 프로바이더 ID (FK: multimedia_provider.id)
   * modelId가 NULL일 때 프로바이더 레벨 기본값으로 사용
   */
  @Column(comment = "FK: multimedia_provider.id")
  val providerId: Long? = null,

  /**
   * 대상 미디어 타입
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "미디어 타입: IMAGE, VIDEO, BGM 등")
  var mediaType: GenerationMediaType,

  // ========== 적용 조건 (NULL = 모든 값에 적용) ==========

  /**
   * 해상도 조건 (이미지/영상용)
   * 예: "1024x1024", "1792x1024", "512x512"
   */
  @Column(length = 20, comment = "해상도 조건 (예: 1024x1024)")
  var resolutionCode: String? = null,

  /**
   * 품질 조건
   * 예: "standard", "hd", "ultra"
   */
  @Column(length = 30, comment = "품질 조건 (예: standard, hd)")
  var qualityCode: String? = null,

  /**
   * 스타일 조건
   * 예: "vivid", "natural", "anime"
   */
  @Column(length = 50, comment = "스타일 조건")
  var styleCode: String? = null,

  /**
   * 길이 조건 - 시작 (영상/음악용, 초 단위)
   * 예: 0 (0초부터)
   */
  @Column(comment = "길이 조건 시작 (초)")
  var durationSecFrom: Int? = null,

  /**
   * 길이 조건 - 종료 (영상/음악용, 초 단위)
   * 예: 10 (10초까지)
   */
  @Column(comment = "길이 조건 종료 (초)")
  var durationSecTo: Int? = null,

  /**
   * 프리셋 ID 조건 (특정 프리셋에만 적용)
   */
  @Column(comment = "FK: multimedia_model_preset.id (특정 프리셋 조건)")
  var presetId: Long? = null,

  // ========== 비용 정보 ==========

  /**
   * 단위당 비용
   */
  @Column(precision = 12, scale = 6, nullable = false, comment = "단위당 비용")
  var costPerUnit: BigDecimal,

  /**
   * 비용 단위 타입
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "비용 단위: PER_IMAGE, PER_SECOND 등")
  var costUnitType: GenerationCostUnitType,

  /**
   * 통화 코드
   */
  @Column(length = 10, nullable = false, comment = "통화 (USD, KRW 등)")
  var costCurrency: String = "USD",

  // ========== 메타 정보 ==========

  /**
   * 규칙 이름 (관리자용)
   */
  @Column(length = 100, comment = "규칙 이름 (관리용)")
  var name: String? = null,

  /**
   * 규칙 설명
   */
  @Column(columnDefinition = "TEXT", comment = "규칙 설명")
  var description: String? = null,

  /**
   * 우선순위 (낮을수록 높은 우선순위)
   * 여러 규칙이 매칭될 때 가장 낮은 priority 값을 가진 규칙 적용
   */
  @Column(nullable = false, comment = "우선순위 (낮을수록 우선)")
  var priority: Int = 100,

  /**
   * 상태
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "상태: ACTIVE, INACTIVE")
  var status: StatusType = StatusType.ACTIVE,

  ) : BaseTimeEntity()
