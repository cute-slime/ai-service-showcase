package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.backoffice.dto.response.BulkDeleteUuidResult
import com.jongmin.ai.core.platform.component.loop.LoopJobRecoveryService
import com.jongmin.ai.core.platform.component.loop.LoopJobService
import com.jongmin.ai.core.platform.dto.*
import com.jongmin.ai.core.platform.entity.LoopJobState
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * (백오피스) Loop Job 관리 컨트롤러
 *
 * 워크플로우 반복 실행 Job에 대한 백오피스 CRUD 및 제어 API를 제공한다.
 * - Job 생성/조회/목록/삭제
 * - Job 일시정지/재개/취소
 * - 좀비 Job 관리
 * - 시스템 상태 확인
 *
 * ### DTE(Distributed Task Executor) 통합 (2026.01.09):
 * - 모든 Loop Job은 DTE Job으로 등록되어 실행됨
 * - create/resume/restart 호출 시 DTE 큐에 Job이 등록됨
 * - pause/cancel은 Redis 플래그 방식으로 빠르게 감지됨
 * - LoopWorkflowExecutor가 DTE에서 Loop Job을 실행
 *
 * ### Loop Job ID와 DTE Job ID:
 * - Loop Job 생성 시 DTE Job ID를 Loop Job ID로 사용
 * - 두 ID가 동일하여 추적 및 관리가 용이함
 *
 * @author Claude Code
 * @since 2026.01.03
 * SSE 스트리밍은 backbone-service의 `/v1.0/bo/event-streams/{jobId}` 엔드포인트를 사용한다.
 */
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoLoopJobController(
  private val loopJobService: LoopJobService,
  private val loopJobRecoveryService: LoopJobRecoveryService,
) : JController() {

  // ========== Job CRUD ==========

  @Operation(
    summary = "(백오피스) Loop Job을 생성하고 DTE 큐에 등록한다.",
    description = """
            권한: ai("admin")
            워크플로우를 N회 반복 실행하는 Loop Job을 생성하고 DTE 큐에 등록한다.

            [주요 파라미터]
            - maxCount: 반복 횟수 (-1: 무한 반복)
            - delayMs: 반복 간 딜레이 (ms)
            - onError: 에러 발생 시 동작 (STOP, CONTINUE, RETRY)
            - maxRetries: 에러 시 재시도 횟수 (onError=RETRY일 때 적용)

            [DTE 통합]
            - Loop Job 생성 시 DTE Job도 함께 생성됨
            - DTE Job ID = Loop Job ID (동일한 UUID 사용)
            - DTE 큐에 등록되어 LoopWorkflowExecutor에서 실행됨
            - DTE의 우선순위/동시성/타임아웃 제어를 받음

            [중복 실행 방지]
            - 동일 워크플로우에 RUNNING 상태 Job이 있으면 생성 거부
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs")
  fun create(@Validated @RequestBody request: CreateLoopJobRequest): LoopJobResponse {
    return loopJobService.createAndExecute(request, session!!.deepCopy())
  }

  @Operation(
    summary = "(백오피스) Loop Job 상세 조회",
    description = """
            권한: ai("admin")
            특정 Loop Job의 상세 정보를 조회한다.
            체크포인트 정보와 진행률도 함께 반환한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/loop-jobs/{jobId}")
  fun findOne(@PathVariable jobId: String): LoopJobResponse {
    return loopJobService.findById(jobId)
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job을 찾을 수 없습니다: $jobId")
  }

  @Operation(
    summary = "(백오피스) Loop Job 삭제",
    description = """
            권한: ai("admin")
            Loop Job을 삭제한다.

            [삭제 가능 상태]
            - PENDING: 대기 중 (DTE 큐 대기)
            - PAUSED: 일시 중지됨
            - COMPLETED: 완료됨
            - CANCELLED: 취소됨
            - ERROR: 오류 발생

            [삭제 불가 상태]
            - RUNNING: 실행 중 → 먼저 pause 또는 cancel 필요
            - RECOVERING: 복구 중 → 먼저 cancel 필요

            [DTE 통합]
            - Loop Job 삭제 시 관련 DTE Job도 정리됨
            - Redis 플래그, 락, 실행 중 Set에서 제거
            - 체크포인트 및 Iteration 기록도 함께 삭제 (Hard Delete)
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai/loop-jobs/{jobId}")
  fun delete(@PathVariable jobId: String): LoopJobActionResponse {
    return loopJobService.delete(jobId)
  }

  @Operation(
    summary = "Loop Job 일괄 삭제",
    description = """
    여러 Loop Job을 한 번에 삭제합니다.

    - 최대 100개까지 동시 삭제 가능
    - Partial Success: 존재하고 삭제 가능한 Job만 삭제
    - RUNNING/RECOVERING 상태의 Job은 삭제 불가 (무시됨)
    - Hard Delete 방식: 관련 체크포인트, Iteration, Redis 데이터도 함께 삭제
    """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @DeleteMapping("/bo/ai/loop-jobs/bulk")
  fun bulkDelete(
    @Parameter(description = "삭제할 Loop Job ID (쉼표로 구분, UUID)", required = true, example = "uuid1,uuid2,uuid3")
    @RequestParam ids: String
  ): BulkDeleteUuidResult {
    val idList = ids.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
    return loopJobService.bulkDelete(idList)
  }

  @Operation(
    summary = "(백오피스) Loop Job 목록 조회",
    description = """
            권한: ai("admin")
            Loop Job 목록을 페이징하여 조회한다.
            - state: 단일 상태 필터
            - states: 복수 상태 필터 (state보다 우선)
            - accountId: 계정 ID 필터
            - workflowId: 워크플로우 ID 필터
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/loop-jobs")
  fun findAll(
    @RequestParam(required = false) state: LoopJobState?,
    @RequestParam(required = false) states: List<LoopJobState>?,
    @RequestParam(required = false) accountId: Long?,
    @RequestParam(required = false) workflowId: Long?,
    @PageableDefault(sort = ["createdAt"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<LoopJobResponse> {
    // 동적 필터링 조회
    return loopJobService.findByFilters(
      state = state,
      states = states,
      accountId = accountId,
      workflowId = workflowId,
      pageable = jPageable(pageable)
    )
  }

  // ========== Job 제어 ==========

  @Operation(
    summary = "(백오피스) Loop Job 일시 중지",
    description = """
            권한: ai("admin")
            실행 중인 Loop Job을 일시 중지한다.
            현재 진행 중인 반복이 완료된 후 중지된다.

            [DTE 통합]
            - Redis 플래그 설정으로 빠르게 중지 신호 전달
            - LoopWorkflowExecutor가 다음 iteration 전에 플래그 감지
            - DTE Job 자체는 유지됨 (resume으로 재개 가능)

            [상태 변화]
            - RUNNING → PAUSED
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/{jobId}/pause")
  fun pause(@PathVariable jobId: String): LoopJobActionResponse {
    return loopJobService.pause(jobId)
  }

  @Operation(
    summary = "(백오피스) Loop Job 재개 (DTE 큐 등록)",
    description = """
            권한: ai("admin")
            일시 중지된 Loop Job을 재개한다.
            마지막 체크포인트 위치부터 실행을 계속한다.

            [DTE 통합]
            - PAUSED/RECOVERING 상태의 Job을 DTE 큐에 재등록
            - 상태가 PENDING으로 변경되고 DTE 큐에 등록됨
            - LoopWorkflowExecutor가 체크포인트 기반으로 재개 실행

            [재개 가능 상태]
            - PAUSED: 일시 중지된 Job
            - RECOVERING: 좀비 감지 후 복구 준비된 Job
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/{jobId}/resume")
  fun resume(@PathVariable jobId: String): LoopJobActionResponse {
    return loopJobService.resume(jobId, session!!.deepCopy())
  }

  @Operation(
    summary = "(백오피스) Loop Job 재시작 (DTE 큐 등록)",
    description = """
            권한: ai("admin")
            종료된 Loop Job을 초기화하고 처음부터 다시 실행한다.

            [재시작 가능 상태]
            - COMPLETED: 완료됨
            - CANCELLED: 취소됨
            - ERROR: 오류 발생

            [처리 내용]
            - 상태, 카운터(currentCount, successCount, failureCount) 초기화
            - 기존 iteration 기록 물리적 삭제
            - 체크포인트 삭제
            - Redis 플래그 정리 후 DTE 큐에 등록

            [DTE 통합]
            - 기존 Loop Job ID를 재사용하여 DTE Job 생성
            - 상태가 PENDING으로 변경되고 DTE 큐에 등록됨
            - LoopWorkflowExecutor가 처음부터 실행

            [옵션 변경]
            - 요청 본문에 옵션을 전달하면 해당 설정으로 변경
            - null인 필드는 기존 값 유지

            예시:
            - 기존 10회 반복 Job을 50회로 변경하여 재시작
            - 에러 정책을 STOP에서 CONTINUE로 변경
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/{jobId}/restart")
  fun restart(
    @PathVariable jobId: String,
    @RequestBody(required = false) request: RestartLoopJobRequest?
  ): LoopJobActionResponse {
    return loopJobService.restart(jobId, request ?: RestartLoopJobRequest(), session!!.deepCopy())
  }

  @Operation(
    summary = "(백오피스) Loop Job 취소",
    description = """
            권한: ai("admin")
            Loop Job을 취소한다.
            취소된 Job은 다시 재개할 수 없다. (restart로 처음부터 재시작은 가능)

            [DTE 통합]
            - Redis 취소 플래그 설정으로 빠르게 취소 신호 전달
            - LoopWorkflowExecutor가 다음 iteration 전에 플래그 감지
            - Redis 실행 중 Set에서 제거 및 락 해제

            [상태 변화]
            - PENDING/RUNNING/PAUSED → CANCELLED
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/{jobId}/cancel")
  fun cancel(
    @PathVariable jobId: String,
    @RequestBody(required = false) request: CancelLoopJobRequest?
  ): LoopJobActionResponse {
    return loopJobService.cancel(jobId, request?.reason)
  }

  // ========== 좀비 Job 관리 ==========

  @Operation(
    summary = "(백오피스) 좀비 Job 목록 조회",
    description = """
            권한: ai("admin")
            Heartbeat 타임아웃으로 좀비 상태가 된 Job 목록을 조회한다.
            Heartbeat가 60초 이상 없는 RUNNING 상태 Job을 좀비로 판단.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/loop-jobs/zombies")
  fun findZombies(): ZombieJobListResponse {
    return loopJobService.findZombieJobs()
  }

  @Operation(
    summary = "(백오피스) 좀비 Job 자동 복구",
    description = """
            권한: ai("admin")
            좀비 Job을 감지하고 자동으로 복구를 시도한다.
            감지된 좀비 Job은 ERROR 상태로 전환되고 Redis 락이 해제된다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/zombies/recover")
  fun recoverZombies(): ZombieDetectionResponse {
    return loopJobService.detectAndRecoverZombieJobs()
  }

  @Operation(
    summary = "(백오피스) 특정 Job 수동 복구",
    description = """
            권한: ai("admin")
            ERROR 상태의 Job을 수동으로 복구하여 PAUSED 상태로 전환한다.
            복구 후 resume API로 재개할 수 있다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/{jobId}/recover")
  fun manualRecover(@PathVariable jobId: String): LoopJobActionResponse {
    val success = loopJobRecoveryService.manualRecover(jobId)
    return if (success) {
      LoopJobActionResponse(true, "Job이 복구되었습니다. resume으로 재개하세요.")
    } else {
      LoopJobActionResponse(false, "Job 복구에 실패했습니다. ERROR 상태의 Job만 복구 가능합니다.")
    }
  }

  // ========== 시스템 관리 ==========

  @Operation(
    summary = "(백오피스) Loop Job 시스템 상태 확인",
    description = """
            권한: ai("admin")
            Loop Job 시스템의 전반적인 상태를 확인한다.
            Redis와 DB 간 일관성, 좀비 Job 수 등을 리포트한다.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @GetMapping("/bo/ai/loop-jobs/health")
  fun healthCheck(): Map<String, Any> {
    return loopJobRecoveryService.healthCheck()
  }

  @Operation(
    summary = "(백오피스) Redis 실행 중 Set 정리",
    description = """
            권한: ai("admin")
            DB와 Redis 간 상태 불일치를 해결한다.
            Redis에 있지만 실제로 RUNNING 상태가 아닌 Job들을 Set에서 제거.
        """,
    security = [SecurityRequirement(name = CommonDto.BEARER_AUTH)]
  )
  @PostMapping("/bo/ai/loop-jobs/cleanup-redis")
  fun cleanupRedis(): LoopJobActionResponse {
    loopJobRecoveryService.cleanupRedisRunningSet()
    return LoopJobActionResponse(true, "Redis 정리가 완료되었습니다.")
  }
}
