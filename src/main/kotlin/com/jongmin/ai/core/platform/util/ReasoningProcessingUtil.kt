package com.jongmin.ai.core.platform.util

import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.LlmDynamicOptions
import com.jongmin.ai.core.ReasoningEffort
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.dto.request.ChatCompletionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.OpenAiChatRequestParameters
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 리즈닝(추론) 처리를 위한 공통 유틸리티 클래스
 *
 * 이 유틸리티는 LLM의 thinking 모델 지원을 위한 공통 로직을 제공합니다.
 * - ChatRequest 변환 시 no-think 트리거 처리
 * - 스트리밍 응답에서 <think> 태그 분리 및 처리
 */
object ReasoningProcessingUtil {
  private val kLogger = KotlinLogging.logger {}

  // Think 태그 관련 상수
  private const val THINK_OPEN_TAG = "<think>"
  private const val THINK_CLOSE_TAG = "</think>"
  private const val THINK_OPEN_TAG_LENGTH = 7  // "<think>".length
  private const val THINK_CLOSE_TAG_LENGTH = 8  // "</think>".length
  private const val PARTIAL_TAG_CHECK_LENGTH = 6  // "<think".length

  // ========== CustomParameters 기반 No-Think 트리거 관련 상수 ==========
  // noThinkTrigger 값이 이 키워드들 중 하나이면 메시지 텍스트 대신 customParameters API 파라미터를 사용
  private const val ENABLE_THINKING_TRIGGER = "enable_thinking"

  // 트리거 키워드별 customParameters 설정 맵 (OCP 준수: 새 트리거 추가 시 여기만 수정)
  // vLLM/SGLang API 스펙: chat_template_kwargs.enable_thinking = false로 thinking 비활성화
  private val CUSTOM_PARAM_CONFIGS: Map<String, Map<String, Any>> = mapOf(
    ENABLE_THINKING_TRIGGER to mapOf(
      "chat_template_kwargs" to mapOf("enable_thinking" to false)
    )
    // 향후 추가 예정:
    // "thinking_disabled" to mapOf("thinking" to mapOf("type" to "disabled"))  // Z.AI API 방식
  )

  // 확장을 위한 특수 트리거 키워드 집합 (CUSTOM_PARAM_CONFIGS의 키와 동기화)
  private val CUSTOM_PARAM_TRIGGERS = CUSTOM_PARAM_CONFIGS.keys

  /**
   * 주어진 트리거가 customParameters 방식을 사용해야 하는지 판단합니다.
   * 특수 키워드인 경우 메시지에 텍스트를 추가하는 대신 API 파라미터로 전달합니다.
   *
   * @param trigger noThinkTrigger 값
   * @return customParameters 방식 사용 여부
   */
  fun isCustomParameterTrigger(trigger: String?): Boolean {
    if (trigger.isNullOrBlank()) return false
    return trigger.lowercase() in CUSTOM_PARAM_TRIGGERS
  }

  /**
   * 트리거 타입에 따른 customParameters 맵을 생성합니다.
   * vLLM/SGLang 환경에서 thinking 모드를 비활성화하기 위한 파라미터를 생성합니다.
   *
   * @param trigger 트리거 키워드 (예: "enable_thinking")
   * @return API 요청에 포함될 customParameters 맵
   */
  fun buildNoThinkCustomParameters(trigger: String): Map<String, Any> {
    return CUSTOM_PARAM_CONFIGS[trigger.lowercase()] ?: emptyMap()
  }

  /**
   * ChatCompletionRequest를 LangChain4j의 ChatRequest로 변환하며 리즈닝 처리를 적용합니다.
   *
   * @param assistant 실행할 AI 어시스턴트
   * @param request 변환할 채팅 요청
   * @return 리즈닝이 처리된 ChatRequest
   */
  fun createChatRequestWithReasoning(
    assistant: RunnableAiAssistant,
    request: ChatCompletionRequest
  ): ChatRequest {
    val builder = ChatRequest.builder()

    request.messages?.let { messages ->
      // 메시지 통계 수집
      var totalChars = 0
      var systemChars = 0
      var userChars = 0
      var assistantChars = 0

      // 마지막 user 메시지 인덱스 찾기
      val userMessageIndices = messages.mapIndexedNotNull { index, message ->
        if (message.role == "user") index else null
      }
      val lastUserMessageIndex = userMessageIndices.lastOrNull()

      val processedMessages = messages.mapIndexed { index, message ->
        var content = message.content

        // 원본 메시지 길이 집계 (싱킹 모델 처리 전)
        val originalLength = content.length
        when (message.role) {
          "system" -> systemChars += originalLength
          "user" -> userChars += originalLength
          "assistant" -> assistantChars += originalLength
        }
        totalChars += originalLength

        // 모든 user 메시지에서 기존 no-think 트리거 제거
        if (message.role == "user") {
          content = content.removeSuffix(" ${assistant.noThinkTrigger}").trim()
        }

        // 싱킹 모델이 활성화되어 있고, reasoning effort가 NONE인 경우
        // 마지막 user 메시지에만 no-think 트리거 추가 (단, customParameters 방식이 아닌 경우에만)
        if (index == lastUserMessageIndex &&
          assistant.reasoningEffort == ReasoningEffort.NONE &&
          message.role == "user" &&
          !assistant.noThinkTrigger.isNullOrBlank() &&
          !isCustomParameterTrigger(assistant.noThinkTrigger) // 텍스트 트리거 방식일 때만
        ) {
          content = "$content ${assistant.noThinkTrigger}"
          kLogger.debug { "No-think 트리거 추가 (텍스트 방식): ${assistant.noThinkTrigger}" }
        }

        // 메시지 타입별 변환
        when (message.role) {
          "system" -> SystemMessage(content)
          "user" -> UserMessage(content)
          "assistant" -> AiMessage(content)
          else -> throw BadRequestException("지원하지 않는 메시지 역할입니다: ${message.role}")
        }
      }

      builder.messages(processedMessages)

      kLogger.debug {
        "ChatRequest 변환 완료 - 메시지 수: ${messages.size}, 총 문자: ${totalChars}자 " +
            "(system: ${systemChars}자, user: ${userChars}자, assistant: ${assistantChars}자), " +
            "추론 지원: ${assistant.supportsReasoning}, 추론 노력: ${assistant.reasoningEffort}"
      }
    } ?: run {
      kLogger.warn { "메시지가 없는 ChatRequest 변환 요청" }
    }

    // 다른 파라미터 적용
    request.temperature?.let { builder.temperature(it) }
    request.maxTokens?.let { builder.maxOutputTokens(it) }

    // customParameters 방식 no-think 트리거 처리
    // noThinkTrigger가 특수 키워드(예: "enable_thinking")인 경우 API 파라미터로 전달
    val noThinkTrigger = assistant.noThinkTrigger
    if (assistant.supportsReasoning &&
      assistant.reasoningEffort == ReasoningEffort.NONE &&
      !noThinkTrigger.isNullOrBlank() &&
      isCustomParameterTrigger(noThinkTrigger)
    ) {
      // Smart cast로 noThinkTrigger가 non-null String으로 확정됨
      val customParams = buildNoThinkCustomParameters(noThinkTrigger)
      if (customParams.isNotEmpty()) {
        builder.parameters(
          OpenAiChatRequestParameters.builder()
            .customParameters(customParams)
            .build()
        )
        kLogger.debug { "No-think 트리거 추가 (customParameters 방식): $customParams" }
      }
    }

    return builder.build()
  }

  /**
   * 스트리밍 응답 처리 시 thinking 태그를 분리하는 핸들러를 생성합니다.
   *
   * @param assistant 실행 중인 AI 어시스턴트
   * @param onContent 일반 컨텐츠 수신 콜백
   * @param onReasoning 추론 컨텐츠 수신 콜백
   * @param onComplete 완료 콜백
   * @param onError 에러 콜백
   * @return thinking 태그를 처리하는 StreamingChatResponseHandler
   */
  fun createReasoningAwareStreamingHandler(
    assistant: RunnableAiAssistant,
    onContent: (String) -> Unit,
    onReasoning: ((String, Int) -> Unit)? = null,
    onComplete: (ChatResponse) -> Unit,
    onError: (Throwable) -> Unit
  ): StreamingChatResponseHandler {
    // think 태그 처리를 위한 상태 변수들
    val contentBuffer = StringBuilder()
    val thinkBuffer = StringBuilder()
    var isInsideThink = false
    var reasoningIndex = 0
    var hasContentStarted = false

    return object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        // 추론을 지원하지 않는 모델은 바로 content로 전송
        if (!assistant.supportsReasoning) {
          onContent(partialResponse)
          return
        }

        contentBuffer.append(partialResponse)

        // think 태그 처리
        var buffer = contentBuffer.toString()
        contentBuffer.clear()

        while (buffer.isNotEmpty()) {
          when {
            // <think> 태그 시작 찾기
            !isInsideThink && buffer.contains(THINK_OPEN_TAG) -> {
              val thinkStart = buffer.indexOf(THINK_OPEN_TAG)

              // <think> 이전의 내용을 content로 전송
              if (thinkStart > 0) {
                val beforeThink = buffer.substring(0, thinkStart)
                val contentToSend = if (!hasContentStarted) {
                  beforeThink.trimStart('\n')
                } else {
                  beforeThink
                }
                if (contentToSend.isNotEmpty()) {
                  onContent(contentToSend)
                  hasContentStarted = true
                }
              }

              // <think> 태그 이후로 이동
              buffer = buffer.substring(thinkStart + THINK_OPEN_TAG_LENGTH)
              isInsideThink = true
              thinkBuffer.clear()
            }

            // </think> 태그 끝 찾기
            isInsideThink && buffer.contains(THINK_CLOSE_TAG) -> {
              val thinkEnd = buffer.indexOf(THINK_CLOSE_TAG)

              // </think> 이전까지의 내용을 think 버퍼에 추가
              if (thinkEnd > 0) {
                thinkBuffer.append(buffer.substring(0, thinkEnd))
              }

              // reasoning 내용 전송
              if (thinkBuffer.isNotEmpty() && onReasoning != null) {
                val reasoningText = thinkBuffer.toString()
                onReasoning(reasoningText, reasoningIndex)
                reasoningIndex++
              }

              // </think> 태그 이후로 이동
              buffer = buffer.substring(thinkEnd + THINK_CLOSE_TAG_LENGTH)
              isInsideThink = false
              thinkBuffer.clear()
            }

            // think 태그 내부에 있을 때
            isInsideThink -> {
              // </think>가 나올 때까지 버퍼링
              if (!buffer.contains(THINK_CLOSE_TAG)) {
                thinkBuffer.append(buffer)
                buffer = ""
              }
            }

            // think 태그 외부에 있을 때
            else -> {
              // <think>가 나올 때까지 또는 모든 내용을 content로 전송
              if (!buffer.contains(THINK_OPEN_TAG)) {
                // 부분 태그 가능성 체크
                val lastChars = buffer.takeLast(PARTIAL_TAG_CHECK_LENGTH)
                var possiblePartial = false

                for (i in 1..PARTIAL_TAG_CHECK_LENGTH.coerceAtMost(lastChars.length)) {
                  if (lastChars.takeLast(i) == THINK_OPEN_TAG.dropLast(1).take(i)) {
                    possiblePartial = true
                    // 부분 태그 가능성이 있으면 그 이전까지만 전송
                    val safeContent = buffer.dropLast(i)
                    if (safeContent.isNotEmpty()) {
                      val contentToSend = if (!hasContentStarted) {
                        safeContent.trimStart('\n')
                      } else {
                        safeContent
                      }
                      if (contentToSend.isNotEmpty()) {
                        onContent(contentToSend)
                        hasContentStarted = true
                      }
                    }
                    contentBuffer.append(buffer.takeLast(i))
                    buffer = ""
                    break
                  }
                }

                if (!possiblePartial) {
                  // 부분 태그 가능성이 없으면 모두 전송
                  val contentToSend = if (!hasContentStarted) {
                    buffer.trimStart('\n')
                  } else {
                    buffer
                  }
                  if (contentToSend.isNotEmpty()) {
                    onContent(contentToSend)
                    hasContentStarted = true
                  }
                  buffer = ""
                }
              }
            }
          }
        }
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        // 남은 버퍼 처리
        if (contentBuffer.isNotEmpty()) {
          val remaining = contentBuffer.toString()
          if (isInsideThink) {
            // think 태그 안에 있었다면 reasoning으로
            if ((thinkBuffer.isNotEmpty() || remaining.isNotEmpty()) && onReasoning != null) {
              val reasoningText = thinkBuffer.toString() + remaining
              onReasoning(reasoningText, reasoningIndex)
            }
          } else {
            // 일반 content로
            val contentToSend = if (!hasContentStarted) {
              remaining.trimStart('\n')
            } else {
              remaining
            }
            if (contentToSend.isNotEmpty()) {
              onContent(contentToSend)
            }
          }
        }

        onComplete(completeResponse)
      }

      override fun onError(error: Throwable) {
        kLogger.error(error) { "스트리밍 처리 중 에러 발생" }
        onError(error)
      }
    }
  }

  /**
   * 간단한 스트리밍 핸들러 생성 (reasoning 처리 없이 모든 내용을 content로)
   */
  fun createSimpleStreamingHandler(
    onContent: (String) -> Unit,
    onComplete: (ChatResponse) -> Unit,
    onError: (Throwable) -> Unit
  ): StreamingChatResponseHandler {
    return object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        onContent(partialResponse)
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        onComplete(completeResponse)
      }

      override fun onError(error: Throwable) {
        onError(error)
      }
    }
  }

  /**
   * 메시지 리스트를 리즈닝 처리를 적용하여 ChatRequest로 변환합니다.
   *
   * @param assistant 실행할 AI 어시스턴트
   * @param messages 변환할 메시지 리스트
   * @param temperature 온도 파라미터 (선택)
   * @param maxTokens 최대 토큰 수 (선택)
   * @return 리즈닝이 처리된 ChatRequest
   */
  fun createChatRequestFromMessages(
    assistant: RunnableAiAssistant,
    messages: List<dev.langchain4j.data.message.ChatMessage>,
    temperature: Double? = null,
    maxTokens: Int? = null
  ): ChatRequest {
    return createChatRequestFromMessages(assistant, messages, null, temperature, maxTokens)
  }

  /**
   * 메시지 리스트를 리즈닝 처리를 적용하여 ChatRequest로 변환합니다.
   * 동적 LLM 옵션을 지원합니다.
   *
   * @param assistant 실행할 AI 어시스턴트 (프리셋)
   * @param messages 변환할 메시지 리스트
   * @param dynamicOptions 동적 LLM 옵션 (null이면 프리셋 사용)
   * @param temperature 온도 파라미터 (선택, dynamicOptions.temperature보다 우선)
   * @param maxTokens 최대 토큰 수 (선택, dynamicOptions.maxTokens보다 우선)
   * @return 리즈닝이 처리된 ChatRequest
   */
  fun createChatRequestFromMessages(
    assistant: RunnableAiAssistant,
    messages: List<dev.langchain4j.data.message.ChatMessage>,
    dynamicOptions: LlmDynamicOptions?,
    temperature: Double? = null,
    maxTokens: Int? = null
  ): ChatRequest {
    val builder = ChatRequest.builder()

    // 동적 옵션과 프리셋 병합하여 최종 리즈닝 설정 결정
    val effectiveSupportsReasoning = dynamicOptions?.supportsReasoning ?: assistant.supportsReasoning
    val effectiveReasoningEffort = dynamicOptions?.reasoningEffort ?: assistant.reasoningEffort
    val effectiveNoThinkTrigger = dynamicOptions?.noThinkTrigger ?: assistant.noThinkTrigger

    // 마지막 user 메시지 인덱스 찾기
    val userMessageIndices = messages.mapIndexedNotNull { index, message ->
      if (message is UserMessage) index else null
    }
    val lastUserMessageIndex = userMessageIndices.lastOrNull()

    val processedMessages = messages.mapIndexed { index, message ->
      when (message) {
        is UserMessage -> {
          var content = message.contents().joinToString { if (it is TextContent) it.text() else "" }

          // 기존 no-think 트리거 제거
          if (!effectiveNoThinkTrigger.isNullOrBlank()) {
            content = content.removeSuffix(" $effectiveNoThinkTrigger").trim()
          }

          // 싱킹 모델이 활성화되어 있고, reasoning effort가 NONE인 경우
          // 마지막 user 메시지에만 no-think 트리거 추가 (단, customParameters 방식이 아닌 경우에만)
          if (index == lastUserMessageIndex &&
            effectiveReasoningEffort == ReasoningEffort.NONE &&
            !effectiveNoThinkTrigger.isNullOrBlank() &&
            !isCustomParameterTrigger(effectiveNoThinkTrigger) // 텍스트 트리거 방식일 때만
          ) {
            content = "$content $effectiveNoThinkTrigger"
            kLogger.debug { "No-think 트리거 추가 (텍스트 방식): $effectiveNoThinkTrigger" }
          }

          UserMessage(content)
        }

        else -> message
      }
    }

    builder.messages(processedMessages)

    // 동적 옵션에서 파라미터 적용 (명시적 파라미터 > 동적옵션 > null)
    val effectiveTemperature = temperature ?: dynamicOptions?.temperature
    val effectiveMaxTokens = maxTokens ?: dynamicOptions?.maxTokens

    effectiveTemperature?.let { builder.temperature(it) }
    effectiveMaxTokens?.let { builder.maxOutputTokens(it) }

    // customParameters 방식 no-think 트리거 처리
    // noThinkTrigger가 특수 키워드(예: "enable_thinking")인 경우 API 파라미터로 전달
    if (effectiveSupportsReasoning &&
      effectiveReasoningEffort == ReasoningEffort.NONE &&
      !effectiveNoThinkTrigger.isNullOrBlank() &&
      isCustomParameterTrigger(effectiveNoThinkTrigger)
    ) {
      // Smart cast로 effectiveNoThinkTrigger가 non-null String으로 확정됨
      val customParams = buildNoThinkCustomParameters(effectiveNoThinkTrigger)
      if (customParams.isNotEmpty()) {
        builder.parameters(
          OpenAiChatRequestParameters.builder()
            .customParameters(customParams)
            .build()
        )
        kLogger.debug { "No-think 트리거 추가 (customParameters 방식): $customParams" }
      }
    }

    // 엔티티 ID 기반 모델 변경은 상위 레벨에서 처리되므로, 로깅에는 프리셋 모델명 사용
    // 동적 옵션에 aiModelId가 있으면 참고용으로 표시
    val modelIdInfo = dynamicOptions?.aiModelId?.let { " (동적 modelId=$it)" } ?: ""
    kLogger.info {
      "ChatRequest 변환 완료 - 모델: ${assistant.model}$modelIdInfo, 메시지 수: ${messages.size}, " +
          "추론 지원: $effectiveSupportsReasoning, 추론 노력: $effectiveReasoningEffort" +
          if (dynamicOptions?.hasAnyOption() == true) " (동적 옵션 적용)" else ""
    }

    return builder.build()
  }
}
