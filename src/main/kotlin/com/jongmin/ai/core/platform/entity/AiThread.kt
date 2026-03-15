package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import jakarta.persistence.*
import java.time.ZonedDateTime

/**
 * @author Jongmin
 * @since  2026. 1. 6
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiThread_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiThread_status", columnList = "status"),
    Index(name = "idx_aiThread_accountId", columnList = "accountId"),
    Index(name = "idx_aiThread_lastUsedAt", columnList = "lastUsedAt"),
  ]
)
data class AiThread(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false, updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "스레드 사용자 아이디")
  val accountId: Long,

  @Column(length = 80, comment = "스레드 제목, AI가 자동으로 생성해준다 물론 사용자가 수정할 수 있다.")
  var title: String?,

  @Column(comment = "스레드를 초기화 할 경우, 마지막 메시지 아이디가 저장된다.")
  var startMessageId: Long? = null,

  @Column(nullable = false, comment = "스레드에서 사용된 입력 토큰의 합")
  var totalInputToken: Long = 0,

  @Column(nullable = false, comment = "스레드에서 사용된 출력 토큰의 합")
  var totalOutputToken: Long = 0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "스레드에서 사용된 입력 토큰의 지불 비용")
  var totalInputTokenSpend: Double = 0.0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "스레드에서 사용된 출력 토큰의 지불 비용")
  var totalOutputTokenSpend: Double = 0.0,

  @Column(comment = "스레드의 마지막 사용 시간")
  var lastUsedAt: ZonedDateTime? = null,
) : BaseTimeAndStatusEntity()

