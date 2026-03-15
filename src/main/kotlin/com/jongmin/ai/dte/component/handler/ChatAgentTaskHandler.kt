package com.jongmin.ai.dte.component.handler

import com.jongmin.jspring.web.dto.TokenType
import com.jongmin.jspring.web.entity.JPayload
import com.jongmin.jspring.web.entity.JPermissions
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository.Companion.BLOCK_BY_AI_THREAD_ID
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.jspring.core.util.convert
import com.jongmin.ai.core.*
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.dto.request.CreateAiMessage
import com.jongmin.ai.core.platform.dto.request.CreateAiRun
import com.jongmin.ai.core.platform.entity.AiMessage
import com.jongmin.ai.core.platform.entity.AiRunStep
import com.jongmin.ai.core.platform.service.AiAgentService
import com.jongmin.ai.core.platform.service.AiMessageService
import com.jongmin.ai.core.platform.service.AiRunService
import com.jongmin.ai.dte.component.SingleWorkflowExecutor
import com.jongmin.ai.dte.dto.WorkflowJobPayload
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.dto.request.ChatAgentRequest
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

/**
 * CHAT_AGENT 타입 태스크 핸들러
 *
 * AI 채팅 에이전트 추론 작업을 처리합니다.
 * AiRunController의 create 로직을 DTE 방식으로 통합하여
 * 분산 환경에서 AI 추론을 실행합니다.
 */
@Component
class ChatAgentTaskHandler(
  private val objectMapper: ObjectMapper,
  private val redisNodeRepository: RedisNodeRepository,
  private val aiMessageService: AiMessageService,
  private val aiAgentService: AiAgentService,
  private val singleWorkflowExecutor: SingleWorkflowExecutor,
  private val aiRunService: AiRunService,
  private val aiRunStepRepository: AiRunStepRepository,
  private val cancellationManager: AIInferenceCancellationManager,
) : TaskHandler {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val TASK_TYPE = "CHAT_AGENT"

    // 작업 타임아웃 설정 (분)
    private const val TIMEOUT_MINUTES = 10L
  }

  override val type: String = TASK_TYPE

  /**
   * CHAT_AGENT 작업을 실행합니다.
   *
   * 처리 흐름:
   * 1. payload에서 ChatAgentRequest 추출
   * 2. JSession 생성 (accountId 기반)
   * 3. AI 메시지 스레드 조회
   * 4. Redis 락 획득 (동시 실행 방지)
   * 5. AiRun 생성 및 취소 관리자 등록
   * 6. AiAgentExecutor로 워크플로우 실행
   * 7. EventBridgeFluxSink를 통해 결과 스트리밍
   * 8. 완료 시 응답 메시지 생성 및 상태 업데이트
   *
   * @param job 실행할 작업
   * @throws BadRequestException 사용자 메시지가 아닌 경우 또는 추론 중인 경우
   * @throws TimeoutException 작업이 10분 내에 완료되지 않은 경우
   */
  override fun execute(job: DistributedJob) {
    kLogger.info { "CHAT_AGENT 작업 실행 - jobId: ${job.id}" }

    // 1. 요청 데이터 및 세션 준비
    val request = extractRequest(job)
    val accountId = extractAccountId(job)
    val session = createSession(accountId)

    // 2. AI 메시지 스레드 조회 및 검증
    val aiMessages = loadAndValidateMessages(session, request.aiMessageId)
    val lastMessage = aiMessages.last()
    val aiThreadId = lastMessage.aiThreadId

    // 3. Redis 락 획득 (동시 실행 방지)
    val lockKey = "${BLOCK_BY_AI_THREAD_ID}_$aiThreadId"
    acquireLock(lockKey)

    // 4. 실행 컨텍스트 생성
    val executionContext = ChatAgentExecutionContext(
      job = job,
      request = request,
      session = session,
      accountId = accountId,
      aiThreadId = aiThreadId,
      lockKey = lockKey,
      aiMessages = aiMessages
    )

    try {
      executeWithContext(executionContext)
    } catch (e: Exception) {
      kLogger.error(e) { "CHAT_AGENT 작업 처리 실패 - jobId: ${job.id}" }
      throw e
    }
  }

  /**
   * 실행 컨텍스트를 사용하여 작업을 실행합니다.
   *
   * AiRun과 AiRunStep을 생성하여 워크플로우 실행을 추적합니다.
   * SingleWorkflowExecutor를 직접 사용하여 DTE Job 중첩을 방지합니다.
   */
  private fun executeWithContext(ctx: ChatAgentExecutionContext) {
    // AiRun 생성 및 취소 관리자 등록
    val aiRunItem = createAiRun(ctx)
    ctx.aiRunId = aiRunItem.id
    val runIdKey = aiRunItem.id.toString()
    cancellationManager.registerInference(runIdKey)

    // AiRunStep 생성 (워크플로우 레벨 추적)
    val aiRunStep = createAiRunStep(aiRunItem.id!!)
    ctx.aiRunStepId = aiRunStep.id

    kLogger.debug { "AI 워크플로우 추적 시작 - aiRunId: ${ctx.aiRunId}, aiRunStepId: ${ctx.aiRunStepId}" }

    // AI 에이전트 조회
    val aiAgent = aiAgentService.findFirstByType(AiAgentType.USING_WEB_SEARCH_TOOL)

    try {
      // WorkflowJobPayload 구성
      val workflowPayload = createWorkflowPayload(ctx, aiAgent.workflow)

      // SingleWorkflowExecutor로 직접 실행 (DTE Job 중첩 방지)
      val output = singleWorkflowExecutor.execute(ctx.job, workflowPayload)

      // 워크플로우 완료 후 후처리
      handleWorkflowCompletion(ctx, aiRunItem.id!!, runIdKey, output)

      kLogger.info { "CHAT_AGENT 작업 처리 완료 - jobId: ${ctx.job.id}" }
    } catch (e: Exception) {
      // 에러 발생 시 AiRunStep 상태를 FAILED로 업데이트
      ctx.aiRunStepId?.let { stepId ->
        try {
          updateAiRunStepStatus(stepId, StatusType.FAILED)
        } catch (trackingError: Exception) {
          kLogger.warn(trackingError) { "AiRunStep 실패 상태 추적 오류 - stepId: $stepId" }
        }
      }

      // 정리 작업
      cleanup(runIdKey, ctx.lockKey)
      throw e
    }
  }

  /**
   * WorkflowJobPayload를 구성합니다.
   */
  private fun createWorkflowPayload(ctx: ChatAgentExecutionContext, workflowData: Map<String, Any>): WorkflowJobPayload {
    // 세션 데이터 변환
    val sessionData: Map<String, Any> = objectMapper.convertValue(
      ctx.session,
      object : TypeReference<Map<String, Any>>() {}
    )

    // 메시지 데이터 변환
    val messagesData = ctx.aiMessages.map { msg ->
      mapOf(
        "role" to msg.role.name.lowercase(),
        "content" to msg.content
      )
    }

    return WorkflowJobPayload(
      workflowId = null,
      workflowData = workflowData,
      accountId = ctx.accountId,
      sessionData = sessionData,
      canvasId = ctx.request.canvasId,
      streaming = true,
      isLoop = false,
      messages = messagesData,
      input = null
    )
  }

  /**
   * 워크플로우 완료 시 처리 로직
   *
   * AiRun과 AiRunStep 상태를 모두 업데이트합니다.
   */
  private fun handleWorkflowCompletion(
    ctx: ChatAgentExecutionContext,
    aiRunId: Long,
    runIdKey: String,
    output: Any?
  ) {
    try {
      // 결과 추출
      val answerContent = when (output) {
        is Map<*, *> -> output.values.filterNotNull().joinToString("\n") { v -> v.toString() }
        is String -> output
        else -> output?.toString() ?: ""
      }

      // 응답 메시지 생성
      createResponseMessage(ctx, answerContent)

      // AiRun 상태 업데이트
      aiRunService.patchStatus(aiRunId, AiRunStatus.ENDED)

      // AiRunStep 상태 업데이트 (완료)
      ctx.aiRunStepId?.let { stepId ->
        try {
          updateAiRunStepStatus(stepId, StatusType.ENDED)
          kLogger.debug { "AiRunStep 완료 - stepId: $stepId" }
        } catch (e: Exception) {
          kLogger.warn(e) { "AiRunStep 완료 상태 추적 오류 - stepId: $stepId" }
        }
      }
    } finally {
      // 취소 관리자에서 제거 및 락 해제
      cleanup(runIdKey, ctx.lockKey)
    }
  }

  // ========== Helper Methods ==========

  private fun extractRequest(job: DistributedJob): ChatAgentRequest {
    return objectMapper.convert(job.payload, ChatAgentRequest::class.java)
  }

  private fun extractAccountId(job: DistributedJob): Long {
    return job.requesterId ?: throw BadRequestException("accountId가 필요합니다.")
  }

  private fun createSession(accountId: Long): JSession {
    return JSession.of(
      accountId,
      "",
      TokenType.AUTH,
      "dte-session",
      null,
      JPermissions(),
      JPayload(),
      null,
      null
    )
  }

  private fun loadAndValidateMessages(session: JSession, aiMessageId: Long): List<AiMessage> {
    val aiMessages = aiMessageService.findAllForAiMessageThread(session, aiMessageId)
    val lastMessage = aiMessages.last()

    if (lastMessage.role == AiMessageRole.ASSISTANT) {
      throw BadRequestException("사용자 메시지에 대해서만 추론할 수 있습니다.")
    }

    return aiMessages
  }

  private fun acquireLock(lockKey: String) {
    if (!redisNodeRepository.lockIfAbsent(lockKey, 15)) {
      throw BadRequestException("추론 중입니다. 잠시 후 다시 시도해주세요.")
    }
  }

  private fun createAiRun(ctx: ChatAgentExecutionContext): com.jongmin.ai.core.platform.dto.response.AiRunItem {
    val createAiRun = CreateAiRun(
      canvasId = ctx.request.canvasId,
      aiMessageId = ctx.request.aiMessageId,
      researchMode = ctx.request.researchMode,
      aiAgentId = ctx.request.aiAgentId
    )
    return aiRunService.create(ctx.session, createAiRun)
  }

  /**
   * AiRunStep 생성
   *
   * 워크플로우 레벨의 실행 단계를 추적합니다.
   * 실제 토큰 사용량 추적은 노드 레벨에서 별도로 구현되어야 합니다.
   */
  private fun createAiRunStep(aiRunId: Long): AiRunStep {
    val aiRunStep = AiRunStep(
      aiRunId = aiRunId,
      aiAssistantId = null,  // 워크플로우 레벨 - 특정 어시스턴트 지정하지 않음
      status = StatusType.RUNNING,
      logs = null
    )
    return aiRunStepRepository.save(aiRunStep)
  }

  /**
   * AiRunStep 상태 업데이트
   */
  private fun updateAiRunStepStatus(aiRunStepId: Long, status: StatusType) {
    aiRunStepRepository.findById(aiRunStepId).ifPresent { step ->
      step.status = status
      if (status == StatusType.ENDED || status == StatusType.FAILED) {
        step.completedAt = now()
      }
      aiRunStepRepository.save(step)
    }
  }

  private fun createResponseMessage(ctx: ChatAgentExecutionContext, content: String): Any {
    return aiMessageService.create(
      ctx.session,
      CreateAiMessage(
        accountId = ctx.accountId,
        aiThreadId = ctx.aiThreadId,
        content = content,
        role = AiMessageRole.ASSISTANT,
        type = AiMessageContentType.TEXT
      )
    )
  }

  private fun cleanup(runIdKey: String, lockKey: String) {
    cancellationManager.unregisterInference(runIdKey)
    redisNodeRepository.unlock(lockKey)
  }
}

/**
 * CHAT_AGENT 실행 컨텍스트
 *
 * 작업 실행에 필요한 데이터를 캡슐화합니다.
 *
 * @property aiRunId AI Run ID (워크플로우 실행 추적용)
 * @property aiRunStepId AI Run Step ID (실행 단계 추적용)
 */
private data class ChatAgentExecutionContext(
  val job: DistributedJob,
  val request: ChatAgentRequest,
  val session: JSession,
  val accountId: Long,
  val aiThreadId: Long,
  val lockKey: String,
  val aiMessages: List<AiMessage>,
  var aiRunId: Long? = null,
  var aiRunStepId: Long? = null,
)
