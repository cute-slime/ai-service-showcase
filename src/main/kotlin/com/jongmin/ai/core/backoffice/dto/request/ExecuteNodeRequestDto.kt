package com.jongmin.ai.core.backoffice.dto.request

import jakarta.validation.constraints.NotBlank

/**
 * 노드 실행 요청 DTO
 *
 * 워크플로우 에디터에서 개별 노드를 테스트하기 위한 요청.
 * - single 모드: 특정 노드만 독립적으로 실행
 * - flow 모드: 특정 노드부터 마지막 노드까지 순차 실행
 *
 * @author Claude Code
 * @since 2025.12.26
 */
data class ExecuteNodeRequest(
  /**
   * 노드 식별자
   * 워크플로우 내에서 노드를 구분하는 고유 ID
   */
  @field:NotBlank
  val nodeId: String,

  /**
   * 노드 타입
   * 지원 타입: scenario-concept, scenario-truth, scenario-characters,
   *           scenario-timeline, generate-text, router, rest-api-call-tool 등
   */
  @field:NotBlank
  val nodeType: String,

  /**
   * AI 어시스턴트 ID (LLM 노드에서만 필수)
   *
   * LLM 호출이 필요한 노드(generate-text, scenario-* 등)에서만 필수.
   * Tool 노드(rest-api-call-tool, web-search-tool 등)는 null 허용.
   */
  val aiAssistantId: Long? = null,

  /**
   * 입력 데이터 (이전 노드들의 output)
   * key: 입력 핸들 ID (예: "concept", "truth", "input", "payload")
   * value: JSON 문자열 형태의 출력값
   */
  val inputs: Map<String, String> = emptyMap(),

  /**
   * 노드 설정 (Tool 노드에서 필수)
   *
   * 노드 자체의 동작을 정의하는 설정 값.
   * - rest-api-call-tool: url, method, headers, pathVariables 등
   * - web-search-tool: searchEngine, maxResults 등
   * - generate-text: systemPrompt, userPrompt 등
   *
   * LLM 노드는 aiAssistantId를 통해 설정을 가져오므로 선택적.
   */
  val nodeConfig: Map<String, Any>? = null,

  /**
   * LLM 오버라이드 설정 (선택)
   * 어시스턴트의 기본 설정을 덮어쓸 수 있다
   */
  val overrides: LlmOverrides? = null,

  /**
   * 스트리밍 모드 사용 여부
   * true: SSE 스트리밍 응답
   * false: 동기 응답 (기본값)
   */
  val streaming: Boolean = false,

  /**
   * 실행 모드
   * - single: 요청한 노드만 독립적으로 실행 (기본값)
   * - flow: 요청한 노드부터 워크플로우 마지막 노드까지 순차 실행
   */
  val executeMode: ExecuteMode = ExecuteMode.SINGLE,

  /**
   * AI Agent ID (flow 모드에서 필수)
   * 저장된 워크플로우를 조회하여 연결된 노드들을 실행
   */
  val aiAgentId: Long? = null
)

/**
 * 노드 실행 모드
 */
enum class ExecuteMode {
  /** 요청한 노드만 독립 실행 */
  SINGLE,

  /** 요청한 노드부터 마지막 노드까지 순차 실행 */
  FLOW
}

/**
 * LLM 오버라이드 설정
 *
 * AI 어시스턴트의 기본 설정을 일시적으로 변경할 때 사용.
 * null인 필드는 어시스턴트의 기본값을 사용한다.
 */
data class LlmOverrides(
  // === 모델 오버라이드 ===
  /** AI 프로바이더 ID (예: OpenAI, Anthropic) */
  val aiProviderId: Long? = null,

  /** AI 모델 ID */
  val aiModelId: Long? = null,

  /** API 키 ID */
  val aiApiKeyId: Long? = null,

  // === 생성 파라미터 ===
  /** 온도 (0.0 ~ 2.0) - 높을수록 창의적 */
  val temperature: Double? = null,

  /** Top P (0.0 ~ 1.0) - 누적 확률 샘플링 */
  val topP: Double? = null,

  /** Top K - 상위 K개 토큰만 고려 */
  val topK: Int? = null,

  /** 최대 생성 토큰 수 */
  val maxTokens: Int? = null,

  /** 빈도 패널티 */
  val frequencyPenalty: Double? = null,

  /** 존재 패널티 */
  val presencePenalty: Double? = null,

  /** 응답 형식 (text, json_object) */
  val responseFormat: String? = null,

  // === 리즈닝 설정 ===
  /** 리즈닝 노력 수준 (low, medium, high) */
  val reasoningEffort: String? = null,

  /** 사고 비활성화 트리거 */
  val noThinkTrigger: String? = null
)
