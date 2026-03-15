package com.jongmin.ai.product_agent.platform.component.marketing

import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import com.jongmin.ai.product_agent.*
import com.jongmin.ai.product_agent.platform.dto.response.*
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 마케팅 카피 생성기
 *
 * LLM을 사용하여 마케팅 캠페인의 각 도구별 카피를 생성한다.
 * 도구별로 최적화된 프롬프트를 사용하여 고품질 마케팅 문구를 생성한다.
 *
 * ### 지원 도구:
 * - 배너 광고: 헤드라인, 서브헤드라인, CTA 버튼 텍스트
 * - 인스타그램 피드: 캡션, 해시태그, 후킹 문구
 * - 인스타그램 스토리: 후킹 텍스트, 메인 텍스트, CTA 텍스트
 * - 검색 광고: 플랫폼별(네이버/구글) 텍스트 카피
 *
 * @see MarketingCopyPrompts 시스템 프롬프트 상수
 * @see MarketingCopyDto 결과 데이터 클래스
 */
@Component
class MarketingCopyGenerator(
  private val aiAssistantService: AiAssistantService,
  private val objectMapper: ObjectMapper,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  // ==================== 배너 광고 카피 생성 ====================

  fun generateBannerAdCopy(
    productName: String,
    category: MarketingCategoryId,
    shortDescription: String?,
    targetAudience: String?,
    promotionInfo: String?,
    ctaText: String?,
    toneAndManner: ToneAndMannerId,
    variationCount: Int = 3,
  ): BannerAdCopyResult {
    kLogger.info { "배너 광고 카피 생성 시작 - productName: $productName, tone: $toneAndManner" }

    val userPrompt = buildString {
      appendLine("Create banner ad copy for the following product:")
      appendLine()
      append(MarketingCopyPrompts.buildProductInfoSection(productName, category, shortDescription, targetAudience, promotionInfo, variationCount))
      ctaText?.let { appendLine("**Preferred CTA:** $it") }
      appendLine("**Tone & Manner:** ${toneAndManner.description}")
    }

    val responseText = callLlm(MarketingCopyPrompts.BANNER_AD_SYSTEM, userPrompt)
    return parseBannerAdCopyResult(responseText, productName, ctaText)
  }

  private fun parseBannerAdCopyResult(responseText: String, productName: String, defaultCta: String?): BannerAdCopyResult {
    return try {
      val jsonText = extractJson(responseText.cleanJsonString())
        ?: return BannerAdCopyResult.fallback(productName, defaultCta)

      val jsonNode = objectMapper.readTree(jsonText)

      val variations = mutableListOf<BannerAdCopyVariation>()
      jsonNode.get("variations")?.forEach { v ->
        variations.add(
          BannerAdCopyVariation(
            headline = v.get("headline")?.asString() ?: "",
            subHeadline = v.get("subHeadline")?.asString() ?: "",
            callToAction = v.get("callToAction")?.asString() ?: defaultCta ?: "자세히 보기"
          )
        )
      }

      BannerAdCopyResult(
        headline = jsonNode.get("headline")?.asString() ?: "${productName}의 새로운 발견",
        subHeadline = jsonNode.get("subHeadline")?.asString() ?: "지금 바로 만나보세요",
        callToAction = jsonNode.get("callToAction")?.asString() ?: defaultCta ?: "자세히 보기",
        variations = variations
      )
    } catch (e: Exception) {
      kLogger.error(e) { "배너 광고 카피 파싱 실패" }
      BannerAdCopyResult.fallback(productName, defaultCta)
    }
  }

  // ==================== 인스타그램 피드 카피 생성 ====================

  fun generateInstagramFeedCopy(
    productName: String,
    category: MarketingCategoryId,
    shortDescription: String?,
    targetAudience: String?,
    promotionInfo: String?,
    hashtags: String?,
    includeEmoji: Boolean,
    captionLength: CaptionLengthId,
    variationCount: Int = 3,
  ): InstagramFeedCopyResult {
    kLogger.info { "인스타그램 피드 카피 생성 시작 - productName: $productName, length: $captionLength" }

    val userPrompt = buildString {
      appendLine("Create Instagram feed caption for the following product:")
      appendLine()
      append(MarketingCopyPrompts.buildProductInfoSection(productName, category, shortDescription, targetAudience, promotionInfo, variationCount))
      hashtags?.let { appendLine("**Include these hashtags:** $it") }
      appendLine("**Include Emojis:** ${if (includeEmoji) "Yes" else "No"}")
      appendLine("**Caption Length:** ${captionLength.description} (max ${captionLength.maxLength} chars)")
    }

    val responseText = callLlm(MarketingCopyPrompts.INSTAGRAM_FEED_SYSTEM, userPrompt)
    return parseInstagramFeedCopyResult(responseText, productName, includeEmoji)
  }

  private fun parseInstagramFeedCopyResult(responseText: String, productName: String, includeEmoji: Boolean): InstagramFeedCopyResult {
    return try {
      val jsonText = extractJson(responseText.cleanJsonString())
        ?: return InstagramFeedCopyResult.fallback(productName, includeEmoji)

      val jsonNode = objectMapper.readTree(jsonText)

      val hashtagsList = mutableListOf<String>()
      jsonNode.get("hashtags")?.forEach { h ->
        val tag = h.asString()
        hashtagsList.add(if (tag.startsWith("#")) tag else "#$tag")
      }

      val variations = mutableListOf<InstagramFeedCopyVariation>()
      jsonNode.get("variations")?.forEach { v ->
        variations.add(
          InstagramFeedCopyVariation(
            caption = v.get("caption")?.asString() ?: "",
            hookLine = v.get("hookLine")?.asString() ?: ""
          )
        )
      }

      InstagramFeedCopyResult(
        caption = jsonNode.get("caption")?.asString() ?: "$productName 소개",
        hashtags = hashtagsList.ifEmpty { listOf("#$productName", "#추천") },
        hookLine = jsonNode.get("hookLine")?.asString() ?: "✨ $productName",
        variations = variations
      )
    } catch (e: Exception) {
      kLogger.error(e) { "인스타그램 피드 카피 파싱 실패" }
      InstagramFeedCopyResult.fallback(productName, includeEmoji)
    }
  }

  // ==================== 인스타그램 스토리 카피 생성 ====================

  fun generateInstagramStoryCopy(
    productName: String,
    category: MarketingCategoryId,
    shortDescription: String?,
    targetAudience: String?,
    promotionInfo: String?,
    ctaText: String?,
    contentType: StoryContentTypeId,
    mood: MoodId,
    variationCount: Int = 3,
  ): InstagramStoryCopyResult {
    kLogger.info { "인스타그램 스토리 카피 생성 시작 - productName: $productName, mood: $mood" }

    val userPrompt = buildString {
      appendLine("Create Instagram ${contentType.description} text overlays for the following product:")
      appendLine()
      append(MarketingCopyPrompts.buildProductInfoSection(productName, category, shortDescription, targetAudience, promotionInfo, variationCount))
      ctaText?.let { appendLine("**Preferred CTA:** $it") }
      appendLine("**Mood:** ${mood.description}")
      appendLine("Remember: Hook text max 15 chars, Main text max 30 chars.")
    }

    val responseText = callLlm(MarketingCopyPrompts.INSTAGRAM_STORY_SYSTEM, userPrompt)
    return parseInstagramStoryCopyResult(responseText, productName, ctaText)
  }

  private fun parseInstagramStoryCopyResult(responseText: String, productName: String, defaultCta: String?): InstagramStoryCopyResult {
    return try {
      val jsonText = extractJson(responseText.cleanJsonString())
        ?: return InstagramStoryCopyResult.fallback(productName, defaultCta)

      val jsonNode = objectMapper.readTree(jsonText)

      val variations = mutableListOf<InstagramStoryCopyVariation>()
      jsonNode.get("variations")?.forEach { v ->
        variations.add(
          InstagramStoryCopyVariation(
            hookText = v.get("hookText")?.asString() ?: "",
            mainText = v.get("mainText")?.asString() ?: "",
            ctaText = v.get("ctaText")?.asString() ?: defaultCta ?: "더보기"
          )
        )
      }

      InstagramStoryCopyResult(
        hookText = jsonNode.get("hookText")?.asString() ?: "이거 봤어?",
        mainText = jsonNode.get("mainText")?.asString() ?: productName,
        ctaText = jsonNode.get("ctaText")?.asString() ?: defaultCta ?: "위로 스와이프",
        variations = variations
      )
    } catch (e: Exception) {
      kLogger.error(e) { "인스타그램 스토리 카피 파싱 실패" }
      InstagramStoryCopyResult.fallback(productName, defaultCta)
    }
  }

  // ==================== 검색 광고 카피 생성 ====================

  fun generateSearchAdCopy(
    productName: String,
    category: MarketingCategoryId,
    shortDescription: String?,
    targetAudience: String?,
    promotionInfo: String?,
    ctaText: String?,
    platform: SearchAdPlatformId,
    targetKeywords: String,
    variationCount: Int = 3,
  ): SearchAdCopyResult {
    kLogger.info { "검색 광고 카피 생성 시작 - productName: $productName, platform: $platform" }

    val userPrompt = buildString {
      appendLine("Create ${platform.description} search ad copy for the following product:")
      appendLine()
      append(MarketingCopyPrompts.buildProductInfoSection(productName, category, shortDescription, targetAudience, promotionInfo, variationCount))
      ctaText?.let { appendLine("**Preferred CTA:** $it") }
      appendLine("**Target Keywords:** $targetKeywords")
      appendLine("**Platform:** ${platform.description}")
      appendLine("Focus on the ${platform.description} platform format.")
    }

    val responseText = callLlm(MarketingCopyPrompts.SEARCH_AD_SYSTEM, userPrompt)
    return parseSearchAdCopyResult(responseText, productName, platform)
  }

  private fun parseSearchAdCopyResult(responseText: String, productName: String, platform: SearchAdPlatformId): SearchAdCopyResult {
    return try {
      val jsonText = extractJson(responseText.cleanJsonString())
        ?: return SearchAdCopyResult.fallback(productName, platform)

      val jsonNode = objectMapper.readTree(jsonText)

      // 네이버 광고 파싱
      val naverAdNode = jsonNode.get("naverAd")
      val naverAd = if (naverAdNode != null) {
        val sitelinks = mutableListOf<String>()
        naverAdNode.get("sitelinks")?.forEach { s -> sitelinks.add(s.asString()) }
        NaverSearchAd(
          title = naverAdNode.get("title")?.asString() ?: "$productName | 공식",
          description = naverAdNode.get("description")?.asString() ?: "지금 바로 확인하세요",
          sitelinks = sitelinks.ifEmpty { listOf("상품보기", "리뷰", "이벤트") }
        )
      } else null

      // 구글 광고 파싱
      val googleAdNode = jsonNode.get("googleAd")
      val googleAd = if (googleAdNode != null) {
        val headlines = mutableListOf<String>()
        googleAdNode.get("headlines")?.forEach { h -> headlines.add(h.asString()) }
        val descriptions = mutableListOf<String>()
        googleAdNode.get("descriptions")?.forEach { d -> descriptions.add(d.asString()) }
        val callouts = mutableListOf<String>()
        googleAdNode.get("callouts")?.forEach { c -> callouts.add(c.asString()) }
        GoogleSearchAd(
          headlines = headlines.ifEmpty { listOf("$productName - 공식", "특별 할인", "지금 구매") },
          descriptions = descriptions.ifEmpty { listOf("$productName 을 만나보세요.", "공식 스토어 특별 혜택") },
          callouts = callouts.ifEmpty { listOf("무료배송", "정품보장", "빠른배송") }
        )
      } else null

      // 변형 파싱
      val variations = mutableListOf<SearchAdVariation>()
      jsonNode.get("variations")?.forEach { v ->
        val varNaverNode = v.get("naverAd")
        val varGoogleNode = v.get("googleAd")
        variations.add(
          SearchAdVariation(
            naverAd = if (varNaverNode != null) {
              NaverSearchAdVariation(
                title = varNaverNode.get("title")?.asString() ?: "",
                description = varNaverNode.get("description")?.asString() ?: ""
              )
            } else null,
            googleAd = if (varGoogleNode != null) {
              val varHeadlines = mutableListOf<String>()
              varGoogleNode.get("headlines")?.forEach { h -> varHeadlines.add(h.asString()) }
              val varDescriptions = mutableListOf<String>()
              varGoogleNode.get("descriptions")?.forEach { d -> varDescriptions.add(d.asString()) }
              GoogleSearchAdVariation(
                headlines = varHeadlines,
                descriptions = varDescriptions
              )
            } else null
          )
        )
      }

      SearchAdCopyResult(
        naverAd = naverAd,
        googleAd = googleAd,
        variations = variations
      )
    } catch (e: Exception) {
      kLogger.error(e) { "검색 광고 카피 파싱 실패" }
      SearchAdCopyResult.fallback(productName, platform)
    }
  }

  // ==================== LLM 호출 ====================

  private fun callLlm(systemPrompt: String, userPrompt: String): String {
    try {
      val assistant = aiAssistantService.findFirst(AiAssistantType.PRODUCT_COPYWRITER)

      val systemMessage = SystemMessage.from(systemPrompt)
      val userMessage = UserMessage.from(userPrompt)

      val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
        assistant = assistant,
        messages = listOf(systemMessage, userMessage)
      )

      val acquireResult = rateLimiter.acquireByProviderName(assistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      val responseBuilder = StringBuilder()
      val latch = CountDownLatch(1)
      var error: Throwable? = null

      assistant.chatWithStreaming(
        chatRequest,
        object : dev.langchain4j.model.chat.response.StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            responseBuilder.append(partialResponse)
          }

          override fun onCompleteResponse(completeResponse: dev.langchain4j.model.chat.response.ChatResponse) {
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

            val content = completeResponse.aiMessage()?.text()
            if (content != null && responseBuilder.isEmpty()) {
              responseBuilder.append(content)
            }
            latch.countDown()
          }

          override fun onError(e: Throwable) {
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (에러) - provider: ${acquireResult.providerName}" }

            error = e
            latch.countDown()
          }
        }
      )

      val completed = latch.await(MarketingCopyPrompts.GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (!completed) {
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (타임아웃) - provider: ${acquireResult.providerName}" }

        throw TimeoutException("LLM 호출 타임아웃: ${MarketingCopyPrompts.GENERATION_TIMEOUT_SECONDS}초 초과")
      }

      if (error != null) throw error

      return responseBuilder.toString().trim()
    } catch (e: Exception) {
      kLogger.error(e) { "LLM 호출 실패" }
      throw e
    }
  }

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
