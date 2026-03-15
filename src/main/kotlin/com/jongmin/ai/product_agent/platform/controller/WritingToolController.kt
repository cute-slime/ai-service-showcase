package com.jongmin.ai.product_agent.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.ratelimit.annotation.RateLimit
import com.jongmin.jspring.core.util.generateUuid
import com.jongmin.ai.product_agent.platform.dto.request.WriteRequest
import com.jongmin.jspring.dte.component.DistributedTaskExecutorProperties
import com.jongmin.jspring.dte.dto.response.DistributedJobItem
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper

/**
 * 글쓰기 도구 컨트롤러
 *
 * AI를 활용하여 사용자의 텍스트를 다양한 스타일로 재작성하거나,
 * 요약/확장/분석 등의 추가 작업을 수행합니다.
 * 분산 태스크 실행기(DTE)를 통해 비동기로 작업을 처리하며,
 * SSE를 통해 토큰 단위로 실시간 스트리밍합니다.
 *
 * ### 지원 작업 유형 (22개):
 *
 * **REWRITE 계열 (13개)** - 텍스트 스타일/문체 변환:
 * - grammar: 문법 교정
 * - clarity: 명확하게
 * - formal: 격식체
 * - friendly: 친근체
 * - persuasive: 설득력
 * - academic: 학술
 * - blog: 블로그
 * - email: 이메일
 * - sns: SNS
 * - article: 기사
 * - poetic: 시적 표현
 * - storytelling: 스토리텔링
 * - ad-copy: 광고 카피
 *
 * **ACTION 계열 (9개)** - 분석, 추출, 생성:
 * - summarize: 요약하기
 * - shorten: 글 줄이기
 * - expand: 글 늘리기
 * - fact-check: 팩트 체크
 * - extract-keywords: 키워드 추출
 * - generate-title: 제목 생성
 * - tone-analysis: 톤 분석
 * - generate-outline: 아웃라인 생성
 * - generate-hashtags: 해시태그 생성
 *
 * ### SSE 이벤트 타입:
 * - STATUS: 상태 변경 (PROCESSING → EVALUATING → GENERATING_PROMPT → GENERATING)
 * - TOKEN: 토큰 스트리밍 (실시간 텍스트 생성)
 * - PROMPT_GENERATED: 프롬프트 최적화 완료
 * - EVALUATION_REJECTED: 입력 평가 거부 (부적절한 컨텐츠)
 * - COMPLETED: 작업 완료
 * - ERROR: 에러 발생
 */
@Tag(name = "AI - Writing Tool", description = "AI 글쓰기 도구 API")
@Validated
@RestController
@RequestMapping("/v1.0")
@EnableConfigurationProperties(DistributedTaskExecutorProperties::class)
class WritingToolController(
  private val properties: DistributedTaskExecutorProperties,
  private val webClientBuilder: WebClient.Builder,
  private val objectMapper: ObjectMapper,
  @param:Value("\${app.system-token}") private val systemToken: String,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** PRODUCT_AGENT 타입으로 통합 - 리소스 효율화 */
    private const val TASK_TYPE = "PRODUCT_AGENT"

    /** 글쓰기 도구 서브타입 (핸들러에서 분기 처리용) */
    private const val SUB_TYPE_WRITING = "WRITING"

    private const val ENQUEUE_TASK_PATH = "/v1.0/system/distributed-tasks"
  }

  private lateinit var webClient: WebClient

  @PostConstruct
  fun init() {
    webClient = webClientBuilder
      .baseUrl(properties.executor.baseUrl)
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .defaultHeader(HttpHeaders.AUTHORIZATION, systemToken)
      .build()

    kLogger.info { "WritingToolController 초기화 완료 - baseUrl: ${properties.executor.baseUrl}" }
  }

  /**
   * 글쓰기 도구 요청
   *
   * 사용자의 텍스트를 분석하고 요청된 작업 유형에 맞게 처리합니다.
   * 작업은 비동기로 처리되며, 등록된 Job 정보가 즉시 반환됩니다.
   * 처리 진행 상태와 생성된 텍스트는 SSE를 통해 토큰 단위로 실시간 스트리밍됩니다.
   *
   * @param request 글쓰기 요청 데이터 (텍스트, 작업 유형, 출력 언어)
   * @return 등록된 분산 작업(Job) 정보
   */
  @Operation(
    summary = "글쓰기 도구 요청",
    description = """
AI를 활용하여 텍스트를 재작성하거나 분석/추출/생성 작업을 수행합니다.

### 지원 작업 유형 (22개)
**REWRITE 계열**: grammar, clarity, formal, friendly, persuasive, academic, blog, email, sns, article, poetic, storytelling, ad-copy

**ACTION 계열**: summarize, shorten, expand, fact-check, extract-keywords, generate-title, tone-analysis, generate-outline, generate-hashtags

### SSE 스트리밍 응답
Job ID로 SSE 스트림을 구독하면 토큰 단위 실시간 스트리밍을 받을 수 있습니다:
- GET /v1.0/distributed-tasks/{jobId}/stream

### 예시 요청
```json
{
  "text": "오늘 날씨가 좋아서 기분이 좋아요.",
  "type": "formal",
  "outputLanguage": "auto"
}
```
        """,
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "작업 등록 성공",
        content = [Content(
          mediaType = MediaType.APPLICATION_JSON_VALUE,
          schema = Schema(implementation = DistributedJobItem::class)
        )]
      ),
      ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검증 실패)"),
      ApiResponse(responseCode = "429", description = "요청 제한 초과")
    ]
  )
  @RateLimit(key = "#{#ip}", limit = 60, window = "1h")
  @RateLimit(key = "#{#ip}", limit = 200, window = "1d")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/product-agents/writes")
  fun write(
    @Parameter(
      description = "글쓰기 요청 데이터",
      required = true,
      content = [Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE,
        examples = [
          ExampleObject(
            name = "문법 교정",
            value = """{"text": "오늘 날씨가 좋아서 기분 이 좋아요.", "type": "grammar", "outputLanguage": "auto"}"""
          ),
          ExampleObject(
            name = "격식체 변환",
            value = """{"text": "이거 완전 대박인데? 진짜 꼭 써봐!", "type": "formal", "outputLanguage": "ko"}"""
          ),
          ExampleObject(
            name = "요약하기",
            value = """{"text": "인공지능(AI)은 컴퓨터 과학의 한 분야로...", "type": "summarize", "outputLanguage": "auto"}"""
          )
        ]
      )]
    )
    @Valid @RequestBody request: WriteRequest
  ): DistributedJobItem {
    val requestKey = generateUuid()

    kLogger.info {
      "글쓰기 도구 요청 - key: $requestKey, type: ${request.type.code()}, " +
          "textLength: ${request.text.length}, outputLanguage: ${request.outputLanguage ?: "auto"}"
    }

    // 비로그인 허용 - session이 없어도 accessToken은 null로 처리됨
    val accessToken = session?.accessToken

    // WriteRequest를 JSON 문자열로 직렬화하여 payload에 포함
    val dataJson = objectMapper.writeValueAsString(request)

    val jobItem = enqueueTask(
      payload = mapOf(
        "subType" to SUB_TYPE_WRITING,
        "key" to requestKey,
        "data" to dataJson
      ),
      accessToken = accessToken,
      correlationId = requestKey,
    )

    kLogger.info { "글쓰기 도구 작업 등록 완료 - jobId: ${jobItem.id}, key: $requestKey" }

    return jobItem
  }

  /**
   * 분산 태스크 큐에 작업을 등록합니다.
   *
   * 내부적으로 enqueueTask API를 호출하여 작업을 등록합니다.
   * accessToken은 요청 바디에 포함되어 작업 실행 시 인증에 사용됩니다.
   *
   * @param payload 작업 데이터
   * @param priority 우선순위 (기본값: null, 서버 기본값 사용)
   * @param accessToken 작업 실행 시 사용할 액세스 토큰 (비로그인 시 null)
   * @param correlationId 연관 요청 추적용 ID
   * @return 등록된 분산 작업(Job) 정보
   */
  private fun enqueueTask(
    payload: Map<String, Any>,
    priority: Int? = null,
    accessToken: String? = null,
    correlationId: String? = null,
  ): DistributedJobItem {
    val requestBody = mutableMapOf(
      "type" to TASK_TYPE,
      "payload" to payload
    ).apply {
      priority?.let { put("priority", it) }
      accessToken?.let { put("accessToken", it) }
      correlationId?.let { put("correlationId", it) }
    }

    return webClient.post()
      .uri(ENQUEUE_TASK_PATH)
      .bodyValue(requestBody)
      .retrieve()
      .bodyToMono(DistributedJobItem::class.java)
      .block()
      ?: throw IllegalStateException("분산 작업 등록에 실패했습니다.")
  }
}
