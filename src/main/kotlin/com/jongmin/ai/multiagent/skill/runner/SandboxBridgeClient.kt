package com.jongmin.ai.multiagent.skill.runner

import com.jongmin.ai.multiagent.skill.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

private val kLogger = KotlinLogging.logger {}

/**
 * 샌드박스 Bridge Service 클라이언트
 * Python FastAPI Bridge Service와 통신
 *
 * Bridge Service 역할:
 * - K8s Agent-Sandbox CRD 관리
 * - 스크립트 실행 요청 처리
 * - 실행 상태/결과 조회
 */
@Component
class SandboxBridgeClient(
  @param:Value($$"${skill.sandbox.bridge.url:http://localhost:8081}")
  private val bridgeUrl: String,

  @param:Value($$"${skill.sandbox.bridge.timeout-seconds:30}")
  private val defaultTimeoutSeconds: Long,
) {
  private val webClient: WebClient = WebClient.builder()
    .baseUrl(bridgeUrl)
    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
    .build()

  /**
   * 스크립트 실행 요청
   *
   * @param request 실행 요청
   * @return 실행 응답 (비동기)
   */
  fun executeScript(request: ExecuteScriptRequest): Mono<ExecuteScriptResponse> {
    kLogger.info { "Bridge 실행 요청 - executionId: ${request.executionId}, language: ${request.language}" }

    return webClient.post()
      .uri("/api/v1/execute")
      .bodyValue(request)
      .retrieve()
      .bodyToMono(ExecuteScriptResponse::class.java)
      .timeout(Duration.ofSeconds(defaultTimeoutSeconds))
      .doOnSuccess { response ->
        kLogger.info { "Bridge 실행 응답 - executionId: ${response?.executionId}, status: ${response?.status}" }
      }
      .doOnError { error ->
        kLogger.error(error) { "Bridge 실행 요청 실패 - executionId: ${request.executionId}" }
      }
      .onErrorResume(WebClientResponseException::class.java) { ex ->
        kLogger.warn { "Bridge 응답 에러 - status: ${ex.statusCode}, body: ${ex.responseBodyAsString}" }
        Mono.error(BridgeClientException("Bridge request failed: ${ex.statusCode}", ex))
      }
  }

  /**
   * 실행 상태 조회
   *
   * @param executionId 실행 ID
   * @return 실행 상태 (비동기)
   */
  fun getExecutionStatus(executionId: Long): Mono<ExecutionStatusResponse> {
    kLogger.debug { "Bridge 상태 조회 - executionId: $executionId" }

    return webClient.get()
      .uri("/api/v1/execution/{id}/status", executionId)
      .retrieve()
      .bodyToMono(ExecutionStatusResponse::class.java)
      .timeout(Duration.ofSeconds(10))
      .doOnError { error ->
        kLogger.error(error) { "Bridge 상태 조회 실패 - executionId: $executionId" }
      }
  }

  /**
   * 실행 결과 조회
   *
   * @param executionId 실행 ID
   * @return 실행 결과 (비동기)
   */
  fun getExecutionResult(executionId: Long): Mono<ExecutionResultResponse> {
    kLogger.debug { "Bridge 결과 조회 - executionId: $executionId" }

    return webClient.get()
      .uri("/api/v1/execution/{id}/result", executionId)
      .retrieve()
      .bodyToMono(ExecutionResultResponse::class.java)
      .timeout(Duration.ofSeconds(10))
      .doOnError { error ->
        kLogger.error(error) { "Bridge 결과 조회 실패 - executionId: $executionId" }
      }
  }

  /**
   * 실행 취소
   *
   * @param executionId 실행 ID
   * @return 성공 여부 (비동기)
   */
  fun cancelExecution(executionId: Long): Mono<Boolean> {
    kLogger.info { "Bridge 실행 취소 - executionId: $executionId" }

    return webClient.delete()
      .uri("/api/v1/execution/{id}", executionId)
      .retrieve()
      .toBodilessEntity()
      .map { true }
      .timeout(Duration.ofSeconds(10))
      .doOnSuccess {
        kLogger.info { "Bridge 실행 취소 완료 - executionId: $executionId" }
      }
      .doOnError { error ->
        kLogger.error(error) { "Bridge 실행 취소 실패 - executionId: $executionId" }
      }
      .onErrorReturn(false)
  }

  /**
   * Bridge Service 헬스 체크
   *
   * @return 헬스 상태 (비동기)
   */
  fun healthCheck(): Mono<BridgeHealthResponse> {
    return webClient.get()
      .uri("/health")
      .retrieve()
      .bodyToMono(BridgeHealthResponse::class.java)
      .timeout(Duration.ofSeconds(5))
      .doOnError { error ->
        kLogger.warn(error) { "Bridge 헬스 체크 실패" }
      }
  }

  /**
   * Bridge Service 연결 가능 여부 확인
   *
   * @return 연결 가능 여부
   */
  fun isAvailable(): Boolean {
    return try {
      healthCheck()
        .map { it.status == "healthy" || it.status == "ok" }
        .block(Duration.ofSeconds(5)) ?: false
    } catch (e: Exception) {
      kLogger.warn { "Bridge 연결 불가 - url: $bridgeUrl, error: ${e.message}" }
      false
    }
  }
}

/**
 * Bridge 클라이언트 예외
 */
class BridgeClientException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
