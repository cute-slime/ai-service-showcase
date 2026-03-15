package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.util.JTimeUtils.now
import jakarta.persistence.*
import java.io.Serializable
import java.time.ZonedDateTime

/**
 * @author Jongmin
 * @since  2025. 7. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_openHandsRun_createdAt", columnList = "createdAt"),
    Index(name = "idx_openHandsRun_gitProvider", columnList = "gitProvider"),
    Index(name = "idx_openHandsRun_repo", columnList = "repo"),
    Index(name = "idx_openHandsRun_issueNumber", columnList = "issueNumber"),
    Index(name = "idx_openHandsRun_conversationId", columnList = "conversationId"),
  ]
)
@IdClass(OpenHandsRunPk::class)
data class OpenHandsRun(
  @Id
  @Column(length = 140, nullable = false)
  val gitProvider: String,
  @Id
  @Column(length = 140, nullable = false)
  val repo: String,
  @Id
  @Column(nullable = false)
  val issueNumber: Int,
  @Column(length = 140)
  val title: String?,
  @Column(length = 32, nullable = false, comment = "OpenHands 대화 아이디")
  val conversationId: String,
  @Column(nullable = false, columnDefinition = "TIMESTAMP", updatable = false)
  val createdAt: ZonedDateTime = now(),
  @Column(columnDefinition = "TIMESTAMP")
  var endedAt: ZonedDateTime? = null,
  @Column(length = 140)
  var model: String? = null, // default
  @Column
  var promptTokens: Int? = null, // 425574
  @Column
  var completionTokens: Int? = null, // 2342
  @Column
  var cacheReadTokens: Int? = null, // 0
  @Column
  var cacheWriteTokens: Int? = null, // 0
  @Column
  var contextWindow: Int? = null, // 0
  @Column
  var perTurnToken: Int? = null, // 29627
  @Column(length = 32)
  var responseId: String? = null, // ""
)

data class OpenHandsRunPk(
  val gitProvider: String? = null,
  val repo: String? = null,
  val issueNumber: Int? = null,
) : Serializable
