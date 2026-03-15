package com.jongmin.ai.dte.component.handler

import com.jongmin.jspring.web.dto.TokenType
import com.jongmin.jspring.web.entity.JPermissions
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.multiagent.executor.MultiAgentEngine
import com.jongmin.ai.multiagent.repository.MultiAgentWorkflowRepository
import com.jongmin.ai.multiagent.service.MultiAgentProgressManager
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private val kLogger = KotlinLogging.logger {}

/**
 * 멀티 에이전트 워크플로우 실행 Task Handler
 *
 * DTE에서 MULTI_AGENT_WORKFLOW 타입 Job을 처리
 * 워크플로우 조회 → 진행 상황 초기화 → 엔진 실행 → 결과 발송
 */
@Component
class MultiAgentWorkflowTaskHandler(
  private val objectMapper: ObjectMapper,
  private val multiAgentWorkflowRepository: MultiAgentWorkflowRepository,
  private val multiAgentEngine: MultiAgentEngine,
  private val progressManager: MultiAgentProgressManager,
) : TaskHandler {

  companion object {
    const val TASK_TYPE = "MULTI_AGENT_WORKFLOW"
  }

  init {
    kLogger.info {
      """
      |========== MultiAgentWorkflowTaskHandler 초기화 ==========
      |태스크 타입: $TASK_TYPE
      |==========================================================
      """.trimMargin()
    }
  }

  override val type: String = TASK_TYPE

  override fun execute(job: DistributedJob) {
    kLogger.info { "멀티 에이전트 워크플로우 실행 시작 - jobId: ${job.id}" }

    try {
      // Payload 파싱
      val payload = parsePayload(job)

      // 워크플로우 조회
      val workflow = multiAgentWorkflowRepository.findById(payload.workflowId)
        .orElseThrow { IllegalArgumentException("Workflow not found: ${payload.workflowId}") }

      // 세션 생성 (Job 정보로부터)
      val session = createSession(job, payload)

      // 진행 상황 초기화
      val jobId = job.id
      progressManager.initializeProgress(jobId, payload.workflowId, workflow.agents.size)

      kLogger.info { "워크플로우 실행 준비 완료 - workflowId: ${payload.workflowId}, agents: ${workflow.agents.size}" }

      // 엔진 실행
      val result = multiAgentEngine.execute(
        session = session,
        workflow = workflow,
        input = payload.input,
      )

      // 완료 이벤트
      progressManager.onWorkflowCompleted(jobId, result.output)

      kLogger.info { "멀티 에이전트 워크플로우 완료 - jobId: ${job.id}, workflowId: ${payload.workflowId}" }

    } catch (e: Exception) {
      kLogger.error(e) { "멀티 에이전트 워크플로우 실패 - jobId: ${job.id}" }

      // 실패 이벤트
      progressManager.onWorkflowFailed(job.id, e.message ?: "Unknown error")

      throw e
    }
  }

  /**
   * Job payload 파싱
   */
  private fun parsePayload(job: DistributedJob): MultiAgentTaskPayload {
    return try {
      objectMapper.convertValue(job.payload, MultiAgentTaskPayload::class.java)
    } catch (e: Exception) {
      kLogger.error(e) { "Payload 파싱 실패 - jobId: ${job.id}, payload: ${job.payload}" }
      throw IllegalArgumentException("MultiAgentTaskPayload 변환 실패: ${e.message}", e)
    }
  }

  /**
   * Job 정보로부터 JSession 생성
   * 시스템 내부 실행용 세션 (서비스 토큰 타입)
   */
  private fun createSession(job: DistributedJob, payload: MultiAgentTaskPayload): JSession {
    return JSession(
      accountId = payload.accountId,
      accessToken = "system-internal-${job.id}",
      tokenType = TokenType.SERVICE,
      username = "multi-agent-system",
      deviceUid = null,
      permissions = JPermissions(),
    )
  }

  /**
   * Task Payload 데이터 클래스
   */
  data class MultiAgentTaskPayload(
    val workflowId: Long,
    val input: Any,
    val options: Map<String, Any>? = null,
    val accountId: Long = 0L,
    val requesterId: Long? = null,
  )
}
