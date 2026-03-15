package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * AI 모델 가격 변경 이력
 *
 * 모델별 토큰 가격 변경 히스토리를 추적합니다.
 * 프로바이더가 가격을 변경할 때마다 새 레코드가 생성됩니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiModelPricingHistory_aiModelId", columnList = "aiModelId"),
    Index(name = "idx_aiModelPricingHistory_effectiveFrom", columnList = "effectiveFrom"),
    Index(name = "idx_aiModelPricingHistory_createdAt", columnList = "createdAt"),
  ]
)
data class AiModelPricingHistory(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0,

  @Column(nullable = false, updatable = false, comment = "가격이 변경된 AI 모델 ID")
  val aiModelId: Long,

  // ========== 입력 토큰 가격 ==========

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "입력 1000 토큰당 $ 원가 (변경 전)"
  )
  val previousInputTokenPrice: BigDecimal,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "입력 1000 토큰당 $ 원가 (변경 후)"
  )
  val newInputTokenPrice: BigDecimal,

  // ========== 출력 토큰 가격 ==========

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "출력 1000 토큰당 $ 원가 (변경 전)"
  )
  val previousOutputTokenPrice: BigDecimal,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "출력 1000 토큰당 $ 원가 (변경 후)"
  )
  val newOutputTokenPrice: BigDecimal,

  // ========== 캐시 토큰 가격 ==========

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "캐시 입력 1000 토큰당 $ 원가 (변경 전)"
  )
  val previousCachedInputTokenPrice: BigDecimal = BigDecimal.ZERO,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(14, 6)",
    comment = "캐시 입력 1000 토큰당 $ 원가 (변경 후)"
  )
  val newCachedInputTokenPrice: BigDecimal = BigDecimal.ZERO,

  // ========== 할인율 ==========

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(5, 4)",
    comment = "캐시 할인율 (변경 전)"
  )
  val previousCacheDiscountRate: BigDecimal = BigDecimal.ZERO,

  @Column(
    nullable = false,
    columnDefinition = "DECIMAL(5, 4)",
    comment = "캐시 할인율 (변경 후)"
  )
  val newCacheDiscountRate: BigDecimal = BigDecimal.ZERO,

  // ========== 메타데이터 ==========

  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "가격 변경 적용 시작일")
  val effectiveFrom: ZonedDateTime,

  @Column(columnDefinition = "TIMESTAMP", comment = "가격 변경 적용 종료일 (NULL = 현재 적용 중)")
  val effectiveTo: ZonedDateTime? = null,

  @Column(length = 100, comment = "가격 변경 출처 (예: LiteLLM, 공식 문서, 수동 입력)")
  val source: String? = null,

  @Column(length = 500, comment = "가격 변경 사유 메모")
  val memo: String? = null,

  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "레코드 생성일")
  val createdAt: ZonedDateTime = now(),
)
