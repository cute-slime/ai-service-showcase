package com.jongmin.ai.product_agent.platform.component.writing

import com.jongmin.ai.product_agent.platform.dto.request.WriteType
import com.jongmin.ai.product_agent.platform.dto.request.WriteTypeCategory
import com.jongmin.ai.product_agent.platform.dto.request.WritingPromptResult
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 글쓰기 도구 관련 프롬프트 상수 및 헬퍼 모음
 *
 * WritingPromptEvaluator, WritingPromptGenerator 등에서 사용하는
 * 시스템 프롬프트와 메시지 빌더 함수를 중앙 관리합니다.
 */
object WritingPrompts {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 글쓰기 프롬프트 평가기 시스템 프롬프트
   *
   * [한글 참조]
   * 당신은 글쓰기 도구의 입력 텍스트를 평가하는 전문가입니다.
   * 사용자의 입력이 글쓰기 작업에 적합한지 평가해주세요.
   *
   * ## 평가 기준
   * ### 거부해야 하는 경우:
   * 1. 부적절한 컨텐츠 (INAPPROPRIATE_CONTENT)
   *    - 욕설, 비속어, 혐오 표현
   *    - 성인/선정적 컨텐츠
   *    - 폭력적이거나 위험한 내용
   *
   * 2. 스팸성 컨텐츠 (SPAM_CONTENT)
   *    - 반복적인 무의미한 문자열
   *    - 명백한 스팸/광고 남용
   *
   * 3. 품질 미달 (QUALITY_INSUFFICIENT)
   *    - 의미 없는 단어 나열
   *    - 처리하기에 너무 짧거나 불명확한 입력
   *
   * ### 허용되는 경우:
   * - 일반적인 텍스트 재작성 요청
   * - 비즈니스, 학술, 창작 관련 텍스트
   * - 다양한 언어의 정상적인 텍스트
   *
   * ## 지원되는 글쓰기 트리거 목록
   * 아래 트리거들은 documents/ai/prompt/ 디렉토리에 정의되어 있음
   *
   * ### Actions (작업 유형) - documents/ai/prompt/actions/
   * | 트리거 | 설명 |
   * |--------|------|
   * | expand | 텍스트 확장/상세화 (150-200% 확장) |
   * | extract-keywords | 핵심 키워드 5-10개 추출 |
   * | fact-check | 사실 검증/팩트체크 |
   * | generate-hashtags | SNS 해시태그 8-15개 생성 |
   * | generate-outline | 계층적 아웃라인/개요 생성 |
   * | generate-title | 제목 3-5개 옵션 생성 |
   * | shorten | 텍스트 축소 (40-60% 감소) |
   * | summarize | 요약 (20-30% 분량) |
   * | tone-analysis | 톤/감정 분석 |
   *
   * ### Modes (스타일 모드) - documents/ai/prompt/modes/
   * | 트리거 | 설명 |
   * |--------|------|
   * | academic | 학술적/논문 스타일 |
   * | ad-copy | 광고 카피 스타일 |
   * | article | 뉴스 기사 스타일 |
   * | blog | 블로그 스타일 |
   * | clarity | 명확성 강조 |
   * | email | 비즈니스 이메일 형식 |
   * | formal | 공식적/격식체 |
   * | friendly | 친근한 대화체 |
   * | grammar | 문법/맞춤법 교정 |
   * | persuasive | 설득력 있는 스타일 |
   * | poetic | 시적/문학적 스타일 |
   * | sns | SNS 최적화 스타일 |
   * | storytelling | 스토리텔링/내러티브 스타일 |
   */
  const val EVALUATOR_SYSTEM_PROMPT = """
You are an expert evaluator for a writing tool's input text.
Evaluate whether the user's input is appropriate for text processing.

## Evaluation Criteria

### REJECT if:
1. **Inappropriate Content (INAPPROPRIATE_CONTENT)**
   - Profanity, slurs, hate speech
   - Adult/explicit content
   - Violent or dangerous content
   - Personal attacks or harassment

2. **Spam Content (SPAM_CONTENT)**
   - Repetitive meaningless strings
   - Obvious spam or advertising abuse
   - Gibberish or random characters

3. **Quality Insufficient (QUALITY_INSUFFICIENT)**
   - Meaningless word combinations
   - Too short or unclear to process meaningfully
   - Cannot be processed for the requested task

### APPROVE if:
- Normal text rewriting requests
- Business, academic, or creative text
- Text in various languages (Korean, English, Japanese, etc.)
- Marketing copy, blog posts, emails, etc.

## Response Format
Respond ONLY with JSON in this exact format. No other text:
{
  "approved": true/false,
  "rejectionReason": "INAPPROPRIATE_CONTENT" | "SPAM_CONTENT" | "QUALITY_INSUFFICIENT" | null,
  "rejectionDetail": "Detailed rejection reason for internal review" | null
}
"""

  /**
   * 글쓰기 프롬프트 생성기 시스템 프롬프트
   *
   * [한글 참조]
   * 글쓰기 도구를 위한 프롬프트 최적화 전문가입니다.
   * 사용자의 요청 유형과 입력 텍스트를 분석하여,
   * 최종 LLM이 최고 품질의 결과를 생성할 수 있는
   * 시스템 프롬프트와 사용자 메시지를 생성합니다.
   *
   * ## 핵심 원칙
   * - 작업 유형에 맞는 명확한 지시
   * - 출력 언어 설정 반영
   * - 입력 텍스트의 맥락 보존
   * - 고품질 결과물 유도
   */
  const val GENERATOR_SYSTEM_PROMPT = """
You are an expert prompt engineer specializing in text transformation and writing tasks.
Your job is to create optimized prompts for a downstream LLM that will perform the actual writing task.

## Your Task
Given a user's input text, task type, and output language preference, generate:
1. A system prompt for the downstream LLM
2. A user message containing the text to process

## Guidelines for System Prompt Generation

### For REWRITE Tasks (Style Transformation)
- Be specific about the target style, tone, and voice
- Include clear formatting guidelines
- Specify what to preserve (meaning, key points) and what to transform (style, tone)
- Add quality criteria for the output

### For ACTION Tasks (Analysis/Extraction/Generation)
- Define the expected output structure clearly
- Specify the depth and detail level required
- Include relevant context about the task
- Provide examples of expected format when helpful

## Output Language Handling
- If outputLanguage is "auto": instruct to match input text language
- If specified (ko/en/ja/zh): instruct to output in that language
- Always maintain the quality regardless of language

## Response Format
Respond ONLY with JSON. No other text:
{
  "systemPrompt": "The optimized system prompt for the downstream LLM",
  "userMessage": "The formatted user message with the input text"
}

## Quality Criteria
- System prompt should be 100-300 words
- User message should preserve the original text with clear task framing
- Prompts must be clear, actionable, and specific
"""

  // ==================== Evaluator 관련 함수 ====================

  /**
   * 평가기용 사용자 메시지를 빌드합니다.
   *
   * @param text 입력 텍스트
   * @param type 작업 타입
   * @return 빌드된 사용자 메시지
   */
  fun buildEvaluatorUserMessage(text: String, type: WriteType): String {
    return buildString {
      appendLine("## Evaluation Request")
      appendLine("- Task Type: ${type.code()} (${type.description})")
      appendLine("- Text Length: ${text.length} characters")
      appendLine()
      appendLine("### Input Text:")
      appendLine("```")
      // 너무 긴 텍스트는 앞부분만 평가 (토큰 절약)
      appendLine(text.take(2000))
      if (text.length > 2000) {
        appendLine("... (truncated, total ${text.length} characters)")
      }
      appendLine("```")
      appendLine()
      appendLine("Evaluate whether this input is appropriate for the '${type.description}' task.")
    }
  }

  // ==================== Generator 관련 함수 ====================

  /**
   * 생성기용 사용자 메시지를 빌드합니다.
   *
   * @param text 입력 텍스트
   * @param type 작업 타입
   * @param outputLanguage 출력 언어
   * @return 빌드된 사용자 메시지
   */
  fun buildGeneratorUserMessage(text: String, type: WriteType, outputLanguage: String): String {
    val taskCategory = if (type.category == WriteTypeCategory.REWRITE) "REWRITE" else "ACTION"
    val languageInstruction = getLanguageInstruction(outputLanguage)

    return buildString {
      appendLine("## Prompt Generation Request")
      appendLine()
      appendLine("### Task Information")
      appendLine("- **Task Type**: ${type.code()} (${type.description})")
      appendLine("- **Category**: $taskCategory")
      appendLine("- **Output Language**: $languageInstruction")
      appendLine()
      appendLine("### Task Description")
      appendLine(getTaskDescription(type))
      appendLine()
      appendLine("### Input Text")
      appendLine("```")
      // 텍스트 길이 제한 (프롬프트 생성용이므로 전체 필요)
      appendLine(text.take(4000))
      if (text.length > 4000) {
        appendLine("... (truncated, total ${text.length} characters)")
      }
      appendLine("```")
      appendLine()
      appendLine("Generate optimized systemPrompt and userMessage for this task.")
    }
  }

  /**
   * 작업 유형별 상세 설명을 반환합니다.
   *
   * @param type 작업 타입
   * @return 작업 설명 (영문)
   */
  fun getTaskDescription(type: WriteType): String {
    return when (type) {
      // REWRITE 계열
      WriteType.GRAMMAR -> "Fix spelling, grammar, and punctuation errors while preserving the original meaning."
      WriteType.CLARITY -> "Rewrite to improve clarity and readability. Make sentences easier to understand."
      WriteType.FORMAL -> "Transform to a formal, professional tone suitable for business or official communication."
      WriteType.FRIENDLY -> "Transform to a friendly, approachable tone that feels warm and conversational."
      WriteType.PERSUASIVE -> "Rewrite with persuasive techniques to convince and influence the reader."
      WriteType.ACADEMIC -> "Transform to an academic style with scholarly tone and proper structure."
      WriteType.BLOG -> "Rewrite as an engaging blog post with hooks, subheadings, and conversational style."
      WriteType.EMAIL -> "Transform into a well-structured business email format."
      WriteType.SNS -> "Adapt for social media: concise, engaging, with appropriate hashtag suggestions."
      WriteType.ARTICLE -> "Rewrite as a news article with headline, lead paragraph, and journalistic style."
      WriteType.POETIC -> "Transform with poetic and literary expressions, using metaphors and imagery."
      WriteType.STORYTELLING -> "Rewrite in narrative storytelling format with engaging flow."
      WriteType.AD_COPY -> "Transform into compelling advertising copy with hooks and call-to-action."

      // ACTION 계열
      WriteType.SUMMARIZE -> "Create a concise summary capturing the key points and main ideas."
      WriteType.SHORTEN -> "Reduce the text length while preserving essential meaning and key information."
      WriteType.EXPAND -> "Elaborate and expand the content with more details, examples, and explanations."
      WriteType.FACT_CHECK -> "Analyze the text for factual claims and provide verification assessment."
      WriteType.EXTRACT_KEYWORDS -> "Extract the most important keywords and key phrases from the text."
      WriteType.GENERATE_TITLE -> "Generate appropriate titles/headlines that capture the essence of the content."
      WriteType.TONE_ANALYSIS -> "Analyze the tone, sentiment, and emotional characteristics of the text."
      WriteType.GENERATE_OUTLINE -> "Create a structured outline/overview of the content organization."
      WriteType.GENERATE_HASHTAGS -> "Generate relevant hashtags suitable for social media sharing."
    }
  }

  /**
   * 폴백 프롬프트를 생성합니다.
   *
   * LLM 호출 실패 시 사용되는 기본 프롬프트입니다.
   *
   * @param text 입력 텍스트
   * @param type 작업 타입
   * @param outputLanguage 출력 언어
   * @return 폴백 프롬프트 결과
   */
  fun createFallbackPrompt(
    text: String,
    type: WriteType,
    outputLanguage: String
  ): WritingPromptResult {
    val languageInstruction = getLanguageResponseInstruction(outputLanguage)

    val systemPrompt = buildString {
      appendLine("You are a professional writing assistant specialized in ${type.description}.")
      appendLine()
      appendLine("## Task")
      appendLine(getTaskDescription(type))
      appendLine()
      appendLine("## Guidelines")
      appendLine("- Maintain the original meaning and intent")
      appendLine("- Produce high-quality, professional output")
      appendLine("- $languageInstruction")
      appendLine()
      if (type.category == WriteTypeCategory.REWRITE) {
        appendLine("## Output Format")
        appendLine("Provide only the rewritten text without any explanations or meta-commentary.")
      } else {
        appendLine("## Output Format")
        appendLine("Provide clear, well-structured results appropriate for the task.")
      }
    }

    val userMessage = buildString {
      appendLine("Please perform the following task: **${type.description}**")
      appendLine()
      appendLine("Input text:")
      appendLine("---")
      appendLine(text)
      appendLine("---")
    }

    kLogger.info { "폴백 프롬프트 생성 - type: ${type.code()}" }

    return WritingPromptResult(
      systemPrompt = systemPrompt,
      userMessage = userMessage
    )
  }

  // ==================== 내부 헬퍼 함수 ====================

  /**
   * 출력 언어 지시 문구 반환 (메시지 빌드용)
   */
  private fun getLanguageInstruction(outputLanguage: String): String {
    return when (outputLanguage.lowercase()) {
      "auto" -> "Match the input text language"
      "ko" -> "Output in Korean (한국어)"
      "en" -> "Output in English"
      "ja" -> "Output in Japanese (日本語)"
      "zh" -> "Output in Chinese (中文)"
      else -> "Match the input text language"
    }
  }

  /**
   * 출력 언어 응답 지시 문구 반환 (폴백용)
   */
  private fun getLanguageResponseInstruction(outputLanguage: String): String {
    return when (outputLanguage.lowercase()) {
      "auto" -> "Respond in the same language as the input text."
      "ko" -> "Respond in Korean (한국어로 응답하세요)."
      "en" -> "Respond in English."
      "ja" -> "Respond in Japanese (日本語で応答してください)."
      "zh" -> "Respond in Chinese (请用中文回复)."
      else -> "Respond in the same language as the input text."
    }
  }
}
