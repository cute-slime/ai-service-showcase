package com.jongmin.ai.core.platform.component.astrology

import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import tools.jackson.databind.ObjectMapper
import java.util.function.Function

/**
 * 오늘의 운세를 알려주는 어시스턴트
 */
class FortuneTeller(
  private val objectMapper: ObjectMapper,
  private val assistant: RunnableAiAssistant,
  private val rateLimiter: LlmRateLimiter,
) : Function<FortuneTellerRequest, FortuneTellerResponse> {

  override fun apply(request: FortuneTellerRequest): FortuneTellerResponse {
    val systemMessage = SystemMessage.from(
      assistant.getInstructionsWithCurrentTime()
        .replace("{{nickname}}", request.nickname)
        .replace("{{gender}}", request.gender)
        .replace("{{age}}", request.age)
        .replace("{{financialFortunePoint}}", request.financialFortunePoint.toString())
        .replace("{{romanceFortunePoint}}", request.romanceFortunePoint.toString())
        .replace("{{healthFortunePoint}}", request.healthFortunePoint.toString())
        .replace("{{luckyFiveElement}}", request.luckyFiveElement)
    )
    val userMessages = UserMessage.from("오늘의 운세를 알려주세요.")
    val chatRequest: ChatRequest = ChatRequest
      .builder()
      .messages(listOf(systemMessage) + userMessages)
      .build()

    // Rate Limiter 적용 - 오늘의 운세 생성
    val response = assistant.chatWithRateLimit(chatRequest, rateLimiter)
    val text = (response.aiMessage()?.text()
      ?: throw IllegalStateException("[FortuneTeller] LLM 응답이 null입니다. response=$response")).replace("`", "")
    val result = objectMapper.readValue(text, FortuneTellerResponse::class.java)
    result.financialFortunePoint = request.financialFortunePoint
    result.romanceFortunePoint = request.romanceFortunePoint
    result.healthFortunePoint = request.healthFortunePoint
    return result
  }
}

data class FortuneTellerRequest(
  val nickname: String,
  val gender: String,
  val age: String,
  val financialFortunePoint: Int,
  val romanceFortunePoint: Int,
  val healthFortunePoint: Int,
  val luckyFiveElement: String,
)

data class FortuneTellerResponse(
  val subject: String,
  val description: String,
  val financialFortuneContent: String,
  val romanceFortuneContent: String,
  val healthFortuneContent: String,
  val luckyFiveElement: String,
  val luckyFiveElementContent: String,
  var financialFortunePoint: Int? = null,
  var romanceFortunePoint: Int? = null,
  var healthFortunePoint: Int? = null,
)
