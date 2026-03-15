package com.jongmin.ai.product_agent.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.ratelimit.annotation.RateLimit
import com.jongmin.jspring.core.util.generateUuid
import com.jongmin.ai.product_agent.platform.dto.request.ProductImageComposeData
import com.jongmin.ai.product_agent.platform.dto.request.ProductImageComposeRequest
import com.jongmin.ai.product_agent.platform.dto.request.ProductImageGenerateRequest
import com.jongmin.jspring.dte.component.DistributedTaskExecutorProperties
import com.jongmin.jspring.dte.dto.response.DistributedJobItem
import com.jongmin.ai.storage.S3Service
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.PostConstruct
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper

/**
 * 상품 이미지 생성/합성 컨트롤러
 *
 * AI를 활용하여 마케팅용 상품 이미지를 생성하거나 여러 이미지를 합성합니다.
 * 분산 태스크 실행기(DTE)를 통해 비동기로 작업을 처리하며,
 * SSE를 통해 생성 진행 상태를 실시간으로 스트리밍합니다.
 *
 * ### 엔드포인트:
 * - POST /v1.0/product-agents/images/generate : 텍스트 기반 이미지 생성 요청
 * - POST /v1.0/product-agents/images/compose : 참조 이미지 기반 합성 요청
 *
 * ### 이력 조회 안내:
 * 이미지 생성/합성 이력은 통합 API를 사용하세요:
 * - GET /v1.0/product-agent-outputs?type=PRODUCT_IMAGE
 */
@Tag(name = "AI - Product Image Generator", description = "상품 이미지 생성/합성 API")
@Validated
@RestController
@RequestMapping("/v1.0")
@EnableConfigurationProperties(DistributedTaskExecutorProperties::class)
class ProductImageController(
  private val properties: DistributedTaskExecutorProperties,
  private val webClientBuilder: WebClient.Builder,
  private val s3Service: S3Service,
  private val objectMapper: ObjectMapper,
  @param:Value($$"${app.system-token}") private val systemToken: String,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** PRODUCT_AGENT 타입으로 통합 (카피라이팅과 동일) - 리소스 효율화 */
    private const val TASK_TYPE = "PRODUCT_AGENT"

    /** 이미지 생성 서브타입 (핸들러에서 분기 처리용) */
    private const val SUB_TYPE_GENERATE = "IMAGE_GENERATE"

    /** 이미지 합성 서브타입 (핸들러에서 분기 처리용) */
    private const val SUB_TYPE_COMPOSE = "IMAGE_COMPOSE"

    private const val ENQUEUE_TASK_PATH = "/v1.0/system/distributed-tasks"

    /** 참조 이미지 임시 저장 경로 프리픽스 */
    private const val REFERENCE_IMAGE_PATH_PREFIX = "product-images/reference"

    /** 지원하는 이미지 Content-Type 목록 */
    private val SUPPORTED_IMAGE_TYPES = setOf(
      "image/png",
      "image/jpeg",
      "image/jpg",
      "image/webp"
    )

    /** 이미지 파일 최대 크기 (10MB) */
    private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024L
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

    kLogger.info { "ProductImageController 초기화 완료 - baseUrl: ${properties.executor.baseUrl}" }
  }

  /**
   * 상품 이미지 생성 요청 (Text-to-Image)
   *
   * 상품 정보를 기반으로 AI 이미지 생성 작업을 분산 태스크 큐에 등록합니다.
   * 작업은 비동기로 처리되며, 등록된 Job 정보가 즉시 반환됩니다.
   * 생성 진행 상태는 SSE를 통해 실시간으로 스트리밍됩니다.
   *
   * @param dto 이미지 생성 요청 데이터 (상품명, 프롬프트, 스타일 등)
   * @return 등록된 분산 작업(Job) 정보
   */
  @Operation(summary = "상품 이미지 생성", description = "AI를 활용하여 마케팅용 상품 이미지를 생성합니다.")
  @RateLimit(key = "#{#ip}", limit = 20, window = "1h")
  @RateLimit(key = "#{#ip}", limit = 50, window = "1d")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/product-agents/images/generate")
  fun generateImage(@Valid dto: ProductImageGenerateRequest): DistributedJobItem {
    dto.key = generateUuid()
    kLogger.info { "상품 이미지 생성 요청 - key: ${dto.key}" }
    val accessToken = session?.accessToken

    val jobItem = enqueueTask(
      payload = mapOf(
        "subType" to SUB_TYPE_GENERATE,
        "key" to dto.key!!,
        "data" to (dto.data ?: "")
      ),
      accessToken = accessToken,
      correlationId = dto.key,
    )

    kLogger.info { "상품 이미지 생성 작업 등록 완료 - jobId: ${jobItem.id}, key: ${dto.key}" }

    return jobItem
  }

  /**
   * 상품 이미지 합성 요청 (Image Mixing)
   *
   * 여러 참조 이미지(제품, 모델, 배경 등)를 합성하여 새로운 마케팅 이미지를 생성합니다.
   * 작업은 비동기로 처리되며, 등록된 Job 정보가 즉시 반환됩니다.
   * 생성 진행 상태는 SSE를 통해 실시간으로 스트리밍됩니다.
   *
   * @param dto 이미지 합성 요청 데이터 (상품명, 프롬프트, 참조 이미지 역할 정보 등)
   * @return 등록된 분산 작업(Job) 정보
   */
  @Operation(
    summary = "상품 이미지 합성",
    description = "여러 참조 이미지를 합성하여 새로운 마케팅 이미지를 생성합니다."
  )
  @RateLimit(key = "#{#ip}", limit = 20, window = "1h")
  @RateLimit(key = "#{#ip}", limit = 50, window = "1d")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/product-agents/images/compose", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun composeImage(@Valid dto: ProductImageComposeRequest): DistributedJobItem {
    dto.key = generateUuid()
    val referenceImages = dto.referenceImages!!

    kLogger.info { "상품 이미지 합성 요청 - key: ${dto.key}, 참조 이미지 수: ${referenceImages.size}" }

    // 1. JSON 데이터 파싱 및 검증
    val composeData = parseAndValidateComposeData(dto.data!!, referenceImages)

    // 2. 참조 이미지 검증 및 S3 업로드
    val referenceImageKeys = uploadReferenceImages(dto.key!!, referenceImages)

    // 3. 참조 이미지 정보 로깅 (향후 워크플로우 연동 시 사용할 파라미터)
    logComposeParameters(dto.key!!, composeData, referenceImageKeys)

    val accessToken = session?.accessToken

    // 4. DTE에 작업 등록
    val jobItem = enqueueTask(
      payload = mapOf(
        "subType" to SUB_TYPE_COMPOSE,
        "key" to dto.key!!,
        "data" to dto.data!!,
        "referenceImageKeys" to referenceImageKeys
      ),
      accessToken = accessToken,
      correlationId = dto.key,
    )

    kLogger.info { "상품 이미지 합성 작업 등록 완료 - jobId: ${jobItem.id}, key: ${dto.key}" }

    return jobItem
  }

  /**
   * JSON 데이터를 파싱하고 참조 이미지 수와 역할 정보 수가 일치하는지 검증합니다.
   *
   * @param dataJson 요청 데이터 JSON 문자열
   * @param referenceImages 업로드된 참조 이미지 목록
   * @return 파싱된 ProductImageComposeData
   * @throws IllegalArgumentException 검증 실패 시
   */
  private fun parseAndValidateComposeData(
    dataJson: String,
    referenceImages: List<MultipartFile>
  ): ProductImageComposeData {
    val composeData = try {
      objectMapper.readValue(dataJson, ProductImageComposeData::class.java)
    } catch (e: Exception) {
      throw IllegalArgumentException("요청 데이터 JSON 파싱 실패: ${e.message}")
    }

    // 이미지 수와 역할 정보 수 일치 검증
    val roleCount = composeData.referenceImageRoles?.size ?: 0
    if (referenceImages.size != roleCount) {
      throw IllegalArgumentException(
        "참조 이미지 수(${referenceImages.size})와 역할 정보 수($roleCount)가 일치하지 않습니다."
      )
    }

    return composeData
  }

  /**
   * 참조 이미지들을 검증하고 S3에 임시 저장합니다.
   *
   * @param requestKey 요청 고유 키
   * @param referenceImages 업로드된 참조 이미지 목록
   * @return S3에 저장된 이미지 키 목록
   * @throws IllegalArgumentException 이미지 검증 실패 시
   */
  private fun uploadReferenceImages(
    requestKey: String,
    referenceImages: List<MultipartFile>
  ): List<String> {
    return referenceImages.mapIndexed { index, file ->
      // 파일 크기 검증
      if (file.size > MAX_IMAGE_SIZE) {
        throw IllegalArgumentException(
          "참조 이미지[$index] 파일 크기(${file.size / 1024 / 1024}MB)가 최대 크기(10MB)를 초과합니다."
        )
      }

      // Content-Type 검증
      val contentType = file.contentType?.lowercase()
      if (contentType == null || contentType !in SUPPORTED_IMAGE_TYPES) {
        throw IllegalArgumentException(
          "참조 이미지[$index] 지원하지 않는 이미지 형식입니다: $contentType (지원: PNG, JPEG, WEBP)"
        )
      }

      // S3 업로드
      val s3Key = s3Service.uploadImageToTempAndGetKey(
        bytes = file.bytes,
        pathPrefix = "$REFERENCE_IMAGE_PATH_PREFIX/$requestKey",
        contentType = contentType
      )

      kLogger.debug { "참조 이미지[$index] S3 업로드 완료 - s3Key: $s3Key, size: ${file.size}, type: $contentType" }

      s3Key
    }
  }

  /**
   * 이미지 합성에 필요한 모든 파라미터를 상세 로깅합니다.
   * 향후 ComfyUI 워크플로우 연동 시 사용할 파라미터들입니다.
   *
   * @param requestKey 요청 고유 키
   * @param composeData 파싱된 요청 데이터
   * @param referenceImageKeys S3에 저장된 참조 이미지 키 목록
   */
  private fun logComposeParameters(
    requestKey: String,
    composeData: ProductImageComposeData,
    referenceImageKeys: List<String>
  ) {
    kLogger.info {
      """
      |========== 이미지 합성 파라미터 (워크플로우 연동 준비) ==========
      |[요청 정보]
      |  - requestKey: $requestKey
      |  - productName: ${composeData.productName}
      |  - prompt: ${composeData.prompt?.take(100)}...
      |  - imageStyle: ${composeData.imageStyle?.code() ?: "기본"}
      |  - aspectRatio: ${composeData.aspectRatio.code()}
      |  - imageCount: ${composeData.imageCount}
      |
      |[참조 이미지 역할 정보]
      |${
        composeData.referenceImageRoles?.mapIndexed { idx, role ->
          "|  [$idx] preset: ${role.preset?.code() ?: "없음"}, description: ${role.description}"
        }?.joinToString("\n") ?: "  없음"
      }
      |
      |[S3 저장 키]
      |${
        referenceImageKeys.mapIndexed { idx, key ->
          "|  [$idx] $key"
        }.joinToString("\n")
      }
      |
      |[워크플로우 연동 시 필요한 추가 작업]
      |  - IP-Adapter 또는 ControlNet 노드 구성
      |  - 각 역할별 이미지 로드 노드 추가
      |  - 합성 로직에 맞는 워크플로우 템플릿 설계
      |================================================================
      """.trimMargin()
    }
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
