package com.jongmin.ai.multiagent.skill.converter

import com.fasterxml.jackson.annotation.JsonInclude
import com.jongmin.ai.multiagent.skill.model.LlmTriggerConfig
import com.jongmin.ai.multiagent.skill.model.SkillAsset
import com.jongmin.ai.multiagent.skill.model.SkillCompatibility
import com.jongmin.ai.multiagent.skill.model.SkillReference
import com.jongmin.ai.multiagent.skill.model.SkillScript
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import tools.jackson.core.JacksonException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 정의 관련 JPA Converter 모음
 *
 * @deprecated PostgreSQL JSON 타입과 호환되지 않음.
 *             Hibernate 7의 @JdbcTypeCode(SqlTypes.JSON) 사용 권장.
 *             이 컨버터들은 AttributeConverter<T, String>을 구현하여 String을 반환하지만,
 *             PostgreSQL JSON 컬럼은 실제 JSON 타입을 기대하므로 타입 불일치 오류 발생.
 */

// 공통 ObjectMapper 빌더
private fun createObjectMapper(): ObjectMapper = JsonMapper.builder()
  .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
  .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
  .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
  .build()

/**
 * Map<String, SkillScript> ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class SkillScriptsConverter : AttributeConverter<Map<String, SkillScript>, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: Map<String, SkillScript>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyMap<String, SkillScript>())
    } catch (ex: JacksonException) {
      kLogger.warn { "SkillScripts 직렬화 실패: ${ex.message}" }
      "{}"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): Map<String, SkillScript> {
    if (dbData.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<Map<String, SkillScript>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "SkillScripts 역직렬화 실패: ${ex.message}" }
      emptyMap()
    }
  }
}

/**
 * Map<String, SkillReference> ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class SkillReferencesConverter : AttributeConverter<Map<String, SkillReference>, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: Map<String, SkillReference>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyMap<String, SkillReference>())
    } catch (ex: JacksonException) {
      kLogger.warn { "SkillReferences 직렬화 실패: ${ex.message}" }
      "{}"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): Map<String, SkillReference> {
    if (dbData.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<Map<String, SkillReference>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "SkillReferences 역직렬화 실패: ${ex.message}" }
      emptyMap()
    }
  }
}

/**
 * Map<String, SkillAsset> ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class SkillAssetsConverter : AttributeConverter<Map<String, SkillAsset>, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: Map<String, SkillAsset>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyMap<String, SkillAsset>())
    } catch (ex: JacksonException) {
      kLogger.warn { "SkillAssets 직렬화 실패: ${ex.message}" }
      "{}"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): Map<String, SkillAsset> {
    if (dbData.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<Map<String, SkillAsset>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "SkillAssets 역직렬화 실패: ${ex.message}" }
      emptyMap()
    }
  }
}

/**
 * SkillCompatibility ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class SkillCompatibilityConverter : AttributeConverter<SkillCompatibility?, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: SkillCompatibility?): String? {
    if (attribute == null) return null
    return try {
      objectMapper.writeValueAsString(attribute)
    } catch (ex: JacksonException) {
      kLogger.warn { "SkillCompatibility 직렬화 실패: ${ex.message}" }
      null
    }
  }

  override fun convertToEntityAttribute(dbData: String?): SkillCompatibility? {
    if (dbData.isNullOrBlank()) return null
    return try {
      objectMapper.readValue(dbData, SkillCompatibility::class.java)
    } catch (ex: Exception) {
      kLogger.warn { "SkillCompatibility 역직렬화 실패: ${ex.message}" }
      null
    }
  }
}

/**
 * List<String> ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class StringListConverter : AttributeConverter<List<String>?, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: List<String>?): String? {
    if (attribute == null) return null
    return try {
      objectMapper.writeValueAsString(attribute)
    } catch (ex: JacksonException) {
      kLogger.warn { "StringList 직렬화 실패: ${ex.message}" }
      null
    }
  }

  override fun convertToEntityAttribute(dbData: String?): List<String>? {
    if (dbData.isNullOrBlank()) return null
    return try {
      objectMapper.readValue(dbData, object : TypeReference<List<String>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "StringList 역직렬화 실패: ${ex.message}" }
      null
    }
  }
}

/**
 * Map<String, Any> ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class MapToJsonConverter : AttributeConverter<Map<String, Any>, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: Map<String, Any>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyMap<String, Any>())
    } catch (ex: JacksonException) {
      kLogger.warn { "Map 직렬화 실패: ${ex.message}" }
      "{}"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): Map<String, Any> {
    if (dbData.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<Map<String, Any>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "Map 역직렬화 실패: ${ex.message}" }
      emptyMap()
    }
  }
}

/**
 * LlmTriggerConfig ↔ JSON 변환
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class LlmTriggerConfigConverter : AttributeConverter<LlmTriggerConfig?, String> {

  private val objectMapper = createObjectMapper()

  override fun convertToDatabaseColumn(attribute: LlmTriggerConfig?): String? {
    if (attribute == null) return null
    return try {
      objectMapper.writeValueAsString(attribute)
    } catch (ex: JacksonException) {
      kLogger.warn { "LlmTriggerConfig 직렬화 실패: ${ex.message}" }
      null
    }
  }

  override fun convertToEntityAttribute(dbData: String?): LlmTriggerConfig? {
    if (dbData.isNullOrBlank()) return null
    return try {
      objectMapper.readValue(dbData, LlmTriggerConfig::class.java)
    } catch (ex: Exception) {
      kLogger.warn { "LlmTriggerConfig 역직렬화 실패: ${ex.message}" }
      null
    }
  }
}
