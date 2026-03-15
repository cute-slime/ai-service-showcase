package com.jongmin.ai.multiagent.skill.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.multiagent.skill.dto.*
import com.jongmin.ai.multiagent.skill.service.SkillDefinitionService
import org.springframework.web.bind.annotation.*

/**
 * 스킬 정의 Platform API
 * 에이전트 실행 시 스킬 컨텍스트 조회용
 *
 * 3단계 Context Loading:
 * 1. Discovery - name + description (~100 tokens)
 * 2. Activation - 전체 instructions (<5000 tokens)
 * 3. Execution - scripts, references 개별 조회
 */
@RestController
@RequestMapping("/v1.0")
class SkillDefinitionController(
  private val skillService: SkillDefinitionService,
) : JController() {

  /**
   * 에이전트용 스킬 Discovery 정보 조회
   * Phase 1: Discovery (~100 tokens)
   *
   * 여러 스킬의 기본 정보만 조회하여 LLM이 적절한 스킬 선택 가능
   */
  @GetMapping("/skills/discovery")
  fun getDiscoveryInfo(
    @RequestParam skillIds: List<String>,
  ): List<SkillDiscoveryResponse> {
    return skillService.getDiscoveryInfo(session!!, skillIds)
  }

  /**
   * 에이전트용 스킬 Activation 정보 조회
   * Phase 2: Activation (<5000 tokens)
   *
   * 선택된 스킬의 전체 instructions 로드
   */
  @GetMapping("/skills/{skillId}/activation")
  fun getActivationContext(
    @PathVariable skillId: String,
  ): SkillActivationResponse {
    return skillService.getActivationContext(session!!, skillId)
  }

  /**
   * 스킬 스크립트 조회 (실행용)
   * Phase 3: Execution
   */
  @GetMapping("/skills/{skillId}/scripts/{filename}")
  fun getScriptForExecution(
    @PathVariable skillId: String,
    @PathVariable filename: String,
  ): SkillScriptExecutionResponse {
    return skillService.getScriptForExecution(session!!, skillId, filename)
  }

  /**
   * 스킬 참조문서 조회 (실행용)
   * Phase 3: Execution
   */
  @GetMapping("/skills/{skillId}/references/{filename}")
  fun getReferenceForExecution(
    @PathVariable skillId: String,
    @PathVariable filename: String,
  ): SkillReferenceExecutionResponse {
    return skillService.getReferenceForExecution(session!!, skillId, filename)
  }

  // ========== 에이전트 기반 스킬 조회 ==========

  /**
   * 에이전트에 할당된 스킬의 Discovery 정보 조회
   * 에이전트 실행 시 사용 가능한 스킬 목록 제공
   */
  @GetMapping("/skills/agents/{agentId}/discovery")
  fun getAgentSkillsDiscovery(
    @PathVariable agentId: Long,
  ): List<AgentSkillDiscoveryResponse> {
    return skillService.getAgentSkillsDiscovery(session!!, agentId)
  }

  /**
   * 에이전트에 할당된 스킬 ID 목록 조회 (간단 버전)
   */
  @GetMapping("/skills/agents/{agentId}/ids")
  fun getAgentSkillIds(
    @PathVariable agentId: Long,
  ): List<String> {
    return skillService.getAgentSkillIds(session!!, agentId)
  }
}
