package com.jongmin.ai.multiagent.skill.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.ai.multiagent.skill.dto.*
import com.jongmin.ai.multiagent.skill.entity.ScriptExecutionEntity
import com.jongmin.ai.multiagent.skill.entity.SkillDefinitionEntity
import com.jongmin.ai.multiagent.skill.model.ExecutionStatus
import com.jongmin.ai.multiagent.skill.model.ScriptLanguage
import com.jongmin.ai.multiagent.skill.model.SkillScript
import com.jongmin.ai.multiagent.skill.repository.ScriptExecutionRepository
import com.jongmin.ai.multiagent.skill.repository.SkillDefinitionRepository
import com.jongmin.ai.multiagent.skill.runner.BridgeClientException
import com.jongmin.ai.multiagent.skill.runner.SandboxBridgeClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val kLogger = KotlinLogging.logger {}

/**
 * 스킬 스크립트 실행 서비스
 * 스킬의 스크립트를 샌드박스에서 안전하게 실행
 *
 * 주요 기능:
 * - 스크립트 실행 요청 및 상태 관리
 * - 실행 기록 저장 및 조회
 * - Bridge Service와 통신
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class SkillScriptRunnerService(
  private val skillRepository: SkillDefinitionRepository,
  private val executionRepository: ScriptExecutionRepository,
  private val bridgeClient: SandboxBridgeClient,
  private val objectMapper: ObjectMapper,

  @param:Value($$"${skill.sandbox.default-timeout-seconds:60}")
  private val defaultTimeoutSeconds: Long,

  @param:Value($$"${skill.sandbox.max-concurrent-executions:10}")
  private val maxConcurrentExecutions: Int,
) {

  /**
   * 스킬 스크립트 실행 (비동기)
   *
   * @param session 세션 정보
   * @param skillId 스킬 ID
   * @param scriptFilename 실행할 스크립트 파일명 (null이면 entrypoint 스크립트)
   * @param input 입력 데이터
   * @param env 환경 변수
   * @param timeoutSeconds 타임아웃 (초)
   * @return 실행 응답 (Mono)
   */
  @Transactional
  fun executeScript(
    session: JSession,
    skillId: Long,
    scriptFilename: String? = null,
    input: Any? = null,
    env: Map<String, String> = emptyMap(),
    timeoutSeconds: Long = defaultTimeoutSeconds,
  ): Mono<SkillExecutionResponse> {
    kLogger.info { "스크립트 실행 요청 - skillId: $skillId, script: $scriptFilename, user: ${session.username}" }

    // 1. 동시 실행 제한 확인
    val activeCount = executionRepository.countActiveExecutions(session.accountId, 1)
    if (activeCount >= maxConcurrentExecutions) {
      kLogger.warn { "동시 실행 제한 초과 - accountId: ${session.accountId}, active: $activeCount, max: $maxConcurrentExecutions" }
      return Mono.error(
        IllegalStateException("Maximum concurrent executions exceeded: $activeCount/$maxConcurrentExecutions")
      )
    }

    // 2. 스킬 조회
    val skill = skillRepository.findById(skillId).orElseThrow {
      ObjectNotFoundException("Skill not found: $skillId")
    }

    // 3. 스크립트 선택
    val script = selectScript(skill, scriptFilename)
      ?: return Mono.error(ObjectNotFoundException("Script not found in skill: $skillId"))

    // 4. 실행 기록 생성
    val inputJson = input?.let {
      when (it) {
        is String -> it
        else -> objectMapper.writeValueAsString(it)
      }
    }

    val execution = executionRepository.save(
      ScriptExecutionEntity.create(
      accountId = session.accountId,
      executorId = session.accountId,
      skillId = skillId,
      scriptFilename = script.filename,
      language = script.language,
      input = inputJson,
      )
    )
    val executionId = execution.id

    // 5. Bridge Service로 실행 요청
    val request = ExecuteScriptRequest(
      executionId = executionId,
      language = script.language,
      content = script.content,
      input = inputJson,
      env = env,
      timeoutSeconds = timeoutSeconds,
      networkPolicy = skill.networkPolicy,
      allowedDomains = skill.allowedDomains,
      resources = ExecutionResourceLimits(),
    )

    return bridgeClient.executeScript(request)
      .map { response ->
        // 6. 실행 상태 업데이트
        execution.markRunning(response.sandboxPodName)
        executionRepository.save(execution)

        SkillExecutionResponse(
          executionId = executionId,
          status = response.status,
          message = "Script execution started",
        )
      }
      .onErrorResume(BridgeClientException::class.java) { ex ->
        // Bridge 에러 시 실행 실패 처리
        execution.markFailed(ex.message ?: "Bridge service error")
        executionRepository.save(execution)

        Mono.just(
          SkillExecutionResponse(
            executionId = executionId,
            status = ExecutionStatus.FAILED,
            message = ex.message,
          )
        )
      }
  }

  /**
   * 실행 상태 조회
   */
  fun getExecutionStatus(session: JSession, executionId: Long): Mono<SkillExecutionStatusResponse> {
    val execution = executionRepository.findById(executionId).orElseThrow {
      ObjectNotFoundException("Execution not found: $executionId")
    }

    // 권한 확인
    if (execution.accountId != session.accountId) {
      throw ObjectNotFoundException("Execution not found: $executionId")
    }

    // 완료된 실행은 DB에서 바로 반환
    if (execution.executionStatus.isTerminal()) {
      return Mono.just(
        SkillExecutionStatusResponse(
          executionId = executionId,
          status = execution.executionStatus,
          exitCode = execution.exitCode,
          durationMs = execution.durationMs,
        )
      )
    }

    // 진행 중인 실행은 Bridge에서 상태 조회
    return bridgeClient.getExecutionStatus(executionId)
      .map { response ->
        // DB 상태도 업데이트
        if (response.status != execution.executionStatus) {
          execution.executionStatus = response.status
          executionRepository.save(execution)
        }

        SkillExecutionStatusResponse(
          executionId = executionId,
          status = response.status,
          progress = response.progress,
          queuePosition = response.queuePosition,
        )
      }
      .onErrorReturn(
        SkillExecutionStatusResponse(
          executionId = executionId,
          status = execution.executionStatus,
        )
      )
  }

  /**
   * 실행 결과 조회
   */
  fun getExecutionResult(session: JSession, executionId: Long): Mono<SkillExecutionResultResponse> {
    val execution = executionRepository.findById(executionId).orElseThrow {
      ObjectNotFoundException("Execution not found: $executionId")
    }

    // 권한 확인
    if (execution.accountId != session.accountId) {
      throw ObjectNotFoundException("Execution not found: $executionId")
    }

    // 완료된 실행은 DB에서 반환
    if (execution.executionStatus.isTerminal()) {
      return Mono.just(
        SkillExecutionResultResponse(
          executionId = executionId,
          status = execution.executionStatus,
          exitCode = execution.exitCode,
          stdout = execution.stdout,
          stderr = execution.stderr,
          errorMessage = execution.errorMessage,
          durationMs = execution.durationMs,
        )
      )
    }

    // 진행 중인 실행은 Bridge에서 결과 조회 (polling)
    return bridgeClient.getExecutionResult(executionId)
      .map { response ->
        // 완료되면 DB 업데이트
        if (response.status.isTerminal()) {
          updateExecutionFromResult(execution, response)
          executionRepository.save(execution)
        }

        SkillExecutionResultResponse(
          executionId = executionId,
          status = response.status,
          exitCode = response.exitCode,
          stdout = response.stdout,
          stderr = response.stderr,
          errorMessage = response.errorMessage,
          durationMs = response.durationMs,
        )
      }
  }

  /**
   * 실행 취소
   */
  @Transactional
  fun cancelExecution(session: JSession, executionId: Long): Mono<Boolean> {
    val execution = executionRepository.findById(executionId).orElseThrow {
      ObjectNotFoundException("Execution not found: $executionId")
    }

    // 권한 확인
    if (execution.accountId != session.accountId) {
      throw ObjectNotFoundException("Execution not found: $executionId")
    }

    // 이미 완료된 실행은 취소 불가
    if (execution.executionStatus.isTerminal()) {
      return Mono.just(false)
    }

    return bridgeClient.cancelExecution(executionId)
      .doOnSuccess { success ->
        if (success == true) {
          execution.executionStatus = ExecutionStatus.CANCELLED
          execution.errorMessage = "Cancelled by user"
          executionRepository.save(execution)
          kLogger.info { "실행 취소 완료 - executionId: $executionId" }
        }
      }
  }

  /**
   * 동기식 스크립트 실행 (결과 대기)
   * 짧은 스크립트 실행에 적합
   */
  @Transactional
  fun executeScriptSync(
    session: JSession,
    skillId: Long,
    scriptFilename: String? = null,
    input: Any? = null,
    env: Map<String, String> = emptyMap(),
    timeoutSeconds: Long = defaultTimeoutSeconds,
  ): SkillExecutionResultResponse {
    // 비동기 실행 시작
    val startResponse = executeScript(session, skillId, scriptFilename, input, env, timeoutSeconds)
      .block(Duration.ofSeconds(10))
      ?: throw IllegalStateException("Failed to start script execution")

    if (startResponse.status == ExecutionStatus.FAILED) {
      return SkillExecutionResultResponse(
        executionId = startResponse.executionId,
        status = ExecutionStatus.FAILED,
        errorMessage = startResponse.message,
      )
    }

    // 결과 대기 (polling)
    val executionId = startResponse.executionId
    var attempts = 0
    val maxAttempts = (timeoutSeconds * 2).toInt() // 500ms 간격으로 polling

    while (attempts < maxAttempts) {
      Thread.sleep(500)
      attempts++

      val result = getExecutionResult(session, executionId)
        .block(Duration.ofSeconds(5))
        ?: continue

      if (result.status.isTerminal()) {
        return result
      }
    }

    // 타임아웃
    return SkillExecutionResultResponse(
      executionId = executionId,
      status = ExecutionStatus.TIMEOUT,
      errorMessage = "Execution timed out after ${timeoutSeconds}s",
    )
  }

  // ========== Private Helpers ==========

  /**
   * 스크립트 선택
   */
  private fun selectScript(skill: SkillDefinitionEntity, filename: String?): SkillScript? {
    val scripts = skill.scripts

    if (scripts.isEmpty()) {
      return null
    }

    // 파일명 지정 시 해당 스크립트 반환
    if (filename != null) {
      return scripts[filename]
    }

    // entrypoint 스크립트 우선
    return scripts.values.find { it.entrypoint }
      ?: scripts.values.firstOrNull()
  }

  /**
   * Bridge 결과로 실행 기록 업데이트
   */
  private fun updateExecutionFromResult(
    execution: ScriptExecutionEntity,
    response: ExecutionResultResponse,
  ) {
    when (response.status) {
      ExecutionStatus.COMPLETED -> {
        execution.markCompleted(
          exitCode = response.exitCode ?: 0,
          stdout = response.stdout,
          stderr = response.stderr,
          durationMs = response.durationMs ?: 0,
        )
      }
      ExecutionStatus.FAILED -> {
        execution.markFailed(
          errorMessage = response.errorMessage ?: "Unknown error",
          exitCode = response.exitCode,
          stderr = response.stderr,
          durationMs = response.durationMs,
        )
      }
      ExecutionStatus.TIMEOUT -> {
        execution.markTimeout(response.durationMs ?: 0)
      }
      else -> {
        execution.executionStatus = response.status
      }
    }

    // 리소스 사용량 업데이트
    response.resourceUsage?.let { usage ->
      execution.cpuUsageMillicores = usage.cpuMillicores
      execution.memoryUsageMb = usage.memoryMb
    }
  }
}

// ========== Response DTOs ==========

/**
 * 스킬 실행 응답 DTO
 */
data class SkillExecutionResponse(
  val executionId: Long,
  val status: ExecutionStatus,
  val message: String? = null,
)

/**
 * 스킬 실행 상태 응답 DTO
 */
data class SkillExecutionStatusResponse(
  val executionId: Long,
  val status: ExecutionStatus,
  val exitCode: Int? = null,
  val durationMs: Long? = null,
  val progress: Int? = null,
  val queuePosition: Int? = null,
)

/**
 * 스킬 실행 결과 응답 DTO
 */
data class SkillExecutionResultResponse(
  val executionId: Long,
  val status: ExecutionStatus,
  val exitCode: Int? = null,
  val stdout: String? = null,
  val stderr: String? = null,
  val errorMessage: String? = null,
  val durationMs: Long? = null,
)
