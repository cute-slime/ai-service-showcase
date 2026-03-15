package com.jongmin.ai.multiagent.converter

import com.fasterxml.jackson.annotation.JsonInclude
import com.jongmin.ai.multiagent.model.AgentEdge
import com.jongmin.ai.multiagent.model.MultiAgentNode
import com.jongmin.ai.multiagent.model.OrchestratorConfig
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
 * MultiAgentNode 리스트를 JSON 문자열로 변환하는 Converter
 *
 * @deprecated PostgreSQL JSON 타입과 호환되지 않음.
 *             Hibernate 7의 @JdbcTypeCode(SqlTypes.JSON) 사용 권장.
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class MultiAgentNodeListConverter : AttributeConverter<List<MultiAgentNode>, String> {

  val objectMapper: ObjectMapper = JsonMapper.builder()
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

  override fun convertToDatabaseColumn(attribute: List<MultiAgentNode>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyList<MultiAgentNode>())
    } catch (ex: JacksonException) {
      kLogger.warn { "MultiAgentNode 직렬화 실패: ${ex.message}" }
      "[]"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): List<MultiAgentNode> {
    if (dbData.isNullOrBlank()) return emptyList()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<List<MultiAgentNode>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "MultiAgentNode 역직렬화 실패: $dbData, message: ${ex.message}" }
      emptyList()
    }
  }
}

/**
 * AgentEdge 리스트를 JSON 문자열로 변환하는 Converter
 *
 * @deprecated PostgreSQL JSON 타입과 호환되지 않음.
 *             Hibernate 7의 @JdbcTypeCode(SqlTypes.JSON) 사용 권장.
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class AgentEdgeListConverter : AttributeConverter<List<AgentEdge>, String> {

  val objectMapper: ObjectMapper = JsonMapper.builder()
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

  override fun convertToDatabaseColumn(attribute: List<AgentEdge>?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: emptyList<AgentEdge>())
    } catch (ex: JacksonException) {
      kLogger.warn { "AgentEdge 직렬화 실패: ${ex.message}" }
      "[]"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): List<AgentEdge> {
    if (dbData.isNullOrBlank()) return emptyList()
    return try {
      objectMapper.readValue(dbData, object : TypeReference<List<AgentEdge>>() {})
    } catch (ex: Exception) {
      kLogger.warn { "AgentEdge 역직렬화 실패: $dbData, message: ${ex.message}" }
      emptyList()
    }
  }
}

/**
 * OrchestratorConfig를 JSON 문자열로 변환하는 Converter
 *
 * @deprecated PostgreSQL JSON 타입과 호환되지 않음.
 *             Hibernate 7의 @JdbcTypeCode(SqlTypes.JSON) 사용 권장.
 */
@Deprecated("@JdbcTypeCode(SqlTypes.JSON) 사용 권장", ReplaceWith("@JdbcTypeCode(SqlTypes.JSON)"))
@Converter
class OrchestratorConfigConverter : AttributeConverter<OrchestratorConfig, String> {

  val objectMapper: ObjectMapper = JsonMapper.builder()
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

  override fun convertToDatabaseColumn(attribute: OrchestratorConfig?): String {
    return try {
      objectMapper.writeValueAsString(attribute ?: OrchestratorConfig())
    } catch (ex: JacksonException) {
      kLogger.warn { "OrchestratorConfig 직렬화 실패: ${ex.message}" }
      "{}"
    }
  }

  override fun convertToEntityAttribute(dbData: String?): OrchestratorConfig {
    if (dbData.isNullOrBlank()) return OrchestratorConfig()
    return try {
      objectMapper.readValue(dbData, OrchestratorConfig::class.java)
    } catch (ex: Exception) {
      kLogger.warn { "OrchestratorConfig 역직렬화 실패: $dbData, message: ${ex.message}" }
      OrchestratorConfig()
    }
  }
}
