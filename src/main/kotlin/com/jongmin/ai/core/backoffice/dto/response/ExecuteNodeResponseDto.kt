package com.jongmin.ai.core.backoffice.dto.response

/**
 * 단일 노드 실행 응답 DTO
 *
 * 노드 실행 결과를 반환한다.
 * 성공 시 sources에 출력 핸들별 결과가 포함되고,
 * 실패 시 error에 에러 정보가 포함된다.
 *
 * @author Claude Code
 * @since 2025.12.26
 */
data class ExecuteNodeResponse(
  /**
   * 실행 성공 여부
   */
  val success: Boolean,

  /**
   * 출력 데이터 (성공 시)
   * key: 출력 핸들 ID (예: "concept", "truth", "characters")
   * value: JSON 문자열 형태의 출력값
   */
  val sources: Map<String, String>? = null,

  /**
   * 에러 정보 (실패 시)
   */
  val error: ExecuteNodeError? = null
) {
  companion object {
    /**
     * 성공 응답 생성
     */
    fun success(sources: Map<String, String>): ExecuteNodeResponse {
      return ExecuteNodeResponse(
        success = true,
        sources = sources
      )
    }

    /**
     * 실패 응답 생성
     */
    fun failure(code: String, message: String, details: Any? = null): ExecuteNodeResponse {
      return ExecuteNodeResponse(
        success = false,
        error = ExecuteNodeError(code, message, details)
      )
    }
  }
}

/**
 * 노드 실행 에러 정보
 */
data class ExecuteNodeError(
  /**
   * 에러 코드
   * - INVALID_NODE_TYPE: 지원하지 않는 노드 타입
   * - MISSING_INPUT: 필수 입력 데이터 누락
   * - INVALID_INPUT_FORMAT: 입력 데이터 JSON 파싱 실패
   * - AI_ASSISTANT_NOT_FOUND: AI 어시스턴트 ID 없음
   * - AI_MODEL_NOT_FOUND: 오버라이드된 모델 ID 없음
   * - API_KEY_INVALID: API 키 유효하지 않음
   * - LLM_GENERATION_FAILED: LLM 생성 실패
   * - TIMEOUT: 실행 시간 초과
   * - INTERNAL_ERROR: 내부 서버 오류
   */
  val code: String,

  /**
   * 에러 메시지 (사람이 읽을 수 있는 형태)
   */
  val message: String,

  /**
   * 추가 상세 정보 (선택)
   */
  val details: Any? = null
)

/**
 * 에러 코드 상수
 */
object ExecuteNodeErrorCode {
  const val INVALID_NODE_TYPE = "INVALID_NODE_TYPE"
  const val MISSING_INPUT = "MISSING_INPUT"
  const val INVALID_INPUT_FORMAT = "INVALID_INPUT_FORMAT"
  const val AI_ASSISTANT_NOT_FOUND = "AI_ASSISTANT_NOT_FOUND"
  const val AI_MODEL_NOT_FOUND = "AI_MODEL_NOT_FOUND"
  const val API_KEY_INVALID = "API_KEY_INVALID"
  const val LLM_GENERATION_FAILED = "LLM_GENERATION_FAILED"
  const val TIMEOUT = "TIMEOUT"
  const val INTERNAL_ERROR = "INTERNAL_ERROR"
}
