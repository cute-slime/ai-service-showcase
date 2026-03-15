package com.jongmin.ai.generation.dto

import com.jongmin.ai.core.GenerationCostUnitType
import java.math.BigDecimal

/**
 * 에셋 생성 결과
 *
 * 프로바이더가 에셋 생성을 완료한 후 반환하는 결과 객체.
 * 성공 시 생성된 리소스 정보를, 실패 시 에러 정보를 담는다.
 *
 * ### 성공 예시:
 * ```kotlin
 * val result = GenerationResult.success(
 *   outputUrl = "https://s3.../generated-image.png",
 *   metadata = mapOf("width" to 1024, "height" to 1024)
 * )
 * ```
 *
 * ### 실패 예시:
 * ```kotlin
 * val result = GenerationResult.failure(
 *   errorCode = "TIMEOUT",
 *   errorMessage = "생성 시간 초과"
 * )
 * ```
 *
 * @property success 성공 여부
 * @property outputUrl 생성된 리소스 URL (성공 시)
 * @property outputUrls 생성된 리소스 URL 목록 (여러 개 생성 시)
 * @property thumbnailUrl 썸네일 URL (선택)
 * @property errorCode 에러 코드 (실패 시)
 * @property errorMessage 에러 메시지 (실패 시)
 * @property metadata 추가 메타데이터 (생성 정보 등)
 * @property providerResponse 프로바이더 원본 응답 (디버깅용)
 * @property durationMs 생성 소요 시간 (밀리초)
 * @property timestamp 결과 생성 시각
 * @property estimatedCost 예상 비용
 * @property costCurrency 비용 통화 (USD, KRW 등)
 * @property costUnitType 비용 단위 타입
 * @property appliedCostRuleId 적용된 비용 규칙 ID
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class GenerationResult(
  val success: Boolean,
  val outputUrl: String? = null,
  val outputUrls: List<String> = emptyList(),
  val thumbnailUrl: String? = null,
  val errorCode: String? = null,
  val errorMessage: String? = null,
  val metadata: Map<String, Any> = emptyMap(),
  val providerResponse: Map<String, Any>? = null,
  val durationMs: Long? = null,
  val timestamp: Long = System.currentTimeMillis(),
  // 비용 정보
  val estimatedCost: BigDecimal? = null,
  val costCurrency: String? = null,
  val costUnitType: GenerationCostUnitType? = null,
  val appliedCostRuleId: Long? = null,
  // 워크플로우 정보 (재생성용)
  val workflowJson: String? = null,
  val seed: Long? = null,
) {
  companion object {
    /**
     * 성공 결과 생성
     */
    fun success(
      outputUrl: String,
      thumbnailUrl: String? = null,
      metadata: Map<String, Any> = emptyMap(),
      durationMs: Long? = null,
      estimatedCost: BigDecimal? = null,
      costCurrency: String? = null,
      costUnitType: GenerationCostUnitType? = null,
      appliedCostRuleId: Long? = null,
      workflowJson: String? = null,
      seed: Long? = null,
    ): GenerationResult {
      return GenerationResult(
        success = true,
        outputUrl = outputUrl,
        outputUrls = listOf(outputUrl),
        thumbnailUrl = thumbnailUrl,
        metadata = metadata,
        durationMs = durationMs,
        estimatedCost = estimatedCost,
        costCurrency = costCurrency,
        costUnitType = costUnitType,
        appliedCostRuleId = appliedCostRuleId,
        workflowJson = workflowJson,
        seed = seed,
      )
    }

    /**
     * 다중 출력 성공 결과 생성
     */
    fun successMultiple(
      outputUrls: List<String>,
      thumbnailUrl: String? = null,
      metadata: Map<String, Any> = emptyMap(),
      durationMs: Long? = null,
      estimatedCost: BigDecimal? = null,
      costCurrency: String? = null,
      costUnitType: GenerationCostUnitType? = null,
      appliedCostRuleId: Long? = null
    ): GenerationResult {
      return GenerationResult(
        success = true,
        outputUrl = outputUrls.firstOrNull(),
        outputUrls = outputUrls,
        thumbnailUrl = thumbnailUrl,
        metadata = metadata,
        durationMs = durationMs,
        estimatedCost = estimatedCost,
        costCurrency = costCurrency,
        costUnitType = costUnitType,
        appliedCostRuleId = appliedCostRuleId
      )
    }

    /**
     * 실패 결과 생성
     */
    fun failure(
      errorMessage: String,
      errorCode: String? = null,
      providerResponse: Map<String, Any>? = null
    ): GenerationResult {
      return GenerationResult(
        success = false,
        errorCode = errorCode ?: "GENERATION_FAILED",
        errorMessage = errorMessage,
        providerResponse = providerResponse
      )
    }

    /**
     * 타임아웃 실패 결과 생성
     */
    fun timeout(timeoutMs: Long): GenerationResult {
      return GenerationResult(
        success = false,
        errorCode = "TIMEOUT",
        errorMessage = "생성 시간 초과: ${timeoutMs}ms"
      )
    }

    /**
     * 프로바이더 연결 실패 결과 생성
     */
    fun connectionFailed(providerCode: String, cause: String): GenerationResult {
      return GenerationResult(
        success = false,
        errorCode = "CONNECTION_FAILED",
        errorMessage = "$providerCode 연결 실패: $cause"
      )
    }
  }

  /**
   * 결과가 성공인지 확인
   */
  fun isSuccess(): Boolean = success

  /**
   * 결과가 실패인지 확인
   */
  fun isFailure(): Boolean = !success

  /**
   * 첫 번째 출력 URL 반환 (없으면 예외)
   */
  fun getOutputUrlOrThrow(): String {
    return outputUrl ?: throw IllegalStateException("Output URL is not available: $errorMessage")
  }
}
