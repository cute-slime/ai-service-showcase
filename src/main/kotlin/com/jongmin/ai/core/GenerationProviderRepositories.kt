package com.jongmin.ai.core

import com.jongmin.ai.core.platform.entity.multimedia.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import org.springframework.stereotype.Repository

/**
 * AI 미디어 생성 프로바이더 관련 Repository
 *
 * @author Claude Code
 * @since 2026.01.10
 */

// ========== Provider ==========

@Repository
interface GenerationProviderRepository : JpaRepository<MultimediaProvider, Long>,
  QuerydslPredicateExecutor<MultimediaProvider> {

  fun findByCodeOrderBySortOrderAscIdAsc(code: String): List<MultimediaProvider>

  fun findByCodeAndStatusOrderBySortOrderAscIdAsc(
    code: String,
    status: GenerationProviderStatus
  ): List<MultimediaProvider>

  fun findByStatusOrderBySortOrderAsc(status: GenerationProviderStatus): List<MultimediaProvider>

  fun findFirstByCodeAndStatusOrderBySortOrderAscIdAsc(
    code: String,
    status: GenerationProviderStatus
  ): MultimediaProvider?

  fun findFirstByCodeOrderBySortOrderAscIdAsc(code: String): MultimediaProvider?
}

// ========== Provider API Config ==========

@Repository
interface GenerationProviderApiConfigRepository : JpaRepository<MultimediaProviderApiConfig, Long>,
  QuerydslPredicateExecutor<MultimediaProviderApiConfig> {

  fun findByProviderId(providerId: Long): MultimediaProviderApiConfig?

  fun deleteByProviderId(providerId: Long)
}

// ========== Provider Model ==========

@Repository
interface GenerationProviderModelRepository : JpaRepository<MultimediaProviderModel, Long>,
  QuerydslPredicateExecutor<MultimediaProviderModel> {

  fun findByProviderId(providerId: Long): List<MultimediaProviderModel>

  /**
   * 여러 프로바이더의 모델 일괄 조회
   *
   * N+1 문제 방지용 - 프로바이더 목록 조회 시 모델 포함 옵션에서 사용
   */
  fun findByProviderIdIn(providerIds: Collection<Long>): List<MultimediaProviderModel>

  fun findByProviderIdAndStatus(providerId: Long, status: GenerationModelStatus): List<MultimediaProviderModel>

  fun findByProviderIdInAndStatus(providerIds: Collection<Long>, status: GenerationModelStatus): List<MultimediaProviderModel>

  fun findByProviderIdAndCode(providerId: Long, code: String): MultimediaProviderModel?

  fun findByProviderIdAndIsDefaultTrue(providerId: Long): MultimediaProviderModel?

  fun existsByProviderIdAndCode(providerId: Long, code: String): Boolean
}

// ========== Model API Spec ==========

@Repository
interface GenerationModelApiSpecRepository : JpaRepository<MultimediaModelApiSpec, Long>,
  QuerydslPredicateExecutor<MultimediaModelApiSpec> {

  fun findByModelId(modelId: Long): List<MultimediaModelApiSpec>

  fun findByModelIdAndMediaType(modelId: Long, mediaType: GenerationMediaType): MultimediaModelApiSpec?

  fun deleteByModelId(modelId: Long)
}

// ========== Model Media Config ==========

@Repository
interface GenerationModelMediaConfigRepository : JpaRepository<MultimediaModelMediaConfig, Long>,
  QuerydslPredicateExecutor<MultimediaModelMediaConfig> {

  fun findByModelId(modelId: Long): List<MultimediaModelMediaConfig>

  fun findByModelIdAndMediaType(modelId: Long, mediaType: GenerationMediaType): MultimediaModelMediaConfig?

  fun deleteByModelId(modelId: Long)
}

// ========== Model Preset ==========

@Repository
interface GenerationModelPresetRepository : JpaRepository<MultimediaModelPreset, Long>,
  QuerydslPredicateExecutor<MultimediaModelPreset> {

  fun findByModelId(modelId: Long): List<MultimediaModelPreset>

  /**
   * 여러 모델의 프리셋 일괄 조회
   *
   * N+1 문제 방지용 - 프로바이더 목록 조회 시 프리셋 포함 옵션에서 사용
   */
  fun findByModelIdIn(modelIds: Collection<Long>): List<MultimediaModelPreset>

  fun findByModelIdAndMediaType(modelId: Long, mediaType: GenerationMediaType): List<MultimediaModelPreset>

  fun findByModelIdAndMediaTypeAndPresetType(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType
  ): List<MultimediaModelPreset>

  fun findByModelIdAndMediaTypeAndPresetTypeAndCode(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType,
    code: String
  ): MultimediaModelPreset?

  fun findByModelIdAndMediaTypeAndIsDefaultTrue(
    modelId: Long,
    mediaType: GenerationMediaType
  ): List<MultimediaModelPreset>

  fun deleteByModelId(modelId: Long)

  // ========== Phase 5: 스타일 프리셋 조회용 쿼리 메서드 ==========

  /**
   * 모델 + 미디어 타입 + 프리셋 타입 + 상태로 프리셋 목록 조회 (정렬 포함)
   *
   * 스타일 프리셋 목록 조회 시 ACTIVE 상태만 필터링하고 sortOrder 순 정렬
   */
  fun findByModelIdAndMediaTypeAndPresetTypeAndStatusOrderBySortOrderAsc(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType,
    status: com.jongmin.jspring.data.entity.StatusType
  ): List<MultimediaModelPreset>

  /**
   * 특정 프리셋 코드로 조회 (상태 필터 포함)
   */
  fun findByModelIdAndMediaTypeAndPresetTypeAndCodeAndStatus(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType,
    code: String,
    status: com.jongmin.jspring.data.entity.StatusType
  ): MultimediaModelPreset?

  /**
   * 모델 + 미디어 타입 + 프리셋 타입 + 기본 프리셋 조회
   *
   * 해당 조합에서 isDefault=true인 프리셋 조회
   */
  fun findByModelIdAndMediaTypeAndPresetTypeAndIsDefaultTrueAndStatus(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType,
    status: com.jongmin.jspring.data.entity.StatusType
  ): MultimediaModelPreset?

  /**
   * 모델 + 미디어 타입 + 프리셋 타입 + 기본 프리셋 조회 (상태 무관)
   *
   * isDefault=true 설정 시 기존 기본 프리셋 해제용
   */
  fun findByModelIdAndMediaTypeAndPresetTypeAndIsDefaultTrue(
    modelId: Long,
    mediaType: GenerationMediaType,
    presetType: GenerationPresetType
  ): List<MultimediaModelPreset>
}

// ========== Multimedia Workflow ==========

@Repository
interface GenerationWorkflowRepository : JpaRepository<MultimediaWorkflow, Long>,
  QuerydslPredicateExecutor<MultimediaWorkflow> {

  fun findByProviderId(providerId: Long): List<MultimediaWorkflow>

  fun findByProviderIdAndMediaTypeAndIsDefaultTrue(
    providerId: Long,
    mediaType: GenerationMediaType,
    pipeline: GenerationWorkflowPipeline
  ): MultimediaWorkflow?

  fun findByProviderIdAndMediaTypeAndPipelineAndStatusOrderByIsDefaultDescVersionDesc(
    providerId: Long,
    mediaType: GenerationMediaType,
    pipeline: GenerationWorkflowPipeline,
    status: GenerationWorkflowStatus
  ): List<MultimediaWorkflow>

  fun findByProviderIdAndMediaTypeAndNameAndPipelineAndStatusOrderByVersionDesc(
    providerId: Long,
    mediaType: GenerationMediaType,
    name: String,
    pipeline: GenerationWorkflowPipeline,
    status: GenerationWorkflowStatus
  ): List<MultimediaWorkflow>

  fun existsByProviderIdAndMediaTypeAndNameAndPipelineAndVersion(
    providerId: Long,
    mediaType: GenerationMediaType,
    name: String,
    pipeline: GenerationWorkflowPipeline,
    version: Int
  ): Boolean

  fun existsByProviderIdAndMediaTypeAndPipelineAndStatus(
    providerId: Long,
    mediaType: GenerationMediaType,
    pipeline: GenerationWorkflowPipeline,
    status: GenerationWorkflowStatus
  ): Boolean
}

// ========== Cost Rule ==========

@Repository
interface GenerationCostRuleRepository : JpaRepository<MultimediaCostRule, Long>,
  QuerydslPredicateExecutor<MultimediaCostRule> {

  /**
   * 모델 + 미디어 타입으로 비용 규칙 조회 (우선순위 순)
   */
  fun findByModelIdAndMediaTypeAndStatusOrderByPriorityAsc(
    modelId: Long,
    mediaType: GenerationMediaType,
    status: com.jongmin.jspring.data.entity.StatusType
  ): List<MultimediaCostRule>

  /**
   * 프로바이더 + 미디어 타입으로 비용 규칙 조회 (우선순위 순)
   * 모델 ID가 NULL인 프로바이더 레벨 기본 규칙
   */
  fun findByProviderIdAndModelIdIsNullAndMediaTypeAndStatusOrderByPriorityAsc(
    providerId: Long,
    mediaType: GenerationMediaType,
    status: com.jongmin.jspring.data.entity.StatusType
  ): List<MultimediaCostRule>

  /**
   * 모델 ID로 비용 규칙 삭제
   */
  fun deleteByModelId(modelId: Long)

  /**
   * 프로바이더 ID로 비용 규칙 삭제
   */
  fun deleteByProviderId(providerId: Long)
}

// ========== Asset Generation Preset ==========

/**
 * AssetMultimediaPreset Repository
 * 에셋 생성 프리셋 조회/저장 (타입별 1개)
 * 플랫폼 설정 > 게임 > 에셋 프리셋 관리
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Repository
interface AssetGenerationPresetRepository : JpaRepository<AssetMultimediaPreset, Long>,
  QuerydslPredicateExecutor<AssetMultimediaPreset> {

  /**
   * 타입별 프리셋 조회
   * @param type BACKGROUND 또는 CHARACTER
   * @return 해당 타입의 프리셋 (없으면 null)
   */
  fun findByType(type: AssetPresetType): AssetMultimediaPreset?
}

// ========== Prompt Enhancer Profile ==========

/**
 * Prompt Enhancer Profile Repository
 *
 * 작품/세계관 단위 프롬프트 인첸터 프로필 저장소.
 * providerCode별로 프로필을 분리한다.
 */
@Repository
interface PromptEnhancerProfileRepository : JpaRepository<PromptEnhancerProfile, Long>,
  QuerydslPredicateExecutor<PromptEnhancerProfile> {

  fun findFirstByProviderCodeAndIsDefaultTrueAndStatusNot(
    providerCode: String,
    status: com.jongmin.jspring.data.entity.StatusType
  ): PromptEnhancerProfile?
}

