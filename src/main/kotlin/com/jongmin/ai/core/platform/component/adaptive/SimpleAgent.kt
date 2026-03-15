package com.jongmin.ai.core.platform.component.adaptive

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.jspring.core.util.cleanJsonString
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.IAiChatMessage
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.ExecuteAiAssistant
import com.jongmin.ai.core.backoffice.dto.request.ExecuteModel
import com.jongmin.ai.core.backoffice.dto.request.TranslateRequest
import com.jongmin.ai.core.platform.component.*
import com.jongmin.ai.core.platform.component.astrology.FortuneTeller
import com.jongmin.ai.core.platform.component.astrology.FortuneTellerRequest
import com.jongmin.ai.core.platform.component.astrology.FortuneTellerResponse
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.service.AiModelService
import com.jongmin.ai.auth.AccountService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI 에이전트 실행을 관리하는 중앙 컴포넌트
 *
 * 이 클래스는 다양한 AI 작업(질문 응답, 번역, 운세 생성 등)을 비동기로 실행하는 역할을 합니다.
 * 내부적으로 스레드 풀을 사용하여 여러 AI 추론 작업을 병렬로 처리합니다.
 *
 * ## 리즈닝(Reasoning) 처리 통합
 *
 * 모든 실행 메서드는 [ReasoningProcessingUtil]을 활용하여 thinking 모델의 추론 처리를 지원합니다:
 *
 * ### 주요 기능:
 * 1. **no-think 트리거 자동 적용**: [ReasoningProcessingUtil.createChatRequestFromMessages]를 통해
 *    마지막 user 메시지에 no-think 트리거를 자동으로 추가합니다.
 * 2. **<think> 태그 분리**: [ReasoningProcessingUtil.createReasoningAwareStreamingHandler]를 통해
 *    스트리밍 응답에서 추론 과정(<think>...</think>)과 최종 답변을 분리하여 처리합니다.
 * 3. **리즈닝 레벨 제어**: reasoning effort (NONE, LOW, MEDIUM, HIGH)에 따라 추론 깊이를 조절합니다.
 *
 * ### 적용 메서드:
 * - [executeQuestionAnswerer]: 대화형 질문 응답 (메시지 기반)
 * - [executeDirectInference]: 직접 추론 (스트리밍 지원)
 * - [executeThreadTitleGenerator]: 스레드 제목 생성
 * - [executeTranslator]: 번역
 * - [executeFortuneTelling]: 운세 생성
 * - [callAiAssistant]: 범용 AI 어시스턴트 호출
 *
 * ### 내부 구현:
 * 각 메서드는 해당 Generator 컴포넌트(SingleInputSingleOutputGenerator, QuestionAnswerer 등)를 호출하며,
 * 이들 컴포넌트 내부에서 [ReasoningProcessingUtil]을 사용하여 실제 리즈닝 처리를 수행합니다.
 *
 * @property objectMapper JSON 직렬화/역직렬화를 위한 ObjectMapper
 * @property chatTopic 이벤트 스트림 토픽 이름
 * @property eventSender 이벤트 전송 컴포넌트
 * @property aiAssistantService AI 어시스턴트 관리 서비스
 * @property accountRepository 계정 정보 저장소
 * @property aiModelService AI 모델 관리 서비스
 *
 * @see ReasoningProcessingUtil
 * @see SingleInputSingleOutputGenerator
 * @see StreamingSingleInputSingleOutputGenerator
 * @see QuestionAnswerer
 */
@Component
class SimpleAgent(
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val cancellationManager: AIInferenceCancellationManager,
  private val eventSender: EventSender,
  private val aiAssistantService: AiAssistantService,
  private val accountService: AccountService,
  private val aiModelService: AiModelService,
  private val rateLimiter: LlmRateLimiter,
  private val llmGateway: LlmGateway,
) : ApplicationListener<ContextClosedEvent>, Ordered {

  private val kLogger = KotlinLogging.logger {}
  private val shutdownHandled = AtomicBoolean(false)

  private val executors = Executors.newScheduledThreadPool(32)

  val id: Long = 0

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

  override fun onApplicationEvent(event: ContextClosedEvent) {
    shutdown()
  }

  fun shutdown() {
    if (!shutdownHandled.compareAndSet(false, true)) return

    kLogger.info { "SimpleAgent 스레드 풀 종료 시작" }
    executors.shutdown() // 새로운 작업을 받지 않음
    try {
      if (!executors.awaitTermination(1, TimeUnit.MINUTES)) executors.shutdownNow()
    } catch (_: InterruptedException) {
      executors.shutdownNow() // 인터럽트 발생 시 강제 종료
      Thread.currentThread().interrupt() // 인터럽트 상태 복원
    }
    kLogger.info { "SimpleAgent 스레드 풀 종료 완료" }
  }

  /**
   * AI 어시스턴트를 호출하여 질문에 답변합니다.
   *
   * 리즈닝(추론) 처리:
   * - SingleInputSingleOutputGenerator 내부에서 어시스턴트의 리즈닝 설정(supportsReasoning, reasoningEffort)에 따라
   *   ReasoningProcessingUtil을 사용하여 추론 처리를 수행합니다.
   * - thinking 모델의 경우 <think> 태그가 자동으로 처리됩니다.
   *
   * @param aiAssistantId AI 어시스턴트 ID
   * @param dto 실행 파라미터 (질문 등)
   * @return 생성된 답변
   */
  fun callAiAssistant(aiAssistantId: Long, dto: ExecuteAiAssistant): String {
    kLogger.info { "AI 어시스턴트 추론 시작됨" }
    val assi = aiAssistantService.findById(aiAssistantId)
    // LlmGateway를 통한 자동 추적 적용
    return SingleInputSingleOutputGenerator(llmGateway, assi).apply(assi.getInstructionsWithCurrentTime(), dto.question!!)
  }

  /**
   * 직접 추론(Direct Inference)을 실행합니다.
   * 지정된 모델과 파라미터로 즉시 추론을 수행하는 특수 목적 API입니다.
   *
   * 리즈닝(추론) 처리:
   * - RunnableAiAssistant 생성 시 supportsReasoning, reasoningEffort, noThinkTrigger 파라미터를 전달하여
   *   thinking 모델 지원을 활성화합니다.
   * - StreamingSingleInputSingleOutputGenerator 내부에서 ReasoningProcessingUtil을 사용하여
   *   스트리밍 응답 중 <think> 태그를 분리하고 처리합니다.
   * - 리즈닝 지원 모델의 경우 추론 과정(reasoning)과 최종 답변(content)이 분리되어 전송됩니다.
   *
   * @param accountId 계정 ID
   * @param dto 모델 실행 파라미터 (모델 ID, API 키, 온도, top-p 등)
   * @param onEnded 추론 완료 시 호출될 콜백 함수
   */
  fun executeDirectInference(
    emitter: FluxSink<String>,
    accountId: Long,
    dto: ExecuteModel,
    onEnded: ((answer: String) -> Unit)? = null
  ) {
    kLogger.info { "DirectInference 추론 시작됨" }
    try {
      if (dto.aiAssistantId != null) {
        val assistant = aiAssistantService.findById(dto.aiAssistantId!!)
        val result = StreamingSingleInputSingleOutputGenerator(
          objectMapper,
          cancellationManager,
          llmGateway,
          assistant,
          emitter,
          eventSender,
          chatTopic,
          accountId,
          dto.canvasId!!,
        ).apply(assistant.getInstructionsWithCurrentTime(), dto.question!!)
        onEnded?.invoke(result)
        return
      }

      // 향후 재해 감지 모델 스위칭이 개발돼도 여기서의 모델에는 적용하지 말자. 이 API는 특수한 목적으로 사용된다.
      val props = aiModelService.findAiCoreProperties(dto.apiKeyId!!, dto.modelId!!)
      // LlmGateway 경유 - 직접 추론 스트리밍 (Rate Limiter 및 추적 자동 처리)
      val result =
        StreamingSingleInputSingleOutputGenerator(
          objectMapper,
          cancellationManager,
          llmGateway,
          RunnableAiAssistant(
            -1,
            "DirectInference",
            null,
            props.provider,
            props.baseUrl,
            props.model,
            props.supportsReasoning, // 리즈닝 지원 여부
            props.reasoningEffort,   // 리즈닝 노력 수준 (LOW, MEDIUM, HIGH, NONE)
            props.noThinkTrigger,    // no-think 트리거 문자열 (예: "/no_think")
            props.apiKey,
            dto.instructions,
            dto.temperature,
            if (dto.topP == 0.0) null else dto.topP,
            null, // topK
            null, // frequencyPenalty
            null, // presencePenalty
            dto.responseFormat,
            if (dto.maxTokens == 0) null else dto.maxTokens,
            StatusType.ACTIVE,
            AiAssistantType.QUESTION_ANSWERER
          ),
          emitter, eventSender, chatTopic, accountId, dto.canvasId!!,
        ).apply(dto.instructions!!, dto.question!!)
      onEnded?.invoke(result)
    } catch (e: Exception) {
      kLogger.error { "DirectInference 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
      emitter.error(e)
      throw e
    } finally {
      kLogger.info { "DirectInference 추론 완료됨" }
    }
  }

  /**
   * QuestionAnswerer를 실행하여 대화형 질문에 답변합니다.
   *
   * 리즈닝(추론) 처리:
   * - QuestionAnswerer 내부에서 ReasoningProcessingUtil.createChatRequestFromMessages를 사용하여
   *   thinking 모델의 <think> 태그를 처리하고 no-think 트리거를 적용합니다.
   * - 리즈닝 지원 모델의 경우 추론 과정이 자동으로 분리되어 처리됩니다.
   *
   * @param accountId 계정 ID
   * @param aiThreadId AI 스레드 ID
   * @param messages 대화 메시지 리스트
   * @param onEnded 추론 완료 시 호출될 콜백 함수
   */
  fun executeQuestionAnswerer(
    accountId: Long,
    aiThreadId: Long,
    messages: List<IAiChatMessage>,
    onEnded: ((answer: String) -> Unit)? = null
  ) {
    executors.submit {
      kLogger.info { "QuestionAnswerer 추론 시작됨 - 메시지 수: ${messages.size}" }
      try {
        val agentAssistant = aiAssistantService.findFirst(AiAssistantType.QUESTION_ANSWERER)
        // LlmGateway를 통한 자동 추적 적용
        val result = QuestionAnswerer(llmGateway, agentAssistant, eventSender, chatTopic, accountId, aiThreadId).apply(messages)
        onEnded?.invoke(result)
      } catch (e: Exception) {
        kLogger.error { "QuestionAnswerer 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
      } finally {
        kLogger.info { "QuestionAnswerer 추론 완료됨" }
      }
    }
  }

  /**
   * 스레드 제목을 자동으로 생성합니다.
   *
   * 리즈닝(추론) 처리:
   * - SingleInputSingleOutputGenerator 내부에서 어시스턴트의 리즈닝 설정에 따라
   *   ReasoningProcessingUtil을 사용하여 추론 처리를 수행합니다.
   * - 제목 생성과 같은 간단한 작업에서도 thinking 모델이 활성화된 경우
   *   no-think 트리거를 통해 빠른 응답을 유도할 수 있습니다.
   *
   * @param accountId 계정 ID
   * @param input 제목을 생성할 대화 내용
   * @param onEnded 생성 완료 시 호출될 콜백 함수
   */
  fun executeThreadTitleGenerator(accountId: Long, input: String, onEnded: ((answer: String) -> Unit)? = null) {
    executors.submit {
      kLogger.info { "ThreadTitleGenerator 시작됨 - accountId: $accountId" }
      try {
        val assistant = aiAssistantService.findFirst(AiAssistantType.THREAD_TITLE_GENERATOR)
        // LlmGateway를 통한 자동 추적 적용
        val result = SingleInputSingleOutputGenerator(
          llmGateway,
          assistant,
          callerComponent = "ThreadTitleGenerator"
        ).apply(assistant.getInstructionsWithCurrentTime(), input)
        onEnded?.invoke(result)
      } catch (e: Exception) {
        kLogger.error { "ThreadTitleGenerator 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
      } finally {
        kLogger.info { "ThreadTitleGenerator 추론 완료됨" }
      }
    }
  }

  /**
   * 번역기를 실행하여 텍스트를 번역합니다.
   *
   * 리즈닝(추론) 처리:
   * - SingleInputSingleOutputGenerator 내부에서 어시스턴트의 리즈닝 설정에 따라
   *   ReasoningProcessingUtil을 사용하여 추론 처리를 수행합니다.
   * - 번역 작업의 경우 정확성이 중요하므로, thinking 모델이 활성화된 경우
   *   추론 과정을 통해 더 정확한 번역 결과를 얻을 수 있습니다.
   *
   * @param emitter 결과를 스트리밍할 FluxSink (선택)
   * @param accountId 계정 ID
   * @param aiAssistantName AI 어시스턴트 이름
   * @param dto 번역 요청 파라미터
   */
  fun executeTranslator(emitter: FluxSink<String>? = null, accountId: Long, aiAssistantName: String, dto: TranslateRequest) {
    kLogger.info { "Translator 시작됨 - accountId: $accountId" }
    var generated = ""
    try {
      val assistant = aiAssistantService.findByName(aiAssistantName)
      // LlmGateway를 통한 자동 추적 적용
      generated = SingleInputSingleOutputGenerator(llmGateway, assistant, callerComponent = "Translator")
        .apply(assistant.getInstructionsWithCurrentTime(), objectMapper.writeValueAsString(dto))
        .cleanJsonString()
      emitter?.next(generated)
    } catch (e: Exception) {
      kLogger.error { "Translator 추론 중 오류 발생: $generated\n${e.message}\n${e.stackTraceToString()}" }
      emitter?.error(e)
    } finally {
      kLogger.info { "Translator 추론 완료됨" }
      emitter?.complete()
    }
  }

  /**
   * 운세를 생성합니다.
   *
   * 리즈닝(추론) 처리:
   * - FortuneTeller 내부에서 어시스턴트의 리즈닝 설정에 따라
   *   ReasoningProcessingUtil을 사용하여 추론 처리를 수행합니다.
   * - 운세 생성과 같은 창의적 작업의 경우 thinking 모델을 통해
   *   더 풍부하고 일관성 있는 결과를 얻을 수 있습니다.
   *
   * @param session 사용자 세션
   * @param onEnded 생성 완료 시 호출될 콜백 함수
   */
  fun executeFortuneTelling(session: JSession, onEnded: ((answer: FortuneTellerResponse) -> Unit)? = null) {

    executors.submit {
      kLogger.info { "운세 생성 시작됨" }
      try {
        val agentAssistant = aiAssistantService.findFirst(AiAssistantType.FORTUNE_TELLER)
        val account = accountService.findByIdOrThrow(session.accountId)

        // 운세 생성을 위한 랜덤 파라미터 생성
        val request = FortuneTellerRequest(
          account.nickname,
          "unknown",
          "unknown",
          ThreadLocalRandom.current().nextInt(100) + 1,
          ThreadLocalRandom.current().nextInt(100) + 1,
          ThreadLocalRandom.current().nextInt(100) + 1,
          when (ThreadLocalRandom.current().nextInt(5)) {
            0 -> "木"
            1 -> "火"
            2 -> "土"
            3 -> "金"
            else -> "水"
          }
        )

        // Rate Limiter 적용 - 운세 생성
        onEnded?.invoke(FortuneTeller(objectMapper, agentAssistant, rateLimiter).apply(request))
      } catch (e: Exception) {
        kLogger.error { "운세 생성 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
      } finally {
        kLogger.info { "운세 생성 완료됨" }
      }
    }
  }
}
