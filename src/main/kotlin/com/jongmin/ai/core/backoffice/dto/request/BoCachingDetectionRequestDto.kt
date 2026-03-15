package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.DetectionStatus
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 캐싱 감지 로그 상태 변경 요청 DTO
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Schema(description = "캐싱 감지 로그 상태 변경 요청")
data class UpdateCachingDetectionStatus(
  @field:Schema(description = "로그 ID", required = true, example = "1")
  val id: Long,

  @field:Schema(description = "변경할 상태", required = true, example = "CONFIRMED_OK")
  val status: DetectionStatus,

  @field:Schema(description = "관리자 메모", required = false, example = "프로바이더 설정 변경으로 인한 정상 캐싱")
  val adminNote: String? = null
)
