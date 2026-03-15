package com.jongmin.ai.multiagent.model

/**
 * 평가 지표 (에이전트가 생성)
 */
data class EvaluationCriteria(
  val criteria: List<Criterion>,
  val passThreshold: Double = 0.7,
  val expectedOutcome: String? = null,
)

/**
 * 개별 평가 기준
 */
data class Criterion(
  val name: String,           // "시나리오 완성도"
  val description: String,    // "시작-중간-끝 구조를 갖추고 있는가"
  val weight: Double = 1.0,
)

/**
 * 자체 평가 결과
 */
data class SelfEvaluation(
  val overallScore: Double,                  // 0.0 ~ 1.0 종합 점수
  val criteriaScores: Map<String, Double>,   // 기준별 점수
  val reasoning: String,                     // 평가 근거
  val confidence: Double = 1.0,              // 확신도
  val suggestions: List<String>? = null,     // 개선 제안 (재시도 시 활용)
)

/**
 * 에이전트 실행 결과
 */
data class AgentExecutionResult(
  val output: Any,                                 // 실제 결과물
  val evaluationCriteria: EvaluationCriteria,      // 자체 평가 지표
  val selfEvaluation: SelfEvaluation?,             // 자체 평가 결과
)
