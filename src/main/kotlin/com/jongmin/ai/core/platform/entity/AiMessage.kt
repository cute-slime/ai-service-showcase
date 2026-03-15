package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.core.*
import dev.langchain4j.data.message.ChatMessageType
import jakarta.persistence.*

/**
 * @author Jongmin
 * @since  2026. 1. 6
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiMessage_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiMessage_status", columnList = "status"),
    Index(name = "idx_aiMessage_accountId", columnList = "accountId"),
    Index(name = "idx_aiMessage_aiThreadId", columnList = "aiThreadId"),
    Index(name = "idx_aiMessage_type", columnList = "type"),
    Index(name = "idx_aiMessage_role", columnList = "role"),
  ]
)
data class AiMessage(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false, updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "메시지를 생성한 계정의 아이디")
  val accountId: Long,

  @Column(nullable = false, updatable = false, comment = "스레드 아이디")
  val aiThreadId: Long,

  @Column(nullable = false, updatable = false, columnDefinition = "TEXT", comment = "컨텐츠")
  var content: String,

  @Convert(converter = AiMessageContentTypeConverter::class)
  @Column(nullable = false, updatable = false, comment = "채팅 내용 타입")
  val type: AiMessageContentType,

  @Convert(converter = AiMessageRoleTypeConverter::class)
  @Column(nullable = false, updatable = false, comment = "메시지 소스")
  val role: AiMessageRole,

  // var attachments: List<Map<String, Any>>? = null,
) : BaseTimeAndStatusEntity(), IAiChatMessage {
  override fun content(): String {
    return content
  }

  override fun type(): ChatMessageType {
    return when (role) {
      AiMessageRole.USER -> ChatMessageType.USER
      AiMessageRole.ASSISTANT -> ChatMessageType.AI
      AiMessageRole.SYSTEM -> ChatMessageType.SYSTEM
      else -> {
        throw IllegalArgumentException("Invalid AiMessageRole: $role")
      }
    }
  }
}


