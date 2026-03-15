package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import jakarta.persistence.*
import java.time.ZonedDateTime

/**
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aak_createdAt", columnList = "createdAt"),
    Index(name = "idx_aak_status", columnList = "status"),
    Index(name = "idx_aak_accountId", columnList = "accountId"),
    Index(name = "idx_aak_aiProviderId", columnList = "aiProviderId"),
    Index(name = "idx_aak_lastUsedAt", columnList = "lastUsedAt"),
  ]
)
data class AiApiKey(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "키의 주인 계정 아이디")
  val accountId: Long,

  @Column(nullable = false, updatable = false, comment = "AI 제공사 아이디")
  val aiProviderId: Long,

  @Column(nullable = false, length = 360, comment = "암호화된 키")
  val encryptedKey: String,

  @Column(nullable = false, comment = "키를 사용해 입력된 토큰의 합")
  var totalInputToken: Long? = 0,

  @Column(nullable = false, comment = "키를 사용해 출력된 토큰의 합")
  var totalOutputToken: Long? = 0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "키를 총 사용량")
  var totalInputTokenSpend: Double? = 0.0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "키를 사용해 출력된 토큰의 총 사용량")
  var totalOutputTokenSpend: Double? = 0.0,

  @Column(comment = "키를 마지막으로 사용한 시간")
  var lastUsedAt: ZonedDateTime? = null,
) : BaseTimeAndStatusEntity()

