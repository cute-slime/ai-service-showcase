package com.jongmin.ai.multiagent.model

/**
 * 에이전트 결과 검증 결과
 */
enum class ValidationResult {
  PASS,   // 통과
  WARN,   // 경고 후 진행
  FAIL    // 재시도 or 오케스트레이터 위임
}
