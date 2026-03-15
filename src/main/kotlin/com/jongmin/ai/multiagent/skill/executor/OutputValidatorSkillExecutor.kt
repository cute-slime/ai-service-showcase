package com.jongmin.ai.multiagent.skill.executor

import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.SkillExecutionResult
import com.jongmin.ai.multiagent.skill.SkillExecutionContext
import com.jongmin.ai.multiagent.skill.SkillExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 출력 검증 스킬 실행기
 * 에이전트 출력이 예상 스키마와 일치하는지 검증
 */
@Component
class OutputValidatorSkillExecutor(
  private val objectMapper: ObjectMapper,
) : SkillExecutor {

  override val skillType: String = "OUTPUT_VALIDATOR"

  override fun canExecute(skill: AgentSkill, context: SkillExecutionContext): Boolean {
    // 검증할 출력이 있어야 함
    return context.previousOutput != null
  }

  override fun execute(skill: AgentSkill, context: SkillExecutionContext): SkillExecutionResult {
    kLogger.debug { "출력 검증 스킬 실행" }

    val output = context.previousOutput ?: return SkillExecutionResult(
      skillId = skill.id,
      success = false,
      output = null,
      error = "검증할 출력이 없습니다"
    )

    return try {
      // 스킬에 정의된 출력 스키마로 검증
      val expectedSchema = skill.outputSchema
      val validationResult = validateOutput(output, expectedSchema)

      SkillExecutionResult(
        skillId = skill.id,
        success = validationResult.isValid,
        output = mapOf(
          "valid" to validationResult.isValid,
          "errors" to validationResult.errors,
          "warnings" to validationResult.warnings
        )
      )
    } catch (e: Exception) {
      kLogger.error(e) { "출력 검증 스킬 실행 실패" }
      SkillExecutionResult(
        skillId = skill.id,
        success = false,
        output = null,
        error = e.message
      )
    }
  }

  /**
   * 출력 검증 수행
   */
  private fun validateOutput(output: Any, schema: Map<String, Any>?): ValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // 스키마가 없으면 기본 검증만 수행
    if (schema == null) {
      // 기본 검증: 빈 출력 체크
      if (output is Map<*, *> && output.isEmpty()) {
        errors.add("출력이 비어 있습니다")
      }
      if (output is String && output.isBlank()) {
        errors.add("출력 문자열이 비어 있습니다")
      }
      return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    // TODO: JSON Schema 기반 검증 구현
    // 현재는 기본 타입 검증만 수행
    val requiredFields = schema["required"] as? List<*>
    val properties = schema["properties"] as? Map<*, *>

    if (output is Map<*, *>) {
      // 필수 필드 검증
      requiredFields?.forEach { field ->
        if (!output.containsKey(field)) {
          errors.add("필수 필드 누락: $field")
        }
      }

      // 프로퍼티 타입 검증
      properties?.forEach { (fieldName, fieldSchema) ->
        val value = output[fieldName]
        if (value != null && fieldSchema is Map<*, *>) {
          val expectedType = fieldSchema["type"]?.toString()
          if (!checkType(value, expectedType)) {
            warnings.add("타입 불일치: $fieldName - expected: $expectedType, actual: ${value::class.simpleName}")
          }
        }
      }
    }

    return ValidationResult(errors.isEmpty(), errors, warnings)
  }

  /**
   * 타입 체크
   */
  private fun checkType(value: Any, expectedType: String?): Boolean {
    return when (expectedType) {
      "string" -> value is String
      "number", "integer" -> value is Number
      "boolean" -> value is Boolean
      "array" -> value is List<*>
      "object" -> value is Map<*, *>
      else -> true  // 알 수 없는 타입은 통과
    }
  }

  /**
   * 검증 결과
   */
  private data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
  )
}
