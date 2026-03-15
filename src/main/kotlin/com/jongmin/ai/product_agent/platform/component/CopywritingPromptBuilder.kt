package com.jongmin.ai.product_agent.platform.component

import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.product_agent.*
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingData
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingRequest
import com.jongmin.ai.product_agent.platform.dto.response.AiCopyOnlyResponse
import dev.langchain4j.data.image.Image
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*

/**
 * 카피라이팅 프롬프트 빌더
 *
 * 카피라이팅 생성에 필요한 시스템 프롬프트, 사용자 프롬프트,
 * 마케팅 인사이트 프롬프트 등을 생성하는 컴포넌트입니다.
 *
 * ### 주요 책임:
 * - 카피라이팅 시스템 프롬프트 생성 (톤앤매너, CoT 전략, 출력 형식 포함)
 * - 사용자 메시지 생성 (텍스트 + 이미지 멀티모달 지원)
 * - 마케팅 인사이트 프롬프트 생성
 * - 스타일별 가이드라인 생성
 */
@Component
class CopywritingPromptBuilder {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 카피라이팅 시스템 메시지 생성
   *
   * @param data 카피라이팅 요청 데이터
   * @return SystemMessage 객체
   */
  fun buildCopywritingSystemMessage(copywriterAssistant: RunnableAiAssistant, data: CopywritingData): SystemMessage {
    return SystemMessage.from(buildCopywritingSystemPrompt(copywriterAssistant, data))
  }

  /**
   * 사용자 메시지 생성 (이미지 포함 가능)
   *
   * 이미지가 포함된 경우 멀티모달 메시지를, 그렇지 않으면 텍스트 전용 메시지를 생성합니다.
   *
   * ### 처리 흐름:
   * 1. 이미지가 없는 경우: 텍스트 전용 UserMessage 반환
   * 2. 이미지가 있는 경우: TextContent + ImageContent 리스트로 UserMessage 생성
   *
   * @param data 파싱된 카피라이팅 요청 데이터
   * @param dto 원본 카피라이팅 요청 DTO (이미지 포함)
   * @return UserMessage 객체
   */
  fun buildUserMessage(data: CopywritingData, dto: CopywritingRequest): UserMessage {
    val promptText = buildUserPromptText(data)

    // 이미지가 없는 경우 텍스트 전용 메시지 반환
    if (dto.images.isNullOrEmpty()) {
      return UserMessage.from(promptText)
    }

    // 이미지가 있는 경우 멀티모달 메시지 생성
    val contents = mutableListOf<dev.langchain4j.data.message.Content>()

    // 텍스트 콘텐츠 추가
    contents.add(TextContent.from(promptText))

    // 이미지 콘텐츠 추가
    dto.images!!.forEach { multipartFile ->
      try {
        val imageBytes = multipartFile.bytes
        val mimeType = multipartFile.contentType ?: "image/jpeg"
        val base64Data = Base64.getEncoder().encodeToString(imageBytes)

        val image = Image.builder()
          .base64Data(base64Data)
          .mimeType(mimeType)
          .build()

        contents.add(ImageContent.from(image))
        kLogger.debug { "이미지 추가됨 - 파일명: ${multipartFile.originalFilename}, MIME: $mimeType, 크기: ${imageBytes.size}" }
      } catch (e: Exception) {
        kLogger.warn(e) { "이미지 처리 실패 - 파일명: ${multipartFile.originalFilename}" }
      }
    }

    kLogger.info { "멀티모달 메시지 생성 완료 - 텍스트 1개, 이미지 ${dto.images!!.size}개" }
    return UserMessage.from(contents)
  }

  /**
   * 마케팅 인사이트 시스템 메시지 생성
   *
   * @return SystemMessage 객체
   */
  fun buildMarketingInsightsSystemMessage(): SystemMessage {
    return SystemMessage.from(buildMarketingInsightsSystemPrompt())
  }

  /**
   * 마케팅 인사이트 사용자 메시지 생성
   *
   * @param data 카피라이팅 요청 데이터
   * @param generatedCopy 1차 LLM에서 생성된 카피라이팅 응답
   * @return UserMessage 객체
   */
  fun buildMarketingInsightsUserMessage(
    data: CopywritingData,
    generatedCopy: AiCopyOnlyResponse
  ): UserMessage {
    return UserMessage.from(buildMarketingInsightsUserPrompt(data, generatedCopy))
  }

  // ============================================================
  // 내부 프롬프트 생성 메서드들
  // ============================================================

  /**
   * 시스템 프롬프트 생성
   *
   * 가이드 문서(commerce_pdp_ai_copywriting_guide.md)의 마스터 프롬프트 구조를 기반으로
   * 카피라이팅 스타일, CoT(Chain of Thought) 프롬프팅 전략, 변환 규칙을 포함한
   * 종합적인 시스템 메시지를 생성합니다.
   *
   * ### 포함 내용:
   * - 10년 경력 이커머스 MD 페르소나
   * - 구매 결정 트리거 우선순위 (실용적 가치 40%, 감성적 어필 30%, 사회적 증명 20%, 긴급성 10%)
   * - 카피라이팅 스타일 지침
   * - CoT(Chain of Thought) 단계별 사고 전략
   * - 출력 형식 가이드라인
   * - 금지 사항
   *
   * @param data 파싱된 카피라이팅 요청 데이터
   * @return 시스템 프롬프트 문자열
   */
  private fun buildCopywritingSystemPrompt(copywriterAssistant: RunnableAiAssistant, data: CopywritingData): String {
    val styleGuide = buildCopywritingStyleGuide(data.copyStyle)
    val prompt = copywriterAssistant.instructions?.takeIf { it.isNotBlank() }
      ?: CopywritingPromptConst.COPYWRITING_PROMPT
    return prompt.trimIndent().replace("{{styleGuide}}", styleGuide)
  }

  /**
   * 카피라이팅 스타일 가이드 생성
   *
   * 톤앤매너 스타일에 따른 상세 지침을 생성합니다.
   *
   * @param copyStyle 카피라이팅 스타일
   * @return 스타일 가이드 문자열
   */
  private fun buildCopywritingStyleGuide(copyStyle: CopywritingStyle?): String {
    return when (copyStyle) {
      CopywritingStyle.EMOTIONAL -> CopywritingPromptConst.STYLE_EMOTIONAL
      CopywritingStyle.TRENDY -> CopywritingPromptConst.STYLE_TRENDY
      CopywritingStyle.PROFESSIONAL -> CopywritingPromptConst.STYLE_PROFESSIONAL
      CopywritingStyle.FRIENDLY -> CopywritingPromptConst.STYLE_FRIENDLY
      CopywritingStyle.LUXURY -> CopywritingPromptConst.STYLE_LUXURY
      CopywritingStyle.ENVIRONMENT -> CopywritingPromptConst.STYLE_ENVIRONMENT
      CopywritingStyle.VALUE_FOR_MONEY -> CopywritingPromptConst.STYLE_VALUE_FOR_MONEY
      CopywritingStyle.STORYTELLING -> CopywritingPromptConst.STYLE_STORYTELLING
      CopywritingStyle.HUMOROUS -> CopywritingPromptConst.STYLE_HUMOROUS
      CopywritingStyle.MINIMAL -> CopywritingPromptConst.STYLE_MINIMAL
      else -> CopywritingPromptConst.STYLE_DEFAULT
    }
  }

  /**
   * 스타일 설정 섹션 생성
   *
   * 설명 스타일, 계절 컨텍스트, 트렌드 강조, 가격 포지셔닝에 따른 추가 지침을 생성합니다.
   *
   * @param stylePreferences 스타일 설정
   * @return 스타일 설정 섹션 문자열
   */
  @Suppress("unused")
  private fun buildStylePreferencesSection(stylePreferences: StylePreferences?): String {
    if (stylePreferences == null) return ""

    val sections = mutableListOf<String>()

    // 설명 스타일
    stylePreferences.descriptionStyle?.let { style ->
      val descStyleGuide = when (style) {
        DescriptionStyle.DETAILED -> CopywritingPromptConst.DESC_STYLE_DETAILED
        DescriptionStyle.CONCISE -> CopywritingPromptConst.DESC_STYLE_CONCISE
        DescriptionStyle.STORYTELLING -> CopywritingPromptConst.DESC_STYLE_STORYTELLING
      }
      sections.add("- **설명 스타일**: $descStyleGuide")
    }

    // 계절 컨텍스트
    stylePreferences.seasonalContext?.let { season ->
      val seasonGuide = when (season) {
        SeasonalContext.SPRING -> CopywritingPromptConst.SEASON_SPRING
        SeasonalContext.SUMMER -> CopywritingPromptConst.SEASON_SUMMER
        SeasonalContext.FALL -> CopywritingPromptConst.SEASON_FALL
        SeasonalContext.WINTER -> CopywritingPromptConst.SEASON_WINTER
        SeasonalContext.NONE -> ""
      }
      if (seasonGuide.isNotEmpty()) {
        sections.add("- **계절 컨텍스트**: $seasonGuide")
      }
    }

    // 트렌드 강조
    stylePreferences.trendEmphasis?.let { trend ->
      val trendGuide = when (trend) {
        TrendEmphasis.TRENDING_40S_WOMEN -> CopywritingPromptConst.TREND_40S_WOMEN
        TrendEmphasis.CELEBRITY_ENDORSED -> CopywritingPromptConst.TREND_CELEBRITY
        TrendEmphasis.BESTSELLER -> CopywritingPromptConst.TREND_BESTSELLER
        TrendEmphasis.NEW_ARRIVAL -> CopywritingPromptConst.TREND_NEW_ARRIVAL
        TrendEmphasis.LIMITED_EDITION -> CopywritingPromptConst.TREND_LIMITED
        TrendEmphasis.NONE -> ""
      }
      if (trendGuide.isNotEmpty()) {
        sections.add("- **트렌드 강조**: $trendGuide")
      }
    }

    // 가격 포지셔닝
    stylePreferences.pricePositioning?.let { price ->
      val priceGuide = when (price) {
        PricePositioning.VALUE_FOCUSED -> CopywritingPromptConst.PRICE_VALUE_FOCUSED
        PricePositioning.PREMIUM_JUSTIFIED -> CopywritingPromptConst.PRICE_PREMIUM_JUSTIFIED
        PricePositioning.COMPETITIVE -> CopywritingPromptConst.PRICE_COMPETITIVE
        PricePositioning.LIMITED_OFFER -> CopywritingPromptConst.PRICE_LIMITED_OFFER
        PricePositioning.NONE -> ""
      }
      if (priceGuide.isNotEmpty()) {
        sections.add("- **가격 포지셔닝**: $priceGuide")
      }
    }

    // 브랜드 톤앤매너
    stylePreferences.brandTone?.let { tone ->
      if (tone.isNotBlank()) {
        sections.add("- **브랜드 톤앤매너**: $tone")
      }
    }

    // 확장 컨텍스트
    stylePreferences.extendedPrompt?.let { extended ->
      if (extended.isNotBlank()) {
        sections.add("- **추가 지시사항**: $extended")
      }
    }

    return if (sections.isNotEmpty()) {
      """

## 스타일 설정
${sections.joinToString("\n")}
"""
    } else ""
  }

  /**
   * 이벤트 강조 섹션 생성
   *
   * 이벤트 정보와 강조 레벨에 따른 작성 지침을 생성합니다.
   *
   * @param eventInfo 이벤트 정보
   * @param eventEmphasis 이벤트 강조 레벨
   * @return 이벤트 강조 섹션 문자열
   */
  @Suppress("unused")
  private fun buildEventEmphasisSection(eventInfo: EventInfo?, eventEmphasis: EventEmphasis?): String {
    // 이벤트 정보가 없으면 빈 문자열 반환
    if (eventInfo == null || eventInfo.eventName.isNullOrBlank()) return ""

    // 이벤트 강조 수준에 따른 가이드 (플레이스홀더를 실제 값으로 치환)
    val emphasisGuide = when (eventEmphasis ?: EventEmphasis.MODERATE) {
      EventEmphasis.HIGH -> CopywritingPromptConst.EVENT_EMPHASIS_HIGH
        .replace("{{eventName}}", eventInfo.eventName)
        .replace("{{discountRate}}", eventInfo.discountRate ?: "특가")
        .replace("{{eventPeriod}}", eventInfo.eventPeriod ?: "지금 바로")

      EventEmphasis.MODERATE -> CopywritingPromptConst.EVENT_EMPHASIS_MODERATE
        .replace("{{eventName}}", eventInfo.eventName)
        .replace("{{discountRate}}", eventInfo.discountRate ?: "혜택")

      EventEmphasis.MINIMAL -> CopywritingPromptConst.EVENT_EMPHASIS_MINIMAL
        .replace("{{discountRate}}", eventInfo.discountRate ?: "특별 혜택")

      EventEmphasis.NONE -> ""
    }

    // 이벤트 유형별 추가 지침
    val eventTypeGuide = eventInfo.eventType?.let { type ->
      when (type) {
        EventType.DISCOUNT -> CopywritingPromptConst.EVENT_TYPE_DISCOUNT
        EventType.BUNDLE -> CopywritingPromptConst.EVENT_TYPE_BUNDLE
        EventType.GIFT -> CopywritingPromptConst.EVENT_TYPE_GIFT
        EventType.MEMBERSHIP -> CopywritingPromptConst.EVENT_TYPE_MEMBERSHIP
        EventType.LIMITED_EDITION -> CopywritingPromptConst.EVENT_TYPE_LIMITED_EDITION
        EventType.PRESALE -> CopywritingPromptConst.EVENT_TYPE_PRESALE
        EventType.LANDING_PAGE -> CopywritingPromptConst.EVENT_TYPE_LANDING_PAGE
      }
    } ?: ""

    return """

## 이벤트 정보
- 이벤트명: ${eventInfo.eventName}
- 이벤트 유형: ${eventInfo.eventType?.name ?: "일반"}
- 할인/혜택: ${eventInfo.discountRate ?: "미지정"}
- 이벤트 기간: ${eventInfo.eventPeriod ?: "미지정"}
- 추가 혜택: ${eventInfo.eventBenefits ?: "없음"}
${if (eventInfo.eventLandingUrl != null) "- 랜딩 URL 안내: ${eventInfo.eventLandingUrl}" else ""}

$emphasisGuide
${if (eventTypeGuide.isNotEmpty()) "- **이벤트 유형별 지침**: $eventTypeGuide" else ""}
"""
  }

  /**
   * 사용자 프롬프트 텍스트 생성
   *
   * 상품 정보, 고객 피드백, 이벤트 정보를 포함한 구체적인 카피라이팅 요청 메시지를 생성합니다.
   *
   * @param data 파싱된 카피라이팅 요청 데이터
   * @return 사용자 프롬프트 문자열
   */
  private fun buildUserPromptText(data: CopywritingData): String {
    val basicInfo = data.productBasicInfo
    val feedback = data.customerFeedbackInfo
    val eventInfo = data.eventInfo

    val prompt = StringBuilder()
    prompt.append("다음 상품에 대한 카피라이팅을 작성해주세요.\n\n")

    // 상품 기본 정보 (입력 데이터)
    prompt.append("## 입력 데이터\n\n")
    prompt.append("### 상품 정보\n")
    basicInfo?.let {
      prompt.append("- **상품명**: ${it.productName}\n")
      prompt.append("- **카테고리**: ${it.category}\n")
      it.brand?.let { brand -> prompt.append("- **브랜드**: $brand\n") }
      it.targetAudience?.let { target -> prompt.append("- **타겟 고객**: $target\n") }
      it.specifications?.let { spec -> prompt.append("- **주요 특징**: $spec\n") }
      it.priceRange?.let { price -> prompt.append("- **가격대**: $price\n") }
      it.competitiveAdvantages?.let { adv -> prompt.append("- **경쟁 제품 대비 장점**: $adv\n") }
    }

    // 고객 피드백 정보 (사회적 증명 데이터)
    feedback?.let {
      prompt.append("\n### 고객 피드백 (사회적 증명)\n")
      it.averageRating?.let { rating -> prompt.append("- **평균 평점**: $rating/5.0\n") }
      it.totalReviews?.let { reviews -> prompt.append("- **총 리뷰 수**: ${reviews}건\n") }
      it.repurchaseRate?.let { rate -> prompt.append("- **재구매율**: ${rate}%\n") }

      if (!it.positiveReviews.isNullOrEmpty()) {
        prompt.append("- **긍정 리뷰 키워드**: ${it.positiveReviews.joinToString(", ")}\n")
        prompt.append("  → 이 키워드들을 카피에 자연스럽게 반영하세요\n")
      }

      if (!it.negativeReviews.isNullOrEmpty()) {
        prompt.append("- **개선 포인트**: ${it.negativeReviews.joinToString(", ")}\n")
        prompt.append("  → 이 부분은 언급하지 않거나, 개선되었음을 암시하세요\n")
      }
    }

    // 이벤트 정보 (이벤트 상품인 경우에만)
    eventInfo?.let {
      if (!it.eventName.isNullOrBlank()) {
        prompt.append("\n### 이벤트 정보\n")
        prompt.append("- **이벤트명**: ${it.eventName}\n")
        it.eventType?.let { type -> prompt.append("- **이벤트 유형**: ${type.name}\n") }
        it.discountRate?.let { rate -> prompt.append("- **할인율/혜택**: $rate\n") }
        it.eventPeriod?.let { period -> prompt.append("- **이벤트 기간**: $period\n") }
        it.eventBenefits?.let { benefits -> prompt.append("- **추가 혜택**: $benefits\n") }
        it.eventLandingUrl?.let { url -> prompt.append("- **이벤트 랜딩 안내**: $url\n") }
      }
    }

    // 추가 프롬프트
    data.additionalPrompt?.let {
      if (it.isNotBlank()) {
        prompt.append("\n### 추가 요청사항\n")
        prompt.append(it)
        prompt.append("\n")
      }
    }

    // 최종 지시사항
    prompt.append("\n---\n")
    prompt.append("위 정보를 바탕으로 구매 전환율을 극대화하는 매력적인 카피라이팅을 작성해주세요.\n")
    prompt.append("반드시 Chain of Thought 단계와 변환 규칙을 적용하여 작성하세요.\n")

    return prompt.toString()
  }

  /**
   * 마케팅 인사이트 전용 시스템 프롬프트 생성 (2차 LLM 호출용)
   *
   * @return 마케팅 인사이트 전용 시스템 프롬프트
   */
  private fun buildMarketingInsightsSystemPrompt(): String {
    return CopywritingPromptConst.MARKETING_INSIGHTS_SYSTEM_PROMPT
  }

  /**
   * 마케팅 인사이트 전용 사용자 프롬프트 생성
   *
   * @param data 카피라이팅 요청 데이터
   * @param generatedCopy 1차 LLM에서 생성된 카피라이팅 응답
   * @return 마케팅 인사이트 요청 프롬프트
   */
  private fun buildMarketingInsightsUserPrompt(
    data: CopywritingData,
    generatedCopy: AiCopyOnlyResponse
  ): String {
    val basicInfo = data.productBasicInfo
    val feedback = data.customerFeedbackInfo
    val eventInfo = data.eventInfo

    val prompt = StringBuilder()
    prompt.append("다음 상품에 대한 마케팅 전략을 분석해주세요.\n\n")

    // 상품 기본 정보
    prompt.append("## 상품 정보\n")
    basicInfo?.let {
      prompt.append("- **상품명**: ${it.productName}\n")
      prompt.append("- **카테고리**: ${it.category}\n")
      it.brand?.let { brand -> prompt.append("- **브랜드**: $brand\n") }
      it.targetAudience?.let { target -> prompt.append("- **타겟 고객층**: $target\n") }
      it.specifications?.let { spec -> prompt.append("- **주요 특징**: $spec\n") }
      it.priceRange?.let { price -> prompt.append("- **가격대**: $price\n") }
      it.competitiveAdvantages?.let { adv -> prompt.append("- **경쟁 우위**: $adv\n") }
    }

    // 고객 피드백 정보 (마케팅 전략 수립에 중요)
    feedback?.let {
      prompt.append("\n## 고객 피드백 데이터\n")
      it.averageRating?.let { rating -> prompt.append("- **평균 평점**: $rating/5.0\n") }
      it.totalReviews?.let { reviews -> prompt.append("- **총 리뷰 수**: ${reviews}건\n") }
      it.repurchaseRate?.let { rate -> prompt.append("- **재구매율**: ${rate}%\n") }

      if (!it.positiveReviews.isNullOrEmpty()) {
        prompt.append("- **주요 긍정 키워드**: ${it.positiveReviews.joinToString(", ")}\n")
      }
      if (!it.negativeReviews.isNullOrEmpty()) {
        prompt.append("- **주요 개선 포인트**: ${it.negativeReviews.joinToString(", ")}\n")
      }
    }

    // 이벤트 정보
    eventInfo?.let {
      if (!it.eventName.isNullOrBlank()) {
        prompt.append("\n## 이벤트 정보\n")
        prompt.append("- **이벤트명**: ${it.eventName}\n")
        it.eventType?.let { type -> prompt.append("- **유형**: ${type.name}\n") }
        it.discountRate?.let { rate -> prompt.append("- **할인/혜택**: $rate\n") }
        it.eventPeriod?.let { period -> prompt.append("- **기간**: $period\n") }
      }
    }

    // 생성된 카피라이팅 요약 (마케팅 전략 참고용)
    prompt.append("\n## 생성된 카피라이팅 요약\n")
    prompt.append("- **메인 카피**: ${generatedCopy.mainCopy.take(100)}...\n")

    // 스타일 설정 정보
    data.stylePreferences?.let { prefs ->
      prompt.append("\n## 스타일 설정\n")
      prefs.seasonalContext?.let { season ->
        if (season != SeasonalContext.NONE) {
          prompt.append("- **계절 컨텍스트**: ${season.name}\n")
        }
      }
      prefs.trendEmphasis?.let { trend ->
        if (trend != TrendEmphasis.NONE) {
          prompt.append("- **트렌드 강조**: ${trend.name}\n")
        }
      }
      prefs.pricePositioning?.let { price ->
        if (price != PricePositioning.NONE) {
          prompt.append("- **가격 포지셔닝**: ${price.name}\n")
        }
      }
    }

    prompt.append("\n---\n")
    prompt.append("위 정보를 바탕으로 구체적이고 실행 가능한 마케팅 전략을 제안해주세요.\n")
    prompt.append("특히 타겟 고객층 분석, 채널 전략, 프로모션 타이밍에 대해 상세히 분석해주세요.\n")

    return prompt.toString()
  }
}
