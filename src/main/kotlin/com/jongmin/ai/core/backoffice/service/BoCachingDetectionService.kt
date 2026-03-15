package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.core.exception.ObjectNotFoundException
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiCachingDetectionLogRepository
import com.jongmin.ai.core.DetectionStatus
import com.jongmin.ai.core.backoffice.dto.request.UpdateCachingDetectionStatus
import com.jongmin.ai.core.backoffice.dto.response.BoCachingDetectionItem
import com.jongmin.ai.core.backoffice.dto.response.BoCachingDetectionStats
import com.jongmin.ai.core.platform.entity.QAiCachingDetectionLog.aiCachingDetectionLog
import com.querydsl.core.types.dsl.BooleanExpression
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 캐싱 감지 로그 백오피스 서비스
 *
 * 예상치 못한 캐싱 감지 로그를 조회하고 관리하는 백오피스 기능을 제공합니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Service
class BoCachingDetectionService(
  private val cachingDetectionLogRepository: AiCachingDetectionLogRepository
) {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 캐싱 감지 로그 단건 조회
   *
   * @param id 로그 ID
   * @return 캐싱 감지 로그 DTO
   * @throws ObjectNotFoundException 로그를 찾을 수 없는 경우
   */
  @Transactional(readOnly = true)
  fun findById(id: Long): BoCachingDetectionItem {
    val log = cachingDetectionLogRepository.findById(id)
      .orElseThrow { ObjectNotFoundException("캐싱 감지 로그를 찾을 수 없습니다. id: $id") }
    return BoCachingDetectionItem.from(log)
  }

  /**
   * 캐싱 감지 로그 목록 조회 (페이징)
   *
   * @param statuses 필터링할 상태 목록 (null이면 전체)
   * @param provider 필터링할 프로바이더명 (null이면 전체)
   * @param model 필터링할 모델명 (null이면 전체)
   * @param pageable 페이징 정보
   * @return 페이징된 캐싱 감지 로그 목록
   */
  @Transactional(readOnly = true)
  fun findAll(
    statuses: Set<DetectionStatus>?,
    provider: String?,
    model: String?,
    pageable: Pageable
  ): Page<BoCachingDetectionItem> {
    val predicate = buildPredicate(statuses, provider, model)

    return if (predicate != null) {
      cachingDetectionLogRepository.findAll(predicate, pageable)
        .map { BoCachingDetectionItem.from(it) }
    } else {
      cachingDetectionLogRepository.findAll(pageable)
        .map { BoCachingDetectionItem.from(it) }
    }
  }

  /**
   * 캐싱 감지 로그 통계 조회
   *
   * @return 캐싱 감지 통계
   */
  @Transactional(readOnly = true)
  fun getStats(): BoCachingDetectionStats {
    val allLogs = cachingDetectionLogRepository.findAll()

    val totalCount = allLogs.size.toLong()
    val pendingCount = allLogs.count { it.status == DetectionStatus.PENDING }.toLong()
    val confirmedOkCount = allLogs.count { it.status == DetectionStatus.CONFIRMED_OK }.toLong()
    val confirmedIssueCount = allLogs.count { it.status == DetectionStatus.CONFIRMED_ISSUE }.toLong()
    val ignoredCount = allLogs.count { it.status == DetectionStatus.IGNORED }.toLong()

    val byProvider = allLogs.groupBy { it.provider }
      .mapValues { it.value.size.toLong() }

    val byModel = allLogs.groupBy { it.model }
      .mapValues { it.value.size.toLong() }

    return BoCachingDetectionStats(
      totalCount = totalCount,
      pendingCount = pendingCount,
      confirmedOkCount = confirmedOkCount,
      confirmedIssueCount = confirmedIssueCount,
      ignoredCount = ignoredCount,
      byProvider = byProvider,
      byModel = byModel
    )
  }

  /**
   * 캐싱 감지 로그 상태 변경
   *
   * @param dto 상태 변경 요청 DTO
   * @param adminId 변경하는 관리자 ID
   * @return 변경된 캐싱 감지 로그 DTO
   * @throws ObjectNotFoundException 로그를 찾을 수 없는 경우
   */
  @Transactional
  fun updateStatus(dto: UpdateCachingDetectionStatus, adminId: Long): BoCachingDetectionItem {
    val log = cachingDetectionLogRepository.findById(dto.id)
      .orElseThrow { ObjectNotFoundException("캐싱 감지 로그를 찾을 수 없습니다. id: ${dto.id}") }

    val oldStatus = log.status

    log.status = dto.status
    log.adminNote = dto.adminNote
    log.statusChangedAt = now()
    log.statusChangedBy = adminId

    val savedLog = cachingDetectionLogRepository.save(log)

    kLogger.info {
      "[캐싱 감지] 상태 변경 - logId: ${dto.id}, " +
          "oldStatus: $oldStatus, newStatus: ${dto.status}, " +
          "adminId: $adminId"
    }

    return BoCachingDetectionItem.from(savedLog)
  }

  /**
   * PENDING 상태 로그 일괄 처리 (무시 처리)
   *
   * @param ids 처리할 로그 ID 목록
   * @param adminId 처리하는 관리자 ID
   * @return 처리된 건수
   */
  @Transactional
  fun bulkIgnore(ids: List<Long>, adminId: Long): Int {
    var count = 0

    ids.forEach { id ->
      try {
        val log = cachingDetectionLogRepository.findById(id).orElse(null)
        if (log != null && log.status == DetectionStatus.PENDING) {
          log.status = DetectionStatus.IGNORED
          log.statusChangedAt = now()
          log.statusChangedBy = adminId
          cachingDetectionLogRepository.save(log)
          count++
        }
      } catch (e: Exception) {
        kLogger.warn(e) { "[캐싱 감지] 일괄 무시 처리 실패 - logId: $id" }
      }
    }

    kLogger.info { "[캐싱 감지] 일괄 무시 처리 완료 - 처리 건수: $count/${ids.size}, adminId: $adminId" }

    return count
  }

  /**
   * 조회 조건 빌드
   */
  private fun buildPredicate(
    statuses: Set<DetectionStatus>?,
    provider: String?,
    model: String?
  ): BooleanExpression? {
    var predicate: BooleanExpression? = null

    if (!statuses.isNullOrEmpty()) {
      predicate = aiCachingDetectionLog.status.`in`(statuses)
    }

    if (!provider.isNullOrBlank()) {
      val providerPredicate = aiCachingDetectionLog.provider.equalsIgnoreCase(provider)
      predicate = predicate?.and(providerPredicate) ?: providerPredicate
    }

    if (!model.isNullOrBlank()) {
      val modelPredicate = aiCachingDetectionLog.model.containsIgnoreCase(model)
      predicate = predicate?.and(modelPredicate) ?: modelPredicate
    }

    return predicate
  }
}
