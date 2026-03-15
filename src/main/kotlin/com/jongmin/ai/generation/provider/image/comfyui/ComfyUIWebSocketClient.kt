package com.jongmin.ai.generation.provider.image.comfyui

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.WebsocketClientSpec
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ComfyUI WebSocket 클라이언트
 *
 * ComfyUI WebSocket(/ws)에 연결하여 실시간 진행 상황을 수신합니다.
 *
 * ### 기능:
 * - WebSocket 연결 및 메시지 수신
 * - 진행 상황 이벤트 파싱 및 콜백 호출
 * - 특정 promptId만 필터링
 * - 타임아웃 및 에러 처리
 *
 * ### 사용 예시:
 * ```kotlin
 * val result = webSocketClient.streamProgress(
 *   promptId = "abc123",
 *   clientId = "my-client",
 *   timeoutMinutes = 10
 * ) { event ->
 *   when (event) {
 *     is ComfyUIEvent.Progress -> println("Step ${event.value}/${event.max}")
 *     is ComfyUIEvent.Executed -> println("완료: ${event.images}")
 *     else -> {}
 *   }
 * }
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Component
class ComfyUIWebSocketClient(
  private val runtimeConfigResolver: ComfyUIRuntimeConfigResolver,
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /**
     * 무시할 이벤트 타입 목록
     *
     * ComfyUI가 지속적으로 보내는 시스템 모니터링 이벤트 등
     * 로그 출력 없이 조용히 무시합니다.
     */
    private val IGNORED_EVENT_TYPES = setOf(
      "crystools.monitor",  // ComfyUI 시스템 모니터링 (매우 빈번하게 발생)
      "progress_state",     // 진행 상태 변경 알림 (이미 progress 이벤트로 처리)
    )
  }

  /**
   * WebSocket 연결 후 진행 상황 스트리밍
   *
   * 지정된 promptId의 작업이 완료될 때까지 WebSocket을 통해
   * 실시간 진행 상황을 수신하고 콜백으로 전달합니다.
   *
   * @param promptId 추적할 프롬프트 ID
   * @param clientId WebSocket 클라이언트 ID
   * @param timeoutMinutes 타임아웃 (분)
   * @param onEvent 이벤트 콜백
   * @return 정상 완료 시 true, 타임아웃/에러 시 false
   */
  fun streamProgress(
    promptId: String,
    clientId: String,
    timeoutMinutes: Long? = null,
    onEvent: (ComfyUIEvent) -> Unit,
  ): Boolean {
    val runtimeConfig = runtimeConfigResolver.resolve()
    val effectiveTimeoutMinutes = timeoutMinutes ?: runtimeConfig.timeoutMinutes
    val wsUrl = "${runtimeConfig.webSocketUrl}?clientId=$clientId"
    val completed = AtomicBoolean(false)
    val completionSink = Sinks.one<Boolean>()

    kLogger.info { "[ComfyUI WS] 연결 시작 - url: $wsUrl, promptId: $promptId" }

    try {
      val httpClient = HttpClient.create()
        .responseTimeout(Duration.ofMinutes(effectiveTimeoutMinutes))

      httpClient
        .websocket(WebsocketClientSpec.builder().maxFramePayloadLength(1024 * 1024).build())
        .uri(URI.create(wsUrl))
        .handle { inbound, _ ->
          inbound.receive()
            .asString()
            .doOnNext { payload ->
              try {
                val message = objectMapper.readValue(payload, ComfyUIWebSocketMessage::class.java)
                val event = ComfyUIEventParser.parse(message)

                // 모든 promptId 또는 일치하는 promptId만 처리
                if (event.promptId == null || event.promptId == promptId) {
                  handleEvent(event, promptId, onEvent, completed, completionSink)
                }
              } catch (e: Exception) {
                kLogger.debug { "[ComfyUI WS] 메시지 파싱 실패: ${e.message}" }
              }
            }
            .doOnComplete {
              kLogger.info { "[ComfyUI WS] 연결 종료 - promptId: $promptId" }
              completionSink.tryEmitValue(completed.get())
            }
            .doOnError { e ->
              kLogger.warn(e) { "[ComfyUI WS] 연결 에러 - promptId: $promptId" }
              completionSink.tryEmitValue(false)
            }
            .then()
        }
        .subscribe()

      // 완료 대기 (타임아웃 포함)
      return completionSink.asMono()
        .timeout(Duration.ofMinutes(effectiveTimeoutMinutes))
        .onErrorReturn(false)
        .block() ?: false

    } catch (e: Exception) {
      kLogger.error(e) { "[ComfyUI WS] 연결 실패 - promptId: $promptId" }
      return false
    }
  }

  /**
   * 이벤트 처리
   */
  private fun handleEvent(
    event: ComfyUIEvent,
    targetPromptId: String,
    onEvent: (ComfyUIEvent) -> Unit,
    completed: AtomicBoolean,
    completionSink: Sinks.One<Boolean>,
  ) {
    when (event) {
      is ComfyUIEvent.Progress -> {
        kLogger.debug { "[ComfyUI WS] Progress - ${event.value}/${event.max} (${event.percentage}%)" }
        onEvent(event)
      }

      is ComfyUIEvent.Executing -> {
        if (event.nodeId == null && event.promptId == targetPromptId) {
          // node가 null이면 작업 완료를 의미
          kLogger.info { "[ComfyUI WS] 작업 완료 신호 - promptId: $targetPromptId" }
          completed.set(true)
          completionSink.tryEmitValue(true)
        } else {
          kLogger.debug { "[ComfyUI WS] Executing node: ${event.nodeId}" }
          onEvent(event)
        }
      }

      is ComfyUIEvent.Executed -> {
        kLogger.info { "[ComfyUI WS] 노드 실행 완료 - node: ${event.nodeId}, images: ${event.images.size}" }
        onEvent(event)
      }

      is ComfyUIEvent.ExecutionStart -> {
        kLogger.info { "[ComfyUI WS] 실행 시작 - promptId: ${event.promptId}" }
        onEvent(event)
      }

      is ComfyUIEvent.ExecutionError -> {
        kLogger.error { "[ComfyUI WS] 실행 에러 - type: ${event.errorType}, message: ${event.errorMessage}" }
        onEvent(event)
        completed.set(false)
        completionSink.tryEmitValue(false)
      }

      is ComfyUIEvent.ExecutionSuccess -> {
        // 워크플로우 전체 실행 완료 신호
        kLogger.info { "[ComfyUI WS] 실행 완료 - promptId: ${event.promptId}" }
        onEvent(event)
        completed.set(true)
        completionSink.tryEmitValue(true)
      }

      is ComfyUIEvent.Status -> {
        kLogger.debug { "[ComfyUI WS] 큐 상태 - remaining: ${event.queueRemaining}" }
        onEvent(event)
      }

      is ComfyUIEvent.ExecutionCached -> {
        kLogger.debug { "[ComfyUI WS] 캐시 사용 - nodes: ${event.cachedNodes}" }
        onEvent(event)
      }

      is ComfyUIEvent.Unknown -> {
        // 무시할 이벤트는 로그 출력하지 않음 (crystools.monitor 등)
        if (event.type !in IGNORED_EVENT_TYPES) {
          kLogger.debug { "[ComfyUI WS] 알 수 없는 이벤트: ${event.type}" }
        }
      }
    }
  }

  /**
   * 비동기 방식으로 진행 상황 스트리밍 (Mono 반환)
   *
   * blocking 없이 비동기로 처리해야 할 경우 사용합니다.
   *
   * @param promptId 추적할 프롬프트 ID
   * @param clientId WebSocket 클라이언트 ID
   * @param timeoutMinutes 타임아웃 (분)
   * @param onEvent 이벤트 콜백
   * @return 정상 완료 시 true, 타임아웃/에러 시 false
   */
  fun streamProgressAsync(
    promptId: String,
    clientId: String,
    timeoutMinutes: Long? = null,
    onEvent: (ComfyUIEvent) -> Unit,
  ): Mono<Boolean> {
    return Mono.fromCallable {
      streamProgress(promptId, clientId, timeoutMinutes, onEvent)
    }
  }
}
