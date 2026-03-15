package com.jongmin.ai.product_agent.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.core.util.cleanThinkingString
import com.jongmin.ai.core.AgentOutputRepository
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.core.RunnableAiAssistant
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.service.OpenLlmBackendService.Companion.CANCELLATION_CHECK_INTERVAL
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import com.jongmin.ai.product_agent.platform.component.CopywritingPromptBuilder
import com.jongmin.ai.product_agent.platform.component.CopywritingResponseParser
import com.jongmin.ai.product_agent.platform.component.CopywritingResultBuilder
import com.jongmin.ai.product_agent.platform.component.CopywritingSseEmitter
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingData
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingRequest
import com.jongmin.ai.product_agent.platform.dto.response.AiCopyOnlyResponse
import com.jongmin.ai.product_agent.platform.entity.ProductAgentOutput
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper
import java.util.*

/**
 * 상품 에이전트 서비스
 *
 * 상품 카피라이팅 생성을 담당하는 핵심 서비스입니다.
 * SSE 스트리밍 방식으로 실시간 진행 상황을 클라이언트에 전송합니다.
 *
 * ### 주요 책임:
 * - 카피라이팅 생성 프로세스 조율 및 실행
 * - LLM과의 통신 및 응답 처리
 * - 단계별 진행 상황 관리
 *
 * ### 위임된 컴포넌트:
 * - [CopywritingPromptBuilder]: 프롬프트 생성 담당
 * - [CopywritingResponseParser]: 응답 파싱 담당
 * - [CopywritingResultBuilder]: 결과 생성 담당
 * - [CopywritingSseEmitter]: SSE 이벤트 전송 담당
 *
 * ### 프로세스 단계:
 * 1. PREPARING: 요청 검증 및 프롬프트 준비
 * 2. COPYWRITING: LLM을 통한 카피라이팅 생성 (스트리밍)
 * 3. COMPLETED: 전체 프로세스 완료
 *
 * @property cancellationManager AI 추론 취소 관리자
 * @property aiAssistantService AI 어시스턴트 조회 서비스
 * @property promptBuilder 프롬프트 생성 컴포넌트
 * @property responseParser 응답 파싱 컴포넌트
 * @property resultBuilder 결과 생성 컴포넌트
 * @property sseEmitter SSE 이벤트 전송 컴포넌트
 * @property transactionTemplate 비동기 콜백에서 프로그래매틱 트랜잭션 관리를 위한 템플릿
 */
@Service
class ProductAgentService(
  private val cancellationManager: AIInferenceCancellationManager,
  private val agentOutputRepository: AgentOutputRepository,
  private val aiAssistantService: AiAssistantService,
  private val promptBuilder: CopywritingPromptBuilder,
  private val responseParser: CopywritingResponseParser,
  private val resultBuilder: CopywritingResultBuilder,
  private val sseEmitter: CopywritingSseEmitter,
  private val objectMapper: ObjectMapper,
  private val transactionTemplate: TransactionTemplate,
  private val rateLimiter: LlmRateLimiter
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val GENERATION_ID_PREFIX = "copy-"
    private const val MARKETING_INSIGHT_ENABLED = false
  }

  /**
   * 카피라이팅 생성 프로세스 실행
   *
   * ### 실행 흐름:
   * 1. 추론 ID 생성 및 등록 (취소 관리)
   * 2. 요청 데이터 검증
   * 3. 카피라이팅 어시스턴트 조회
   * 4. 시스템 프롬프트 및 사용자 프롬프트 생성
   * 5. LLM 호출 및 스트리밍 응답 처리 (주기적 취소 확인)
   * 6. 완료 이벤트 전송 또는 취소 처리
   *
   * @param session 요청 사용자 세션
   * @param emitter SSE Flux Emitter
   * @param dto 카피라이팅 요청 DTO
   */
  fun generateCopywriting(
    session: JSession?,
    emitter: FluxSink<String>,
    dto: CopywritingRequest
  ) {
    // 추론 ID 생성 및 등록 (취소 관리)
    val inferenceId = "$GENERATION_ID_PREFIX${UUID.randomUUID()}"
    cancellationManager.registerInference(inferenceId)
    kLogger.info { "카피라이팅 추론 등록 - ID: $inferenceId, 계정: ${session?.accountId}" }

    // 취소 확인을 위한 변수들
    var copywritingTokenCount = 0
    var isCancelled = false

    try {
      // === 0단계: 요청 데이터 파싱 ===
      val copywritingData = responseParser.parseCopywritingData(dto.data)
      kLogger.info { "카피라이팅 생성 시작 - ID: $inferenceId, 계정: ${session?.accountId}, 스타일: ${copywritingData.copyStyle}" }

      // 입력 분석 시작 이벤트 전송
      sseEmitter.emitInputAnalysisActive(
        emitter = emitter,
        accountId = session?.accountId,
        copywritingData = copywritingData,
        hasImages = !dto.images.isNullOrEmpty()
      )

      // 카피라이터 어시스턴트 조회
      val copywriterAssistant = aiAssistantService.findFirst(AiAssistantType.PRODUCT_COPYWRITER)
      kLogger.debug { "카피라이터 어시스턴트 조회 완료 - ID: ${copywriterAssistant.id}" }

      // === 1단계: 카피라이팅 전용 프롬프트 생성 ===
      val copywritingSystemMessage = promptBuilder.buildCopywritingSystemMessage(copywriterAssistant, copywritingData)
      val copywritingUserMessage = promptBuilder.buildUserMessage(copywritingData, dto)

      // 입력 분석 완료 이벤트 전송
      sseEmitter.emitInputAnalysisDone(emitter)

      // === 2단계: 카피라이팅 생성 (1차 LLM 호출 - 스트리밍) ===
      sseEmitter.emitCopywritingActive(emitter)

      // ChatRequest 생성 (리즈닝 모델 처리 포함)
      val copywritingChatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
        assistant = copywriterAssistant,
        messages = listOf(copywritingSystemMessage, copywritingUserMessage)
      )

      // 시간 측정
      val totalStartTime = System.currentTimeMillis()
      val copywritingStartTime = System.currentTimeMillis()
      val copywritingContentBuilder = StringBuilder()

      // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
      val acquireResult = rateLimiter.acquireByProviderName(copywriterAssistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      // 리즈닝 인식 스트리밍 핸들러 생성
      val internalCopywritingHandler = createCopywritingStreamHandler(
        session = session,
        emitter = emitter,
        inferenceId = inferenceId,
        copywriterAssistant = copywriterAssistant,
        copywritingData = copywritingData,
        copywritingContentBuilder = copywritingContentBuilder,
        copywritingStartTime = copywritingStartTime,
        totalStartTime = totalStartTime,
        isCancelledGetter = { isCancelled },
        tokenCountGetter = { copywritingTokenCount },
        onRateLimitRelease = {
          rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
          kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }
        }
      )

      // 취소 확인을 위한 래퍼 핸들러
      val copywritingStreamingHandler = createCancellationAwareHandler(
        inferenceId = inferenceId,
        internalHandler = internalCopywritingHandler,
        tokenCountIncrementer = { copywritingTokenCount++ },
        tokenCountGetter = { copywritingTokenCount },
        isCancelledSetter = { isCancelled = true },
        isCancelledGetter = { isCancelled }
      )

      // 1차 LLM 스트리밍 호출 시작
      copywriterAssistant.chatWithStreaming(copywritingChatRequest, copywritingStreamingHandler)
    } catch (e: Exception) {
      kLogger.error(e) { "카피라이팅 생성 프로세스 중 예외 발생 - ID: $inferenceId" }
      cancellationManager.unregisterInference(inferenceId)
      sseEmitter.handleError(e, emitter)
    }
  }

  /**
   * 카피라이팅 스트리밍 핸들러 생성
   *
   * ReasoningProcessingUtil을 사용하여 리즈닝 인식 스트리밍 핸들러를 생성합니다.
   */
  private fun createCopywritingStreamHandler(
    session: JSession?,
    emitter: FluxSink<String>,
    inferenceId: String,
    copywriterAssistant: RunnableAiAssistant,
    copywritingData: CopywritingData,
    copywritingContentBuilder: StringBuilder,
    copywritingStartTime: Long,
    totalStartTime: Long,
    isCancelledGetter: () -> Boolean,
    tokenCountGetter: () -> Int,
    onRateLimitRelease: () -> Unit
  ): StreamingChatResponseHandler {
    return ReasoningProcessingUtil.createReasoningAwareStreamingHandler(
      assistant = copywriterAssistant,
      onContent = { content ->
        // 취소되지 않은 경우에만 content 토큰 처리
        if (!isCancelledGetter()) {
          copywritingContentBuilder.append(content)
          sseEmitter.emitCopywritingToken(
            emitter = emitter,
            token = content,
            currentLength = copywritingContentBuilder.length
          )
        }
      },
      onReasoning = { reasoningText, index ->
        // 취소되지 않은 경우에만 reasoning 토큰 처리
        if (!isCancelledGetter()) {
          sseEmitter.emitReasoningToken(
            emitter = emitter,
            reasoningText = reasoningText,
            index = index
          )
        }
      },
      onComplete = { _ ->
        // Rate Limiter 슬롯 반환
        onRateLimitRelease()

        handleCopywritingComplete(
          session = session,
          emitter = emitter,
          inferenceId = inferenceId,
          copywriterAssistant = copywriterAssistant,
          copywritingData = copywritingData,
          copywritingContentBuilder = copywritingContentBuilder,
          copywritingStartTime = copywritingStartTime,
          totalStartTime = totalStartTime,
          isCancelled = isCancelledGetter(),
          tokenCount = tokenCountGetter()
        )
      },
      onError = { error ->
        // Rate Limiter 슬롯 반환 (에러 시)
        onRateLimitRelease()

        kLogger.error(error) { "1차 LLM (카피라이팅) 생성 중 오류 발생 - ID: $inferenceId" }
        cancellationManager.unregisterInference(inferenceId)
        sseEmitter.handleError(Exception(error), emitter)
      }
    )
  }

  /**
   * 카피라이팅 완료 처리
   */
  private fun handleCopywritingComplete(
    session: JSession?,
    emitter: FluxSink<String>,
    inferenceId: String,
    copywriterAssistant: RunnableAiAssistant,
    copywritingData: CopywritingData,
    copywritingContentBuilder: StringBuilder,
    copywritingStartTime: Long,
    totalStartTime: Long,
    isCancelled: Boolean,
    tokenCount: Int
  ) {
    val copywritingDuration = System.currentTimeMillis() - copywritingStartTime
    val content = copywritingContentBuilder.toString().cleanThinkingString()

    // 취소된 경우: 부분 결과 전송 후 종료
    if (isCancelled) {
      sseEmitter.handleCancelledResponse(
        emitter = emitter,
        inferenceId = inferenceId,
        content = content,
        copywritingData = copywritingData,
        duration = copywritingDuration,
        tokenCount = tokenCount
      )
      return
    }

    kLogger.info { "1차 LLM 완료 (카피라이팅) - ID: $inferenceId, 길이: ${content.length}, 소요시간: ${copywritingDuration}ms" }

    // 1차 카피라이팅 결과 파싱
    val parsedCopywriting = responseParser.parseCopywritingResponse(content)

    // 카피라이팅 단계 완료 이벤트 (중간 결과)
    sseEmitter.emitCopywritingDone(
      emitter = emitter,
      parsedCopywriting = parsedCopywriting,
      duration = copywritingDuration,
      tokenCount = tokenCount
    )

    if (MARKETING_INSIGHT_ENABLED) {
      // === 3단계: 마케팅 인사이트 생성 (2차 LLM 호출 - 스트리밍) ===
      startMarketingInsightsGeneration(
        emitter = emitter,
        inferenceId = inferenceId,
        copywriterAssistant = copywriterAssistant,
        copywritingData = copywritingData,
        parsedCopywriting = parsedCopywriting,
        copywritingDuration = copywritingDuration,
        copywritingTokenCount = tokenCount,
        totalStartTime = totalStartTime
      )
    }

    // copywritingData를 JSON 문자열로 변환하여 저장
    val outputDataJson = try {
      objectMapper.writeValueAsString(copywritingData)
    } catch (e: Exception) {
      kLogger.warn(e) { "copywritingData JSON 직렬화 실패 - ID: $inferenceId" }
      """{"error":"copywritingData JSON 직렬화 실패", "inferenceId":"$inferenceId"}"""
    }

    // 비동기 콜백 컨텍스트에서 트랜잭션 보장을 위해 TransactionTemplate 사용
    try {
      transactionTemplate.execute {
        agentOutputRepository.save(
          ProductAgentOutput(
            accountId = session?.accountId,
            type = ProductAgentOutputType.PRODUCT_COPY,
            title = parsedCopywriting.mainCopy,
            description = parsedCopywriting.subCopy,
            thumbnailUrl = null,
            outputDataJson = outputDataJson
          )
        )
      }
      kLogger.info { "AgentOutput 저장 완료 - ID: $inferenceId, 계정: ${session?.accountId}, 타입: PRODUCT_COPY" }
    } catch (e: Exception) {
      // DB 저장 실패 시 로깅 (이미 클라이언트에 결과가 전송된 상태이므로 별도 에러 이벤트 불필요)
      kLogger.error(e) { "AgentOutput 저장 실패 - ID: $inferenceId, 계정: ${session?.accountId}" }
    }

    cancellationManager.unregisterInference(inferenceId)
    emitter.complete()
  }

  /**
   * 취소 확인을 위한 래퍼 핸들러 생성
   */
  private fun createCancellationAwareHandler(
    inferenceId: String,
    internalHandler: StreamingChatResponseHandler,
    tokenCountIncrementer: () -> Unit,
    tokenCountGetter: () -> Int,
    isCancelledSetter: () -> Unit,
    isCancelledGetter: () -> Boolean
  ): StreamingChatResponseHandler {
    return object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        // 토큰 카운트 증가 및 주기적 취소 확인
        tokenCountIncrementer()
        if (tokenCountGetter() % CANCELLATION_CHECK_INTERVAL == 0) {
          if (cancellationManager.isCancelled(inferenceId)) {
            kLogger.warn { "카피라이팅 생성 중 취소 감지 - ID: $inferenceId, 토큰 수: ${tokenCountGetter()}" }
            isCancelledSetter()
            return
          }
        }

        // 취소되지 않은 경우에만 내부 핸들러에 위임
        if (!isCancelledGetter()) {
          internalHandler.onPartialResponse(partialResponse)
        }
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        internalHandler.onCompleteResponse(completeResponse)
      }

      override fun onError(error: Throwable) {
        internalHandler.onError(error)
      }
    }
  }

  /**
   * 2차 LLM 호출: 마케팅 인사이트 생성 (스트리밍)
   *
   * 1차 LLM에서 생성된 카피라이팅 결과를 기반으로 마케팅 인사이트를 생성합니다.
   */
  private fun startMarketingInsightsGeneration(
    emitter: FluxSink<String>,
    inferenceId: String,
    copywriterAssistant: RunnableAiAssistant,
    copywritingData: CopywritingData,
    parsedCopywriting: AiCopyOnlyResponse,
    copywritingDuration: Long,
    copywritingTokenCount: Int,
    totalStartTime: Long
  ) {
    // 마케팅 인사이트 생성 시작 이벤트
    sseEmitter.emitMarketingInsightsActive(emitter)

    // 2차 LLM 프롬프트 생성
    val marketingSystemMessage = promptBuilder.buildMarketingInsightsSystemMessage()
    val marketingUserMessage = promptBuilder.buildMarketingInsightsUserMessage(copywritingData, parsedCopywriting)

    // ChatRequest 생성
    val marketingChatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
      assistant = copywriterAssistant,
      messages = listOf(marketingSystemMessage, marketingUserMessage)
    )

    // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
    val acquireResult = rateLimiter.acquireByProviderName(copywriterAssistant.provider)
    kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

    // 2차 LLM 호출 변수
    val marketingStartTime = System.currentTimeMillis()
    val marketingContentBuilder = StringBuilder()
    var marketingTokenCount = 0
    var isCancelled = false

    // 리즈닝 인식 스트리밍 핸들러 생성
    val internalMarketingHandler = ReasoningProcessingUtil.createReasoningAwareStreamingHandler(
      assistant = copywriterAssistant,
      onContent = { content ->
        // 취소되지 않은 경우에만 content 토큰 처리
        if (!isCancelled) {
          marketingContentBuilder.append(content)
          sseEmitter.emitMarketingInsightsToken(
            emitter = emitter,
            token = content,
            currentLength = marketingContentBuilder.length
          )
        }
      },
      onReasoning = { reasoningText, index ->
        // 취소되지 않은 경우에만 reasoning 토큰 처리
        if (!isCancelled) {
          sseEmitter.emitMarketingInsightsReasoningToken(
            emitter = emitter,
            reasoningText = reasoningText,
            index = index
          )
        }
      },
      onComplete = { _ ->
        // Rate Limiter 슬롯 반환
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

        handleMarketingInsightsComplete(
          emitter = emitter,
          inferenceId = inferenceId,
          marketingContentBuilder = marketingContentBuilder,
          marketingStartTime = marketingStartTime,
          totalStartTime = totalStartTime,
          copywritingData = copywritingData,
          parsedCopywriting = parsedCopywriting,
          copywritingDuration = copywritingDuration,
          copywritingTokenCount = copywritingTokenCount,
          marketingTokenCount = marketingTokenCount
        )
      },
      onError = { error ->
        // Rate Limiter 슬롯 반환 (에러 시)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (에러) - provider: ${acquireResult.providerName}" }

        handleMarketingInsightsError(
          error = error,
          emitter = emitter,
          inferenceId = inferenceId,
          totalStartTime = totalStartTime,
          copywritingData = copywritingData,
          parsedCopywriting = parsedCopywriting,
          copywritingDuration = copywritingDuration,
          copywritingTokenCount = copywritingTokenCount
        )
      }
    )

    // 취소 확인을 위한 래퍼 핸들러
    val marketingStreamingHandler = object : StreamingChatResponseHandler {
      override fun onPartialResponse(partialResponse: String) {
        // 토큰 카운트 증가 및 주기적 취소 확인
        marketingTokenCount++
        if (marketingTokenCount % CANCELLATION_CHECK_INTERVAL == 0) {
          if (cancellationManager.isCancelled(inferenceId)) {
            kLogger.warn { "마케팅 인사이트 생성 중 취소 감지 - ID: $inferenceId, 토큰 수: $marketingTokenCount" }
            isCancelled = true
            return
          }
        }

        // 취소되지 않은 경우에만 내부 핸들러에 위임
        if (!isCancelled) {
          internalMarketingHandler.onPartialResponse(partialResponse)
        }
      }

      override fun onCompleteResponse(completeResponse: ChatResponse) {
        internalMarketingHandler.onCompleteResponse(completeResponse)
      }

      override fun onError(error: Throwable) {
        internalMarketingHandler.onError(error)
      }
    }

    // 2차 LLM 스트리밍 호출 시작
    copywriterAssistant.chatWithStreaming(marketingChatRequest, marketingStreamingHandler)
  }

  /**
   * 마케팅 인사이트 완료 처리
   */
  private fun handleMarketingInsightsComplete(
    emitter: FluxSink<String>,
    inferenceId: String,
    marketingContentBuilder: StringBuilder,
    marketingStartTime: Long,
    totalStartTime: Long,
    copywritingData: CopywritingData,
    parsedCopywriting: AiCopyOnlyResponse,
    copywritingDuration: Long,
    copywritingTokenCount: Int,
    marketingTokenCount: Int
  ) {
    val marketingContent = marketingContentBuilder.toString()
    val marketingDuration = System.currentTimeMillis() - marketingStartTime
    val totalDuration = System.currentTimeMillis() - totalStartTime

    kLogger.info { "2차 LLM 완료 (마케팅 인사이트) - ID: $inferenceId, 길이: ${marketingContent.length}, 소요시간: ${marketingDuration}ms" }

    // 마케팅 인사이트 파싱
    val parsedMarketingInsights = responseParser.parseMarketingInsightsResponse(marketingContent)

    // 마케팅 인사이트 단계 완료 이벤트
    sseEmitter.emitMarketingInsightsDone(
      emitter = emitter,
      parsedMarketingInsights = parsedMarketingInsights,
      duration = marketingDuration,
      tokenCount = marketingTokenCount
    )

    // === 최종 응답 생성 및 전송 ===
    val dataQuality = resultBuilder.evaluateDataQuality(copywritingData)
    val finalResponse = resultBuilder.buildFinalResponse(
      copywriting = parsedCopywriting,
      marketingInsights = parsedMarketingInsights,
      copywritingData = copywritingData,
      dataQuality = dataQuality,
      copywritingTokenCount = copywritingTokenCount,
      marketingTokenCount = marketingTokenCount
    )

    // 전체 프로세스 완료 이벤트
    sseEmitter.emitCompleted(
      emitter = emitter,
      result = finalResponse,
      totalDuration = totalDuration,
      copywritingDuration = copywritingDuration,
      marketingInsightsDuration = marketingDuration,
      totalTokenCount = copywritingTokenCount + marketingTokenCount
    )

    kLogger.info { "전체 카피라이팅 프로세스 완료 - ID: $inferenceId, 총 소요시간: ${totalDuration}ms, 총 토큰: ${copywritingTokenCount + marketingTokenCount}" }
  }

  /**
   * 마케팅 인사이트 오류 처리
   */
  private fun handleMarketingInsightsError(
    error: Throwable,
    emitter: FluxSink<String>,
    inferenceId: String,
    totalStartTime: Long,
    copywritingData: CopywritingData,
    parsedCopywriting: AiCopyOnlyResponse,
    copywritingDuration: Long,
    copywritingTokenCount: Int
  ) {
    kLogger.error(error) { "2차 LLM (마케팅 인사이트) 생성 중 오류 발생 - ID: $inferenceId" }

    // 2차 LLM 실패 시에도 1차 카피라이팅 결과는 전달
    val totalDuration = System.currentTimeMillis() - totalStartTime
    val dataQuality = resultBuilder.evaluateDataQuality(copywritingData)

    // 마케팅 인사이트 없이 최종 응답 생성
    val fallbackResponse = resultBuilder.buildFinalResponseWithoutMarketingInsights(
      copywriting = parsedCopywriting,
      copywritingData = copywritingData,
      dataQuality = dataQuality,
      copywritingTokenCount = copywritingTokenCount
    )

    // 마케팅 인사이트 실패 이벤트
    sseEmitter.emitMarketingInsightsFailed(emitter)

    // 폴백 완료 이벤트
    sseEmitter.emitCompletedWithMarketingInsightsFailed(
      emitter = emitter,
      fallbackResponse = fallbackResponse,
      totalDuration = totalDuration,
      copywritingDuration = copywritingDuration
    )

    // 추론 등록 해제 및 스트림 종료
    cancellationManager.unregisterInference(inferenceId)
    emitter.complete()
  }
}
