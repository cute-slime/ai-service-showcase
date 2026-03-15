package com.jongmin.ai.core

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageType

interface IAiChatMessage : ChatMessage {
  fun content(): String

  companion object {
    fun from(type: ChatMessageType, content: String): IAiChatMessage {
      return object : IAiChatMessage {
        override fun content(): String {
          return content
        }

        override fun type(): ChatMessageType {
          return type
        }
      }
    }
  }
}

data class ChatCompletionMessage(
  val role: String,
  val content: String,
) {
  fun toChatMessage(): ChatMessage = IAiChatMessage.from(getChatMessageType(), content)

  fun getChatMessageType(): ChatMessageType {
    return when (role) {
      "system" -> ChatMessageType.SYSTEM
      "user" -> ChatMessageType.USER
      "assistant" -> ChatMessageType.AI
      else -> ChatMessageType.CUSTOM
    }
  }
}


data class AiUsage(
  var inputs: MutableList<Long> = mutableListOf(),
  var outputs: MutableList<Long> = mutableListOf(),
)
