package com.jongmin.ai.dte.component.handler

import com.jongmin.ai.dte.component.LoopWorkflowExecutor
import com.jongmin.ai.dte.component.SingleWorkflowExecutor
import com.jongmin.ai.dte.dto.WorkflowJobPayload
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 워크플로우 실행을 담당하는 DTE TaskHandler
 *
 * WORKFLOW 타입 DTE Job을 처리하며, payload의 isLoop 여부에 따라
 * 적절한 Executor로 작업을 라우팅합니다.
 *
 * ### 지원 실행 모드:
 * - **Single (기본)**: 단일 워크플로우 1회 실행 → [SingleWorkflowExecutor]
 * - **Loop**: N회 반복 실행 (isLoop=true) → [LoopWorkflowExecutor]
 *
 * ### SOLID 원칙 적용:
 * - **SRP**: 라우팅 역할만 담당, 실제 작업 실행은 Executor에 위임
 * - **OCP**: 새 실행 모드 추가 시 새 Executor만 구현하면 됨
 * - **DIP**: Executor 추상화에 의존
 *
 * ### 사용 예시:
 * ```kotlin
 * // DTE TaskHandlerRegistry에 자동 등록됨
 * // Job payload 예시:
 * {
 *   "workflowId": 123,           // AI 에이전트 ID
 *   "streaming": true,           // SSE 스트리밍 여부
 *   "isLoop": false,             // Loop 실행 여부
 *   "accountId": 1,              // 계정 ID
 *   "loopConfig": { ... }        // isLoop=true일 때만 필요
 * }
 * ```
 *
 * @property objectMapper JSON 직렬화/역직렬화 (payload → WorkflowJobPayload 변환)
 * @property singleWorkflowExecutor 단일 워크플로우 실행기
 * @property loopWorkflowExecutor Loop 워크플로우 실행기
 *
 * @author Claude Code
 * @since 2026.01.09
 */
@Component
class WorkflowTaskHandler(
  private val objectMapper: ObjectMapper,
  private val singleWorkflowExecutor: SingleWorkflowExecutor,
  private val loopWorkflowExecutor: LoopWorkflowExecutor,
) : TaskHandler {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    /**
     * 태스크 타입 상수
     *
     * DTE에서 이 핸들러가 처리할 Job 타입을 식별하는 데 사용됩니다.
     * TaskHandlerRegistry에 자동 등록 시 이 값으로 매핑됩니다.
     */
    const val TASK_TYPE = "WORKFLOW"
  }

  init {
    kLogger.info {
      """
      |========== WorkflowTaskHandler 초기화 ==========
      |태스크 타입: $TASK_TYPE
      |Single Executor: ${singleWorkflowExecutor::class.simpleName}
      |Loop Executor: ${loopWorkflowExecutor::class.simpleName}
      |=================================================
      """.trimMargin()
    }
  }

  /**
   * 이 핸들러가 처리할 수 있는 태스크 타입을 반환합니다.
   *
   * @return "WORKFLOW" 문자열
   */
  override val type: String = TASK_TYPE

  /**
   * 워크플로우 DTE Job을 실행합니다.
   *
   * 처리 흐름:
   * 1. Job payload를 WorkflowJobPayload로 변환
   * 2. isLoop 여부에 따라 적절한 Executor로 라우팅
   *    - isLoop=true: LoopWorkflowExecutor로 N회 반복 실행
   *    - isLoop=false: SingleWorkflowExecutor로 1회 실행
   * 3. Executor 실행 완료 후 반환 (예외 발생 시 상위로 전파)
   *
   * @param job 실행할 DTE Job (payload에 WorkflowJobPayload 데이터 포함)
   * @throws IllegalArgumentException payload 변환 실패 시
   * @throws Exception Executor 실행 중 발생한 예외
   */
  override fun execute(job: DistributedJob) {
    kLogger.info { "WORKFLOW 작업 실행 시작 - jobId: ${job.id}" }

    // 1. payload → WorkflowJobPayload 변환
    val payload = convertPayload(job)

    kLogger.debug {
      "payload 변환 완료 - jobId: ${job.id}, " +
          "isLoop: ${payload.isLoop}, " +
          "streaming: ${payload.streaming}, " +
          "workflowId: ${payload.workflowId}, " +
          "hasWorkflowData: ${payload.workflowData != null}"
    }

    // 2. isLoop 여부에 따라 적절한 Executor로 라우팅
    if (payload.isLoop) {
      kLogger.info { "🔄 Loop 모드 실행 - jobId: ${job.id}, maxCount: ${payload.loopConfig?.maxCount}" }
      loopWorkflowExecutor.execute(job, payload)
    } else {
      kLogger.info { "▶ Single 모드 실행 - jobId: ${job.id}, streaming: ${payload.streaming}" }
      singleWorkflowExecutor.execute(job, payload)
    }

    kLogger.info { "WORKFLOW 작업 실행 완료 - jobId: ${job.id}" }
  }

  /**
   * Job payload를 WorkflowJobPayload로 변환합니다.
   *
   * ObjectMapper.convertValue()를 사용하여 Map → Data Class 변환을 수행합니다.
   * payload가 Map 형태가 아니거나 필수 필드가 누락된 경우 예외가 발생합니다.
   *
   * @param job DTE Job
   * @return 변환된 WorkflowJobPayload
   * @throws IllegalArgumentException 변환 실패 시
   */
  private fun convertPayload(job: DistributedJob): WorkflowJobPayload {
    try {
      return objectMapper.convertValue(job.payload, WorkflowJobPayload::class.java)
    } catch (e: Exception) {
      kLogger.error(e) { "payload 변환 실패 - jobId: ${job.id}, payload: ${job.payload}" }
      throw IllegalArgumentException("WorkflowJobPayload 변환 실패: ${e.message}", e)
    }
  }
}
