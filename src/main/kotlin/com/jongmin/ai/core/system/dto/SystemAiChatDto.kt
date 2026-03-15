package com.jongmin.ai.core.system.dto

import com.jongmin.ai.core.AiAssistantType

/**
 * 시스템 AI 채팅 API DTO
 *
 * 다른 마이크로서비스(game-service 등)에서 LLM 채팅을 요청할 때 사용하는 DTO.
 * ai-service가 모든 LLM 호출을 담당하고, 다른 서비스는 HTTP API로 요청만 보낸다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */

/**
 * 시스템 채팅 요청 DTO
 *
 * assistantId 또는 (type + category)로 어시스턴트를 지정할 수 있다.
 * - assistantId가 있으면 해당 ID로 조회
 * - 없으면 type (+ category)로 조회
 */
data class SystemChatRequest(
  /** 어시스턴트 ID (이 값이 있으면 type/category 무시) */
  val assistantId: Long? = null,

  /** 어시스턴트 타입 (assistantId가 없을 때 사용) */
  val assistantType: AiAssistantType? = null,

  /** 어시스턴트 카테고리 (선택, assistantType과 함께 사용) */
  val assistantCategory: String? = null,

  /** 채팅 메시지 목록 (system, user, assistant) */
  val messages: List<SystemChatMessage>,

  /** 컨텍스트 ID (추적용, 예: sessionId, threadId) */
  val contextId: Long? = null,

  // === 템플릿 변수 ===

  /**
   * 시스템 프롬프트 템플릿 변수
   *
   * 어시스턴트의 시스템 프롬프트에 있는 `{{key}}` 형태의 플레이스홀더를 대체한다.
   * 예: {"nickname": "홍길동", "age": "25"} → {{nickname}}, {{age}} 대체
   */
  val templateVariables: Map<String, String>? = null,

  // === 동적 오버라이드 옵션 (캐릭터별 파라미터 등) ===

  /** Temperature 오버라이드 */
  val temperature: Double? = null,

  /** Top P 오버라이드 */
  val topP: Double? = null,

  /** Top K 오버라이드 */
  val topK: Int? = null,

  /** Frequency Penalty 오버라이드 */
  val frequencyPenalty: Double? = null,

  /** Presence Penalty 오버라이드 */
  val presencePenalty: Double? = null,

  /** Max Tokens 오버라이드 */
  val maxTokens: Int? = null,
)

/**
 * 시스템 채팅 메시지
 */
data class SystemChatMessage(
  /** 역할: system, user, assistant */
  val role: String,

  /** 메시지 내용 */
  val content: String,
)

/**
 * 시스템 채팅 응답 DTO (동기)
 */
data class SystemChatResponse(
  /** AI 응답 내용 */
  val content: String,

  /** 사용된 어시스턴트 ID */
  val assistantId: Long,

  /** 사용된 어시스턴트 이름 */
  val assistantName: String,

  /** 사용된 모델명 */
  val model: String,

  /** 토큰 사용량 */
  val usage: SystemChatUsage? = null,
)

/**
 * 토큰 사용량 정보
 */
data class SystemChatUsage(
  /** 입력 토큰 수 */
  val inputTokens: Long? = null,

  /** 출력 토큰 수 */
  val outputTokens: Long? = null,

  /** 총 토큰 수 */
  val totalTokens: Long? = null,
)

/**
 * 스트리밍 채팅 청크 (SSE 이벤트 데이터)
 */
data class SystemChatStreamChunk(
  /** 토큰 조각 */
  val content: String,

  /** 완료 여부 */
  val done: Boolean = false,

  /** 에러 메시지 (에러 발생 시) */
  val error: String? = null,

  /** 최종 사용량 (done=true 일 때만) */
  val usage: SystemChatUsage? = null,
)
