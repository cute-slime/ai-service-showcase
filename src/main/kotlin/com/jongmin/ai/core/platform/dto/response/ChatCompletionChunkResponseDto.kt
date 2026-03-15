package com.jongmin.ai.core.platform.dto.response

data class ChatCompletionChunkResponseDto(
  val id: String,
  val provider: String,
  val model: String,
  val `object`: String,
  val created: Long,
  val choices: List<ChatCompletionChunkChoiceDto>
)

data class ChatCompletionChunkChoiceDto(
  val index: Int,
  val delta: ChatCompletionChunkDeltaDto,
  val finishReason: String?
)

data class ChatCompletionChunkDeltaDto(
  val role: String,
  val content: String?,
  val reasoning: String? = null,
  val reasoningDetails: List<ReasoningDetail> = emptyList()
)

data class ReasoningDetail(
  val type: String = "reasoning.text",
  val text: String,
  val format: String = "unknown",
  val index: Int = 0
)
