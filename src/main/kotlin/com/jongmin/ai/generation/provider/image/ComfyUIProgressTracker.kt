package com.jongmin.ai.generation.provider.image

import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.dto.ProgressEvent
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIEvent
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIWebSocketClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * ComfyUI WebSocket 진행 상황 추적기
 *
 * WebSocket을 통한 실시간 진행 상황 수신 및 이벤트 변환을 담당한다.
 * 부모 클래스의 emitProgressWithContext 호출은 콜백 함수로 위임한다.
 *
 * @author Claude Code
 * @since 2026.02.19 (ComfyUIProvider에서 분리)
 */
@Component
class ComfyUIProgressTracker(
  private val webSocketClient: ComfyUIWebSocketClient,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * WebSocket으로 실시간 진행 상황 수신
   *
   * @param promptId ComfyUI 프롬프트 ID
   * @param clientId WebSocket 클라이언트 ID
   * @param context 생성 요청 컨텍스트
   * @param totalSteps 모델별 전체 스텝 수
   * @param emitProgress 진행 이벤트 발행 콜백 (ProgressEvent, GenerationContext) -> Unit
   * @return 정상 완료 시 true, 실패 시 false
   */
  fun tryWebSocketProgress(
    promptId: String,
    clientId: String,
    context: GenerationContext,
    totalSteps: Int,
    emitProgress: (ProgressEvent, GenerationContext) -> Unit,
  ): Boolean {
    return try {
      webSocketClient.streamProgress(
        promptId = promptId,
        clientId = clientId,
      ) { event ->
        handleWebSocketEvent(event, context, totalSteps, emitProgress)
      }
    } catch (e: Exception) {
      kLogger.warn(e) { "[ComfyUI] WebSocket 진행 상황 수신 실패 - promptId: $promptId" }
      false
    }
  }

  /**
   * WebSocket 이벤트 처리 -> emitProgress 콜백 호출
   *
   * 각 이벤트에 itemIndex, totalItems, overallProgress 정보를 포함하여 발행
   */
  private fun handleWebSocketEvent(
    event: ComfyUIEvent,
    context: GenerationContext,
    totalSteps: Int,
    emitProgress: (ProgressEvent, GenerationContext) -> Unit,
  ) {
    when (event) {
      is ComfyUIEvent.Progress -> {
        // 30% ~ 85% 범위에서 진행률 계산
        val progressPercent = 30 + ((event.value.toDouble() / event.max) * 55).toInt()
        val estimatedRemaining = ((event.max - event.value) * 100L)

        emitProgress(
          ProgressEvent.generating(
            jobId = context.jobId,
            providerCode = ComfyUIConstants.PROVIDER_CODE,
            progress = progressPercent,
            currentStep = event.value,
            maxStep = event.max,
            estimatedRemainingMs = estimatedRemaining,
            message = "Sampling step ${event.value}/${event.max}..."
          ),
          context
        )
      }

      is ComfyUIEvent.Executing -> {
        if (event.nodeId != null) {
          kLogger.debug { "[ComfyUI] 노드 실행 중 - node: ${event.nodeId}" }
        }
      }

      is ComfyUIEvent.Executed -> {
        emitProgress(
          ProgressEvent.postProcessing(
            jobId = context.jobId,
            providerCode = ComfyUIConstants.PROVIDER_CODE,
            progress = 88,
            message = "이미지 생성 완료, 후처리 중..."
          ),
          context
        )
      }

      is ComfyUIEvent.Status -> {
        if (event.queueRemaining > 0) {
          emitProgress(
            ProgressEvent.queued(
              jobId = context.jobId,
              providerCode = ComfyUIConstants.PROVIDER_CODE,
              queuePosition = event.queueRemaining,
              estimatedWaitMs = event.queueRemaining * 5000L,
              message = "대기열 ${event.queueRemaining}번째..."
            ),
            context
          )
        }
      }

      is ComfyUIEvent.ExecutionError -> {
        kLogger.error { "[ComfyUI] 실행 에러 - type: ${event.errorType}, message: ${event.errorMessage}" }
      }

      else -> {
        // 기타 이벤트는 무시
      }
    }
  }
}
