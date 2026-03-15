package com.jongmin.ai.generation.dto

/**
 * 생성 진행 상황 이벤트
 *
 * SSE를 통해 FE에 실시간 전달되는 이벤트 구조.
 * DTE의 기본 이벤트 구조와 호환되면서 상세 진행 상황을 추가로 제공한다.
 *
 * ### DTE 기본 이벤트 (eventType=STATUS):
 * ```json
 * {"jobId":"...", "type":"ASSET_PROMPT_GENERATION", "eventType":"STATUS", "status":"RUNNING", ...}
 * ```
 *
 * ### 상세 진행 이벤트 (eventType=PROGRESS):
 * ```json
 * {
 *   "jobId": "abc-123",
 *   "type": "ASSET_PROMPT_GENERATION",
 *   "eventType": "PROGRESS",
 *   "providerCode": "COMFYUI",
 *   "phase": "GENERATING",
 *   "phaseLabel": "이미지 생성 중",
 *   "step": 3,
 *   "totalSteps": 5,
 *   "progress": 65,
 *   "itemIndex": 2,
 *   "totalItems": 7,
 *   "overallProgress": 35,
 *   "message": "Sampling step 20/30...",
 *   "estimatedRemainingMs": 15000,
 *   "timestamp": 1234567890
 * }
 * ```
 *
 * @property jobId DTE Job ID
 * @property type DTE 태스크 타입 (예: "ASSET_PROMPT_GENERATION")
 * @property eventType 이벤트 타입 (STATUS: DTE 상태, PROGRESS: 상세 진행)
 * @property providerCode 프로바이더 코드 (예: "COMFYUI", "NOVELAI")
 * @property phase 현재 단계 코드
 * @property phaseLabel 현재 단계 라벨 (UI 표시용)
 * @property step 현재 단계 번호 (1부터 시작)
 * @property totalSteps 전체 단계 수
 * @property progress 현재 아이템 진행률 (0~100)
 * @property itemIndex 현재 처리 중인 아이템 인덱스 (0-based)
 * @property totalItems 전체 아이템 수
 * @property overallProgress 전체 Job 진행률 (0~100)
 * @property status 진행 상태
 * @property message 상태 메시지 (상세 정보)
 * @property estimatedRemainingMs 예상 남은 시간 (밀리초, 선택)
 * @property metadata 추가 메타데이터 (선택)
 * @property timestamp 이벤트 발생 시각 (epoch millis)
 *
 * @author Claude Code
 * @since 2026.01.21
 *
 * 참고:
 * 생성 진행 이벤트는 DTE 이벤트 브릿지를 통해 backbone-service SSE 게이트웨이로 전달된다.
 */
data class ProgressEvent(
  val jobId: String,
  val type: String = "ASSET_PROMPT_GENERATION",
  val eventType: EventType = EventType.PROGRESS,
  val providerCode: String,
  val phase: GenerationPhase,
  val phaseLabel: String,
  val step: Int,
  val totalSteps: Int,
  val progress: Int,
  val itemIndex: Int = 0,
  val totalItems: Int = 1,
  val overallProgress: Int = 0,
  val successCount: Int? = null,
  val failedCount: Int? = null,
  val totalDurationMs: Long? = null,
  val status: ProgressStatus,
  val message: String? = null,
  val estimatedRemainingMs: Long? = null,
  val metadata: Map<String, Any>? = null,
  val timestamp: Long = System.currentTimeMillis()
) {
  companion object {

    /**
     * 초기화 이벤트 생성
     */
    fun initializing(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.INITIALIZING,
        phaseLabel = "초기화 중",
        step = 1,
        totalSteps = totalSteps,
        progress = 0,
        status = ProgressStatus.STARTED,
        message = message ?: "생성 작업 초기화 중..."
      )
    }

    /**
     * API 연결 이벤트 생성
     */
    fun connecting(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      progress: Int = 10,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.CONNECTING,
        phaseLabel = "API 연결 중",
        step = 1,
        totalSteps = totalSteps,
        progress = progress,
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: "$providerCode API 연결 중..."
      )
    }

    /**
     * 프롬프트 처리 이벤트 생성
     */
    fun processingPrompt(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      progress: Int = 20,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.PROCESSING_PROMPT,
        phaseLabel = "프롬프트 처리 중",
        step = 2,
        totalSteps = totalSteps,
        progress = progress,
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: "프롬프트 분석 및 전처리 중..."
      )
    }

    /**
     * 대기열 이벤트 생성
     */
    fun queued(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      queuePosition: Int? = null,
      estimatedWaitMs: Long? = null,
      message: String? = null
    ): ProgressEvent {
      val queueMsg = queuePosition?.let { "대기열 ${it}번째..." } ?: "대기열 진입..."
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.QUEUED,
        phaseLabel = "대기열",
        step = 2,
        totalSteps = totalSteps,
        progress = 25,
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: queueMsg,
        estimatedRemainingMs = estimatedWaitMs,
        metadata = queuePosition?.let { mapOf("queuePosition" to it) }
      )
    }

    /**
     * 생성 중 이벤트 생성 (세밀한 진행률 지원)
     */
    fun generating(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      progress: Int,
      currentStep: Int? = null,
      maxStep: Int? = null,
      estimatedRemainingMs: Long? = null,
      message: String? = null
    ): ProgressEvent {
      val stepMsg = if (currentStep != null && maxStep != null) {
        "생성 중... ($currentStep/$maxStep)"
      } else {
        "생성 중... ($progress%)"
      }
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.GENERATING,
        phaseLabel = "생성 중",
        step = 3,
        totalSteps = totalSteps,
        progress = progress.coerceIn(30, 85),
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: stepMsg,
        estimatedRemainingMs = estimatedRemainingMs,
        metadata = if (currentStep != null && maxStep != null) {
          mapOf("currentStep" to currentStep, "maxStep" to maxStep)
        } else null
      )
    }

    /**
     * 후처리 이벤트 생성
     */
    fun postProcessing(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      progress: Int = 90,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.POST_PROCESSING,
        phaseLabel = "후처리 중",
        step = 4,
        totalSteps = totalSteps,
        progress = progress.coerceIn(85, 95),
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: "결과 후처리 및 최적화 중..."
      )
    }

    /**
     * 업로드 이벤트 생성
     */
    fun uploading(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      progress: Int = 95,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.UPLOADING,
        phaseLabel = "업로드 중",
        step = 5,
        totalSteps = totalSteps,
        progress = progress.coerceIn(90, 99),
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: "결과물 저장 중..."
      )
    }

    /**
     * 개별 아이템 완료 이벤트 생성
     */
    fun itemCompleted(
      jobId: String,
      providerCode: String,
      totalSteps: Int = 5,
      itemIndex: Int,
      totalItems: Int,
      outputUrl: String? = null,
      durationMs: Long? = null,
      message: String? = null,
      additionalMetadata: Map<String, Any> = emptyMap(),
    ): ProgressEvent {
      val overallProgress = ((itemIndex + 1) * 100) / totalItems
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.ITEM_COMPLETED,
        phaseLabel = "아이템 완료",
        step = totalSteps,
        totalSteps = totalSteps,
        progress = 100,
        itemIndex = itemIndex,
        totalItems = totalItems,
        overallProgress = overallProgress,
        status = ProgressStatus.IN_PROGRESS,
        message = message ?: "아이템 ${itemIndex + 1}/${totalItems} 생성 완료!",
        metadata = buildMap {
          outputUrl?.let { put("outputUrl", it) }
          durationMs?.let { put("durationMs", it) }
          put("itemIndex", itemIndex)
          put("totalItems", totalItems)
          additionalMetadata.forEach { (key, value) ->
            put(key, value)
          }
        }.ifEmpty { null }
      )
    }

    /**
     * 전체 Job 완료 이벤트 생성
     */
    fun jobCompleted(
      jobId: String,
      providerCode: String,
      totalItems: Int,
      successCount: Int,
      failedCount: Int = 0,
      totalDurationMs: Long? = null,
      message: String? = null
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = GenerationPhase.JOB_COMPLETED,
        phaseLabel = "전체 완료",
        step = 5,
        totalSteps = 5,
        progress = 100,
        itemIndex = totalItems - 1,
        totalItems = totalItems,
        overallProgress = 100,
        successCount = successCount,
        failedCount = failedCount,
        totalDurationMs = totalDurationMs,
        status = ProgressStatus.COMPLETED,
        message = message ?: "전체 생성 완료! (성공: $successCount, 실패: $failedCount)",
        metadata = buildMap {
          put("totalItems", totalItems)
          put("successCount", successCount)
          put("failedCount", failedCount)
          totalDurationMs?.let { put("totalDurationMs", it) }
        }
      )
    }

    /**
     * 실패 이벤트 생성
     */
    fun failed(
      jobId: String,
      providerCode: String,
      phase: GenerationPhase,
      step: Int,
      totalSteps: Int = 5,
      errorCode: String? = null,
      errorMessage: String
    ): ProgressEvent {
      return ProgressEvent(
        jobId = jobId,
        providerCode = providerCode,
        phase = phase,
        phaseLabel = phase.label,
        step = step,
        totalSteps = totalSteps,
        progress = 0,
        status = ProgressStatus.FAILED,
        message = errorMessage,
        metadata = errorCode?.let { mapOf("errorCode" to it) }
      )
    }
  }
}

/**
 * 이벤트 타입
 */
enum class EventType {
  /** DTE 상태 이벤트 (PENDING, RUNNING, COMPLETED 등) */
  STATUS,

  /** 상세 진행 상황 이벤트 */
  PROGRESS
}

/**
 * 생성 단계 (대시보드 표시용)
 */
enum class GenerationPhase(val label: String, val order: Int) {
  /** 초기화 */
  INITIALIZING("초기화 중", 1),

  /** API 연결 */
  CONNECTING("API 연결 중", 2),

  /** 프롬프트 처리 */
  PROCESSING_PROMPT("프롬프트 처리 중", 3),

  /** 대기열 */
  QUEUED("대기열", 4),

  /** 생성 중 */
  GENERATING("생성 중", 5),

  /** 후처리 */
  POST_PROCESSING("후처리 중", 6),

  /** 업로드 */
  UPLOADING("업로드 중", 7),

  /** 개별 아이템 완료 */
  ITEM_COMPLETED("아이템 완료", 9),

  /** 전체 Job 완료 */
  JOB_COMPLETED("전체 완료", 10),

  /** 실패 */
  FAILED("실패", -1)
}

/**
 * 진행 상태
 */
enum class ProgressStatus {
  /** 시작됨 */
  STARTED,

  /** 진행 중 */
  IN_PROGRESS,

  /** 완료 */
  COMPLETED,

  /** 실패 */
  FAILED
}
