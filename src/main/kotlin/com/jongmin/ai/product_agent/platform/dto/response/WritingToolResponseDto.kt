package com.jongmin.ai.product_agent.platform.dto.response

/**
 * 글쓰기 도구 SSE 이벤트 타입
 */
object WritingEventType {
  /** 상태 변경 이벤트 */
  const val STATUS = "STATUS"

  /** 토큰 스트리밍 이벤트 */
  const val TOKEN = "TOKEN"

  /** 프롬프트 생성 완료 이벤트 */
  const val PROMPT_GENERATED = "PROMPT_GENERATED"

  /** 평가 거부 이벤트 */
  const val EVALUATION_REJECTED = "EVALUATION_REJECTED"

  /** 완료 이벤트 */
  const val COMPLETED = "COMPLETED"

  /** 에러 이벤트 */
  const val ERROR = "ERROR"
}

/**
 * 글쓰기 도구 SSE 상태 값
 */
object WritingStatus {
  /** 처리 시작 */
  const val PROCESSING = "PROCESSING"

  /** 평가 중 */
  const val EVALUATING = "EVALUATING"

  /** 평가 통과 */
  const val EVALUATION_PASSED = "EVALUATION_PASSED"

  /** 평가 거부 */
  const val REJECTED = "REJECTED"

  /** 프롬프트 생성 중 */
  const val GENERATING_PROMPT = "GENERATING_PROMPT"

  /** 프롬프트 생성 완료 */
  const val PROMPT_GENERATED = "PROMPT_GENERATED"

  /** 텍스트 생성 중 */
  const val GENERATING = "GENERATING"

  /** 완료 */
  const val COMPLETED = "COMPLETED"

  /** 실패 */
  const val FAILED = "FAILED"
}

/**
 * 글쓰기 도구 에러 코드
 */
object WritingErrorCode {
  /** 유효하지 않은 텍스트 */
  const val INVALID_TEXT = "INVALID_TEXT"

  /** 유효하지 않은 타입 */
  const val INVALID_TYPE = "INVALID_TYPE"

  /** 유효하지 않은 모델 */
  const val INVALID_MODEL = "INVALID_MODEL"

  /** LLM 호출 오류 */
  const val LLM_ERROR = "LLM_ERROR"

  /** 타임아웃 */
  const val TIMEOUT = "TIMEOUT"

  /** 서비스 이용 불가 */
  const val SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"

  /** 알 수 없는 오류 */
  const val UNKNOWN_ERROR = "UNKNOWN_ERROR"
}

/**
 * 글쓰기 완료 결과 데이터
 */
data class WritingCompletedData(
  /** 최종 출력 텍스트 */
  val output: String,

  /** 사용된 작업 타입 */
  val type: String,

  /** 원본 텍스트 */
  val originalText: String,

  /** 사용 토큰 수 */
  val tokenCount: Int,

  /** 소요 시간 (ms) */
  val duration: Long
)

/**
 * 글쓰기 작업 출력 데이터 (ProductAgentOutput 저장용)
 *
 * 현재는 저장하지 않지만, 추후 저장 기능 추가 시 사용
 */
data class WritingOutputData(
  /** 원본 텍스트 */
  val originalText: String,

  /** 출력 텍스트 */
  val output: String,

  /** 작업 타입 */
  val type: String,

  /** 출력 언어 */
  val outputLanguage: String,

  /** 사용 토큰 수 */
  val tokenCount: Int,

  /** 소요 시간 (ms) */
  val duration: Long
)
