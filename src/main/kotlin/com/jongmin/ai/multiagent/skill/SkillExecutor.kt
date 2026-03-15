package com.jongmin.ai.multiagent.skill

import com.jongmin.ai.multiagent.executor.MultiAgentExecutionContext
import com.jongmin.ai.multiagent.model.AgentSkill
import com.jongmin.ai.multiagent.model.SkillExecutionResult
import com.jongmin.ai.multiagent.model.SkillInvocationReason

/**
 * 스킬 실행기 인터페이스
 * 각 스킬 타입별로 구현체 생성
 */
interface SkillExecutor {

  /**
   * 이 실행기가 처리하는 스킬 타입
   */
  val skillType: String

  /**
   * 스킬 실행 가능 여부 판단
   */
  fun canExecute(skill: AgentSkill, context: SkillExecutionContext): Boolean

  /**
   * 스킬 실행
   */
  fun execute(skill: AgentSkill, context: SkillExecutionContext): SkillExecutionResult
}

/**
 * 스킬 실행 컨텍스트
 */
data class SkillExecutionContext(
  val agentId: String,                              // 실행 중인 에이전트 ID
  val input: Any,                                   // 에이전트 입력
  val previousOutput: Any?,                         // 이전 출력 (POST_PROCESS용)
  val executionContext: MultiAgentExecutionContext, // 전체 실행 컨텍스트
  val invocationReason: SkillInvocationReason,      // 호출 사유
)
