package com.jongmin.ai.core.platform.service

import com.jongmin.jspring.core.enums.JService
import com.jongmin.jspring.core.util.serviceTrace
import com.jongmin.ai.core.AiCoreProperties
import com.jongmin.ai.core.LlmDynamicOptions
import com.jongmin.ai.core.ResolvedLlmOptions
import com.jongmin.ai.core.RunnableAiAssistant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * LLM 동적 옵션 해석기
 *
 * 워크플로우 UI에서 전달받은 엔티티 ID 기반 동적 옵션을
 * 실제 LLM 호출에 사용할 수 있는 값으로 변환한다.
 *
 * 동작 방식:
 * 1. LlmDynamicOptions에 aiModelId, aiApiKeyId가 있으면
 * 2. DB에서 해당 엔티티들을 조회하여
 * 3. 실제 provider, model, apiKey, baseUrl 등의 값을 반환
 *
 * @author Claude Code
 * @since 2025.12.26
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class LlmDynamicOptionsResolver(
  private val aiModelService: AiModelService,
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 동적 옵션에서 엔티티 ID 기반 설정을 해석하여 AiCoreProperties 반환
   *
   * aiModelId와 aiApiKeyId가 모두 있어야 해석 가능.
   * 둘 중 하나라도 없으면 null 반환 (프리셋 사용).
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @return 해석된 AI 코어 프로퍼티, 해석 불가시 null
   */
  fun resolveEntityBasedOptions(dynamicOptions: LlmDynamicOptions?): AiCoreProperties? {
    if (dynamicOptions == null) return null

    val modelId = dynamicOptions.aiModelId
    val apiKeyId = dynamicOptions.aiApiKeyId

    // 모델 ID와 API 키 ID가 모두 있어야 조회 가능
    if (modelId == null || apiKeyId == null) {
      if (dynamicOptions.hasEntityOverride()) {
        kLogger.warn {
          "동적 옵션에 엔티티 ID 일부만 설정됨 - " +
              "aiProviderId=${dynamicOptions.aiProviderId}, " +
              "aiModelId=$modelId, aiApiKeyId=$apiKeyId. " +
              "aiModelId와 aiApiKeyId가 모두 필요합니다. 프리셋 사용."
        }
      }
      return null
    }

    return try {
      kLogger.info { "동적 LLM 옵션 해석 - aiModelId=$modelId, aiApiKeyId=$apiKeyId" }
      val props = aiModelService.findAiCoreProperties(apiKeyId, modelId)
      kLogger.info { "  └─ 해석 결과: provider=${props.provider}, model=${props.model}" }
      props
    } catch (e: Exception) {
      kLogger.error(e) {
        "동적 LLM 옵션 해석 실패 - aiModelId=$modelId, aiApiKeyId=$apiKeyId. 프리셋 사용."
      }
      null
    }
  }

  /**
   * 동적 옵션과 기존 assistant를 병합하여 최종 AiCoreProperties 반환
   *
   * 우선순위:
   * 1. 엔티티 ID 기반 설정 (aiModelId, aiApiKeyId로 DB 조회)
   * 2. 기존 assistant 프리셋
   *
   * 생성 파라미터(temperature, topP 등)는 이 메서드에서 처리하지 않음.
   * 해당 파라미터는 RunnableAiModel.resolveOptions()에서 처리.
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트 (프리셋)
   * @return 최종 AI 코어 프로퍼티
   */
  fun resolveToProperties(
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): AiCoreProperties {
    // 엔티티 ID 기반 옵션 해석 시도
    val resolvedProps = resolveEntityBasedOptions(dynamicOptions)

    // 해석 성공하면 해당 값 사용, 실패하면 assistant 프리셋 사용
    return resolvedProps ?: AiCoreProperties(
      provider = assistant.provider,
      baseUrl = assistant.baseUrl,
      model = assistant.model,
      supportsReasoning = assistant.supportsReasoning,
      reasoningEffort = assistant.reasoningEffort,
      noThinkTrigger = assistant.noThinkTrigger,
      apiKey = assistant.apiKey,
    )
  }

  /**
   * 동적 옵션에 엔티티 ID 기반 변경이 있는지 확인
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @return 엔티티 ID 기반 변경이 있으면 true
   */
  fun hasEntityOverride(dynamicOptions: LlmDynamicOptions?): Boolean {
    return dynamicOptions?.hasEntityOverride() == true
  }

  /**
   * AiCoreProperties와 LlmDynamicOptions를 병합하여 ResolvedLlmOptions 생성
   *
   * AiCoreProperties에서 provider, model, apiKey, baseUrl 등 핵심 설정을 가져오고,
   * LlmDynamicOptions에서 생성 파라미터(temperature, topP 등)를 가져와서 병합한다.
   *
   * @param coreProps DB에서 조회한 AI 코어 프로퍼티
   * @param dynamicOptions 동적 LLM 옵션 (생성 파라미터)
   * @param assistant 기존 assistant (fallback용 생성 파라미터)
   * @return ChatModel/StreamingChatModel 생성에 사용할 최종 옵션
   */
  fun toResolvedLlmOptions(
    coreProps: AiCoreProperties,
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): ResolvedLlmOptions {
    return ResolvedLlmOptions(
      // AiCoreProperties에서 핵심 설정
      provider = coreProps.provider,
      baseUrl = coreProps.baseUrl,
      model = coreProps.model,
      apiKey = coreProps.apiKey,
      // AiCoreProperties에서 리즈닝 설정
      supportsReasoning = coreProps.supportsReasoning,
      reasoningEffort = coreProps.reasoningEffort,
      noThinkTrigger = coreProps.noThinkTrigger,
      // LlmDynamicOptions에서 생성 파라미터 (없으면 assistant 프리셋)
      temperature = dynamicOptions?.temperature ?: assistant.temperature,
      topP = dynamicOptions?.topP ?: assistant.topP,
      topK = dynamicOptions?.topK ?: assistant.topK,
      frequencyPenalty = dynamicOptions?.frequencyPenalty ?: assistant.frequencyPenalty,
      presencePenalty = dynamicOptions?.presencePenalty ?: assistant.presencePenalty,
      responseFormat = dynamicOptions?.responseFormat ?: assistant.responseFormat,
      maxTokens = dynamicOptions?.maxTokens ?: assistant.maxTokens,
    )
  }

  /**
   * 동적 옵션을 완전히 해석하여 ResolvedLlmOptions 반환
   *
   * 엔티티 ID 기반 설정이 있으면 DB에서 조회하고,
   * 생성 파라미터는 동적 옵션과 assistant 프리셋을 병합한다.
   *
   * @param dynamicOptions 동적 LLM 옵션
   * @param assistant 기존 AI 어시스턴트 (프리셋)
   * @return ChatModel/StreamingChatModel 생성에 사용할 최종 옵션
   */
  fun resolveToResolvedLlmOptions(
    dynamicOptions: LlmDynamicOptions?,
    assistant: RunnableAiAssistant
  ): ResolvedLlmOptions {
    // 엔티티 ID 기반 옵션 해석 시도
    val resolvedProps = resolveEntityBasedOptions(dynamicOptions)

    return if (resolvedProps != null) {
      // DB에서 조회 성공 → 조회된 props + 동적 생성 파라미터
      kLogger.serviceTrace(JService.AI) { "엔티티 ID 기반 옵션으로 ResolvedLlmOptions 생성" }
      toResolvedLlmOptions(resolvedProps, dynamicOptions, assistant)
    } else {
      // 조회 실패 또는 ID 없음 → assistant 프리셋 + 동적 생성 파라미터
      kLogger.serviceTrace(JService.AI) { "프리셋 기반 ResolvedLlmOptions 생성" }
      ResolvedLlmOptions(
        provider = assistant.provider,
        baseUrl = assistant.baseUrl,
        model = assistant.model,
        apiKey = assistant.apiKey,
        supportsReasoning = dynamicOptions?.supportsReasoning ?: assistant.supportsReasoning,
        reasoningEffort = dynamicOptions?.reasoningEffort ?: assistant.reasoningEffort,
        noThinkTrigger = dynamicOptions?.noThinkTrigger ?: assistant.noThinkTrigger,
        temperature = dynamicOptions?.temperature ?: assistant.temperature,
        topP = dynamicOptions?.topP ?: assistant.topP,
        topK = dynamicOptions?.topK ?: assistant.topK,
        frequencyPenalty = dynamicOptions?.frequencyPenalty ?: assistant.frequencyPenalty,
        presencePenalty = dynamicOptions?.presencePenalty ?: assistant.presencePenalty,
        responseFormat = dynamicOptions?.responseFormat ?: assistant.responseFormat,
        maxTokens = dynamicOptions?.maxTokens ?: assistant.maxTokens,
      )
    }
  }
}
