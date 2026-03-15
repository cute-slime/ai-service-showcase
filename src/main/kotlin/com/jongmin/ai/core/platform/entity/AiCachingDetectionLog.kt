package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.DetectionStatus
import com.jongmin.ai.core.DetectionStatusConverter
import jakarta.persistence.*
import java.time.ZonedDateTime

/**
 * AI 캐싱 감지 로그
 *
 * 예상치 못한 캐싱이 발생했을 때 이를 기록하여
 * 비용 모니터링 및 디버깅에 활용합니다.
 *
 * 주요 사용 케이스:
 * - 자동 프리픽스 캐싱이 예상치 않게 발생한 경우
 * - Anthropic 명시적 캐싱 블록이 예상치 않게 트리거된 경우
 * - 캐싱 정책 변경 후 모니터링
 *
 * @see com.jongmin.ai.core.platform.component.monitor.UnexpectedCachingDetector
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_cachingLog_detectedAt", columnList = "detectedAt"),
    Index(name = "idx_cachingLog_provider", columnList = "provider"),
    Index(name = "idx_cachingLog_status", columnList = "status"),
    Index(name = "idx_cachingLog_provider_model", columnList = "provider,model"),
  ]
)
data class AiCachingDetectionLog(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, length = 50, updatable = false, comment = "AI 프로바이더명 (OPENAI, ANTHROPIC 등)")
  val provider: String,

  @Column(nullable = false, length = 100, updatable = false, comment = "사용된 모델명")
  val model: String,

  @Column(nullable = false, updatable = false, comment = "캐시된 토큰 수")
  val cachedTokens: Long,

  @Column(nullable = false, updatable = false, comment = "캐시 생성 토큰 수 (Anthropic)")
  val cacheCreationTokens: Long = 0,

  @Lob
  @Column(columnDefinition = "TEXT", updatable = false, comment = "원본 API 응답 (usage 부분)")
  val rawResponse: String,

  @Column(nullable = false, columnDefinition = "TIMESTAMP", updatable = false, comment = "감지 시간")
  val detectedAt: ZonedDateTime = now(),

  @Column(nullable = false, comment = "처리 상태")
  @Convert(converter = DetectionStatusConverter::class)
  var status: DetectionStatus = DetectionStatus.PENDING,

  @Column(length = 500, comment = "관리자 메모")
  var adminNote: String? = null,

  @Column(columnDefinition = "TIMESTAMP", comment = "상태 변경 시간")
  var statusChangedAt: ZonedDateTime? = null,

  @Column(comment = "상태 변경한 관리자 ID")
  var statusChangedBy: Long? = null,
)
