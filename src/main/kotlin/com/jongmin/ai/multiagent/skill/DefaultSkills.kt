package com.jongmin.ai.multiagent.skill

import com.jongmin.ai.multiagent.model.*

/**
 * 기본 제공 스킬 정의
 */
object DefaultSkills {

  /**
   * 웹 검색 스킬
   */
  val WEB_SEARCH = AgentSkill(
    id = "web-search",
    name = "웹 검색",
    description = "인터넷에서 최신 정보를 검색합니다. 실시간 정보, 뉴스, 최신 트렌드 조회에 유용합니다.",
    triggerConfig = SkillTriggerConfig(
      triggerKeywords = listOf("검색", "찾아", "최신", "현재", "뉴스", "트렌드"),
      triggerMode = SkillTriggerMode.ON_DEMAND
    ),
    executorType = "WEB_SEARCH",
    tags = listOf("search", "internet", "realtime")
  )

  /**
   * RAG 문서 검색 스킬
   */
  val RAG_RETRIEVAL = AgentSkill(
    id = "rag-retrieval",
    name = "문서 검색",
    description = "내부 문서 저장소에서 관련 정보를 검색합니다. 회사 문서, 매뉴얼, 가이드 조회에 유용합니다.",
    triggerConfig = SkillTriggerConfig(
      triggerKeywords = listOf("문서", "자료", "참고", "매뉴얼", "가이드"),
      triggerMode = SkillTriggerMode.ON_DEMAND
    ),
    executorType = "RAG",
    tags = listOf("search", "document", "internal")
  )

  /**
   * JSON 스키마 생성 스킬
   */
  val JSON_SCHEMA_GENERATOR = AgentSkill(
    id = "json-schema-generator",
    name = "JSON 스키마 생성",
    description = "구조화된 JSON 출력을 위한 스키마를 생성합니다. 출력 형식 표준화에 유용합니다.",
    triggerConfig = SkillTriggerConfig(
      triggerMode = SkillTriggerMode.PRE_PROCESS  // 에이전트 실행 전 항상 실행
    ),
    executorType = "JSON_SCHEMA",
    tags = listOf("format", "schema", "output")
  )

  /**
   * 출력 검증 스킬
   */
  val OUTPUT_VALIDATOR = AgentSkill(
    id = "output-validator",
    name = "출력 검증",
    description = "에이전트 출력이 예상 스키마와 일치하는지 검증합니다.",
    triggerConfig = SkillTriggerConfig(
      triggerMode = SkillTriggerMode.POST_PROCESS  // 에이전트 실행 후 항상 실행
    ),
    executorType = "OUTPUT_VALIDATOR",
    tags = listOf("validation", "schema", "quality")
  )

  /**
   * 컨텍스트 요약 스킬
   */
  val CONTEXT_SUMMARIZER = AgentSkill(
    id = "context-summarizer",
    name = "컨텍스트 요약",
    description = "이전 에이전트들의 출력을 요약하여 현재 에이전트에게 전달합니다.",
    triggerConfig = SkillTriggerConfig(
      conditions = listOf(
        SkillCondition(
          type = ConditionType.CONTEXT_FIELD,
          field = "previousOutputs.size",
          operator = "gt",
          value = 2  // 이전 출력이 2개 초과일 때
        )
      ),
      triggerMode = SkillTriggerMode.AUTO
    ),
    executorType = "CONTEXT_SUMMARIZER",
    tags = listOf("context", "summary", "optimization")
  )

  /**
   * 모든 기본 스킬 목록
   */
  val ALL = listOf(
    WEB_SEARCH,
    RAG_RETRIEVAL,
    JSON_SCHEMA_GENERATOR,
    OUTPUT_VALIDATOR,
    CONTEXT_SUMMARIZER
  )
}
