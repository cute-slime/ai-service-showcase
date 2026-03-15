package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.DetectionStatus
import com.jongmin.ai.core.backoffice.dto.request.UpdateCachingDetectionStatus
import com.jongmin.ai.core.backoffice.dto.response.BoCachingDetectionItem
import com.jongmin.ai.core.backoffice.dto.response.BoCachingDetectionStats
import com.jongmin.ai.core.backoffice.service.BoCachingDetectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * 캐싱 감지 로그 백오피스 컨트롤러
 *
 * 예상치 못한 캐싱 발생을 모니터링하고 관리하는 백오피스 API를 제공합니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoCachingDetectionController(
  private val boCachingDetectionService: BoCachingDetectionService
) : JController() {

  @Operation(
    summary = "(백오피스) 캐싱 감지 로그 단건 조회",
    description = """
        권한: ai("admin")
        예상치 못한 캐싱이 감지된 로그를 상세 조회합니다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/caching-detection/logs/{id}")
  fun findOne(
    @Parameter(description = "로그 ID")
    @PathVariable id: Long
  ): BoCachingDetectionItem {
    return boCachingDetectionService.findById(id)
  }

  @Operation(
    summary = "(백오피스) 캐싱 감지 로그 목록 조회",
    description = """
        권한: ai("admin")
        예상치 못한 캐싱이 감지된 로그 목록을 페이징하여 조회합니다.
        상태, 프로바이더, 모델로 필터링할 수 있습니다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/caching-detection/logs")
  fun findAll(
    @Parameter(description = "상태 필터 (PENDING, CONFIRMED_OK, CONFIRMED_ISSUE, IGNORED)")
    @RequestParam(required = false) statuses: Set<DetectionStatus>?,

    @Parameter(description = "프로바이더 필터")
    @RequestParam(required = false) provider: String?,

    @Parameter(description = "모델 필터")
    @RequestParam(required = false) model: String?,

    @PageableDefault(sort = ["detectedAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<BoCachingDetectionItem> {
    return boCachingDetectionService.findAll(statuses, provider, model, jPageable(pageable))
  }

  @Operation(
    summary = "(백오피스) 캐싱 감지 통계 조회",
    description = """
        권한: ai("admin")
        캐싱 감지 로그의 전체 통계를 조회합니다.
        상태별, 프로바이더별, 모델별 집계 정보를 제공합니다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/caching-detection/stats")
  fun getStats(): BoCachingDetectionStats {
    return boCachingDetectionService.getStats()
  }

  @Operation(
    summary = "(백오피스) 캐싱 감지 로그 상태 변경",
    description = """
        권한: ai("admin")
        캐싱 감지 로그의 처리 상태를 변경합니다.
        관리자 메모를 함께 기록할 수 있습니다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PutMapping("/bo/ai/caching-detection/logs/{id}/status")
  fun updateStatus(
    @Parameter(description = "로그 ID")
    @PathVariable id: Long,

    @Validated @RequestBody dto: UpdateCachingDetectionStatus
  ): BoCachingDetectionItem {
    val adminId = session!!.accountId
    return boCachingDetectionService.updateStatus(dto.copy(id = id), adminId)
  }

  @Operation(
    summary = "(백오피스) 캐싱 감지 로그 일괄 무시 처리",
    description = """
        권한: ai("admin")
        여러 캐싱 감지 로그를 일괄로 무시 처리합니다.
        PENDING 상태의 로그만 처리됩니다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/caching-detection/logs/bulk-ignore")
  fun bulkIgnore(
    @Parameter(description = "무시 처리할 로그 ID 목록")
    @RequestBody ids: List<Long>
  ): CommonDto.JApiResponse<Int> {
    val adminId = session!!.accountId
    val count = boCachingDetectionService.bulkIgnore(ids, adminId)
    return CommonDto.JApiResponse(data = count)
  }
}
