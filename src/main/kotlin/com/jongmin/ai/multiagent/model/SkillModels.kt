package com.jongmin.ai.multiagent.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 에이전트 스킬 정의
 * 재사용 가능한 능력 단위
 */
data class AgentSkill(
  val id: String = "",                     // "web-search", "rag-retrieval"
  val name: String = "",                   // "웹 검색"
  val description: String = "",            // 스킬 설명 (LLM 판단용)

  // 트리거 설정
  val triggerConfig: SkillTriggerConfig = SkillTriggerConfig(),

  // 입출력 스키마 (선택적, JSON Schema 형태)
  val inputSchema: Map<String, Any>? = null,
  val outputSchema: Map<String, Any>? = null,

  // 실행기 설정
  val executorType: String = "CUSTOM",     // "WEB_SEARCH", "RAG", "JSON_SCHEMA", "CUSTOM"
  val executorConfig: Map<String, Any>? = null,

  // 메타데이터
  val version: String = "1.0",
  val tags: List<String>? = null,
)

/**
 * 스킬 트리거 설정
 */
data class SkillTriggerConfig(
  // 키워드 기반 트리거
  val triggerKeywords: List<String> = emptyList(),      // ["검색", "찾아", "조사"]
  val triggerPatterns: List<String>? = null,            // 정규식 패턴

  // 조건 기반 트리거
  val conditions: List<SkillCondition>? = null,

  // 트리거 모드
  val triggerMode: SkillTriggerMode = SkillTriggerMode.ON_DEMAND,

  // 트리거 우선순위 (같은 조건에서 여러 스킬 매칭 시)
  val priority: Int = 0,
)

/**
 * 스킬 트리거 모드
 */
enum class SkillTriggerMode(private val typeCode: Int) {
  AUTO(1),          // 조건 충족 시 자동 실행
  ON_DEMAND(2),     // 에이전트가 명시적으로 호출
  ALWAYS(3),        // 항상 실행 (전처리/후처리)
  PRE_PROCESS(4),   // 에이전트 실행 전 항상 실행
  POST_PROCESS(5),  // 에이전트 실행 후 항상 실행
  ;

  companion object {
    private val map = entries.associateBy(SkillTriggerMode::typeCode)

    @JsonCreator
    fun getType(value: Int): SkillTriggerMode = map[value] ?: ON_DEMAND
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}

/**
 * 스킬 조건
 */
data class SkillCondition(
  val type: ConditionType = ConditionType.CUSTOM,
  val field: String? = null,              // 검사할 필드 (JSON Path 형태)
  val operator: String = "equals",        // "contains", "equals", "gt", "lt", "regex"
  val value: Any? = null,
)

/**
 * 조건 타입
 */
enum class ConditionType(private val typeCode: Int) {
  INPUT_CONTAINS(1),      // 입력에 특정 내용 포함
  INPUT_FIELD(2),         // 입력의 특정 필드 값 검사
  CONTEXT_HAS(3),         // 컨텍스트에 특정 키 존재
  CONTEXT_FIELD(4),       // 컨텍스트의 특정 필드 값 검사
  OUTPUT_MISSING(5),      // 출력에 특정 필드 누락
  SCORE_BELOW(6),         // Self-Evaluation 점수 미달
  RETRY_COUNT(7),         // 재시도 횟수 조건
  PREVIOUS_AGENT_FAILED(8), // 이전 에이전트 실패
  CUSTOM(99),             // 커스텀 조건 (SpEL 표현식 등)
  ;

  companion object {
    private val map = entries.associateBy(ConditionType::typeCode)

    @JsonCreator
    fun getType(value: Int): ConditionType = map[value] ?: CUSTOM
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
