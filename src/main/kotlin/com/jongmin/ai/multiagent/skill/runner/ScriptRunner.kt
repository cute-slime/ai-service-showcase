package com.jongmin.ai.multiagent.skill.runner

import com.jongmin.ai.multiagent.skill.model.ScriptLanguage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private val kLogger = KotlinLogging.logger {}

/**
 * 스크립트 실행기
 * Python, Bash, JavaScript 스크립트 실행 지원
 *
 * 보안 참고:
 * - 프로세스 타임아웃 강제
 * - 출력 크기 제한
 * - 임시 파일 자동 정리
 */
@Component
class ScriptRunner(
  private val objectMapper: ObjectMapper,
) {

  companion object {
    // 스크립트 실행 타임아웃 (초)
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    // 최대 출력 길이 (100KB)
    private const val MAX_OUTPUT_LENGTH = 100_000
  }

  /**
   * 스크립트 실행
   *
   * @param language 스크립트 언어
   * @param content 스크립트 내용
   * @param input 입력 데이터 (JSON 직렬화되어 stdin으로 전달)
   * @param env 환경 변수
   * @param timeoutSeconds 타임아웃 (초)
   * @return 실행 결과
   */
  fun run(
    language: ScriptLanguage,
    content: String,
    input: Any? = null,
    env: Map<String, String> = emptyMap(),
    timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
  ): ScriptResult {
    kLogger.debug { "스크립트 실행 시작 - language: $language, timeout: ${timeoutSeconds}s" }

    val startTime = System.currentTimeMillis()

    // 1. 임시 파일 생성
    val tempFile = createTempScript(language, content)

    try {
      // 2. 실행 명령어 구성
      val command = buildCommand(language, tempFile)

      // 3. 프로세스 실행
      val processBuilder = ProcessBuilder(command)
        .apply {
          environment().putAll(env)
          redirectErrorStream(false)
        }

      val process = processBuilder.start()

      // 4. stdin으로 입력 전달
      input?.let {
        process.outputStream.bufferedWriter().use { writer ->
          val jsonInput = when (it) {
            is String -> it
            else -> objectMapper.writeValueAsString(it)
          }
          writer.write(jsonInput)
        }
      }

      // 5. 프로세스 완료 대기
      val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

      if (!completed) {
        process.destroyForcibly()
        kLogger.warn { "스크립트 타임아웃 - language: $language, timeout: ${timeoutSeconds}s" }
        return ScriptResult(
          exitCode = ScriptResult.TIMEOUT_EXIT_CODE,
          stdout = "",
          stderr = "Script execution timed out after ${timeoutSeconds}s",
          durationMs = System.currentTimeMillis() - startTime,
        )
      }

      // 6. 출력 읽기 (크기 제한)
      val stdout = process.inputStream.bufferedReader().readText()
        .take(MAX_OUTPUT_LENGTH)
      val stderr = process.errorStream.bufferedReader().readText()
        .take(MAX_OUTPUT_LENGTH)

      val durationMs = System.currentTimeMillis() - startTime

      kLogger.debug {
        "스크립트 실행 완료 - exitCode: ${process.exitValue()}, durationMs: $durationMs"
      }

      return ScriptResult(
        exitCode = process.exitValue(),
        stdout = stdout,
        stderr = stderr,
        durationMs = durationMs,
      )

    } finally {
      // 7. 임시 파일 정리
      tempFile.delete()
    }
  }

  /**
   * JSON 형태의 입력으로 스크립트 실행하고 JSON 결과 반환
   *
   * @throws ScriptExecutionException 스크립트 실행 실패 시
   */
  fun runWithJsonResult(
    language: ScriptLanguage,
    content: String,
    input: Any? = null,
    env: Map<String, String> = emptyMap(),
    timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
  ): Any? {
    val result = run(language, content, input, env, timeoutSeconds)

    if (!result.success) {
      throw ScriptExecutionException(
        "Script failed with exit code ${result.exitCode}: ${result.stderr}"
      )
    }

    return try {
      objectMapper.readValue(result.stdout, Any::class.java)
    } catch (e: Exception) {
      // JSON 파싱 실패 시 문자열 그대로 반환
      result.stdout.trim()
    }
  }

  /**
   * 특정 언어 지원 여부 확인
   */
  fun isLanguageSupported(language: ScriptLanguage): Boolean {
    return try {
      val command = getInterpreterCommand(language)
      val process = ProcessBuilder(listOf(command, "--version"))
        .redirectErrorStream(true)
        .start()
      process.waitFor(5, TimeUnit.SECONDS)
      process.exitValue() == 0
    } catch (e: Exception) {
      kLogger.debug { "언어 지원 확인 실패 - language: $language, error: ${e.message}" }
      false
    }
  }

  // ========== Private Helpers ==========

  /**
   * 임시 스크립트 파일 생성
   */
  private fun createTempScript(language: ScriptLanguage, content: String): File {
    val extension = when (language) {
      ScriptLanguage.PYTHON -> ".py"
      ScriptLanguage.BASH -> ".sh"
      ScriptLanguage.JAVASCRIPT -> ".js"
      ScriptLanguage.TYPESCRIPT -> ".ts"
      ScriptLanguage.KOTLIN -> ".kts"
      ScriptLanguage.GO -> ".go"
      ScriptLanguage.JAVA -> ".java"
      ScriptLanguage.GROOVY -> ".groovy"
      ScriptLanguage.JSHELL -> ".jsh"
    }

    val tempFile = Files.createTempFile("skill_script_", extension).toFile()
    tempFile.writeText(content)

    // Bash 스크립트는 실행 권한 필요 (Unix 계열)
    if (language == ScriptLanguage.BASH) {
      tempFile.setExecutable(true)
    }

    return tempFile
  }

  /**
   * 실행 명령어 구성
   */
  private fun buildCommand(language: ScriptLanguage, scriptFile: File): List<String> {
    val interpreter = getInterpreterCommand(language)
    // GO는 "go run file.go" 형태로 실행
    return when (language) {
      ScriptLanguage.GO -> listOf(interpreter, "run", scriptFile.absolutePath)
      else -> listOf(interpreter, scriptFile.absolutePath)
    }
  }

  /**
   * 언어별 인터프리터 명령어
   */
  private fun getInterpreterCommand(language: ScriptLanguage): String {
    return when (language) {
      ScriptLanguage.PYTHON -> detectPythonCommand()
      ScriptLanguage.BASH -> "bash"
      ScriptLanguage.JAVASCRIPT -> "node"
      ScriptLanguage.TYPESCRIPT -> "ts-node"
      ScriptLanguage.KOTLIN -> "kotlin"
      ScriptLanguage.GO -> "go"
      ScriptLanguage.JAVA -> "jbang"
      ScriptLanguage.GROOVY -> "jbang"
      ScriptLanguage.JSHELL -> "jshell"
    }
  }

  /**
   * Python 명령어 감지 (python3 우선, 없으면 python)
   */
  private fun detectPythonCommand(): String {
    return try {
      val process = ProcessBuilder(listOf("python3", "--version"))
        .redirectErrorStream(true)
        .start()
      if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) {
        "python3"
      } else {
        "python"
      }
    } catch (e: Exception) {
      "python"
    }
  }
}
