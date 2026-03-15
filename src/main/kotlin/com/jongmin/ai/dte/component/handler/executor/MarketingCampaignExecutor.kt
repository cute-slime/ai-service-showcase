package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.ai.core.AgentOutputRepository
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.product_agent.MarketingCampaignToolId
import com.jongmin.ai.product_agent.platform.component.marketing.MarketingCampaignToolExecutor
import com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignData
import com.jongmin.ai.product_agent.platform.dto.response.*
import com.jongmin.ai.product_agent.platform.entity.ProductAgentOutput
import com.jongmin.ai.storage.StorageServiceClient
import com.jongmin.jspring.dte.component.EventBridgeFluxSink
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/**
 * 마케팅 캠페인 작업 실행기
 *
 * 선택된 마케팅 도구들을 순차적으로 실행하고 결과를 SSE로 스트리밍합니다.
 * 모든 도구가 1개의 DTE Job(트랜잭션)으로 처리됩니다.
 *
 * ### 파이프라인 흐름:
 * 1. EventBridgeFluxSink 생성 (jobId, jobType 연결)
 * 2. 캠페인 데이터 파싱
 * 3. 선택된 도구별 순차 실행 (LLM 카피 생성 → 이미지 생성)
 * 4. 각 도구 완료 시 TOOL_RESULT SSE 이벤트 전송
 * 5. 전체 완료 시 ProductAgentOutput 저장
 * 6. COMPLETED SSE 이벤트 전송
 *
 * @property objectMapper JSON 변환
 * @property sseHelper SSE 이벤트 헬퍼
 * @property marketingCampaignToolExecutor 마케팅 도구 실행기
 * @property agentOutputRepository 출력 저장소
 * @property transactionTemplate 트랜잭션 템플릿
 */
@Component
class MarketingCampaignExecutor(
  private val objectMapper: ObjectMapper,
  private val sseHelper: ProductAgentSseHelper,
  private val marketingCampaignToolExecutor: MarketingCampaignToolExecutor,
  private val agentOutputRepository: AgentOutputRepository,
  private val transactionTemplate: TransactionTemplate,
  private val storageServiceClient: StorageServiceClient,
) : ProductAgentExecutor {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    const val SUB_TYPE = "MARKETING_CAMPAIGN"
  }

  override fun getSubType(): String = SUB_TYPE

  override fun execute(job: DistributedJob, payload: Map<*, *>) {
    val dataJson = payload["data"] as String
    val productImageKey = payload["productImageKey"] as? String

    val campaignData = objectMapper.readValue(dataJson, MarketingCampaignData::class.java)
    val selectedTools = campaignData.selectedTools
      ?: throw IllegalArgumentException("선택된 도구가 없습니다")
    val commonInput = campaignData.commonInput
      ?: throw IllegalArgumentException("공통 입력 정보가 없습니다")

    kLogger.info {
      """
            |========== 마케팅 캠페인 작업 시작 ==========
            |[작업 정보]
            |  - jobId: ${job.id}
            |  - productName: ${commonInput.productName}
            |  - category: ${commonInput.category}
            |  - selectedTools: ${selectedTools.map { it.code() }}
            |  - productImageKey: ${productImageKey ?: "없음"}
            |================================================
            """.trimMargin()
    }

    val emitter = sseHelper.createEmitter(job)
    val startTime = System.currentTimeMillis()
    val toolResults = mutableListOf<ToolResultData>()

    try {
      // 1. PROCESSING 상태 전송
      sseHelper.emitStatus(emitter, "PROCESSING", "마케팅 캠페인 생성을 시작합니다...")

      // 2. 각 도구 순차 실행
      executeTools(emitter, selectedTools, campaignData, productImageKey, toolResults)

      // 3. 결과 저장
      val savedOutput = saveMarketingCampaignOutput(job, campaignData, toolResults)

      // 4. 완료 이벤트 전송
      val successCount = toolResults.count { it.success }
      val failedCount = toolResults.count { !it.success }

      emitMarketingCampaignCompleted(
        emitter,
        MarketingCampaignCompletedEvent(
          outputId = savedOutput.id,
          summary = MarketingCampaignSummary(
            totalTools = selectedTools.size,
            successCount = successCount,
            failedCount = failedCount
          )
        )
      )

      emitter.complete()

      val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
      kLogger.info {
        "마케팅 캠페인 작업 완료 - jobId: ${job.id}, outputId: ${savedOutput.id}, " +
            "성공: $successCount, 실패: $failedCount, 소요시간: ${String.format("%.2f", elapsedSeconds)}초"
      }

    } catch (e: Exception) {
      kLogger.error(e) { "마케팅 캠페인 작업 실패 - jobId: ${job.id}" }
      sseHelper.handleError(emitter, e)
      emitter.complete()
      throw e
    } finally {
      cancelStagedProductImage(productImageKey)
    }
  }

  /**
   * 각 도구를 순차 실행합니다.
   */
  private fun executeTools(
    emitter: EventBridgeFluxSink,
    selectedTools: List<MarketingCampaignToolId>,
    campaignData: MarketingCampaignData,
    productImageKey: String?,
    toolResults: MutableList<ToolResultData>
  ) {
    selectedTools.forEachIndexed { index, toolId ->
      val toolIndex = index + 1
      val totalTools = selectedTools.size

      kLogger.info { "도구 구동 시작 - toolId: ${toolId.code()}, index: $toolIndex/$totalTools" }
      sseHelper.emitStatus(emitter, "TOOL_PROCESSING", "${toolId.description} 생성 중... ($toolIndex/$totalTools)")

      try {
        val result = executeTool(toolId, campaignData, productImageKey)
        toolResults.add(result)

        // TOOL_RESULT SSE 이벤트 전송
        emitToolResult(
          emitter,
          ToolResultEvent(
            toolId = toolId.code(),
            toolIndex = toolIndex,
            totalTools = totalTools,
            status = "COMPLETED",
            result = result.result,
            errorMessage = null
          )
        )

        kLogger.info { "도구 실행 완료 - toolId: ${toolId.code()}" }

      } catch (e: Exception) {
        kLogger.error(e) { "도구 실행 실패 - toolId: ${toolId.code()}" }

        val failedResult = ToolResultData(
          toolId = toolId.code(),
          success = false,
          errorMessage = e.message ?: "알 수 없는 오류"
        )
        toolResults.add(failedResult)

        // 실패한 도구도 TOOL_RESULT 이벤트로 전송
        emitToolResult(
          emitter,
          ToolResultEvent(
            toolId = toolId.code(),
            toolIndex = toolIndex,
            totalTools = totalTools,
            status = "FAILED",
            result = null,
            errorMessage = e.message
          )
        )
      }
    }
  }

  /**
   * 개별 도구를 실행합니다.
   */
  private fun executeTool(
    toolId: MarketingCampaignToolId,
    campaignData: MarketingCampaignData,
    productImageKey: String?
  ): ToolResultData {
    return when (toolId) {
      MarketingCampaignToolId.BANNER_AD -> {
        val bannerResult = marketingCampaignToolExecutor.executeBannerAd(campaignData, productImageKey)
        ToolResultData(toolId = toolId.code(), success = true, result = bannerResult)
      }

      MarketingCampaignToolId.INSTAGRAM_FEED -> {
        val feedResult = marketingCampaignToolExecutor.executeInstagramFeed(campaignData, productImageKey)
        ToolResultData(toolId = toolId.code(), success = true, result = feedResult)
      }

      MarketingCampaignToolId.INSTAGRAM_STORY -> {
        val storyResult = marketingCampaignToolExecutor.executeInstagramStory(campaignData, productImageKey)
        ToolResultData(toolId = toolId.code(), success = true, result = storyResult)
      }

      MarketingCampaignToolId.SEARCH_AD -> {
        val searchResult = marketingCampaignToolExecutor.executeSearchAd(campaignData)
        ToolResultData(toolId = toolId.code(), success = true, result = searchResult)
      }
    }
  }

  /**
   * 도구별 결과 SSE 이벤트를 전송합니다.
   */
  private fun emitToolResult(emitter: EventBridgeFluxSink, event: ToolResultEvent) {
    val eventMap = mapOf(
      "type" to event.type,
      "toolId" to event.toolId,
      "toolIndex" to event.toolIndex,
      "totalTools" to event.totalTools,
      "status" to event.status,
      "result" to event.result,
      "errorMessage" to event.errorMessage,
      "timestamp" to event.timestamp
    )
    emitter.next(objectMapper.writeValueAsString(eventMap))
  }

  /**
   * 마케팅 캠페인 완료 SSE 이벤트를 전송합니다.
   */
  private fun emitMarketingCampaignCompleted(emitter: EventBridgeFluxSink, event: MarketingCampaignCompletedEvent) {
    val eventMap = mapOf(
      "type" to event.type,
      "status" to event.status,
      "message" to event.message,
      "outputId" to event.outputId,
      "summary" to mapOf(
        "totalTools" to event.summary.totalTools,
        "successCount" to event.summary.successCount,
        "failedCount" to event.summary.failedCount
      ),
      "timestamp" to event.timestamp
    )
    emitter.next(objectMapper.writeValueAsString(eventMap))
  }

  /**
   * 마케팅 캠페인 결과를 ProductAgentOutput에 저장합니다.
   */
  private fun saveMarketingCampaignOutput(
    job: DistributedJob,
    campaignData: MarketingCampaignData,
    toolResults: List<ToolResultData>
  ): ProductAgentOutput {
    val commonInput = campaignData.commonInput!!

    val outputData = MarketingCampaignOutputData(
      productName = commonInput.productName!!,
      category = commonInput.category!!.code(),
      targetAudience = commonInput.targetAudience,
      selectedTools = campaignData.selectedTools!!.map { it.code() },
      toolResults = toolResults
    )
    val outputDataJson = objectMapper.writeValueAsString(outputData)
    val thumbnailKey = toolResults
      .asSequence()
      .mapNotNull { extractGeneratedImageKey(it.result) }
      .firstOrNull()

    return try {
      transactionTemplate.execute {
        agentOutputRepository.save(
          ProductAgentOutput(
            accountId = job.requesterId,
            type = ProductAgentOutputType.MARKETING_CAMPAIGN,
            title = "${commonInput.productName} 마케팅 캠페인",
            description = "선택된 도구: ${campaignData.selectedTools.joinToString(", ") { it.description }}",
            thumbnailUrl = thumbnailKey,
            outputDataJson = outputDataJson
          )
        )
      } ?: throw IllegalStateException("마케팅 캠페인 출력 저장에 실패했습니다.")
    } catch (e: Exception) {
      cleanupGeneratedKeys(toolResults)
      throw e
    }
  }

  private fun extractGeneratedImageKey(result: Any?): String? {
    return when (result) {
      is BannerAdResult -> result.generatedImageKey
      is InstagramFeedResult -> result.generatedImageKey
      is InstagramStoryResult -> result.generatedImageKey
      else -> null
    }?.takeIf { it.isNotBlank() }
  }

  private fun cancelStagedProductImage(productImageKey: String?) {
    val stagedKey = productImageKey?.takeIf { it.startsWith("_tmp/") } ?: return
    runCatching { storageServiceClient.cancel(listOf(stagedKey)) }
      .onFailure { kLogger.warn(it) { "마케팅 입력 이미지 temp 정리 실패 - key: $stagedKey" } }
  }

  private fun cleanupGeneratedKeys(toolResults: List<ToolResultData>) {
    toolResults
      .asSequence()
      .mapNotNull { extractGeneratedImageKey(it.result) }
      .filter { !it.startsWith("_tmp/") }
      .distinct()
      .forEach { key ->
        runCatching { storageServiceClient.deleteBySourceUrl(key) }
          .onFailure { kLogger.warn(it) { "마케팅 출력 이미지 보상 삭제 실패 - key: $key" } }
      }
  }
}
