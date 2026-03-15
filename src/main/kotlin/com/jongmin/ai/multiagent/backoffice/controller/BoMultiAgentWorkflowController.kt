package com.jongmin.ai.multiagent.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.multiagent.backoffice.dto.*
import com.jongmin.ai.multiagent.backoffice.service.BoMultiAgentWorkflowService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * 멀티 에이전트 워크플로우 백오피스 API
 *
 * 워크플로우 CRUD, 에이전트 노드 관리, 오케스트레이터 설정
 */
@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@RestController
@RequestMapping("/v1.0/bo/multi-agent/workflows")
class BoMultiAgentWorkflowController(
  private val objectMapper: ObjectMapper,
  private val workflowService: BoMultiAgentWorkflowService,
) : JController() {

  /**
   * 워크플로우 목록 조회
   */
  @GetMapping
  fun list(
    @RequestParam(required = false) accountId: Long?,
    @PageableDefault(sort = ["id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable,
  ): Page<BoWorkflowListResponse> {
    return workflowService.list(session!!, accountId, jPageable(pageable))
  }

  /**
   * 워크플로우 상세 조회
   */
  @GetMapping("/{id}")
  fun get(@PathVariable id: Long): BoWorkflowDetailResponse {
    return workflowService.get(session!!, id)
  }

  /**
   * 워크플로우 생성
   */
  @PostMapping
  fun create(@Valid @RequestBody request: BoCreateWorkflowRequest): BoWorkflowDetailResponse {
    return workflowService.create(session!!, request)
  }

  /**
   * 워크플로우 수정 (PATCH)
   */
  @PatchMapping("/{id}")
  fun patch(
    @PathVariable id: Long,
    @Valid @RequestBody request: BoPatchWorkflowRequest,
  ): Map<String, Any?> {
    request.id = id
    return workflowService.patch(
      session!!,
      objectMapper.convertValue(request, object : TypeReference<Map<String, Any>>() {})
    )
  }

  /**
   * 워크플로우 삭제
   */
  @DeleteMapping("/{id}")
  fun delete(@PathVariable id: Long): CommonDto.JApiResponse<Boolean> {
    return CommonDto.JApiResponse(data = workflowService.delete(session!!, id))
  }

  /**
   * 에이전트 노드 추가
   */
  @PostMapping("/{id}/agents")
  fun addAgent(
    @PathVariable id: Long,
    @Valid @RequestBody request: BoAddAgentRequest,
  ): BoWorkflowDetailResponse {
    return workflowService.addAgent(session!!, id, request)
  }

  /**
   * 에이전트 노드 제거
   */
  @DeleteMapping("/{id}/agents/{agentNodeId}")
  fun removeAgent(
    @PathVariable id: Long,
    @PathVariable agentNodeId: String,
  ): BoWorkflowDetailResponse {
    return workflowService.removeAgent(session!!, id, agentNodeId)
  }

  /**
   * 에이전트 연결(Edge) 추가
   */
  @PostMapping("/{id}/edges")
  fun addEdge(
    @PathVariable id: Long,
    @Valid @RequestBody request: BoAddEdgeRequest,
  ): BoWorkflowDetailResponse {
    return workflowService.addEdge(session!!, id, request)
  }

  /**
   * 에이전트 연결(Edge) 제거
   */
  @DeleteMapping("/{id}/edges/{edgeId}")
  fun removeEdge(
    @PathVariable id: Long,
    @PathVariable edgeId: String,
  ): BoWorkflowDetailResponse {
    return workflowService.removeEdge(session!!, id, edgeId)
  }

  /**
   * 오케스트레이터 설정 업데이트
   */
  @PutMapping("/{id}/orchestrator-config")
  fun updateOrchestratorConfig(
    @PathVariable id: Long,
    @Valid @RequestBody request: BoOrchestratorConfigRequest,
  ): BoWorkflowDetailResponse {
    return workflowService.updateOrchestratorConfig(session!!, id, request)
  }
}
