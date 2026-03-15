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
 * 이미지 프롬프트 생성 어시스턴트
 *
 * LLM을 사용하여 사용자의 요청을 ComfyUI에 최적화된 고품질 프롬프트로 변환합니다.
 *
 * ### 생성 결과:
 * - positivePrompt: 이미지에 포함할 요소
 * - negativePrompt: 이미지에서 제외할 요소
 * - styleModifiers: 적용된 스타일 수식어 목록
 */
@Component
class ImagePromptGenerator(
  private val aiAssistantService: AiAssistantService,
  private val objectMapper: ObjectMapper,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // 생성 타임아웃 (초)
    private const val GENERATION_TIMEOUT_SECONDS = 30L

    /**
     * 시스템 프롬프트 (영문) - Z-Image 스타일 최적화
     *
     * [한글 참조]
     * 최신 이미지 생성 모델(Flux, SD3 등)에 최적화된 자연어 프롬프트 생성기입니다.
     *
     * ## 핵심 원칙
     * - 키워드 나열이 아닌 자연어 서술 (flowing narrative)
     * - 모든 단어는 시각적으로 구체적인 것만 사용
     * - 구조: 샷 타입 → 피사체 → 환경 → 조명 → 안전 제약
     *
     * ## 금지 요소
     * - "8K, masterpiece, best quality" 등 메타 태그 금지
     * - 추상적 형용사 ("beautiful", "mysterious") 금지
     * - 유명인 이름 금지
     *
     * ## 조명 가이드
     * - 구체적 조명 기법 사용: Rembrandt, butterfly, rim lighting
     * - 조명 방향, 색온도 명시
     *
     * ## 안전 제약
     * - 항상 마지막에 안전 제약조건 포함
     *
     * ## 적정 길이: 80-200 단어
     */
    private val SYSTEM_PROMPT = """
You are an expert prompt engineer specializing in modern image generation models (Flux, SD3, ComfyUI).
Transform user requests into flowing, natural language prompts optimized for high-quality product photography.

## Core Principle
Write **flowing natural language narratives**, NOT keyword lists.
Every word must describe something visually concrete—colors, shapes, textures, materials, compositions, and lighting.

## Prompt Structure (follow this order)
1. Shot type and framing (e.g., "medium close-up", "full product shot", "hero angle")
2. Subject description (product details: material, color, texture, shape, size)
3. Environment/Background (specific setting, props, surface materials)
4. Lighting (technique, direction, color temperature)
5. Safety constraints (always end with these)

## Style-Specific Guidelines

### studio
Write about: professional three-point lighting setup, soft key light from upper left, clean seamless backdrop (white/gray/colored), controlled shadows, product photography aesthetic.

### lifestyle
Write about: real-world environment, natural context of product use, ambient daylight through windows, lived-in backgrounds, complementary props, candid composition.

### minimal
Write about: generous negative space, single-color backdrop, centered subject, clean geometric arrangement, soft even lighting, zen-like simplicity.

### vibrant
Write about: saturated color palette, bold color contrasts, dynamic composition, punchy lighting with strong highlights, energetic mood.

### natural
Write about: golden hour warmth, soft diffused daylight, organic textures, earthy tones, gentle shadows, outdoor or window-lit indoor setting.

## Lighting Vocabulary (use these specific terms)
- Rembrandt lighting, butterfly lighting, rim lighting, backlight
- Soft key light, fill light, hair light, accent light
- Color temperature: warm (3200K), neutral (5500K), cool (6500K)
- Light direction: upper left, frontal, side, overhead

## PROHIBITED Elements (never include)
- Meta-tags: "8K", "masterpiece", "best quality", "high quality", "HDR", "ultra detailed"
- Abstract adjectives without visual meaning: "beautiful", "stunning", "amazing", "perfect"
- Celebrity or real person names
- Contradictory elements

## Safety Constraints (always append at the end)
End every prompt with: "correct anatomy, no text overlays, no watermarks, no logos, commercially appropriate."

## Target Length
Write 80-200 words in a single flowing paragraph.

## Response Format
Respond ONLY with JSON. No other text:
{
  "positivePrompt": "Your flowing natural language prompt here (80-200 words, single paragraph)",
  "negativePrompt": "minimal negative prompt if needed, or empty string",
  "styleModifiers": ["lighting_technique", "composition_style", "color_mood"]
}
    """.trimIndent()
  }

  /**
   * 이미지 생성 프롬프트를 생성합니다.
   *
   * @param productName 상품명
   * @param userPrompt 사용자 프롬프트
   * @param imageStyle 이미지 스타일
   * @param aspectRatio 종횡비
   * @return 생성된 프롬프트 결과
   * @throws TimeoutException 생성 타임아웃 발생 시
   */
  fun generate(
    productName: String,
    userPrompt: String,
    imageStyle: String? = null,
    aspectRatio: String? = null,
  ): PromptGenerationResult {
    kLogger.info { "프롬프트 생성 시작 - productName: $productName, style: $imageStyle" }
    val startTime = System.currentTimeMillis()

    try {
      // 어시스턴트 조회
      val generatorAssistant = aiAssistantService.findFirst(AiAssistantType.IMAGE_PROMPT_GENERATOR)

      // 메시지 생성 (어시스턴트에 설정된 instructions 우선 사용)
      val systemMessage = SystemMessage.from(
        generatorAssistant.instructions?.takeIf { it.isNotBlank() } ?: SYSTEM_PROMPT
      )
      val userMessage = UserMessage.from(buildUserMessage(productName, userPrompt, imageStyle, aspectRatio))

      // ChatRequest 생성
      val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
        assistant = generatorAssistant,
        messages = listOf(systemMessage, userMessage)
      )

      // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
      val acquireResult = rateLimiter.acquireByProviderName(generatorAssistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      // 동기 호출 (스트리밍 불필요)
      val responseBuilder = StringBuilder()
      val latch = CountDownLatch(1)
      var error: Throwable? = null

      generatorAssistant.chatWithStreaming(
        chatRequest,
        object : dev.langchain4j.model.chat.response.StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            responseBuilder.append(partialResponse)
          }

          override fun onCompleteResponse(completeResponse: dev.langchain4j.model.chat.response.ChatResponse) {
            // Rate Limiter 슬롯 반환
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

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
      val completed = latch.await(GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!completed) {
        // Rate Limiter 슬롯 반환 (타임아웃 시)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (타임아웃) - provider: ${acquireResult.providerName}" }

        kLogger.warn { "프롬프트 생성 타임아웃 - productName: $productName" }
        throw TimeoutException("프롬프트 생성 타임아웃: ${GENERATION_TIMEOUT_SECONDS}초 초과")
      }

      val capturedError = error
      if (capturedError != null) {
        throw capturedError
      }

      // 응답 파싱
      val responseText = responseBuilder.toString().trim()
      val result = parseResponse(responseText, productName, userPrompt)

      val duration = System.currentTimeMillis() - startTime
      kLogger.info { "프롬프트 생성 완료 - duration: ${duration}ms, promptLength: ${result.positivePrompt.length}" }

      return result
    } catch (e: TimeoutException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "프롬프트 생성 실패 - productName: $productName" }
      // 생성 실패 시 폴백 프롬프트 반환
      return PromptGenerationResult.fallback(productName, userPrompt)
    }
  }

  /**
   * 사용자 메시지를 빌드합니다.
   *
   * [한글 참조]
   * 상품 이미지 생성을 위한 자연어 프롬프트 요청.
   * 상품명, 사용자 요청, 스타일, 종횡비 정보를 기반으로
   * 80-200 단어의 flowing narrative 프롬프트를 생성합니다.
   */
  private fun buildUserMessage(
    productName: String,
    userPrompt: String,
    imageStyle: String?,
    aspectRatio: String?,
  ): String {
    val styleDesc = imageStyle?.let { getStyleDescription(it) } ?: "professional product photography"
    val aspectDesc = aspectRatio?.let { getAspectDescription(it) } ?: ""

    return buildString {
      appendLine("Create a flowing natural language prompt for this product image:")
      appendLine()
      appendLine("**Product:** $productName")
      appendLine("**User's Vision:** $userPrompt")
      appendLine("**Style:** $styleDesc")
      if (aspectDesc.isNotEmpty()) {
        appendLine("**Composition:** $aspectDesc")
      }
      appendLine()
      appendLine("Write a single flowing paragraph (80-200 words) describing this product shot.")
      appendLine("Include specific lighting techniques, material descriptions, and end with safety constraints.")
    }
  }

  /**
   * 스타일 코드를 설명으로 변환합니다.
   */
  private fun getStyleDescription(style: String): String {
    return when (style.lowercase()) {
      "studio" -> "professional studio photography with controlled lighting and seamless backdrop"
      "lifestyle" -> "lifestyle photography showing product in natural, real-world context"
      "minimal" -> "minimalist composition with generous negative space and clean design"
      "vibrant" -> "vibrant and energetic with bold colors and dynamic composition"
      "natural" -> "natural lighting with warm tones and organic aesthetic"
      else -> "professional product photography"
    }
  }

  /**
   * 종횡비를 구도 설명으로 변환합니다.
   */
  private fun getAspectDescription(aspectRatio: String): String {
    return when (aspectRatio) {
      "1:1" -> "square format, centered composition"
      "16:9" -> "wide cinematic format, horizontal emphasis"
      "9:16" -> "vertical format for mobile/stories, tall composition"
      "4:3" -> "classic photography format, balanced proportions"
      "3:4" -> "portrait format, vertical emphasis"
      else -> ""
    }
  }

  /**
   * LLM 응답을 파싱합니다.
   */
  private fun parseResponse(
    responseText: String,
    productName: String,
    userPrompt: String,
  ): PromptGenerationResult {
    try {
      // 마크다운 코드 블록 제거 후 JSON 추출
      val cleanedText = responseText.cleanJsonString()
      val jsonText = extractJson(cleanedText)
      if (jsonText.isNullOrBlank()) {
        kLogger.warn { "프롬프트 생성 응답에서 JSON을 찾을 수 없음: ${responseText.take(100)}" }
        return PromptGenerationResult.fallback(productName, userPrompt)
      }

      // JSON 파싱
      val jsonNode = objectMapper.readTree(jsonText)

      val positivePrompt = jsonNode.get("positivePrompt")?.asString()
        ?: return PromptGenerationResult.fallback(productName, userPrompt)
      val negativePrompt = jsonNode.get("negativePrompt")?.asString()
        ?: "blurry, ugly, bad quality, distorted, artifacts"
      val styleModifiersNode = jsonNode.get("styleModifiers")
      val styleModifiers = when {
        styleModifiersNode == null || styleModifiersNode.isNull -> null
        styleModifiersNode.isArray -> styleModifiersNode
          .mapNotNull { it.asString()?.takeIf(String::isNotBlank) }
          .takeIf { it.isNotEmpty() }
        else -> styleModifiersNode.asString()
          ?.takeIf(String::isNotBlank)
          ?.split(",")
          ?.map(String::trim)
          ?.filter(String::isNotEmpty)
          ?.takeIf { it.isNotEmpty() }
      }

      return PromptGenerationResult(
        positivePrompt = positivePrompt,
        negativePrompt = negativePrompt,
        styleModifiers = styleModifiers,
      )
    } catch (e: Exception) {
      kLogger.error(e) { "프롬프트 생성 응답 파싱 실패: ${responseText.take(100)}" }
      return PromptGenerationResult.fallback(productName, userPrompt)
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
}
