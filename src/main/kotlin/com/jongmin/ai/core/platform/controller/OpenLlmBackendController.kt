package com.jongmin.ai.core.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.core.exception.BadRequestException
import com.jongmin.ai.core.platform.dto.request.ChatCompletionRequest
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux

@Validated
@RestController
@CrossOrigin(origins = ["*"])
@RequestMapping("/api/v1")
class OpenLlmBackendController(
  private val openLlmBackendService: com.jongmin.ai.core.platform.service.OpenLlmBackendService
) : JController() {

  /**
   * OpenAI 호환 채팅 완료 API
   *
   * 스트리밍 또는 일반 채팅 완료 요청을 처리합니다.
   * 응답에 포함된 id를 사용하여 추론을 취소할 수 있습니다.
   */
  @PostMapping("/chat/completions", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun chatCompletions(
    @RequestHeader(name = "Authorization") authorizationHeader: String?,
    @Valid @RequestBody dto: ChatCompletionRequest,
  ): Flux<String> {
    return openLlmBackendService.processChatCompletions(authorizationHeader, dto)
  }


  /**
   * 실행 중인 추론 요청을 취소합니다.
   *
   * @param id 취소할 추론 요청의 ID (chatCompletions 응답의 id 필드)
   * @return 취소 성공 여부를 포함한 응답
   */
  @DeleteMapping("/chat/completions/{id}")
  fun cancelChatCompletion(
    @RequestHeader(name = "Authorization") authorizationHeader: String?,
    @PathVariable id: String
  ): Map<String, Any> {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer sk-co-v1-"))
      throw BadRequestException("유효한 인증 토큰이 필요합니다.")

    return openLlmBackendService.cancelChatCompletion(id)
  }

}
