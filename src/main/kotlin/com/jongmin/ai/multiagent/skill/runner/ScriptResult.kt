package com.jongmin.ai.multiagent.skill.runner

/**
 * 스크립트 실행 결과
 */
data class ScriptResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
  val durationMs: Long,
) {
  /**
   * 실행 성공 여부 (exit code 0)
   */
  val success: Boolean get() = exitCode == 0

  /**
   * 타임아웃 여부
   */
  val timedOut: Boolean get() = exitCode == TIMEOUT_EXIT_CODE

  companion object {
    // 타임아웃 시 반환할 exit code
    const val TIMEOUT_EXIT_CODE = -1
  }
}

/**
 * 스크립트 실행 예외
 */
class ScriptExecutionException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
