package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.util.ReasoningProcessingUtil
import com.jongmin.ai.product_agent.platform.component.writing.WritingPromptEvaluator
import com.jongmin.ai.product_agent.platform.component.writing.WritingPromptGenerator
import com.jongmin.ai.product_agent.platform.component.writing.WritingSseEmitter
import com.jongmin.ai.product_agent.platform.dto.request.WriteRequest
import com.jongmin.ai.product_agent.platform.dto.request.WritingRejectionReason
import com.jongmin.ai.product_agent.platform.dto.response.WritingCompletedData
import com.jongmin.jspring.dte.entity.DistributedJob
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 글쓰기 도구 작업 실행기
 *
 * 사용자의 텍스트를 다양한 스타일로 재작성하거나,
 * 요약/확장/분석 등의 추가 작업을 수행합니다.
 *
 * ### 파이프라인 흐름:
 * 1. EventBridgeFluxSink 생성 (jobId, jobType 연결)
 * 2. 입력 텍스트 평가 (LLM 기반 가드레일)
 * 3. 프롬프트 최적화 (LLM 기반 최적화)
 * 4. 최종 LLM 스트리밍 생성 (토큰 단위 SSE 전송)
 * 5. 완료 이벤트 발행
 *
 * @property objectMapper JSON 변환
 * @property sseHelper SSE 이벤트 헬퍼
 * @property writingPromptEvaluator 글쓰기 프롬프트 평가기
 * @property writingPromptGenerator 글쓰기 프롬프트 생성기
 * @property writingSseEmitter 글쓰기 SSE 이미터
 * @property aiAssistantService AI 어시스턴트 서비스
 */
@Component
class WritingToolExecutor(
  private val objectMapper: ObjectMapper,
  private val sseHelper: ProductAgentSseHelper,
  private val writingPromptEvaluator: WritingPromptEvaluator,
  private val writingPromptGenerator: WritingPromptGenerator,
  private val writingSseEmitter: WritingSseEmitter,
  private val aiAssistantService: AiAssistantService,
  private val rateLimiter: LlmRateLimiter
) : ProductAgentExecutor {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val SUB_TYPE = "WRITING"
  }

  override fun getSubType(): String = SUB_TYPE

  override fun execute(job: DistributedJob, payload: Map<*, *>) {
    // data JSON 문자열 추출 및 파싱
    val dataJson = payload["data"] as String
    val writeRequest = objectMapper.readValue(dataJson, WriteRequest::class.java)

    val text = writeRequest.text
    val type = writeRequest.type
    val outputLanguage = writeRequest.outputLanguage ?: "auto"

    kLogger.info {
      """
            |========== 글쓰기 도구 작업 시작 ==========
            |[작업 정보]
            |  - jobId: ${job.id}
            |  - type: ${type.code()} (${type.description})
            |  - textLength: ${text.length}
            |  - outputLanguage: $outputLanguage
            |================================================
            """.trimMargin()
    }

    val emitter = sseHelper.createEmitter(job)
    val startTime = System.currentTimeMillis()
    val outputBuilder = StringBuilder()
    var tokenCount = 0

    try {
      // 1. PROCESSING 상태 전송
      writingSseEmitter.emitProcessing(emitter)

      // ========== 2. 입력 텍스트 평가 (LLM 기반 가드레일) ==========
      writingSseEmitter.emitEvaluating(emitter)

      val evaluationResult = writingPromptEvaluator.evaluate(text, type)

      // 평가 거부 시 즉시 종료
      if (evaluationResult.isRejected) {
        // rejectionReason이 null인 경우 기본값(QUALITY_INSUFFICIENT) 사용
        val reason = evaluationResult.rejectionReason ?: WritingRejectionReason.QUALITY_INSUFFICIENT
        kLogger.warn { "글쓰기 입력 평가 거부 - jobId: ${job.id}, reason: ${reason.code()}" }
        writingSseEmitter.emitEvaluationRejected(emitter, reason)
        emitter.complete()
        return
      }

      writingSseEmitter.emitEvaluationPassed(emitter)
      kLogger.info { "글쓰기 입력 평가 통과 - jobId: ${job.id}" }

      // ========== 3. 프롬프트 최적화 (LLM 기반) ==========
      writingSseEmitter.emitGeneratingPrompt(emitter)

      val promptResult = writingPromptGenerator.generate(text, type, outputLanguage)

      writingSseEmitter.emitPromptGenerated(emitter)
      kLogger.info { "글쓰기 프롬프트 생성 완료 - jobId: ${job.id}" }

      // ========== 4. 최종 LLM 스트리밍 생성 ==========
      writingSseEmitter.emitGenerating(emitter)

      // WRITING_TOOL 어시스턴트 조회
      val writingAssistant = aiAssistantService.findFirst(AiAssistantType.WRITING_TOOL)

      // 메시지 생성
      val systemMessage = SystemMessage.from(promptResult.systemPrompt)
      val userMessage = UserMessage.from(promptResult.userMessage)

      // ChatRequest 생성
      val chatRequest = ReasoningProcessingUtil.createChatRequestFromMessages(
        assistant = writingAssistant,
        messages = listOf(systemMessage, userMessage)
      )

      // Rate Limiter 슬롯 확보 (스트리밍 시작 전에 확보, 완료/에러 시 반환)
      val acquireResult = rateLimiter.acquireByProviderName(writingAssistant.provider)
      kLogger.info { "🔒 [Rate Limit] 스트리밍 슬롯 확보 - provider: ${acquireResult.providerName}, requestId: ${acquireResult.requestId.take(8)}..." }

      // 스트리밍 호출
      val latch = CountDownLatch(1)
      var error: Throwable? = null

      writingAssistant.chatWithStreaming(
        chatRequest,
        object : StreamingChatResponseHandler {
          override fun onPartialResponse(partialResponse: String) {
            outputBuilder.append(partialResponse)
            tokenCount++
            // 토큰 단위로 SSE 전송
            writingSseEmitter.emitToken(emitter, partialResponse, outputBuilder.length)
          }

          override fun onCompleteResponse(completeResponse: ChatResponse) {
            // Rate Limiter 슬롯 반환
            rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
            kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 - provider: ${acquireResult.providerName}" }

            val content = completeResponse.aiMessage()?.text()
            if (content != null && outputBuilder.isEmpty()) {
              outputBuilder.append(content)
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
      val completed = latch.await(ProductAgentSseHelper.TIMEOUT_MINUTES, TimeUnit.MINUTES)
      if (!completed) {
        // Rate Limiter 슬롯 반환 (타임아웃 시)
        rateLimiter.release(acquireResult.providerId, acquireResult.requestId)
        kLogger.info { "🔓 [Rate Limit] 스트리밍 슬롯 반환 (타임아웃) - provider: ${acquireResult.providerName}" }

        kLogger.warn { "글쓰기 생성 타임아웃 - jobId: ${job.id}" }
        throw TimeoutException("글쓰기 생성 타임아웃: ${ProductAgentSseHelper.TIMEOUT_MINUTES}분 초과")
      }

      if (error != null) throw error

      // ========== 5. 완료 이벤트 발행 ==========
      val duration = System.currentTimeMillis() - startTime
      val completedData = WritingCompletedData(
        output = outputBuilder.toString(),
        type = type.code(),
        originalText = text,
        tokenCount = tokenCount,
        duration = duration
      )

      writingSseEmitter.emitCompleted(emitter, completedData)
      emitter.complete()

      kLogger.info {
        "글쓰기 도구 작업 완료 - jobId: ${job.id}, type: ${type.code()}, " +
            "outputLength: ${outputBuilder.length}, tokenCount: $tokenCount, 소요시간: ${duration}ms"
      }

    } catch (e: TimeoutException) {
      kLogger.error(e) { "글쓰기 타임아웃 - jobId: ${job.id}" }
      writingSseEmitter.handleError(emitter, e)
      emitter.complete()
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "글쓰기 작업 실패 - jobId: ${job.id}" }
      writingSseEmitter.handleError(emitter, e)
      emitter.complete()
      throw e
    }
  }
}
