package com.jongmin.ai.product_agent.platform.component.writing

import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import com.jongmin.ai.product_agent.platform.dto.request.WriteType
import com.jongmin.ai.product_agent.platform.dto.request.WritingEvaluationResult
import com.jongmin.ai.product_agent.platform.dto.request.WritingRejectionReason
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 글쓰기 프롬프트 평가 어시스턴트
 *
 * LLM을 사용하여 사용자의 입력 텍스트가 적절한지 평가합니다.
 * 부적절한 컨텐츠, 스팸성 내용 등을 필터링합니다.
 *
 * ### 평가 기준:
 * - INAPPROPRIATE_CONTENT: 욕설, 혐오 표현, 성인 컨텐츠
 * - SPAM_CONTENT: 스팸성, 광고성 남용
 * - QUALITY_INSUFFICIENT: 의미 없는 입력, 처리하기에 부적합
 *
 * @property aiAssistantService AI 어시스턴트 조회 서비스
 * @property objectMapper JSON 파싱
 */
@Component
class WritingPromptEvaluator(
  private val aiAssistantService: AiAssistantService,
  private val objectMapper: ObjectMapper,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val EVALUATION_TIMEOUT_SECONDS = 30L
  }

  /**
   * 입력 텍스트를 평가합니다.
   *
   * @param text 입력 텍스트
   * @param type 작업 타입
   * @return 평가 결과
   * @throws TimeoutException 평가 타임아웃 발생 시
   */
  fun evaluate(text: String, type: WriteType): WritingEvaluationResult {
    kLogger.info { "글쓰기 입력 평가 시작 - type: ${type.code()}, textLength: ${text.length}" }
    val startTime = System.currentTimeMillis()

    try {
      val evaluatorAssistant = aiAssistantService.findFirst(AiAssistantType.WRITING_PROMPT_EVALUATOR, type.name)

      // 시스템 프롬프트: 플레이스홀더 치환 적용
      val rawInstructions = evaluatorAssistant.instructions?.takeIf { it.isNotBlank() }
        ?: WritingPrompts.EVALUATOR_SYSTEM_PROMPT
      val processedInstructions = replacePlaceholders(
        template = rawInstructions,
        taskType = type.code(),
        inputText = text
      )
      kLogger.debug { "[Evaluator] 플레이스홀더 치환 완료 - taskType: ${type.code()}, inputTextLength: ${text.length}" }

      val systemMessage = SystemMessage.from(processedInstructions)
      val userMessage = UserMessage.from(WritingPrompts.buildEvaluatorUserMessage(text, type))

      // ChatRequest 생성
      val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(evaluatorAssistant, listOf(systemMessage, userMessage))

      // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
      val acquireResult = rateLimiter.acquireByProviderName(evaluatorAssistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      // 동기 호출 (스트리밍 불필요)
      val responseBuilder = StringBuilder()
      val latch = CountDownLatch(1)
      var error: Throwable? = null

      evaluatorAssistant.chatWithStreaming(
        chatRequest,
        object : StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            responseBuilder.append(partialResponse)
          }

          override fun onCompleteResponse(completeResponse: ChatResponse) {
            // Rate Limiter 슬롯 반환
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

            val content = completeResponse.aiMessage()?.text()
            if (content != null && responseBuilder.isEmpty()) responseBuilder.append(content)
            latch.countDown()
          }

          override fun onError(e: Throwable) {
            // Rate Limiter 슬롯 반환 (에러 시)
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (에러) - provider: ${acquireResult.providerName}" }

            error = e
            latch.countDown()
          }
        }
      )

      val completed = latch.await(EVALUATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!completed) {
        // Rate Limiter 슬롯 반환 (타임아웃 시)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (타임아웃) - provider: ${acquireResult.providerName}" }

        kLogger.warn { "글쓰기 입력 평가 타임아웃 - type: ${type.code()}" }
        throw TimeoutException("글쓰기 입력 평가 타임아웃: ${EVALUATION_TIMEOUT_SECONDS}초 초과")
      }

      if (error != null) throw error

      val responseText = responseBuilder.toString().trim()
      val result = parseResponse(responseText)

      val duration = System.currentTimeMillis() - startTime
      kLogger.info { "글쓰기 입력 평가 완료 - approved: ${result.approved}, duration: ${duration}ms" }

      return result
    } catch (e: TimeoutException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "글쓰기 입력 평가 실패 - type: ${type.code()}" }
      // 평가 실패 시 안전을 위해 거부 처리 (폴백)
      return WritingEvaluationResult.fallbackRejected()
    }
  }

  /**
   * LLM 응답을 파싱합니다.
   */
  private fun parseResponse(responseText: String): WritingEvaluationResult {
    try {
      // 마크다운 코드 블록 제거 후 JSON 추출
      val cleanedText = responseText.cleanJsonString()
      val jsonText = extractJson(cleanedText)
      if (jsonText.isNullOrBlank()) {
        kLogger.warn { "글쓰기 평가 응답에서 JSON을 찾을 수 없음: ${responseText.take(100)}" }
        return WritingEvaluationResult.fallbackRejected()
      }

      // JSON 파싱
      val jsonNode = objectMapper.readTree(jsonText)

      val approved = jsonNode.get("approved")?.asBoolean() ?: true
      val rejectionReasonStr = jsonNode.get("rejectionReason")?.asString()
      val rejectionDetail = jsonNode.get("rejectionDetail")?.asString()
      val rejectionReason = WritingRejectionReason.fromCode(rejectionReasonStr)

      // approved=false인데 rejectionReason이 null이면 기본값(QUALITY_INSUFFICIENT) 사용
      val finalRejectionReason = if (!approved) {
        rejectionReason ?: WritingRejectionReason.QUALITY_INSUFFICIENT
      } else null

      return WritingEvaluationResult(
        approved = approved,
        rejectionReason = finalRejectionReason,
        rejectionDetail = if (!approved) rejectionDetail else null
      )
    } catch (e: Exception) {
      kLogger.error(e) { "글쓰기 평가 응답 파싱 실패: ${responseText.take(100)}" }
      return WritingEvaluationResult.fallbackRejected()
    }
  }

  /**
   * 텍스트에서 JSON을 추출합니다.
   */
  private fun extractJson(text: String): String? {
    val startIndex = text.indexOf('{')
    if (startIndex == -1) return null

    var depth = 0
    var endIndex = -1

    for (i in startIndex until text.length) {
      when (text[i]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) {
            endIndex = i
            break
          }
        }
      }
    }

    return if (endIndex != -1) {
      text.substring(startIndex, endIndex + 1)
    } else null
  }

  /**
   * 프롬프트 템플릿의 플레이스홀더를 실제 값으로 치환합니다.
   *
   * 지원 플레이스홀더:
   * - {{taskType}}: 작업 유형 코드 (예: expand, formal)
   * - {{inputText}}: 사용자 입력 텍스트
   *
   * @param template 플레이스홀더가 포함된 템플릿
   * @param taskType 작업 유형 코드
   * @param inputText 입력 텍스트
   * @return 플레이스홀더가 치환된 문자열
   */
  private fun replacePlaceholders(
    template: String,
    taskType: String,
    inputText: String
  ): String {
    return template
      .replace("{{taskType}}", taskType)
      .replace("{{inputText}}", inputText)
  }
}
