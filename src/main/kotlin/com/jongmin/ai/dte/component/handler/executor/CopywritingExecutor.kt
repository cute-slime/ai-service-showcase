package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.jspring.core.util.convert
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingRequest
import com.jongmin.ai.product_agent.platform.service.ProductAgentService
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 카피라이팅 작업 실행기
 *
 * 상품 카피라이팅 생성 작업을 담당합니다.
 * ProductAgentService에 위임하여 LLM 기반 카피라이팅을 생성합니다.
 *
 * ### 스트리밍 흐름:
 * 1. EventBridgeFluxSink 생성 (jobId, jobType 연결)
 * 2. ProductAgentService.generateCopywriting 호출 (비동기 스트리밍 시작)
 * 3. 서비스에서 emitter.next(data) 호출 시 EventBridge를 통해 Redis로 발행
 * 4. Redis Pub/Sub → DistributedJobEventBridge → SSE 클라이언트
 * 5. awaitCompletion()으로 스트리밍 완료 대기 후 Job 상태 업데이트
 *
 * @property objectMapper JSON 변환
 * @property productAgentService 카피라이팅 생성 서비스
 * @property sseHelper SSE 이벤트 헬퍼
 */
@Component
class CopywritingExecutor(
  private val objectMapper: ObjectMapper,
  private val productAgentService: ProductAgentService,
  private val sseHelper: ProductAgentSseHelper
) : ProductAgentExecutor {

  private val kLogger = KotlinLogging.logger {}

  /**
   * 기본 실행기로 동작 (subType이 null이거나 매칭되는 실행기가 없을 때)
   */
  override fun getSubType(): String? = null

  override fun execute(job: DistributedJob, payload: Map<*, *>) {
    kLogger.info { "카피라이팅 작업 시작 - jobId: ${job.id}" }

    val dto = objectMapper.convert(payload, CopywritingRequest::class.java)
    val emitter = sseHelper.createEmitter(job)

    try {
      // ProductAgentService에 위임하여 카피라이팅 생성
      productAgentService.generateCopywriting(null, emitter, dto)
      sseHelper.awaitCompletionOrThrow(emitter, job.id)

      kLogger.info { "카피라이팅 작업 완료 - jobId: ${job.id}" }
    } catch (e: Exception) {
      kLogger.error(e) { "카피라이팅 작업 실패 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    }
  }
}
