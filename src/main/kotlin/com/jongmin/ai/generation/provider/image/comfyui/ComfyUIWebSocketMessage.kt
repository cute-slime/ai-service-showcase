package com.jongmin.ai.generation.provider.image.comfyui

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * ComfyUI WebSocket 메시지 DTO
 *
 * ComfyUI WebSocket(/ws)에서 전송되는 메시지를 파싱합니다.
 *
 * ### 메시지 타입:
 * - `status`: 큐 상태 정보 (queue_remaining)
 * - `executing`: 현재 실행 중인 노드 정보
 * - `progress`: Sampling 진행 상황 (value/max)
 * - `executed`: 노드 실행 완료 및 출력 정보
 * - `execution_start`: 실행 시작
 * - `execution_cached`: 캐시된 노드 사용
 * - `execution_error`: 실행 에러
 *
 * ### 메시지 예시:
 * ```json
 * // status
 * {"type": "status", "data": {"status": {"exec_info": {"queue_remaining": 2}}}}
 *
 * // executing
 * {"type": "executing", "data": {"node": "3", "prompt_id": "abc123"}}
 *
 * // progress
 * {"type": "progress", "data": {"value": 5, "max": 30, "prompt_id": "abc123", "node": "3"}}
 *
 * // executed
 * {"type": "executed", "data": {"node": "9", "output": {"images": [{"filename": "result.png", "type": "output"}]}}}
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ComfyUIWebSocketMessage(
  /** 메시지 타입 (status, executing, progress, executed 등) */
  val type: String,

  /** 메시지 데이터 (타입에 따라 구조가 다름, 내부 값도 null일 수 있음) */
  val data: Map<String, Any?>?,
) {
  companion object {
    // 메시지 타입 상수
    const val TYPE_STATUS = "status"
    const val TYPE_EXECUTING = "executing"
    const val TYPE_PROGRESS = "progress"
    const val TYPE_EXECUTED = "executed"
    const val TYPE_EXECUTION_START = "execution_start"
    const val TYPE_EXECUTION_CACHED = "execution_cached"
    const val TYPE_EXECUTION_ERROR = "execution_error"
    const val TYPE_EXECUTION_SUCCESS = "execution_success"  // 실행 완료
  }
}

/**
 * 파싱된 ComfyUI 이벤트 (sealed class)
 *
 * WebSocket 메시지를 의미 있는 이벤트로 변환합니다.
 */
sealed class ComfyUIEvent {
  /** 이벤트 관련 promptId (없을 수 있음) */
  abstract val promptId: String?

  /**
   * 큐 상태 이벤트
   *
   * @param queueRemaining 대기열에 남은 작업 수
   */
  data class Status(
    val queueRemaining: Int,
    override val promptId: String? = null,
  ) : ComfyUIEvent()

  /**
   * 실행 시작 이벤트
   *
   * @param promptId 실행 시작된 프롬프트 ID
   */
  data class ExecutionStart(
    override val promptId: String,
  ) : ComfyUIEvent()

  /**
   * 노드 실행 중 이벤트
   *
   * @param nodeId 현재 실행 중인 노드 ID
   * @param promptId 프롬프트 ID
   */
  data class Executing(
    val nodeId: String?,
    override val promptId: String?,
  ) : ComfyUIEvent()

  /**
   * 진행 상황 이벤트 (Sampling)
   *
   * @param value 현재 스텝
   * @param max 전체 스텝
   * @param nodeId 실행 중인 노드 ID
   * @param promptId 프롬프트 ID
   */
  data class Progress(
    val value: Int,
    val max: Int,
    val nodeId: String?,
    override val promptId: String?,
  ) : ComfyUIEvent() {
    /** 진행률 (0~100%) */
    val percentage: Int get() = if (max > 0) (value * 100 / max) else 0
  }

  /**
   * 노드 실행 완료 이벤트
   *
   * @param nodeId 완료된 노드 ID
   * @param images 생성된 이미지 정보 목록
   * @param promptId 프롬프트 ID
   */
  data class Executed(
    val nodeId: String?,
    val images: List<ImageInfo>,
    override val promptId: String?,
  ) : ComfyUIEvent() {

    /**
     * 이미지 정보
     */
    data class ImageInfo(
      val filename: String,
      val type: String,  // "output", "temp" 등
      val subfolder: String = "",
    )
  }

  /**
   * 캐시 사용 이벤트
   *
   * @param cachedNodes 캐시된 노드 ID 목록
   * @param promptId 프롬프트 ID
   */
  data class ExecutionCached(
    val cachedNodes: List<String>,
    override val promptId: String?,
  ) : ComfyUIEvent()

  /**
   * 실행 에러 이벤트
   *
   * @param nodeId 에러 발생 노드 ID
   * @param errorType 에러 타입
   * @param errorMessage 에러 메시지
   * @param promptId 프롬프트 ID
   */
  data class ExecutionError(
    val nodeId: String?,
    val errorType: String?,
    val errorMessage: String?,
    override val promptId: String?,
  ) : ComfyUIEvent()

  /**
   * 실행 성공 이벤트
   *
   * 워크플로우 전체 실행이 완료되면 발생합니다.
   *
   * @param promptId 완료된 프롬프트 ID
   */
  data class ExecutionSuccess(
    override val promptId: String?,
  ) : ComfyUIEvent()

  /**
   * 알 수 없는 이벤트
   */
  data class Unknown(
    val type: String,
    val rawData: Map<String, Any?>?,
    override val promptId: String? = null,
  ) : ComfyUIEvent()
}

/**
 * WebSocket 메시지 → ComfyUIEvent 파서
 */
object ComfyUIEventParser {

  /**
   * WebSocket 메시지를 ComfyUIEvent로 변환
   */
  @Suppress("UNCHECKED_CAST")
  fun parse(message: ComfyUIWebSocketMessage): ComfyUIEvent {
    val data = message.data ?: emptyMap()

    return when (message.type) {
      ComfyUIWebSocketMessage.TYPE_STATUS -> parseStatus(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTION_START -> parseExecutionStart(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTING -> parseExecuting(data)
      ComfyUIWebSocketMessage.TYPE_PROGRESS -> parseProgress(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTED -> parseExecuted(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTION_CACHED -> parseExecutionCached(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTION_ERROR -> parseExecutionError(data)
      ComfyUIWebSocketMessage.TYPE_EXECUTION_SUCCESS -> parseExecutionSuccess(data)
      else -> ComfyUIEvent.Unknown(message.type, data)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseStatus(data: Map<String, Any?>): ComfyUIEvent.Status {
    val status = data["status"] as? Map<String, Any?> ?: emptyMap()
    val execInfo = status["exec_info"] as? Map<String, Any?> ?: emptyMap()
    val queueRemaining = (execInfo["queue_remaining"] as? Number)?.toInt() ?: 0

    return ComfyUIEvent.Status(queueRemaining = queueRemaining)
  }

  private fun parseExecutionStart(data: Map<String, Any?>): ComfyUIEvent.ExecutionStart {
    val promptId = data["prompt_id"] as? String ?: ""
    return ComfyUIEvent.ExecutionStart(promptId = promptId)
  }

  private fun parseExecuting(data: Map<String, Any?>): ComfyUIEvent.Executing {
    return ComfyUIEvent.Executing(
      nodeId = data["node"] as? String,
      promptId = data["prompt_id"] as? String,
    )
  }

  private fun parseProgress(data: Map<String, Any?>): ComfyUIEvent.Progress {
    return ComfyUIEvent.Progress(
      value = (data["value"] as? Number)?.toInt() ?: 0,
      max = (data["max"] as? Number)?.toInt() ?: 1,
      nodeId = data["node"] as? String,
      promptId = data["prompt_id"] as? String,
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseExecuted(data: Map<String, Any?>): ComfyUIEvent.Executed {
    val output = data["output"] as? Map<String, Any?> ?: emptyMap()
    val imagesList = output["images"] as? List<Map<String, Any?>> ?: emptyList()

    val images = imagesList.map { img ->
      ComfyUIEvent.Executed.ImageInfo(
        filename = img["filename"] as? String ?: "",
        type = img["type"] as? String ?: "output",
        subfolder = img["subfolder"] as? String ?: "",
      )
    }

    return ComfyUIEvent.Executed(
      nodeId = data["node"] as? String,
      images = images,
      promptId = data["prompt_id"] as? String,
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseExecutionCached(data: Map<String, Any?>): ComfyUIEvent.ExecutionCached {
    val nodes = data["nodes"] as? List<String> ?: emptyList()
    return ComfyUIEvent.ExecutionCached(
      cachedNodes = nodes,
      promptId = data["prompt_id"] as? String,
    )
  }

  @Suppress("UNCHECKED_CAST")
  private fun parseExecutionError(data: Map<String, Any?>): ComfyUIEvent.ExecutionError {
    val exceptionMessage = data["exception_message"] as? String
    val exceptionType = data["exception_type"] as? String

    return ComfyUIEvent.ExecutionError(
      nodeId = data["node_id"] as? String,
      errorType = exceptionType,
      errorMessage = exceptionMessage,
      promptId = data["prompt_id"] as? String,
    )
  }

  private fun parseExecutionSuccess(data: Map<String, Any?>): ComfyUIEvent.ExecutionSuccess {
    return ComfyUIEvent.ExecutionSuccess(
      promptId = data["prompt_id"] as? String,
    )
  }
}
