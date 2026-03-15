package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.backoffice.dto.request.BoOpenHandsCreateConversation
import com.jongmin.ai.core.backoffice.dto.response.CamelCaseOpenHandsRepositoryItem
import com.jongmin.ai.core.backoffice.service.BoOpenHandsService
import com.jongmin.ai.core.platform.entity.OpenHandsRun
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoOpenHandsServiceController(
  private val openHandsService: BoOpenHandsService
) : JController() {

  @GetMapping("/bo/open-hands/repositories")
  fun findAllGitRepositories(): List<CamelCaseOpenHandsRepositoryItem> {
    return openHandsService.findAllRepository().map { CamelCaseOpenHandsRepositoryItem.of(it) }.toList()
  }

  @GetMapping("/bo/open-hands/agent-status")
  fun isEnabled(): CommonDto.JApiResponse<String> {
    return CommonDto.JApiResponse.of(if (openHandsService.isAgentEnabled()) "enabled" else "disabled")
  }

  @PostMapping("/bo/open-hands/enables")
  fun enables(): CommonDto.JApiResponse<Boolean> {
    openHandsService.enableAgent()
    return CommonDto.JApiResponse.TRUE
  }

  @PostMapping("/bo/open-hands/disables")
  fun disables(): CommonDto.JApiResponse<Boolean> {
    openHandsService.disableAgent()
    return CommonDto.JApiResponse.TRUE
  }

  //  // For TEST:------------------------------------------------------------
  @PostMapping("/bo/open-hands/conversations")
  fun createConversation(@Valid @RequestBody dto: BoOpenHandsCreateConversation): CommonDto.JApiResponse<OpenHandsRun?> {
    return CommonDto.JApiResponse.of(openHandsService.createConversation(dto))
  }

  @PostMapping("/bo/open-hands/tasks/run")
  fun runIfAvailableTaskHas(): CommonDto.JApiResponse<Boolean> {
    openHandsService.runIfAvailableTaskHas()
    return CommonDto.JApiResponse.TRUE
  }

//  @GetMapping("/bo/open-hands/health")
//  fun health(): CommonDto.JApiResponse<Boolean> {
//    return CommonDto.JApiResponse.of(openHandsService.isOpenHandsHealthy(true))
//  }
//
//  @GetMapping("/bo/open-hands/conversations/running")
//  fun conversations(): CommonDto.JApiResponse<Boolean> {
//    return CommonDto.JApiResponse.of(openHandsService.hasRunningTask())
//  }
//
//  @GetMapping("/bo/open-hands/tasks")
//  fun tasks(): List<BoOpenHandsTaskItem> = openHandsService.availableTasks()
//
//  @GetMapping("/bo/open-hands/conversations/{conversationId}/trajectories")
//  fun trajectories(@PathVariable conversationId: String): BoOpenHandsTrajectoryResponse? = openHandsService.trajectories(conversationId)
}
