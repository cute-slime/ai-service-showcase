package com.jongmin.ai.multiagent.skill.backoffice.dto

import com.jongmin.ai.multiagent.skill.model.LlmTriggerConfig
import com.jongmin.ai.multiagent.skill.model.NetworkPolicy
import com.jongmin.ai.multiagent.skill.model.ScriptLanguage
import com.jongmin.ai.multiagent.skill.model.SkillCompatibility
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// ========== 생성 요청 ==========

/**
 * 스킬 생성 요청 DTO
 */
data class BoCreateSkillRequest(
  @field:NotBlank(message = "name is required")
  @field:Size(min = 1, max = 64, message = "name must be 1-64 characters")
  val name: String,

  @field:NotBlank(message = "description is required")
  @field:Size(min = 1, max = 1024, message = "description must be 1-1024 characters")
  val description: String,

  val license: String? = null,

  @field:Valid
  val compatibility: BoCompatibilityRequest? = null,

  val metadata: Map<String, Any>? = null,

  val allowedTools: List<String>? = null,

  @field:NotBlank(message = "body is required")
  val body: String,

  @field:Valid
  val scripts: List<BoScriptRequest>? = null,

  @field:Valid
  val references: List<BoReferenceRequest>? = null,

  // ======== 샌드박스 실행 설정 ========

  /** 네트워크 접근 정책 (기본값: ALLOW_SPECIFIC) */
  val networkPolicy: NetworkPolicy = NetworkPolicy.ALLOW_SPECIFIC,

  /** 허용된 도메인 목록 (networkPolicy가 ALLOW_SPECIFIC일 때 사용) */
  val allowedDomains: List<String>? = null,

  // ======== 트리거 전략 설정 (Phase 4) ========

  /** 트리거 전략 (RULE_BASED, LLM_BASED, HYBRID) */
  val triggerStrategy: TriggerStrategy = TriggerStrategy.RULE_BASED,

  /** LLM 트리거 설정 (triggerStrategy가 LLM_BASED 또는 HYBRID일 때 사용) */
  @field:Valid
  val llmTriggerConfig: BoLlmTriggerConfigRequest? = null,
)

/**
 * 호환성 요청 DTO
 */
data class BoCompatibilityRequest(
  val requiredProducts: List<String>? = null,
  val requiredPackages: List<String>? = null,
  val networkAccess: Boolean = false,
  val customRequirements: List<String>? = null,
) {
  fun toModel(): SkillCompatibility {
    return SkillCompatibility(
      requiredProducts = requiredProducts,
      requiredPackages = requiredPackages,
      networkAccess = networkAccess,
      customRequirements = customRequirements,
    )
  }
}

/**
 * 스크립트 요청 DTO
 */
data class BoScriptRequest(
  @field:NotBlank(message = "filename is required")
  val filename: String,

  val language: ScriptLanguage? = null,  // null이면 확장자에서 추론

  @field:NotBlank(message = "content is required")
  val content: String,

  val entrypoint: Boolean = false,

  val description: String? = null,
)

/**
 * 참조문서 요청 DTO
 */
data class BoReferenceRequest(
  @field:NotBlank(message = "filename is required")
  val filename: String,

  @field:NotBlank(message = "content is required")
  val content: String,

  val loadOnDemand: Boolean = true,

  val priority: Int = 0,
)

/**
 * 에셋 요청 DTO
 */
data class BoAssetRequest(
  @field:NotBlank(message = "filename is required")
  val filename: String,

  /** 에셋 타입 (OTHER, TEMPLATE, IMAGE 등) */
  val type: String? = null,

  /** MIME 타입 */
  val mimeType: String? = null,

  /** 텍스트 기반 에셋 내용 */
  val content: String? = null,

  /** 바이너리 에셋 경로 (S3 URL 등) */
  val path: String? = null,
)

// ========== 수정 요청 ==========

/**
 * 스킬 수정 요청 DTO (PATCH)
 * 모든 필드 선택적 (null이면 수정하지 않음)
 */
data class BoPatchSkillRequest(
  var id: Long? = null,  // PathVariable에서 설정

  @field:Size(min = 1, max = 64, message = "name must be 1-64 characters")
  val name: String? = null,

  @field:Size(min = 1, max = 1024, message = "description must be 1-1024 characters")
  val description: String? = null,

  val license: String? = null,

  @field:Valid
  val compatibility: BoCompatibilityRequest? = null,

  val metadata: Map<String, Any>? = null,

  val allowedTools: List<String>? = null,

  val body: String? = null,

  val status: Int? = null,

  // ======== 스크립트/참조문서/에셋 ========

  /** 스크립트 목록 */
  @field:Valid
  val scripts: List<BoScriptRequest>? = null,

  /** 참조문서 목록 */
  @field:Valid
  val references: List<BoReferenceRequest>? = null,

  /** 에셋 목록 */
  @field:Valid
  val assets: List<BoAssetRequest>? = null,

  // ======== 샌드박스 실행 설정 ========

  /** 네트워크 접근 정책 */
  val networkPolicy: NetworkPolicy? = null,

  /** 허용된 도메인 목록 */
  val allowedDomains: List<String>? = null,

  // ======== 트리거 전략 설정 (Phase 4) ========

  /** 트리거 전략 (RULE_BASED, LLM_BASED, HYBRID) */
  val triggerStrategy: TriggerStrategy? = null,

  /** LLM 트리거 설정 */
  @field:Valid
  val llmTriggerConfig: BoLlmTriggerConfigRequest? = null,
)

// ========== 목록 조회 응답 ==========

/**
 * 스킬 목록 응답 DTO
 */
data class BoSkillListResponse(
  val id: Long,
  val name: String,
  val description: String,
  val license: String?,
  val scriptsCount: Int,
  val referencesCount: Int,
  val assetsCount: Int,
  val tags: List<String>,
  /** 트리거 전략 (Phase 4) */
  val triggerStrategy: TriggerStrategy,
  val status: Int,
  val createdAt: String,
  val updatedAt: String,
)

// ========== 상세 조회 응답 ==========

/**
 * 스킬 상세 응답 DTO
 * scripts, references, assets 전체 데이터 포함
 */
data class BoSkillDetailResponse(
  val id: Long,
  val accountId: Long,
  val ownerId: Long,
  val name: String,
  val description: String,
  val license: String?,
  val compatibility: BoCompatibilityResponse?,
  val metadata: Map<String, Any>,
  val allowedTools: List<String>?,
  val body: String,
  /** 스크립트 목록 (코드 포함) */
  val scripts: List<BoSkillScriptDetailResponse>,
  /** 참조문서 목록 (내용 포함) */
  val references: List<BoSkillReferenceDetailResponse>,
  /** 에셋 목록 (내용 포함) */
  val assets: List<BoSkillAssetDetailResponse>,

  // ======== 샌드박스 실행 설정 ========

  /** 네트워크 접근 정책 */
  val networkPolicy: NetworkPolicy,
  /** 허용된 도메인 목록 */
  val allowedDomains: List<String>?,

  // ======== 트리거 전략 설정 (Phase 4) ========

  /** 트리거 전략 */
  val triggerStrategy: TriggerStrategy,
  /** LLM 트리거 설정 */
  val llmTriggerConfig: BoLlmTriggerConfigResponse?,

  val status: Int,
  val createdAt: String,
  val updatedAt: String,
)

/**
 * 호환성 정보 응답 DTO
 */
data class BoCompatibilityResponse(
  val requiredProducts: List<String>?,
  val requiredPackages: List<String>?,
  val networkAccess: Boolean,
  val customRequirements: List<String>?,
) {
  companion object {
    fun from(compatibility: SkillCompatibility?): BoCompatibilityResponse? {
      if (compatibility == null) return null
      return BoCompatibilityResponse(
        requiredProducts = compatibility.requiredProducts,
        requiredPackages = compatibility.requiredPackages,
        networkAccess = compatibility.networkAccess,
        customRequirements = compatibility.customRequirements,
      )
    }
  }
}

// ========== 스크립트 응답 ==========

/**
 * 스크립트 목록 응답 DTO
 */
data class BoSkillScriptResponse(
  val filename: String,
  val language: String,
  val entrypoint: Boolean,
  val description: String?,
  val contentLength: Int,
)

/**
 * 스크립트 상세 응답 DTO (코드 포함)
 */
data class BoSkillScriptDetailResponse(
  val filename: String,
  val language: String,
  val entrypoint: Boolean,
  val description: String?,
  val content: String,
  val lineCount: Int,
)

// ========== 참조문서 응답 ==========

/**
 * 참조문서 목록 응답 DTO
 */
data class BoSkillReferenceResponse(
  val filename: String,
  val loadOnDemand: Boolean,
  val priority: Int,
  val contentLength: Int,
)

/**
 * 참조문서 상세 응답 DTO (내용 포함)
 */
data class BoSkillReferenceDetailResponse(
  val filename: String,
  val loadOnDemand: Boolean,
  val priority: Int,
  val content: String,
)

// ========== 에셋 응답 ==========

/**
 * 에셋 목록 응답 DTO
 */
data class BoSkillAssetResponse(
  val filename: String,
  val type: String,
  val mimeType: String?,
  val hasContent: Boolean,
  val hasPath: Boolean,
)

/**
 * 에셋 상세 응답 DTO (내용 포함)
 */
data class BoSkillAssetDetailResponse(
  val filename: String,
  val type: String,
  val mimeType: String?,
  /** 텍스트 기반 에셋 내용 (JSON, YAML, Markdown 등) */
  val content: String?,
  /** 바이너리 에셋 경로 (S3 URL 등) */
  val path: String?,
)

// ========== 페이징 ==========

/**
 * 스킬 목록 페이징 응답 DTO
 */
data class Paging<T>(
  val content: List<T>,
  val totalElements: Long,
  val totalPages: Int,
  val page: Int,
  val size: Int,
)

// ========== 스킬 존재 여부 확인 ==========

/**
 * 스킬 존재 여부 확인 응답 DTO
 */
data class SkillExistsResponse(
  /** 스킬 존재 여부 */
  val exists: Boolean,
  /** 존재 시 기존 스킬 정보 */
  val skill: SkillExistsInfo? = null,
)

/**
 * 기존 스킬 정보 DTO
 */
data class SkillExistsInfo(
  val id: Long,
  val name: String,
  val description: String?,
  val updatedAt: String?,
)

// ========== ZIP 업로드 ==========

/**
 * 스킬 ZIP 업로드 결과 DTO
 */
data class SkillUploadResponse(
  /** 처리 성공 여부 */
  val success: Boolean,
  /** 수행 작업 (생성/업데이트) */
  val action: SkillUploadAction,
  /** 생성/업데이트된 스킬 정보 */
  val skill: SkillUploadInfo,
  /** 결과 메시지 */
  val message: String,
)

/**
 * 업로드 액션 타입
 */
enum class SkillUploadAction {
  /** 신규 생성 */
  CREATED,
  /** 기존 스킬 업데이트 */
  UPDATED,
}

/**
 * 업로드된 스킬 정보 DTO
 */
data class SkillUploadInfo(
  val id: Long,
  val name: String,
  val description: String?,
  val license: String?,
  val scriptsCount: Int,
  val referencesCount: Int,
  val assetsCount: Int,
)

// ========== LLM 트리거 설정 (Phase 4) ==========

/**
 * LLM 트리거 설정 요청 DTO
 * triggerStrategy가 LLM_BASED 또는 HYBRID일 때 사용
 */
data class BoLlmTriggerConfigRequest(
  /** 사용할 모델 ID (null이면 기본 모델) */
  val modelId: String? = null,

  /** 트리거 결정을 위한 신뢰도 임계값 (0.0~1.0, 기본값: 0.7) */
  @field:jakarta.validation.constraints.DecimalMin("0.0")
  @field:jakarta.validation.constraints.DecimalMax("1.0")
  val confidenceThreshold: Double = 0.7,

  /** LLM 응답 최대 토큰 수 */
  @field:jakarta.validation.constraints.Min(100)
  @field:jakarta.validation.constraints.Max(2000)
  val maxTokens: Int = 500,

  /** 커스텀 평가 프롬프트 (null이면 기본 프롬프트) */
  val customPrompt: String? = null,

  /** LLM 호출 타임아웃 (ms) */
  @field:jakarta.validation.constraints.Min(1000)
  @field:jakarta.validation.constraints.Max(60000)
  val timeoutMs: Long = 10000,

  /** LLM 오류 시 규칙 기반으로 폴백 여부 */
  val fallbackToRuleOnError: Boolean = true,
) {
  /**
   * 모델로 변환
   */
  fun toModel(): LlmTriggerConfig {
    return LlmTriggerConfig(
      modelId = modelId,
      confidenceThreshold = confidenceThreshold,
      maxTokens = maxTokens,
      customPrompt = customPrompt,
      timeoutMs = timeoutMs,
      fallbackToRuleOnError = fallbackToRuleOnError,
    )
  }
}

/**
 * LLM 트리거 설정 응답 DTO
 */
data class BoLlmTriggerConfigResponse(
  val modelId: String?,
  val confidenceThreshold: Double,
  val maxTokens: Int,
  val customPrompt: String?,
  val timeoutMs: Long,
  val fallbackToRuleOnError: Boolean,
) {
  companion object {
    fun from(config: LlmTriggerConfig?): BoLlmTriggerConfigResponse? {
      if (config == null) return null
      return BoLlmTriggerConfigResponse(
        modelId = config.modelId,
        confidenceThreshold = config.confidenceThreshold,
        maxTokens = config.maxTokens,
        customPrompt = config.customPrompt,
        timeoutMs = config.timeoutMs,
        fallbackToRuleOnError = config.fallbackToRuleOnError,
      )
    }
  }
}
