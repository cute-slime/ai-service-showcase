//package com.jongmin.ai.core.platform.controller
//
//import tools.jackson.databind.ObjectMapper
//import com.jongmin.jspring.web.aspect.MatchingCondition
//import com.jongmin.jspring.web.aspect.PermissionCheck
//import com.jongmin.jspring.web.aspect.RequiredPermission
//import com.jongmin.jspring.messaging.event.EventSender
//import com.jongmin.jspring.web.controller.JController
//import com.jongmin.jspring.core.dto.MessageType
//import com.jongmin.jspring.web.dto.CommonDto
//import com.jongmin.ai.core.platform.component.adaptive.ContentCreatorAgent
//import com.jongmin.ai.core.platform.dto.request.CreateAiCreativeContent
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.validation.annotation.Validated
//import org.springframework.web.bind.annotation.PostMapping
//import org.springframework.web.bind.annotation.RequestBody
//import org.springframework.web.bind.annotation.RequestMapping
//import org.springframework.web.bind.annotation.RestController
//
//@Validated
//@PermissionCheck(RequiredPermission(businessSource = "ai", required = ["admin"]), condition = MatchingCondition.AllMatches)
//@RestController
//@RequestMapping("/v1.0")
//class AiContentCreatorController(
//  private val objectMapper: ObjectMapper,
//  @param:Value($$"${app.stream.event.topic.event-app}") private val chatTopic: String,
//  private val eventSender: EventSender,
//  private val contentCreatorAgent: ContentCreatorAgent,
//) : JController() {
//  @PostMapping("/ai-content-creator")
//  fun create(@RequestBody dto: CreateAiCreativeContent): CommonDto.JApiResponse<Boolean> {
//    val accountId = session!!.accountId
//    contentCreatorAgent.execute(accountId, 1L, dto) {
//      eventSender.sendEventToAccount(
//        chatTopic,
//        MessageType.AI_INFERENCE_COMPLETED,
//        mapOf("canvasId" to dto.canvasId, "contents" to it.state().generation()),
//        accountId
//      )
//    }
//    return CommonDto.JApiResponse.TRUE
//  }
//}
