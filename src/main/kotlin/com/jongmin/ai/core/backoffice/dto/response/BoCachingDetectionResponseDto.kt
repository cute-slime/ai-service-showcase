package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.ai.core.DetectionStatus
import com.jongmin.ai.core.platform.entity.AiCachingDetectionLog
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

/**
 * 캐싱 감지 로그 응답 DTO
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Schema(description = "캐싱 감지 로그 항목")
data class BoCachingDetectionItem(
  @field:Schema(description = "로그 ID", example = "1")
  val id: Long,

  @field:Schema(description = "AI 프로바이더명", example = "OPENAI")
  val provider: String,

  @field:Schema(description = "사용된 모델명", example = "gpt-4o")
  val model: String,

  @field:Schema(description = "캐시된 토큰 수", example = "1500")
  val cachedTokens: Long,

  @field:Schema(description = "캐시 생성 토큰 수", example = "0")
  val cacheCreationTokens: Long,

  @field:Schema(description = "원본 API 응답 (usage 부분)")
  val rawResponse: String,

  @field:Schema(description = "감지 시간")
  val detectedAt: ZonedDateTime,

  @field:Schema(description = "처리 상태", example = "PENDING")
  val status: DetectionStatus,

  @field:Schema(description = "관리자 메모", nullable = true)
  val adminNote: String?,

  @field:Schema(description = "상태 변경 시간", nullable = true)
  val statusChangedAt: ZonedDateTime?,

  @field:Schema(description = "상태 변경한 관리자 ID", nullable = true)
  val statusChangedBy: Long?
) {
  companion object {
    /**
     * 엔티티에서 DTO 변환
     */
    fun from(entity: AiCachingDetectionLog): BoCachingDetectionItem {
      return BoCachingDetectionItem(
        id = entity.id,
        provider = entity.provider,
        model = entity.model,
        cachedTokens = entity.cachedTokens,
        cacheCreationTokens = entity.cacheCreationTokens,
        rawResponse = entity.rawResponse,
        detectedAt = entity.detectedAt,
        status = entity.status,
        adminNote = entity.adminNote,
        statusChangedAt = entity.statusChangedAt,
        statusChangedBy = entity.statusChangedBy
      )
    }
  }
}

/**
 * 캐싱 감지 로그 통계 응답 DTO
 */
@Schema(description = "캐싱 감지 로그 통계")
data class BoCachingDetectionStats(
  @field:Schema(description = "전체 감지 건수", example = "150")
  val totalCount: Long,

  @field:Schema(description = "대기 중 건수", example = "10")
  val pendingCount: Long,

  @field:Schema(description = "확인 완료 (정상) 건수", example = "100")
  val confirmedOkCount: Long,

  @field:Schema(description = "확인 완료 (조치 필요) 건수", example = "5")
  val confirmedIssueCount: Long,

  @field:Schema(description = "무시됨 건수", example = "35")
  val ignoredCount: Long,

  @field:Schema(description = "프로바이더별 감지 건수")
  val byProvider: Map<String, Long>,

  @field:Schema(description = "모델별 감지 건수")
  val byModel: Map<String, Long>
)
