package com.jongmin.ai.insight.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.dto.CommonDto
import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.StreamingSingleInputSingleOutputGenerator
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.insight.platform.dto.request.LargeInferenceRequestV2
import com.jongmin.ai.insight.platform.dto.request.ProductAnalyze
import com.jongmin.ai.insight.platform.service.ProductAnalysisService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.*

@Validated
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
@RestController
@RequestMapping("/v1.0")
class ProductAnalysisController(
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val cancellationManager: AIInferenceCancellationManager,
  private val eventSender: EventSender,
  private val aiAssistantService: AiAssistantService,
  private val productAnalysisService: ProductAnalysisService,
  private val llmGateway: LlmGateway,
) : JController() {
  val kLogger = KotlinLogging.logger {}

  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping("/product-analyses/pdp-parses")
  fun parseMusinsaPdpHtml(@Validated @RequestBody dto: LargeInferenceRequestV2): Any {
    kLogger.info { "AiAssistant(parsePdpHtml) 추론 실행됨" }
    val aiAssistant = aiAssistantService.findFirst(AiAssistantType.PDP_DOCUMENT_PARSER)
    // LlmGateway 경유 - PDP 문서 파싱 (Rate Limiter 및 추적 자동 처리)
    val output = StreamingSingleInputSingleOutputGenerator(
      objectMapper,
      cancellationManager,
      llmGateway,
      aiAssistant,
      null, eventSender, chatTopic, session!!.accountId, UUID.randomUUID().toString(),
    ).apply(aiAssistant.instructions!!, dto.question!!)
    return try {
      objectMapper.readValue(output, object : TypeReference<Map<String, Any>>() {})
    } catch (_: Exception) {
      return CommonDto.JApiResponse.FALSE
    }
  }

  /**
   * PDP 카피라이팅 생성 엔드포인트
   *
   * SSE(Server-Sent Events) 방식으로 실시간 진행 상황을 클라이언트에 스트리밍합니다.
   *
   * ### 프로세스:
   * 1. INPUT_ANALYSIS: 입력 데이터 분석
   * 2. IMAGE_PROCESSING: 이미지 처리
   * 3. COPYWRITING: 카피라이팅 생성
   * 4. COMPLETED: 전체 완료
   *
   * ### 응답:
   * - Content-Type: text/event-stream
   * - 각 단계별 JSON 이벤트 스트리밍
   *
   * @param dto PDP 카피라이팅 요청 DTO
   * @return SSE 스트림
   */
  @PostMapping(
    path = ["/product-analyses"],
    produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
  )
  fun generatePdpCopyWrite(@Valid dto: ProductAnalyze): Flux<String> {
    val session = session!!.deepCopy()

    return Flux.create { emitter ->
      Thread.startVirtualThread { productAnalysisService.executeProductAnalysis(session, emitter, dto) }
    }
  }
}
