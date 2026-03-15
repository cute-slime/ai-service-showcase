package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.converter.StatusConverter
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.AiExecutionType
import com.jongmin.ai.core.AiExecutionTypeConverter
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.ZonedDateTime

/**
 * @author Jongmin
 * @since  2026. 1. 6
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiRunStep_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiRunStep_status", columnList = "status"),
    Index(name = "idx_aiRunStep_aiAssistantId", columnList = "aiAssistantId"),
    Index(name = "idx_aiRunStep_aiRunId", columnList = "aiRunId"),
    Index(name = "idx_aiRunStep_expiredAt", columnList = "expiredAt"),
    Index(name = "idx_aiRunStep_cancelledAt", columnList = "cancelledAt"),
    Index(name = "idx_aiRunStep_failedAt", columnList = "failedAt"),
    Index(name = "idx_aiRunStep_completedAt", columnList = "completedAt"),
    Index(name = "idx_aiRunStep_executionType", columnList = "executionType"),
    Index(name = "idx_aiRunStep_provider", columnList = "provider"),
  ]
)
data class AiRunStep(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false, updatable = false)
  val id: Long = 0L,

  @Column(updatable = false, comment = "어시스턴트 ID")
  val aiAssistantId: Long?,

  @Column(nullable = false, updatable = false, comment = "실행 ID")
  val aiRunId: Long,

  @Column(nullable = false, comment = "단계 상태. in_progress, cancelled, failed, completed, expired 등")
  @Convert(converter = StatusConverter::class)
  var status: StatusType,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "오류 정보")
  var lastError: Map<String, Any>? = null,

  @Column(nullable = false, columnDefinition = "TIMESTAMP", comment = "생성일")
  val createdAt: ZonedDateTime = now(),

  @Column(columnDefinition = "TIMESTAMP", comment = "만료일")
  val expiredAt: ZonedDateTime? = null,

  @Column(columnDefinition = "TIMESTAMP", comment = "취소일")
  val cancelledAt: ZonedDateTime? = null,

  @Column(columnDefinition = "TIMESTAMP", comment = "실패일")
  val failedAt: ZonedDateTime? = null,

  @Column(columnDefinition = "TIMESTAMP", comment = "완료일")
  var completedAt: ZonedDateTime? = null,

  @Column(nullable = false, comment = "총 입력 토큰 수")
  var totalInputToken: Long = 0,

  @Column(nullable = false, comment = "총 출력 토큰 수")
  var totalOutputToken: Long = 0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "입력 토큰 비용")
  var totalInputTokenSpend: Double = 0.0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "출력 토큰 비용")
  var totalOutputTokenSpend: Double = 0.0,

  // ========== 캐시 관련 필드 (AI 캐시 모니터링 시스템) ==========

  @Column(nullable = false, comment = "캐시 히트 토큰 수 (캐시에서 재사용된 입력 토큰)")
  var cachedInputTokens: Long = 0,

  @Column(nullable = false, comment = "캐시 생성 토큰 수 (Anthropic 전용, 캐시에 새로 저장된 토큰)")
  var cacheCreationTokens: Long = 0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "캐시 토큰 비용 (할인된 가격 적용)")
  var cachedInputTokenSpend: Double = 0.0,

  // ========== 캐시 관련 필드 끝 ==========

  // ========== AI 로깅 고도화 필드 (100% 커버리지) ==========

  @Column(comment = "AI 실행 유형")
  @Convert(converter = AiExecutionTypeConverter::class)
  var executionType: AiExecutionType? = null,

  @Column(length = 50, comment = "AI 제공자 (openai, anthropic, comfyui 등)")
  var provider: String? = null,

  @Column(length = 100, comment = "사용된 모델명")
  var modelName: String? = null,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "총 비용 (USD) - 모든 유형 통합")
  var totalCostUsd: Double = 0.0,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "유형별 상세 메트릭")
  var executionMetrics: Map<String, Any>? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "요청 데이터")
  var requestPayload: Map<String, Any>? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "응답 데이터 (프로바이더 원본)")
  var responsePayload: Map<String, Any>? = null,

  // ========== AI 로깅 고도화 필드 끝 ==========

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "진행 로그. 1달 후 정리")
  var logs: Map<String, Any>?,
)

