package com.jongmin.ai.core.backoffice.dto.response

/**
 * 일괄 삭제 API 응답 DTO
 *
 * Partial Success 방식: 존재하는 ID만 삭제, 존재하지 않는 ID는 무시
 *
 * @property deletedCount 실제 삭제된 항목 수
 * @property deletedIds 삭제에 성공한 ID 목록
 * @property failedIds 존재하지 않아 삭제되지 않은 ID 목록
 */
data class BulkDeleteResult(
  val deletedCount: Int,
  val deletedIds: List<Long>,
  val failedIds: List<Long> = emptyList(),
)

/**
 * UUID 기반 일괄 삭제 API 응답 DTO (Loop Job 등에서 사용)
 *
 * @property deletedCount 실제 삭제된 항목 수
 * @property deletedIds 삭제에 성공한 ID 목록 (UUID 문자열)
 * @property failedIds 존재하지 않아 삭제되지 않은 ID 목록 (UUID 문자열)
 */
data class BulkDeleteUuidResult(
  val deletedCount: Int,
  val deletedIds: List<String>,
  val failedIds: List<String> = emptyList(),
)
