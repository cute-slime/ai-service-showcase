package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsRunItem
import com.jongmin.ai.core.backoffice.service.BoOpenHandsRunService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoOpenHandsRunController(
  private val openHandsRunService: BoOpenHandsRunService,
) : JController() {

  @GetMapping("/bo/open-hands-runs")
  fun findAllOpenHandsRun(
    @Parameter(name = "q", description = "타이틀")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoOpenHandsRunItem> = openHandsRunService.findAll(q, jPageable(pageable))
}
