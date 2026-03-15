package com.jongmin.ai.core.system.controller

import com.jongmin.ai.core.system.dto.SystemChatRequest
import com.jongmin.ai.core.system.dto.SystemChatResponse
import com.jongmin.ai.core.system.dto.SystemChatStreamChunk
import com.jongmin.ai.core.system.service.SystemAiChatService
import com.jongmin.jspring.web.aspect.SystemCall
import com.jongmin.jspring.web.controller.JController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import tools.jackson.databind.json.JsonMapper

/**
 * 시스템 AI 채팅 컨트롤러
 *
 * 다른 마이크로서비스(game-service 등)에서 LLM 채팅을 요청할 때 사용하는 내부 API.
 * @SystemCall 어노테이션으로 시스템 토큰 인증 필수.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Validated
@RestController
@RequestMapping("/v1.0")
@Tag(name = "System - AI Chat", description = "AI 채팅 시스템 API (내부용)")
class SystemAiChatController(
  private val systemAiChatService: SystemAiChatService,
  private val jsonMapper: JsonMapper,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 동기 채팅
   *
   * LLM 호출을 수행하고 전체 응답을 반환한다.
   * Rate Limiter가 적용되어 Provider별 동시 요청 수가 제한된다.
   *
   * @param request 채팅 요청
   * @return 채팅 응답
   */
  @Operation(
    summary = "동기 채팅",
    description = """
      LLM 호출을 수행하고 전체 응답을 반환합니다.

      ## 어시스턴트 지정 방법
      1. assistantId로 직접 지정
      2. assistantType (+ assistantCategory)로 조회

      ## 요청 예시
      ```json
      {
        "assistantType": "GAME_CHARACTER",
        "messages": [
          {"role": "system", "content": "당신은 탐정입니다."},
          {"role": "user", "content": "안녕하세요"}
        ],
        "temperature": 0.7
      }
      ```

      ## 응답 예시
      ```json
      {
        "content": "안녕하세요, 무엇을 도와드릴까요?",
        "assistantId": 1,
        "assistantName": "Game Character",
        "model": "gpt-4o-mini",
        "usage": {
          "inputTokens": 50,
          "outputTokens": 20,
          "totalTokens": 70
        }
      }
      ```
    """
  )
  @SystemCall
  @PostMapping("/system/ai/chat")
  fun chat(
    @RequestBody request: SystemChatRequest
  ): SystemChatResponse {
    kLogger.debug { "System AI Chat 요청 - type: ${request.assistantType}, id: ${request.assistantId}" }
    return systemAiChatService.chat(request)
  }

  /**
   * 스트리밍 채팅 (SSE)
   *
   * LLM 호출을 수행하고 토큰을 SSE로 스트리밍한다.
   * Rate Limiter가 적용되어 Provider별 동시 요청 수가 제한된다.
   *
   * @param request 채팅 요청
   * @return SSE 스트림
   */
  @Operation(
    summary = "스트리밍 채팅 (SSE)",
    description = """
      LLM 호출을 수행하고 토큰을 SSE로 스트리밍합니다.

      ## SSE 이벤트 형식
      ```
      data: {"content": "안녕", "done": false}
      data: {"content": "하세요", "done": false}
      data: {"content": "", "done": true, "usage": {"inputTokens": 50, "outputTokens": 20, "totalTokens": 70}}
      ```

      ## 에러 발생 시
      ```
      data: {"content": "", "done": true, "error": "Timeout"}
      ```
    """
  )
  @SystemCall
  @PostMapping("/system/ai/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
  fun chatStream(
    @RequestBody request: SystemChatRequest
  ): Flux<String> {
    kLogger.debug { "System AI Chat Stream 요청 - type: ${request.assistantType}, id: ${request.assistantId}" }
    return systemAiChatService.chatStream(request)
      .map { chunk ->
        // SSE 형식으로 변환 (data: {...}\n\n)
        "data: ${jsonMapper.writeValueAsString(chunk)}\n\n"
      }
  }
}
