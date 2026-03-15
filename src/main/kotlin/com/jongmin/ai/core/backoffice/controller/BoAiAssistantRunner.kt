package com.jongmin.ai.core.backoffice.controller

import com.jongmin.jspring.web.aspect.MatchingCondition
import com.jongmin.jspring.web.aspect.PermissionCheck
import com.jongmin.jspring.web.aspect.RequiredPermission
import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.controller.JController
import com.jongmin.ai.core.backoffice.dto.request.TranslateRequest
import com.jongmin.ai.core.backoffice.service.BoAiAssistantService
import com.jongmin.ai.core.platform.component.adaptive.SimpleAgent
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import tools.jackson.databind.ObjectMapper

@Validated
@PermissionCheck(
  RequiredPermission(businessSource = "ai", required = ["admin"]),
  condition = MatchingCondition.AllMatches
)
@Tag(name = "900-2. BackOffice - AI")
@RestController
@RequestMapping("/v1.0")
class BoAiAssistantRunner(
  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
  private val eventSender: EventSender,
  private val objectMapper: ObjectMapper,
  @param:Value($$"${ai.assistant.translator-name}") private val translatorName: String,
  private val simpleAgent: SimpleAgent,
  private val boAiAssistantService: BoAiAssistantService,
) : JController() {

  @PostMapping(
    "/bo/ai-assistants/translator",
    produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
  )
  fun execute(@RequestBody dto: TranslateRequest): Flux<String> {
    val session = session!!.deepCopy()
    val accountId = session.accountId

    return Flux.create { emitter ->
      Thread.startVirtualThread {
        try {
          simpleAgent.executeTranslator(emitter, accountId, translatorName, dto)
        } catch (e: Exception) {
          emitter.error(e)
        }
      }
    }
  }
}
