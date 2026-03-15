package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.core.CachingType
import com.jongmin.ai.core.CachingTypeConverter
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "unq_aiProvider_name", columnList = "name"),
    Index(name = "idx_aiProvider_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiProvider_status", columnList = "status"),
  ]
)
data class AiProvider(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(length = 40, nullable = false, updatable = false, comment = "AI 제공사 이름, 수정 불가로 작성할 때 유의해야함")
  var name: String,

  @Column(length = 500, comment = "AI 제공사 설명")
  var description: String,

  @Column(nullable = true, length = 240, comment = "API Base URL, 인증된 글로벌 모델은 이 필드를 사용하지 않음")
  var baseUrl: String?,

  // ========== 캐싱 정책 필드 (AI 캐시 모니터링 시스템) ==========

  @Convert(converter = CachingTypeConverter::class)
  @Column(nullable = false, comment = "캐싱 방식: AUTOMATIC_PREFIX(자동), EXPLICIT(명시적), NONE(미지원)")
  var cachingType: CachingType = CachingType.UNKNOWN,

  @Column(nullable = false, comment = "캐싱 최소 토큰 수 (이 이상이어야 캐싱 효과 발생, 기본 1024)")
  var minCacheTokens: Int = 1024,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(5, 4)",
    comment = "이 프로바이더의 기본 캐시 할인율 (개별 모델에서 재정의 가능)"
  )
  var defaultCacheDiscountRate: BigDecimal = BigDecimal.ZERO,

  // ========== 캐싱 정책 필드 끝 ==========

  // ========== Rate Limit 설정 필드 (분산 LLM Rate Limiter) ==========

  @Column(nullable = false, comment = "동시 API 호출 제한 수 (분산 환경에서 Redis로 제어)")
  var maxConcurrency: Int = 3,

  @Column(nullable = false, comment = "Rate Limit 시 기본 재시도 대기시간 (ms)")
  var retryDelayMs: Long = 2000,

  @Column(nullable = false, comment = "재시도 시 지수 백오프 배수")
  var retryBackoffMultiplier: Double = 2.5,

  @Column(nullable = false, comment = "최대 재시도 대기시간 (ms)")
  var maxRetryDelayMs: Long = 30000,

  @Column(nullable = false, comment = "Rate Limit 시 최대 재시도 횟수")
  var maxRetryAttempts: Int = 10,

  // ========== Rate Limit 설정 필드 끝 ==========

  @Column(comment = "이 AI 제공사의 모델의 마지막 사용 시각")
  var lastUsedAt: ZonedDateTime?,
) : BaseTimeAndStatusEntity()

