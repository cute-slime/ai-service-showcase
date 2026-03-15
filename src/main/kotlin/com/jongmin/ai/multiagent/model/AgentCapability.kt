package com.jongmin.ai.multiagent.model

/**
 * 에이전트 역량 메타데이터
 * 오케스트레이터가 "이 에이전트가 이 일에 적합한가?" 판단에 사용
 */
data class AgentCapability(
  // 기본 정보
  val summary: String = "",                    // "복잡한 스토리 시나리오를 생성"

  // 트리거 조건 (오케스트레이터 판단용)
  val triggerKeywords: List<String> = emptyList(),  // ["시나리오", "스토리", "플롯"]
  val triggerPatterns: List<String>? = null,   // 정규식 패턴 (선택)
  val triggerDescription: String? = null,      // "스토리/시나리오 생성 요청 시"

  // 입출력 정의
  val inputTypes: List<String> = emptyList(),  // ["text", "context"]
  val outputTypes: List<String> = emptyList(), // ["scenario_json", "text"]

  // 우선순위 & 제약
  val priority: Int = 0,                       // 매칭 시 우선순위
  val requiredContext: List<String>? = null,   // 필요한 선행 컨텍스트
  val exclusiveWith: List<String>? = null,     // 동시 실행 불가 에이전트
)
