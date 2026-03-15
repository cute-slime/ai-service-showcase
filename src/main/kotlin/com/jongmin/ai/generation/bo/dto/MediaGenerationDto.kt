package com.jongmin.ai.generation.bo.dto

import com.jongmin.ai.core.GenerationMediaType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * 미디어 생성 요청 DTO
 *
 * FE에서 BO API를 통해 미디어 생성을 요청할 때 사용.
 * 도메인에 무관하게 generationConfig를 받아 프로바이더에 전달한다.
 */
data class GenerateRequest(
  /** 미디어 타입 (IMAGE, VIDEO, BGM) */
  @field:NotBlank(message = "mediaType은 필수입니다")
  val mediaType: String,

  /** 프로바이더 코드 (예: COMFYUI, NOVELAI) */
  @field:NotBlank(message = "providerCode는 필수입니다")
  val providerCode: String,

  /** 프로바이더 ID (선택, 동일 code 내 특정 엔드포인트 강제) */
  val providerId: Long? = null,

  /** 모델 코드 (선택, 예: FLUX_DEV) */
  val modelCode: String? = null,

  /** 생성 설정 (프롬프트, 파라미터 포함) */
  @field:NotNull(message = "generationConfig는 필수입니다")
  val generationConfig: Map<String, Any> = emptyMap(),
)

/**
 * 미디어 재생성 요청 DTO
 *
 * 이전 생성 결과를 기반으로 재생성 시 seed/workflowJson 오버라이드 지원.
 */
data class RegenerateRequest(
  /** 미디어 타입 (IMAGE, VIDEO, BGM) */
  @field:NotBlank(message = "mediaType은 필수입니다")
  val mediaType: String,

  /** 프로바이더 코드 (예: COMFYUI, NOVELAI) */
  @field:NotBlank(message = "providerCode는 필수입니다")
  val providerCode: String,

  /** 프로바이더 ID (선택, 동일 code 내 특정 엔드포인트 강제) */
  val providerId: Long? = null,

  /** 모델 코드 (선택) */
  val modelCode: String? = null,

  /** 생성 설정 */
  @field:NotNull(message = "generationConfig는 필수입니다")
  val generationConfig: Map<String, Any> = emptyMap(),

  /** 시드 값 오버라이드 (재현용) */
  val seed: Long? = null,

  /** 워크플로우 JSON 오버라이드 (ComfyUI 등) */
  val workflowJson: String? = null,
)

/**
 * 미디어 생성 응답 DTO
 *
 * 생성 요청 후 jobId를 반환하여 SSE 구독에 사용.
 */
data class GenerateResponse(
  /** 요청 성공 여부 */
  val success: Boolean,

  /** 결과 메시지 */
  val message: String? = null,

  /** DTE Job ID (SSE 구독용) */
  val jobId: String? = null,

  /** 사용된 프로바이더 코드 */
  val providerCode: String? = null,

  /** 미디어 타입 */
  val mediaType: String? = null,
) {
  companion object {
    fun success(jobId: String, providerCode: String, mediaType: String): GenerateResponse {
      return GenerateResponse(
        success = true,
        message = "미디어 생성 작업이 등록되었습니다",
        jobId = jobId,
        providerCode = providerCode,
        mediaType = mediaType,
      )
    }

    fun failure(message: String): GenerateResponse {
      return GenerateResponse(
        success = false,
        message = message,
      )
    }
  }
}

/**
 * 프로바이더 상태 응답 DTO
 *
 * SystemMediaGenerationDto.ProviderStatusResponse와 동일한 구조를 BO용으로 재사용.
 */
data class BoProviderStatusResponse(
  /** 프로바이더 코드 */
  val providerCode: String,

  /** 프로바이더 설명 */
  val description: String,

  /** 지원 미디어 타입 */
  val supportedMediaTypes: List<GenerationMediaType>,

  /** 사용 가능 여부 */
  val available: Boolean,
)

/**
 * 프로바이더 목록 응답 DTO
 */
data class BoProvidersListResponse(
  /** 프로바이더 목록 */
  val providers: List<BoProviderStatusResponse>,

  /** 전체 프로바이더 수 */
  val totalCount: Int = providers.size,
)

/**
 * 활성 Job 정보 응답 DTO
 */
data class ActiveJobResponse(
  /** DTE Job ID */
  val jobId: String,

  /** 미디어 타입 */
  val mediaType: String,

  /** 프로바이더 코드 */
  val providerCode: String,

  /** 현재 상태 */
  val status: String,

  /** 진행률 (0~100) */
  val progress: Int = 0,

  /** 생성 시간 (epoch millis) */
  val createdAt: Long,
)

/**
 * 활성 Job 목록 응답 DTO
 */
data class ActiveJobsListResponse(
  /** 활성 Job 목록 */
  val jobs: List<ActiveJobResponse>,

  /** 전체 활성 Job 수 */
  val totalCount: Int = jobs.size,

  /** 계정당 최대 동시 실행 수 */
  val maxConcurrentJobs: Int,
)

/**
 * NovelAI 프롬프트 인첸터 대상 에셋 종류
 */
enum class NovelAiAssetKind {
  CHARACTER,
  BACKGROUND,
}

/**
 * NovelAI 일관성 운영 모드
 */
enum class NovelAiConsistencyMode {
  /** 고정 블록 우선, 변동 최소화 */
  STRICT,

  /** 고정/변동 균형 */
  BALANCED,

  /** 장면 변형 허용 */
  FLEXIBLE,
}

/**
 * NovelAI 프롬프트 인첸터 요청 DTO
 *
 * 기존 캐릭터/배경 프롬프트를 기반으로 LLM이 개선 프롬프트를 생성한다.
 */
data class NovelAiPromptEnhanceRequest(
  /** 대상 에셋 종류 */
  @field:NotNull(message = "assetKind는 필수입니다")
  val assetKind: NovelAiAssetKind,

  /** 현재 사용 중인 기준 프롬프트 */
  @field:NotBlank(message = "basePrompt는 필수입니다")
  val basePrompt: String,

  /** 스타일 일관성 참고용 추가 프롬프트 (예: 캐릭터/배경 레퍼런스) */
  val referencePrompts: List<String> = emptyList(),

  /** 현재 네거티브 프롬프트 */
  val baseNegativePrompt: String? = null,

  /** 프로젝트에서 고정하고 싶은 작가 태그 조합 */
  @field:Size(min = 2, max = 30, message = "preferredArtistTags는 최소 2개 이상, 30개 이하로 입력해야 합니다")
  val preferredArtistTags: List<String> = emptyList(),

  /** 고정 스타일 키워드 */
  val styleKeywords: List<String> = emptyList(),

  /** 분위기/톤 키워드 */
  val vibeKeywords: List<String> = emptyList(),

  /** 장면별 가변 지시문 */
  val sceneInstruction: String? = null,

  /** 일관성 운영 모드 */
  val consistencyMode: NovelAiConsistencyMode = NovelAiConsistencyMode.BALANCED,

  /** 고정 템플릿/생성 파라미터 */
  val lockedTemplate: NovelAiLockedTemplateRequest? = null,

  /** 특정 어시스턴트 강제 사용 (선택) */
  val assistantId: Long? = null,
)

/**
 * NovelAI 고정 템플릿/파라미터
 */
data class NovelAiLockedTemplateRequest(
  /** 항상 고정할 스타일 블록 */
  val styleBlock: String? = null,

  /** CHARACTER 전용 고정 블록 */
  val characterBlock: String? = null,

  /** BACKGROUND 전용 고정 블록 */
  val backgroundBlock: String? = null,

  /** 샘플러 고정값 */
  val sampler: String? = null,

  /** 스텝 고정값 */
  val steps: Int? = null,

  /** CFG 고정값 */
  val cfgScale: Double? = null,

  /** 해상도 고정 width */
  val width: Int? = null,

  /** 해상도 고정 height */
  val height: Int? = null,

  /** 시드 고정값 */
  val seed: Long? = null,
)

/**
 * NovelAI 프롬프트 블록 응답
 */
data class NovelAiPromptTemplateBlockResponse(
  /** 스타일 고정 블록 */
  val styleBlock: String,

  /** 캐릭터/배경 본문 블록 */
  val subjectBlock: String,

  /** 장면 가변 블록 */
  val variableSceneBlock: String,
)

/**
 * NovelAI 생성 설정 권장값
 */
data class NovelAiGenerationSettingsResponse(
  val sampler: String,
  val steps: Int,
  val cfgScale: Double,
  val width: Int,
  val height: Int,
  val seed: Long? = null,
  val seedPolicy: String,
)

/**
 * NovelAI 프롬프트 인첸터 응답 DTO
 */
data class NovelAiPromptEnhanceResponse(
  /** 처리 성공 여부 */
  val success: Boolean,

  /** LLM 실패/파싱 실패로 규칙 기반 fallback 적용 여부 */
  val fallbackApplied: Boolean,

  /** 최종 개선 프롬프트 */
  val enhancedPrompt: String,

  /** 최종 개선 네거티브 프롬프트 */
  val enhancedNegativePrompt: String,

  /** 블록 단위 템플릿 */
  val promptTemplate: NovelAiPromptTemplateBlockResponse,

  /** 권장 생성 설정 */
  val settings: NovelAiGenerationSettingsResponse,

  /** 적용된 최적화 전략 */
  val appliedStrategies: List<String> = emptyList(),

  /** 경고/주의사항 */
  val warnings: List<String> = emptyList(),

  /** 사용된 어시스턴트 ID */
  val llmAssistantId: Long? = null,

  /** 사용된 어시스턴트 이름 */
  val llmAssistantName: String? = null,

  /** 처리 시간 (ms) */
  val durationMs: Long,
)

/**
 * ComfyUI 프롬프트 인첸터 요청 DTO
 *
 * 기존 캐릭터/배경 프롬프트를 기반으로 ComfyUI/Stable Diffusion 계열 워크플로우에
 * 넣기 좋은 positive/negative prompt를 생성한다.
 */
data class ComfyUiPromptEnhanceRequest(
  /** 대상 에셋 종류 */
  @field:NotNull(message = "assetKind는 필수입니다")
  val assetKind: NovelAiAssetKind,

  /** 현재 사용 중인 기준 프롬프트 */
  @field:NotBlank(message = "basePrompt는 필수입니다")
  val basePrompt: String,

  /** 스타일 일관성 참고용 추가 프롬프트 */
  val referencePrompts: List<String> = emptyList(),

  /** 현재 네거티브 프롬프트 */
  val baseNegativePrompt: String? = null,

  /** 선호 작가/비주얼 레퍼런스 */
  val preferredArtistTags: List<String> = emptyList(),

  /** 고정 스타일 키워드 */
  val styleKeywords: List<String> = emptyList(),

  /** 분위기/톤 키워드 */
  val vibeKeywords: List<String> = emptyList(),

  /** 장면별 가변 지시문 */
  val sceneInstruction: String? = null,

  /** 일관성 운영 모드 */
  val consistencyMode: NovelAiConsistencyMode = NovelAiConsistencyMode.BALANCED,

  /** 고정 템플릿/생성 파라미터 */
  val lockedTemplate: NovelAiLockedTemplateRequest? = null,

  /** 특정 어시스턴트 강제 사용 (선택) */
  val assistantId: Long? = null,
)

/**
 * ComfyUI 프롬프트 블록 응답
 */
data class ComfyUiPromptTemplateBlockResponse(
  /** 스타일 고정 블록 */
  val styleBlock: String,

  /** 캐릭터/배경 본문 블록 */
  val subjectBlock: String,

  /** 장면 가변 블록 */
  val variableSceneBlock: String,
)

/**
 * ComfyUI 생성 설정 권장값
 */
data class ComfyUiGenerationSettingsResponse(
  val sampler: String? = null,
  val steps: Int? = null,
  val cfgScale: Double? = null,
  val width: Int? = null,
  val height: Int? = null,
  val seed: Long? = null,
  val seedPolicy: String? = null,
)

/**
 * ComfyUI 프롬프트 인첸터 응답 DTO
 */
data class ComfyUiPromptEnhanceResponse(
  /** 처리 성공 여부 */
  val success: Boolean,

  /** LLM 실패/파싱 실패로 규칙 기반 fallback 적용 여부 */
  val fallbackApplied: Boolean,

  /** 최종 개선 프롬프트 */
  val enhancedPrompt: String,

  /** 최종 개선 네거티브 프롬프트 */
  val enhancedNegativePrompt: String,

  /** 블록 단위 템플릿 */
  val promptTemplate: ComfyUiPromptTemplateBlockResponse,

  /** 권장 생성 설정 */
  val settings: ComfyUiGenerationSettingsResponse,

  /** 적용된 최적화 전략 */
  val appliedStrategies: List<String> = emptyList(),

  /** 경고/주의사항 */
  val warnings: List<String> = emptyList(),

  /** 사용된 어시스턴트 ID */
  val llmAssistantId: Long? = null,

  /** 사용된 어시스턴트 이름 */
  val llmAssistantName: String? = null,

  /** 처리 시간 (ms) */
  val durationMs: Long,
)
