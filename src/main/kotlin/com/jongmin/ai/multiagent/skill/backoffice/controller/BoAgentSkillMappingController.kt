package com.jongmin.ai.multiagent.skill.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.multiagent.skill.backoffice.dto.*
import com.jongmin.ai.multiagent.skill.backoffice.service.BoAgentSkillMappingService
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 에이전트-스킬 매핑 백오피스 API
 *
 * 에이전트별 스킬 할당/수정/해제 관리
 */
@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@RestController
@RequestMapping("/v1.0/bo")
class BoAgentSkillMappingController(
  private val objectMapper: ObjectMapper,
  private val mappingService: BoAgentSkillMappingService,
) : JController() {

  /**
   * 에이전트의 스킬 매핑 목록 조회
   */
  @GetMapping("/agents/{agentId}/skills")
  fun list(@PathVariable agentId: Long): List<BoAgentSkillMappingResponse> {
    return mappingService.getAgentSkillMappings(session!!, agentId)
  }

  /**
   * 에이전트의 스킬 상세 목록 조회 (스킬 정보 포함)
   */
  @GetMapping("/agents/{agentId}/skills/details")
  fun listDetails(@PathVariable agentId: Long): List<BoAgentSkillDetailResponse> {
    return mappingService.getAgentSkillDetails(session!!, agentId)
  }

  /**
   * 에이전트에 스킬 할당
   */
  @PostMapping("/agents/{agentId}/skills")
  fun assign(
    @PathVariable agentId: Long,
    @Valid @RequestBody request: BoAssignSkillRequest,
  ): BoAgentSkillMappingResponse {
    return mappingService.assignSkill(session!!, agentId, request)
  }

  /**
   * 에이전트에 스킬 일괄 할당
   */
  @PostMapping("/agents/{agentId}/skills/batch")
  fun batchAssign(
    @PathVariable agentId: Long,
    @Valid @RequestBody request: BoBatchAssignSkillsRequest,
  ): List<BoAgentSkillMappingResponse> {
    return mappingService.batchAssignSkills(session!!, agentId, request)
  }

  /**
   * 스킬 매핑 수정 (PATCH)
   */
  @PatchMapping("/agents/{agentId}/skills/{mappingId}")
  fun patch(
    @PathVariable agentId: Long,
    @PathVariable mappingId: Long,
    @Valid @RequestBody dto: BoPatchSkillMappingRequest,
  ): Map<String, Any?> {
    dto.id = mappingId
    return mappingService.patchMapping(
      session!!,
      agentId,
      mappingId,
      objectMapper.convertValue(dto, object : TypeReference<Map<String, Any>>() {})
    )
  }

  /**
   * 스킬 할당 해제
   */
  @DeleteMapping("/agents/{agentId}/skills/{mappingId}")
  fun unassign(
    @PathVariable agentId: Long,
    @PathVariable mappingId: Long,
  ) {
    mappingService.unassignSkill(session!!, agentId, mappingId)
  }

  /**
   * 에이전트의 모든 스킬 할당 해제
   */
  @DeleteMapping("/agents/{agentId}/skills")
  fun unassignAll(@PathVariable agentId: Long) {
    mappingService.unassignAllSkills(session!!, agentId)
  }
}
