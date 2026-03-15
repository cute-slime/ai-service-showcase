package com.jongmin.ai.config

import dev.langchain4j.model.anthropic.AnthropicChatModelName
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel
import dev.langchain4j.model.chat.Capability
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.time.Duration

/**
 * LangChain4j AI 모델 설정
 *
 * 다양한 LLM 프로바이더(OpenAI, Anthropic, Ollama, DeepSeek)의
 * 스트리밍 채팅 모델 빈을 정의한다.
 *
 * @author Jongmin
 */
@Configuration
class Langchain4jConfig {

  @Bean(name = ["ollamaPhi4ChatModel"])
  fun ollamaPhi4ChatModel(
    @Value($$"${app.ai.ollama.base-url}") baseUrl: String,
    @Value($$"${app.ai.ollama.model.phi-4-14b}") model: String,
  ): OllamaStreamingChatModel {
    return OllamaStreamingChatModel.builder()
      .baseUrl(baseUrl)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .modelName(model)
      .timeout(Duration.ofSeconds(60))
      .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
      .build()
  }

  @Bean(name = ["openaiGpt4oChatModel"])
  fun openaiGpt4oChatModel(
    @Value($$"${app.ai.openai.api-key}") apiKey: String,
  ): OpenAiStreamingChatModel {
    return OpenAiStreamingChatModel.builder()
      .modelName(OpenAiChatModelName.GPT_5_MINI)
      .apiKey(apiKey)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .responseFormat("json_schema")
      .strictJsonSchema(true)
      .timeout(Duration.ofSeconds(60))
      .build()
  }

  @Primary
  @Bean(name = ["openaiGpt4oMiniChatModel"])
  fun openaiGpt4oMiniChatModel(
    @Value($$"${app.ai.openai.api-key}") apiKey: String,
  ): OpenAiStreamingChatModel {
    return OpenAiStreamingChatModel.builder()
      .modelName(OpenAiChatModelName.GPT_4_O_MINI)
      .apiKey(apiKey)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .responseFormat("json_schema")
      .strictJsonSchema(true)
      .timeout(Duration.ofSeconds(60))
      .build()
  }

  @Bean(name = ["anthropicSonnetChatModel"])
  fun anthropicSonnetChatModel(
    @Value($$"${app.ai.anthropic.api-key}") apiKey: String,
  ): AnthropicStreamingChatModel {
    return AnthropicStreamingChatModel.builder()
      .apiKey(apiKey)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
      .timeout(Duration.ofSeconds(60))
      .build()
  }

  @Primary
  @Bean(name = ["anthropicHaikuChatModel"])
  fun anthropicHaikuChatModel(
    @Value($$"${app.ai.anthropic.api-key}") apiKey: String,
  ): AnthropicStreamingChatModel {
    return AnthropicStreamingChatModel.builder()
      .apiKey(apiKey)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
      .timeout(Duration.ofSeconds(60))
      .build()
  }

  @Bean(name = ["deepseekChatModel"])
  fun deepseekChatModel(
    @Value($$"${app.ai.deepseek.base-url}") baseUrl: String,
    @Value($$"${app.ai.deepseek.api-key}") apiKey: String,
    @Value($$"${app.ai.deepseek.model.deepseek-chat}") model: String,
  ): OpenAiStreamingChatModel {
    return OpenAiStreamingChatModel.builder()
      .baseUrl(baseUrl)
      .modelName(model)
      .apiKey(apiKey)
      .temperature(0.5)
      .logRequests(true)
      .logResponses(true)
      .responseFormat("json_schema")
      .strictJsonSchema(true)
      .timeout(Duration.ofSeconds(60))
      .build()
  }

  /**
   * Ultrathink 모델 설정
   *
   * 복잡한 추론과 데이터 정제 작업을 위한 고급 모델.
   * Claude 3.5 Sonnet을 사용하여 깊은 사고와 분석 수행.
   */
  @Bean(name = ["ultrathinkModel"])
  fun ultrathinkModel(
    @Value($$"${app.ai.anthropic.api-key}") apiKey: String,
  ): AnthropicStreamingChatModel {
    return AnthropicStreamingChatModel.builder()
      .apiKey(apiKey)
      .temperature(0.3)
      .logRequests(true)
      .logResponses(true)
      .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
      .timeout(Duration.ofSeconds(120))
      .build()
  }
}
