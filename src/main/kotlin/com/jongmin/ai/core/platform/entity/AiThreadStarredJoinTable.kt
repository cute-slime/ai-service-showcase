package com.jongmin.ai.core.platform.entity

import jakarta.persistence.*
import java.io.Serializable

/**
 * @author Jongmin
 * @since  2026. 1. 9
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_FATJT_accountId", columnList = "accountId"),
    Index(name = "idx_FATJT_aiThreadId", columnList = "aiThreadId"),
  ]
)
@IdClass(AiThreadStarredPk::class)
data class AiThreadStarredJoinTable(
  @Id
  @Column(updatable = false, nullable = false)
  val accountId: Long,

  @Id
  @Column(updatable = false, nullable = false)
  val aiThreadId: Long,
)

data class AiThreadStarredPk(
  val accountId: Long? = null,
  val aiThreadId: Long? = null,
) : Serializable
