package com.jongmin.ai.multiagent.skill.model

/**
 * 스킬 트리거 전략
 * 스킬이 언제 실행될지 판단하는 방식 결정
 */
enum class TriggerStrategy {
  /**
   * 규칙 기반 (기본값)
   * - 키워드 매칭
   * - 정규식 패턴 매칭
   * - 조건 평가
   * - 빠름, 비용 없음, 예측 가능
   */
  RULE_BASED,

  /**
   * LLM 기반
   * - LLM이 문맥을 이해하고 스킬 실행 필요 여부 판단
   * - 유연함, 복잡한 판단 가능
   * - 토큰 비용 발생, 상대적으로 느림
   */
  LLM_BASED,

  /**
   * 하이브리드
   * - 규칙 먼저 체크 → 매칭 안되면 LLM 판단
   * - 비용 효율적이면서 유연함
   * - 대부분의 케이스에 권장
   */
  HYBRID,
}
