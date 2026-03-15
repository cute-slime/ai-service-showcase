package com.jongmin.ai.product_agent.platform.component.writing

import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.product_agent.platform.dto.request.WriteType
import com.jongmin.ai.product_agent.platform.dto.request.WritingPromptResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 글쓰기 프롬프트 최적화기
 *
 * 프롬프트 엔지니어가 작성한 최적화된 프롬프트 템플릿을 기반으로
 * 최종 글쓰기 LLM에 전달할 프롬프트를 조합합니다.
 *
 * ### 핵심 원칙:
 * - LLM 추가 호출 없음 (비용 절감, 속도 향상)
 * - 프롬프트 엔지니어가 최적화한 템플릿을 그대로 활용
 * - 플레이스홀더 치환만 수행
 *
 * ### 생성 결과:
 * - systemPrompt: 최적화된 프롬프트 템플릿 (플레이스홀더 치환 완료)
 * - userMessage: 실행 트리거 메시지
 *
 * ### 지원 플레이스홀더:
 * - {{outputLanguage}}: 출력 언어 (auto/ko/en/ja/zh)
 * - {{inputText}}: 사용자 입력 텍스트
 *
 * @property aiAssistantService AI 어시스턴트 조회 서비스
 */
@Component
class WritingPromptGenerator(
  private val aiAssistantService: AiAssistantService
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // userMessage 트리거 (systemPrompt에 이미 모든 정보가 포함되어 있으므로 간단한 실행 요청)
    private const val EXECUTION_TRIGGER = "Please process the text according to the instructions above."
  }

  /**
   * 글쓰기 프롬프트를 조합합니다.
   *
   * 프롬프트 엔지니어가 작성한 최적화된 템플릿에서 플레이스홀더를 치환하여
   * 최종 LLM에 전달할 프롬프트를 생성합니다. LLM 추가 호출 없이 즉시 반환됩니다.
   *
   * @param text 입력 텍스트
   * @param type 작업 유형
   * @param outputLanguage 출력 언어 (auto/ko/en/ja/zh)
   * @return 조합된 프롬프트 결과
   */
  fun generate(
    text: String,
    type: WriteType,
    outputLanguage: String = "auto"
  ): WritingPromptResult {
    val startTime = System.currentTimeMillis()
    kLogger.info { "글쓰기 프롬프트 조합 시작 - type: ${type.code()}, textLength: ${text.length}" }

    try {
      // 1. 어시스턴트 조회 (최적화된 프롬프트 템플릿)
      val assistant = aiAssistantService.findFirst(AiAssistantType.WRITING_PROMPT_GENERATOR, type.code())
      kLogger.debug { "[프롬프트 조합] 어시스턴트 조회 완료 - name: ${assistant.name}, hasInstructions: ${!assistant.instructions.isNullOrBlank()}" }

      // 2. 템플릿 가져오기 (없으면 폴백 사용)
      val template = assistant.instructions?.takeIf { it.isNotBlank() }
      if (template == null) {
        kLogger.warn { "[프롬프트 조합] 어시스턴트에 instructions가 없음 - 폴백 프롬프트 사용" }
        return WritingPrompts.createFallbackPrompt(text, type, outputLanguage)
      }

      // 3. 출력 언어 지시문 생성
      val languageInstruction = getLanguageInstruction(outputLanguage)
      kLogger.debug { "[프롬프트 조합] 언어 지시문: $languageInstruction" }

      // 4. 플레이스홀더 치환
      val processedPrompt = replacePlaceholders(
        template = template,
        outputLanguage = languageInstruction,
        inputText = text
      )
      kLogger.debug { "[프롬프트 조합] 플레이스홀더 치환 완료 - promptLength: ${processedPrompt.length}" }

      // 5. 결과 생성
      val result = WritingPromptResult(
        systemPrompt = processedPrompt,
        userMessage = EXECUTION_TRIGGER
      )

      val duration = System.currentTimeMillis() - startTime
      kLogger.info { "글쓰기 프롬프트 조합 완료 - type: ${type.code()}, duration: ${duration}ms" }

      return result
    } catch (e: Exception) {
      kLogger.error(e) { "글쓰기 프롬프트 조합 실패 - type: ${type.code()}" }
      // 조합 실패 시 폴백 프롬프트 반환
      kLogger.warn { "[프롬프트 조합] 예외 발생으로 폴백 프롬프트 사용" }
      return WritingPrompts.createFallbackPrompt(text, type, outputLanguage)
    }
  }

  /**
   * 프롬프트 템플릿의 플레이스홀더를 실제 값으로 치환합니다.
   *
   * @param template 플레이스홀더가 포함된 템플릿
   * @param outputLanguage 출력 언어 지시문
   * @param inputText 입력 텍스트
   * @return 플레이스홀더가 치환된 문자열
   */
  private fun replacePlaceholders(
    template: String,
    outputLanguage: String,
    inputText: String
  ): String {
    return template
      .replace("{{outputLanguage}}", outputLanguage)
      .replace("{{inputText}}", inputText)
  }

  /**
   * 출력 언어 코드를 사람이 읽기 쉬운 지시문으로 변환합니다.
   *
   * @param outputLanguage 출력 언어 코드 (auto/ko/en/ja/zh)
   * @return 언어 지시문
   */
  private fun getLanguageInstruction(outputLanguage: String): String {
    return when (outputLanguage.lowercase()) {
      "auto" -> "auto (detect and match the input language)"
      "ko" -> "Korean (한국어)"
      "en" -> "English"
      "ja" -> "Japanese (日本語)"
      "zh" -> "Chinese (中文)"
      else -> "auto (detect and match the input language)"
    }
  }
}
