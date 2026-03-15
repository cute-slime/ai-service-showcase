package com.jongmin.ai.product_agent.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.ratelimit.annotation.RateLimit
import com.jongmin.jspring.core.util.generateUuid
import com.jongmin.ai.product_agent.platform.dto.request.CopywritingRequest
import com.jongmin.jspring.dte.component.DistributedTaskExecutorProperties
import com.jongmin.jspring.dte.dto.response.DistributedJobItem
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient

/**
 * 상품 카피라이팅 AI 에이전트 컨트롤러
 *
 * 상품 정보를 기반으로 AI가 PDP(Product Detail Page) 카피라이팅을 생성합니다.
 * 분산 태스크 실행기(DTE)를 통해 비동기로 작업을 처리합니다.
 */
@Validated
@RestController
@RequestMapping("/v1.0")
@EnableConfigurationProperties(DistributedTaskExecutorProperties::class)
class ProductAgentController(
  private val properties: DistributedTaskExecutorProperties,
  private val webClientBuilder: WebClient.Builder,
  @param:Value($$"${app.system-token}") private val systemToken: String,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val TASK_TYPE = "PRODUCT_AGENT"
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

    kLogger.info { "ProductAgentController 초기화 완료 - baseUrl: ${properties.executor.baseUrl}" }
  }

  /**
   * 카피라이팅 생성 요청
   *
   * 상품 정보를 기반으로 AI 카피라이팅 생성 작업을 분산 태스크 큐에 등록합니다.
   * 작업은 비동기로 처리되며, 등록된 Job 정보가 즉시 반환됩니다.
   *
   * @param dto 카피라이팅 요청 데이터 (상품 정보, 스타일, 이미지 등)
   * @return 등록된 분산 작업(Job) 정보
   */
  @RateLimit(key = "#{#ip}", limit = 20, window = "1h")
  @RateLimit(key = "#{#ip}", limit = 50, window = "1d")
  @PostMapping("/product-agents/copies")
  fun generateCopywriting(@Valid dto: CopywritingRequest): DistributedJobItem {
    dto.key = generateUuid()
    kLogger.info { "카피라이팅 생성 요청 - key: ${dto.key}, canvasId: ${dto.canvasId}" }
    val accessToken = session?.accessToken

    val jobItem = enqueueTask(
      payload = mapOf("key" to dto.key!!, "canvasId" to (dto.canvasId ?: ""), "data" to (dto.data ?: "")),
      accessToken = accessToken,
      correlationId = dto.key,
    )

    kLogger.info { "카피라이팅 작업 등록 완료 - jobId: ${jobItem.id}, key: ${dto.key}" }

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
   * @param accessToken 작업 실행 시 사용할 액세스 토큰
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
// class ProductAgentController(
//   private val productAgentService: ProductAgentService
// ) : JController() {
//   val kLogger = KotlinLogging.logger {}
//
//   @PostMapping(
//     path = ["/product-agents/copies"],
//     produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
//   )
//   fun generateCopywriting(@Valid dto: CopywritingRequest): Flux<String> {
//     val session = session!!.deepCopy()
//
//     return Flux.create { emitter ->
//       // Now, with Java 21+ and the new reactor-core 3.6.x, a new BoundedElasticThreadPerTaskScheduler implementation can replace the default one to use virtual threads instead of platform threads with Schedulers.boundedElastic().
//       // All you need is to run your app on Java 21+ and set the -Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true system property:
//       Thread.startVirtualThread { productAgentService.generateCopywriting(session, emitter, dto) }
//     }
//   }
// }
