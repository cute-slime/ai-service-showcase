package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.StreamingSingleInputSingleOutputGenerator
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.insight.platform.dto.request.LargeInferenceRequestV2
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper
import java.util.*


@Validated
@RequestMapping("/v1.0")
@RestController
@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["read"]), condition = MatchingCondition.AllMatches)
class AiAssistantController(
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val cancellationManager: AIInferenceCancellationManager,
  private val eventSender: EventSender,
  private val aiAssistantService: AiAssistantService,
  private val llmGateway: LlmGateway,
) : JController() {
  val kLogger = KotlinLogging.logger {}

  @PermissionCheck(RequiredPermission(businessSource = "ai", required = ["write"]), condition = MatchingCondition.AnyMatches)
  @PostMapping(
    path = ["/ai-assistants/{id}"],
    produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
  )
  fun stream(@PathVariable id: Long, @Validated @RequestBody dto: LargeInferenceRequestV2): Flux<String> {
    kLogger.info { "AiAssistant 추론 실행됨 - id: $id" }
    val session = session!!.deepCopy()
    return Flux.create { emitter ->
      Thread.startVirtualThread {
        try {
          val aiAssistant = aiAssistantService.findById(id)
          // 향후 재해 감지 모델 스위칭이 개발돼도 여기서의 모델에는 적용하지 말자. 이 API는 특수한 목적으로 사용된다.
          // LlmGateway 경유 - AI 어시스턴트 스트리밍 추론 (Rate Limiter 및 추적 자동 처리)
          StreamingSingleInputSingleOutputGenerator(
            objectMapper,
            cancellationManager,
            llmGateway,
            aiAssistant,
            emitter, eventSender, chatTopic, session.accountId, UUID.randomUUID().toString(),
          ).apply(aiAssistant.instructions!!, dto.question!!)
        } catch (e: Exception) {
          kLogger.error { "AiAssistant 추론 중 오류 발생: ${e.message}\n${e.stackTraceToString()}" }
          emitter.error(e)
        } finally {
          kLogger.info { "AiAssistant 추론 완료됨" }
        }
      }
    }
  }
}
