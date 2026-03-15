package com.jongmin.ai.multiagent.skill.executor

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.SkillExecutionMetadata
import com.jongmin.ai.multiagent.model.SkillExecutionResult
import com.jongmin.ai.multiagent.skill.SkillExecutionContext
import com.jongmin.ai.multiagent.skill.SkillExecutor
import com.jongmin.ai.multiagent.skill.context.SkillContextProvider
import com.jongmin.ai.multiagent.skill.runner.ScriptRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val kLogger = KotlinLogging.logger {}

/**
 * 스크립트 기반 스킬 실행기
 * scripts/ 폴더의 스크립트를 실행하여 스킬 수행
 */
@Component
class ScriptBasedSkillExecutor(
  private val scriptRunner: ScriptRunner,
  private val skillContextProvider: SkillContextProvider,
  private val objectMapper: ObjectMapper,
) : SkillExecutor {

  override val skillType: String = SKILL_TYPE_SCRIPT

  /**
   * 스킬 실행 가능 여부 판단
   * scripts가 있고, entrypoint 스크립트가 있으면 실행 가능
   */
  override fun canExecute(skill: AgentSkill, context: SkillExecutionContext): Boolean {
    val entryScript = skillContextProvider.getEntrypointScript(skill.id)
    return entryScript != null
  }

  /**
   * 스킬 실행
   */
  override fun execute(skill: AgentSkill, context: SkillExecutionContext): SkillExecutionResult {
    kLogger.info { "스크립트 기반 스킬 실행 - skillId: ${skill.id}" }

    val startTime = System.currentTimeMillis()

    // 1. Entrypoint 스크립트 조회
    val entryScript = skillContextProvider.getEntrypointScript(skill.id)
    if (entryScript == null) {
      kLogger.warn { "Entrypoint 스크립트 없음 - skillId: ${skill.id}" }
      return SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = "No entrypoint script found for skill: ${skill.id}",
      )
    }

    // 2. 환경 변수 구성
    val env = buildEnv(skill, context)

    // 3. 스크립트 실행
    return try {
      val result = scriptRunner.run(
        language = entryScript.language,
        content = entryScript.content,
        input = context.input,
        env = env,
      )

      val durationMs = System.currentTimeMillis() - startTime

      if (result.success) {
        kLogger.info {
          "스킬 실행 성공 - skillId: ${skill.id}, durationMs: $durationMs"
        }

        SkillExecutionResult(
          skillId = skill.id,
          success = true,
          output = parseOutput(result.stdout),
          error = null,
          metadata = SkillExecutionMetadata(
            executedAt = Instant.now(),
            durationMs = durationMs,
            costIncurred = null,
            invocationReason = context.invocationReason,
            inputSummary = context.input.toString().take(INPUT_SUMMARY_MAX_LENGTH),
            outputSummary = result.stdout.take(OUTPUT_SUMMARY_MAX_LENGTH),
          )
        )
      } else {
        kLogger.warn {
          "스킬 실행 실패 - skillId: ${skill.id}, stderr: ${result.stderr.take(ERROR_LOG_MAX_LENGTH)}"
        }

        SkillExecutionResult(
          skillId = skill.id,
          success = false,
          output = null,
          error = result.stderr,
          metadata = SkillExecutionMetadata(
            executedAt = Instant.now(),
            durationMs = durationMs,
            invocationReason = context.invocationReason,
          )
        )
      }

    } catch (e: Exception) {
      kLogger.error(e) { "스킬 실행 예외 - skillId: ${skill.id}" }

      SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = e.message,
        metadata = SkillExecutionMetadata(
          executedAt = Instant.now(),
          durationMs = System.currentTimeMillis() - startTime,
          invocationReason = context.invocationReason,
        )
      )
    }
  }

  // ========== Private Helpers ==========

  /**
   * 환경 변수 구성
   */
  private fun buildEnv(skill: AgentSkill, context: SkillExecutionContext): Map<String, String> {
    val env = mutableMapOf<String, String>()

    // 기본 스킬 정보
    env["SKILL_ID"] = skill.id
    env["SKILL_NAME"] = skill.name
    env["AGENT_ID"] = context.agentId

    // 스킬 메타데이터
    skill.executorConfig?.forEach { (key, value) ->
      env["SKILL_${key.uppercase()}"] = value.toString()
    }

    return env
  }

  /**
   * 출력 파싱 (JSON 시도, 실패 시 문자열 그대로)
   */
  private fun parseOutput(stdout: String): Any {
    if (stdout.isBlank()) return emptyMap<String, Any>()

    return try {
      // JSON 파싱 시도
      val trimmed = stdout.trim()
      when {
        trimmed.startsWith("{") || trimmed.startsWith("[") -> {
          objectMapper.readValue(trimmed, Any::class.java)
        }
        else -> trimmed
      }
    } catch (e: Exception) {
      stdout.trim()
    }
  }

  companion object {
    // 스킬 타입 상수
    const val SKILL_TYPE_SCRIPT = "SCRIPT"

    // 요약 최대 길이
    private const val INPUT_SUMMARY_MAX_LENGTH = 100
    private const val OUTPUT_SUMMARY_MAX_LENGTH = 100
    private const val ERROR_LOG_MAX_LENGTH = 200
  }
}
