package com.jongmin.ai.multiagent.executor

import com.jongmin.ai.multiagent.model.AgentLlmConfig
import dev.langchain4j.model.anthropic.AnthropicChatModel
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

private val kLogger = KotlinLogging.logger {}

/**
 * LLM 제공자
 * AgentLlmConfig 기반으로 ChatModel 인스턴스 생성
 */
@Component
class ChatModelProvider(
  @param:Value($$"${app.ai.openai.api-key}") private val openaiApiKey: String,
  @param:Value($$"${app.ai.anthropic.api-key}") private val anthropicApiKey: String,
  @param:Value($$"${app.ai.ollama.base-url}") private val ollamaBaseUrl: String,
) {

  /**
   * AgentLlmConfig 기반으로 동기 ChatModel 생성
   */
  fun getChatModel(config: AgentLlmConfig): ChatModel {
    kLogger.debug { "ChatModel 생성 - provider: ${config.provider}, model: ${config.model}" }

    return when (config.provider.lowercase()) {
      "openai" -> buildOpenAiModel(config)
      "anthropic" -> buildAnthropicModel(config)
      "ollama" -> buildOllamaModel(config)
      else -> throw IllegalArgumentException("지원하지 않는 LLM provider: ${config.provider}")
    }
  }

  private fun buildOpenAiModel(config: AgentLlmConfig): ChatModel {
    return OpenAiChatModel.builder()
      .apiKey(openaiApiKey)
      .modelName(config.model)
      .temperature(config.temperature)
      .apply {
        config.maxTokens?.let { maxTokens(it) }
      }
      .timeout(Duration.ofSeconds(60))
      .logRequests(true)
      .logResponses(true)
      .build()
  }

  private fun buildAnthropicModel(config: AgentLlmConfig): ChatModel {
    return AnthropicChatModel.builder()
      .apiKey(anthropicApiKey)
      .modelName(config.model)
      .temperature(config.temperature)
      .apply {
        config.maxTokens?.let { maxTokens(it) }
      }
      .timeout(Duration.ofSeconds(60))
      .logRequests(true)
      .logResponses(true)
      .build()
  }

  private fun buildOllamaModel(config: AgentLlmConfig): ChatModel {
    return OllamaChatModel.builder()
      .baseUrl(ollamaBaseUrl)
      .modelName(config.model)
      .temperature(config.temperature)
      .timeout(Duration.ofSeconds(60))
      .logRequests(true)
      .logResponses(true)
      .build()
  }
}
