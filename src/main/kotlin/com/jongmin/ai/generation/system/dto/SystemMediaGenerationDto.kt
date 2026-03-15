package com.jongmin.ai.generation.system.dto

import com.jongmin.ai.core.GenerationCostUnitType
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.generation.dto.AssetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

/**
 * 시스템 미디어 생성 요청 DTO
 *
 * 다른 마이크로서비스(game-service 등)에서 미디어 생성을 요청할 때 사용.
 * GenerationContext로 변환되어 프로바이더에 전달된다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class SystemMediaGenerationRequest(
  /**
   * DTE Job ID (고유 식별자)
   */
  @field:NotBlank(message = "jobId는 필수입니다")
  val jobId: String,

  /**
   * 에셋 그룹 ID
   */
  @field:NotNull(message = "groupId는 필수입니다")
  val groupId: Long,

  /**
   * 에셋 아이템 ID
   */
  @field:NotNull(message = "itemId는 필수입니다")
  val itemId: Long,

  /**
   * 에셋 타입 (BACKGROUND, CHARACTER)
   */
  @field:NotNull(message = "assetType은 필수입니다")
  val assetType: AssetType,

  /**
   * 미디어 타입 (IMAGE, VIDEO, BGM)
   */
  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  /**
   * 프로바이더 코드 (예: "COMFYUI", "NOVELAI")
   */
  @field:NotBlank(message = "providerCode는 필수입니다")
  val providerCode: String,

  /**
   * 프로바이더 ID (비용 계산용)
   */
  val providerId: Long? = null,

  /**
   * 모델 코드 (예: "FLUX_DEV", "NAI_V3")
   */
  val modelCode: String? = null,

  /**
   * 모델 ID (비용 계산용)
   */
  val modelId: Long? = null,

  /**
   * 프롬프트 설정
   *
   * 지원 형식:
   * - 단순: {"positive": "...", "negative": "..."}
   * - 중첩: {"image": {"COMFYUI": {"positive": "...", "negative": "..."}}}
   */
  val promptConfig: Map<String, Any> = emptyMap(),

  /**
   * 생성 설정
   *
   * 예시:
   * - width, height: 이미지 크기
   * - steps: 샘플링 스텝 수
   * - cfgScale: CFG 스케일
   * - seed: 시드 값 (재현용)
   * - workflowJson: 커스텀 워크플로우 (ComfyUI)
   */
  val generationConfig: Map<String, Any> = emptyMap(),

  /**
   * 추가 메타데이터
   */
  val metadata: Map<String, Any> = emptyMap(),

  /**
   * 요청자 ID
   */
  val requesterId: Long? = null,

  /**
   * 상관관계 ID (추적용)
   */
  val correlationId: String? = null,

  /**
   * 현재 아이템 인덱스 (0-based)
   * 다중 아이템 생성 시 진행 상황 추적용
   */
  val itemIndex: Int = 0,

  /**
   * 전체 아이템 수
   */
  val totalItems: Int = 1,
)

/**
 * 시스템 미디어 생성 응답 DTO
 *
 * GenerationResult를 HTTP 응답으로 변환한 DTO.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class SystemMediaGenerationResponse(
  /**
   * 성공 여부
   */
  val success: Boolean,

  /**
   * 생성된 리소스 URL (성공 시)
   */
  val outputUrl: String? = null,

  /**
   * 생성된 리소스 URL 목록 (여러 개 생성 시)
   */
  val outputUrls: List<String> = emptyList(),

  /**
   * 썸네일 URL (선택)
   */
  val thumbnailUrl: String? = null,

  /**
   * 에러 코드 (실패 시)
   */
  val errorCode: String? = null,

  /**
   * 에러 메시지 (실패 시)
   */
  val errorMessage: String? = null,

  /**
   * 추가 메타데이터
   */
  val metadata: Map<String, Any> = emptyMap(),

  /**
   * 생성 소요 시간 (밀리초)
   */
  val durationMs: Long? = null,

  /**
   * 예상 비용
   */
  val estimatedCost: BigDecimal? = null,

  /**
   * 비용 통화 (USD, KRW 등)
   */
  val costCurrency: String? = null,

  /**
   * 비용 단위 타입
   */
  val costUnitType: GenerationCostUnitType? = null,

  /**
   * 적용된 비용 규칙 ID
   */
  val appliedCostRuleId: Long? = null,

  /**
   * 사용된 워크플로우 JSON (재생성용)
   */
  val workflowJson: String? = null,

  /**
   * 사용된 시드 값 (재생성용)
   */
  val seed: Long? = null,

  /**
   * 결과 생성 시각
   */
  val timestamp: Long = System.currentTimeMillis(),
) {
  companion object {
    /**
     * GenerationResult → SystemMediaGenerationResponse 변환
     */
    fun from(result: com.jongmin.ai.generation.dto.GenerationResult): SystemMediaGenerationResponse {
      return SystemMediaGenerationResponse(
        success = result.success,
        outputUrl = result.outputUrl,
        outputUrls = result.outputUrls,
        thumbnailUrl = result.thumbnailUrl,
        errorCode = result.errorCode,
        errorMessage = result.errorMessage,
        metadata = result.metadata,
        durationMs = result.durationMs,
        estimatedCost = result.estimatedCost,
        costCurrency = result.costCurrency,
        costUnitType = result.costUnitType,
        appliedCostRuleId = result.appliedCostRuleId,
        workflowJson = result.workflowJson,
        seed = result.seed,
        timestamp = result.timestamp,
      )
    }
  }
}

/**
 * 프로바이더 상태 응답 DTO
 */
data class ProviderStatusResponse(
  /**
   * 프로바이더 코드
   */
  val providerCode: String,

  /**
   * 프로바이더 설명
   */
  val description: String,

  /**
   * 지원 미디어 타입 목록
   */
  val supportedMediaTypes: List<GenerationMediaType>,

  /**
   * 프로바이더 사용 가능 여부
   */
  val available: Boolean,
)

/**
 * 전체 프로바이더 목록 응답 DTO
 */
data class ProvidersListResponse(
  /**
   * 프로바이더 목록
   */
  val providers: List<ProviderStatusResponse>,

  /**
   * 전체 프로바이더 수
   */
  val totalCount: Int = providers.size,
)
