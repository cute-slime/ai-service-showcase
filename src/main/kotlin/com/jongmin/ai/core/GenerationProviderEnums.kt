package com.jongmin.ai.core

/**
 * AI 미디어 생성 프로바이더 관련 Enum 정의
 *
 * 이미지, 영상, 음악 등 미디어 생성에 사용되는 프로바이더/모델 관리를 위한 타입 정의
 *
 * @author Claude Code
 * @since 2026.01.10
 */

/**
 * 프로바이더 상태
 */
enum class GenerationProviderStatus {
  /** 활성 - 정상 사용 가능 */
  ACTIVE,

  /** 비활성 - 사용 불가 */
  INACTIVE,

  /** 점검 중 - 일시적으로 사용 불가 */
  MAINTENANCE
}

/**
 * 모델 상태
 */
enum class GenerationModelStatus {
  /** 활성 - 정상 사용 가능 */
  ACTIVE,

  /** 베타 - 테스트 단계 */
  BETA,

  /** 지원 종료 예정 - 곧 비활성화 예정 */
  DEPRECATED,

  /** 비활성 - 사용 불가 */
  INACTIVE
}

/**
 * API 인증 타입
 */
enum class GenerationAuthType {
  /** API Key 헤더 (X-Api-Key) */
  API_KEY,

  /** Bearer 토큰 (Authorization: Bearer xxx) */
  BEARER,

  /** OAuth2 인증 */
  OAUTH2,

  /** 커스텀 인증 (프로바이더별 특수 인증) */
  CUSTOM
}

/**
 * API 응답 타입
 */
enum class GenerationResponseType {
  /** 동기 응답 - 즉시 결과 반환 */
  SYNC,

  /** 비동기 폴링 - 작업 ID 반환 후 상태 조회 */
  ASYNC_POLLING,

  /** 비동기 웹훅 - 완료 시 콜백 호출 */
  ASYNC_WEBHOOK,

  /** Server-Sent Events - 실시간 스트리밍 */
  SSE
}

/**
 * 프롬프트 포맷
 */
enum class GenerationPromptFormat {
  /** 자연어 - 일반 문장 형태 (Flux, DALL-E) */
  NATURAL,

  /** 태그 기반 - 쉼표로 구분된 태그 (NovelAI, Stable Diffusion) */
  TAG_BASED,

  /** 구조화 - 특수 파라미터 포함 (Midjourney --ar, --style) */
  STRUCTURED
}

/**
 * 프리셋 타입
 */
enum class GenerationPresetType {
  /** 해상도 프리셋 */
  RESOLUTION,

  /**
   * 배경 이미지 스타일 프리셋
   *
   * StylePresetParams (artistTag, timeOfDay, mood, lighting, weather 등)를 사용하여
   * 배경 이미지 생성 시 분위기/스타일을 지정합니다.
   */
  BACKGROUND,

  /** 품질 프리셋 */
  QUALITY,

  /** 샘플러 프리셋 */
  SAMPLER,

  /** 길이 프리셋 (영상/음악용) */
  DURATION
}

/**
 * 비용 단위 타입
 */
enum class GenerationCostUnitType {
  /** 이미지당 */
  PER_IMAGE,

  /** 초당 (영상) */
  PER_SECOND,

  /** 분당 (음악) */
  PER_MINUTE,

  /** 토큰당 */
  PER_TOKEN,

  /** 요청당 */
  PER_REQUEST
}

/**
 * 생성 미디어 타입
 *
 * 기존 game/enums/BackgroundAssetEnums.kt의 MediaType과 구분하기 위해
 * Generation prefix 사용. 실제 사용 시 필요에 따라 통합 검토.
 */
enum class GenerationMediaType {
  /** 이미지 */
  IMAGE,

  /** 영상 */
  VIDEO,

  /** 배경음악 */
  BGM,

  /** 사운드트랙 */
  OST,

  /** 효과음 */
  SFX
}

/**
 * 멀티미디어 워크플로우 상태
 */
enum class GenerationWorkflowStatus {
  /** 작성 중 */
  DRAFT,

  /** 활성 */
  ACTIVE,

  /** 사용 중단 예정 */
  DEPRECATED,

  /** 보관 */
  ARCHIVED
}

/**
 * 멀티미디어 워크플로우 포맷
 */
enum class GenerationWorkflowFormat {
  /** ComfyUI API 그래프 JSON */
  COMFYUI_API,

  /** 플레이스홀더 기반 일반 JSON 템플릿 */
  JSON_TEMPLATE,

  /** 프로바이더 고유 포맷 JSON */
  PROVIDER_SPECIFIC
}

/**
 * 멀티미디어 워크플로우 동작 파이프라인
 *
 * 워크플로우의 입출력 형태를 정의한다.
 */
enum class GenerationWorkflowPipeline {
  /** 텍스트 프롬프트 → 미디어 생성 */
  PROMPT_TO_MEDIA,

  /** 기존 미디어 입력 → 미디어 변환 (예: Remove BG) */
  MEDIA_TO_MEDIA
}

/**
 * 멀티미디어 워크플로우 변수 타입
 */
enum class GenerationWorkflowVariableType {
  STRING,
  NUMBER,
  BOOLEAN,
  JSON
}

/**
 * 에셋 프리셋 타입
 *
 * 플랫폼 설정에서 관리되는 에셋 생성 프리셋 타입
 * 배경/캐릭터 에셋 그룹 생성 시 기본 미디어 생성 설정으로 사용
 *
 * @author Claude Code
 * @since 2026.01.10
 */
enum class AssetPresetType {
  /** 배경 에셋 프리셋 */
  BACKGROUND,

  /** 캐릭터 에셋 프리셋 */
  CHARACTER
}
