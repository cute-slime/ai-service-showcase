package com.jongmin.ai.core.platform.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.jongmin.ai.core.ChatCompletionMessage
import dev.langchain4j.model.openai.internal.chat.Function
import dev.langchain4j.model.openai.internal.chat.FunctionCall
import dev.langchain4j.model.openai.internal.chat.ResponseFormat
import dev.langchain4j.model.openai.internal.chat.Tool
import dev.langchain4j.model.openai.internal.shared.StreamOptions

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ChatCompletionRequest(
  var model: String? = null,

  var messages: List<ChatCompletionMessage>? = null,

  var temperature: Double? = null,

  var topP: Double? = null,

  var n: Int? = null,

  var stream: Boolean? = null,

  var streamOptions: StreamOptions? = null,

  var stop: List<String>? = null,

  var maxTokens: Int? = null,

  var maxCompletionTokens: Int? = null,

  var presencePenalty: Double? = null,

  var frequencyPenalty: Double? = null,

  var logitBias: Map<String, Int>? = null,

  var user: String? = null,

  var responseFormat: ResponseFormat? = null,

  var seed: Int? = null,

  var tools: List<Tool>? = null,

  var toolChoice: Any? = null,

  var parallelToolCalls: Boolean? = null,

  var store: Boolean? = null,

  var metadata: Map<String, String>? = null,

  var reasoningEffort: String? = null,

  var serviceTier: String? = null,

  @Deprecated("Functions are deprecated")
  var functions: List<Function>? = null,

  @Deprecated("FunctionCall is deprecated")
  var functionCall: FunctionCall? = null,

  var customParameters: Map<String, Any>? = null
)
