package com.jongmin.ai.core.platform.component.agent.executor

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.platform.component.agent.executor.model.ExecutionState
import com.jongmin.ai.core.platform.component.agent.executor.model.NodeExecutionState
import com.jongmin.ai.core.platform.component.agent.executor.model.Workflow
import com.jongmin.jspring.dte.component.DistributedTaskQueue
import com.jongmin.ai.dte.component.handler.WorkflowTaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import com.jongmin.jspring.dte.entity.JobPriority
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * AI Agent 워크플로우 실행기
 *
 * 워크플로우 실행 요청을 DTE(Distributed Task Executor) 큐에 등록합니다.
 * 기존 메서드 시그니처를 유지하면서 내부적으로는 DTE를 통해 분산 실행됩니다.
 *
 * ### 실행 흐름:
 * 1. 워크플로우/세션/메시지를 WorkflowJobPayload 형식으로 변환
 * 2. DistributedTaskQueue.enqueue(type="WORKFLOW")로 Job 등록
 * 3. WorkflowTaskHandler가 Job을 처리하여 SingleWorkflowExecutor 또는 LoopWorkflowExecutor로 위임
 *
 * ### 주요 변경점 (DTE 마이그레이션):
 * - 기존: 직접 BasicWorkflowEngine 생성 및 실행
 * - 변경: DTE Job으로 등록하여 분산 실행 (동시성 제어, 큐잉, 타임아웃 등 지원)
 *
 * ### SSE 스트리밍:
 * - emitter != null: streaming=true로 Job 등록, SSE를 통해 실시간 이벤트 수신 가능
 * - emitter == null: streaming=false로 백그라운드 실행
 *
 * ### onFinish 콜백:
 * - DTE 기반에서는 SSE 이벤트(WORKFLOW_COMPLETED/WORKFLOW_FAILED)를 통해 완료 감지
 * - 기존 인터페이스 호환성을 위해 파라미터는 유지하나, DTE에서는 별도 처리되지 않음
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property distributedTaskQueue DTE 큐 (Job 등록용)
 *
 * @author Claude Code
 * @since 2026.01.09
 */
@Component
class AiAgentExecutor(
  private val objectMapper: ObjectMapper,
  private val distributedTaskQueue: DistributedTaskQueue,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * Workflow 객체로 워크플로우를 실행합니다.
   *
   * 워크플로우를 DTE Job으로 등록하여 분산 실행합니다.
   * Job ID를 반환하여 클라이언트가 SSE 구독에 사용할 수 있도록 합니다.
   *
   * @param session 사용자 세션 (deepCopy되어 Job payload에 저장)
   * @param workflow 실행할 워크플로우 객체
   * @param messages 채팅 메시지 리스트 (선택)
   * @return DTE Job ID (SSE 구독용 executionId)
   */
  fun execute(
    session: JSession,
    workflow: Workflow,
    messages: List<IAiChatMessage>? = null,
  ): String {
    kLogger.info {
      "워크플로우 실행 요청 - accountId: ${session.accountId}, " +
          "workflowId: ${workflow.id}, canvasId: ${workflow.canvasId}, " +
          "hasMessages: ${messages != null}"
    }

    // 1. 노드 실행 상태 초기화
    workflow.nodes.forEach { node ->
      node.data.executionState = ExecutionState(NodeExecutionState.IDLE)
    }

    // 2. Workflow → Map 변환
    val workflowData: Map<String, Any> = objectMapper.convertValue(
      workflow,
      object : TypeReference<Map<String, Any>>() {}
    )

    // 3. JSession → Map 변환
    val sessionData: Map<String, Any> = objectMapper.convertValue(
      session.deepCopy(),
      object : TypeReference<Map<String, Any>>() {}
    )

    // 4. messages → List<Map> 변환
    val messagesData: List<Map<String, Any>>? = messages?.map { msg ->
      mapOf(
        "role" to msg.type().name.lowercase(),
        "content" to msg.content()
      )
    }

    // 5. WorkflowJobPayload 형식의 payload 구성
    val payload = mutableMapOf<String, Any>(
      "workflowData" to workflowData,
      "accountId" to session.accountId,
      "sessionData" to sessionData,
      "streaming" to true,  // SSE 이벤트 발행 활성화
      "isLoop" to false
    )

    // canvasId가 있으면 추가
    workflow.canvasId?.let { payload["canvasId"] = it }

    // messages가 있으면 추가
    messagesData?.let { payload["messages"] = it }

    // 6. DTE Job 생성
    val job = DistributedJob.create(
      type = WorkflowTaskHandler.TASK_TYPE,
      payload = payload,
      priority = JobPriority.NORMAL,
      requesterId = session.accountId
    )

    kLogger.debug { "DTE Job 생성 - jobId: ${job.id}, type: ${job.type}" }

    // 7. DTE 큐에 등록
    val enqueuedJob = distributedTaskQueue.enqueue(WorkflowTaskHandler.TASK_TYPE, job)

    kLogger.info {
      "DTE Job 등록 완료 - jobId: ${enqueuedJob.id}, accountId: ${session.accountId}"
    }

    // Job ID 반환 - 클라이언트는 이 ID로 SSE 구독
    return enqueuedJob.id
  }

  /**
   * workflowData Map으로 워크플로우를 실행합니다.
   *
   * Map 형태의 워크플로우 데이터를 받아 DTE Job으로 등록합니다.
   *
   * @param session 사용자 세션
   * @param canvasId 캔버스 ID (SSE 토픽 구분용)
   * @param workflowData 워크플로우 데이터 (Map)
   * @param messages 채팅 메시지 리스트 (선택)
   * @return DTE Job ID (SSE 구독용 executionId)
   */
  fun execute(
    session: JSession,
    canvasId: String?,
    workflowData: Map<String, Any>,
    messages: List<IAiChatMessage>? = null,
  ): String {
    // Workflow 객체로 변환하여 execute 호출
    val workflow = objectMapper.convertValue(workflowData, Workflow::class.java)
    workflow.canvasId = canvasId
    return execute(session, workflow, messages)
  }
}
