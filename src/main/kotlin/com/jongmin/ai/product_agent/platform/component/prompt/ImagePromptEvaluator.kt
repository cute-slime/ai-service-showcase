package com.jongmin.ai.product_agent.platform.component.prompt

import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 이미지 프롬프트 평가 어시스턴트
 *
 * LLM을 사용하여 사용자의 이미지 생성 프롬프트가 적절한지 평가합니다.
 * 성인 컨텐츠, 특정 인물 지칭, 품질 미달 등의 부적절한 요청을 필터링합니다.
 *
 * ### 평가 기준:
 * - ADULT_CONTENT: 선정적인 노출, 과도한 신체 노출
 * - CELEBRITY_REFERENCE: 특정 인물(연예인, 유명인) 지칭
 * - QUALITY_INSUFFICIENT: 상품 이미지로서 품질 미달
 *
 * ### 참고:
 * - 일반적인 속옷/수영복 광고는 허용됩니다.
 * - 평가 실패 시 재시도 없이 즉시 에러를 반환합니다.
 */
@Component
class ImagePromptEvaluator(
  private val aiAssistantService: AiAssistantService,
  private val objectMapper: ObjectMapper,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // 평가 타임아웃 (초)
    private const val EVALUATION_TIMEOUT_SECONDS = 30L

    /**
     * 시스템 프롬프트 (영문)
     *
     * [한글 참조]
     * 당신은 상품 이미지 생성 요청을 평가하는 전문가입니다.
     *
     * ## 평가 기준
     * ### 거부해야 하는 경우:
     * 1. 성인 컨텐츠 (ADULT_CONTENT)
     *    - 과도하게 선정적인 속옷 (끈 팬티, 시스루, 본디지 등)
     *    - 누드 또는 누드에 가까운 노출
     *    - 성적 암시가 포함된 포즈나 표현
     *    - 과도하게 노출된 수영복/비키니
     *
     * 2. 특정 인물 지칭 (CELEBRITY_REFERENCE)
     *    - 연예인, 아이돌, 배우 등 유명인 이름 언급
     *    - "~처럼", "~스타일" 등으로 특정 인물 지칭
     *    - 정치인, 역사적 인물 포함
     *
     * 3. 품질 미달 (QUALITY_INSUFFICIENT)
     *    - 상품과 무관한 요청
     *    - 지나치게 모호하거나 구체성이 없는 요청
     *
     * ### 허용되는 경우:
     * - 일반적인 상품 촬영, 속옷/수영복 광고 (상업적 수준), 라이프스타일 컨셉
     */
    private val SYSTEM_PROMPT = """
You are an expert evaluator for product image generation requests.
Evaluate whether the user's prompt is appropriate for product image generation.

## Evaluation Criteria

### REJECT if:
1. **Adult Content (ADULT_CONTENT)**
   - Overly provocative underwear (thong, see-through, bondage style)
   - Nudity or near-nude exposure
   - Sexually suggestive poses or expressions
   - Excessively revealing swimwear/bikini

2. **Celebrity Reference (CELEBRITY_REFERENCE)**
   - Mentioning names of celebrities, idols, or actors
   - Phrases like "like [person]" or "[person] style"
   - Politicians or historical figures

3. **Quality Insufficient (QUALITY_INSUFFICIENT)**
   - Requests unrelated to products
   - Overly vague or lacking specificity
   - Requests unsuitable for product images

### APPROVE if:
- Standard product photography requests
- Models wearing appropriate clothing
- **Normal underwear/swimwear advertising** (commercially appropriate level)
- Product-only shots
- Lifestyle concept shots

## Response Format
Respond ONLY with JSON in this exact format. No other text:
{
  "approved": true/false,
  "rejectionReason": "ADULT_CONTENT" | "CELEBRITY_REFERENCE" | "QUALITY_INSUFFICIENT" | null,
  "rejectionDetail": "Detailed rejection reason for internal review" | null
}
    """.trimIndent()
  }

  /**
   * 프롬프트를 평가합니다.
   *
   * @param productName 상품명
   * @param userPrompt 사용자 프롬프트
   * @param imageStyle 이미지 스타일 (옵션)
   * @return 평가 결과
   * @throws TimeoutException 평가 타임아웃 발생 시
   */
  fun evaluate(
    productName: String,
    userPrompt: String,
    imageStyle: String? = null,
  ): PromptEvaluationResult {
    kLogger.info { "프롬프트 평가 시작 - productName: $productName, prompt: ${userPrompt.take(50)}..." }
    val startTime = System.currentTimeMillis()

    try {
      // 어시스턴트 조회
      val evaluatorAssistant = aiAssistantService.findFirst(AiAssistantType.IMAGE_PROMPT_EVALUATOR)

      // 메시지 생성
      val systemMessage = SystemMessage.from(
        evaluatorAssistant.instructions?.takeIf { it.isNotBlank() } ?: SYSTEM_PROMPT
      )
      val userMessage = UserMessage.from(buildUserMessage(productName, userPrompt, imageStyle))

      // ChatRequest 생성
      val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
        assistant = evaluatorAssistant,
        messages = listOf(systemMessage, userMessage)
      )

      // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
      val acquireResult = rateLimiter.acquireByProviderName(evaluatorAssistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      // 동기 호출 (스트리밍 불필요)
      val responseBuilder = StringBuilder()
      val latch = CountDownLatch(1)
      var error: Throwable? = null

      evaluatorAssistant.chatWithStreaming(
        chatRequest,
        object : dev.langchain4j.model.chat.response.StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            responseBuilder.append(partialResponse)
          }

          override fun onCompleteResponse(completeResponse: dev.langchain4j.model.chat.response.ChatResponse) {
            // Rate Limiter 슬롯 반환
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

            // 완료 시 content 추출
            val content = completeResponse.aiMessage()?.text()
            if (content != null && responseBuilder.isEmpty()) {
              responseBuilder.append(content)
            }
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

      // 타임아웃 대기
      val completed = latch.await(EVALUATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!completed) {
        // Rate Limiter 슬롯 반환 (타임아웃 시)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (타임아웃) - provider: ${acquireResult.providerName}" }

        kLogger.warn { "프롬프트 평가 타임아웃 - productName: $productName" }
        throw TimeoutException("프롬프트 평가 타임아웃: ${EVALUATION_TIMEOUT_SECONDS}초 초과")
      }

      if (error != null) throw error

      // 응답 파싱
      val responseText = responseBuilder.toString().trim()
      val result = parseResponse(responseText)

      val duration = System.currentTimeMillis() - startTime
      kLogger.info { "프롬프트 평가 완료 - approved: ${result.approved}, duration: ${duration}ms" }

      return result
    } catch (e: TimeoutException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "프롬프트 평가 실패 - productName: $productName" }
      // 평가 실패 시 기본 승인 (폴백) - 비즈니스 정책에 따라 변경 가능
      return PromptEvaluationResult.fallbackApproved()
    }
  }

  /**
   * 사용자 메시지를 빌드합니다.
   *
   * [한글 참조]
   * ## 평가 요청
   * - 상품명: {productName}
   * - 사용자 프롬프트: {userPrompt}
   * - 이미지 스타일: {imageStyle}
   * 위 요청이 상품 이미지 생성에 적합한지 평가해주세요.
   */
  private fun buildUserMessage(productName: String, userPrompt: String, imageStyle: String?): String {
    return buildString {
      appendLine("## Evaluation Request")
      appendLine("- Product Name: $productName")
      appendLine("- User Prompt: $userPrompt")
      if (imageStyle != null) {
        appendLine("- Image Style: $imageStyle")
      }
      appendLine()
      appendLine("Evaluate whether this request is appropriate for product image generation.")
    }
  }

  /**
   * LLM 응답을 파싱합니다.
   */
  private fun parseResponse(responseText: String): PromptEvaluationResult {
    try {
      // 마크다운 코드 블록 제거 후 JSON 추출
      val cleanedText = responseText.cleanJsonString()
      val jsonText = extractJson(cleanedText)
      if (jsonText.isNullOrBlank()) {
        kLogger.warn { "프롬프트 평가 응답에서 JSON을 찾을 수 없음: ${responseText.take(100)}" }
        return PromptEvaluationResult.fallbackApproved()
      }

      // JSON 파싱
      val jsonNode = objectMapper.readTree(jsonText)

      val approved = jsonNode.get("approved")?.asBoolean() ?: true
      val rejectionReasonStr = jsonNode.get("rejectionReason")?.asString()
      val rejectionDetail = jsonNode.get("rejectionDetail")?.asString()
      val rejectionReason = PromptRejectionReason.fromCode(rejectionReasonStr)

      return PromptEvaluationResult(
        approved = approved,
        rejectionReason = if (!approved) rejectionReason else null,
        rejectionDetail = if (!approved) rejectionDetail else null,
      )
    } catch (e: Exception) {
      kLogger.error(e) { "프롬프트 평가 응답 파싱 실패: ${responseText.take(100)}" }
      return PromptEvaluationResult.fallbackApproved()
    }
  }

  /**
   * 텍스트에서 JSON을 추출합니다.
   */
  private fun extractJson(text: String): String? {
    // JSON 객체 찾기 (중첩된 중괄호 처리)
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
}
