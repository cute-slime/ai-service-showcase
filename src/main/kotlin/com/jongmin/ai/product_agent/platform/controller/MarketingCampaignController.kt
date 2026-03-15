package com.jongmin.ai.product_agent.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.web.ratelimit.annotation.RateLimit
import com.jongmin.jspring.core.util.generateUuid
import com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignData
import com.jongmin.ai.product_agent.platform.dto.request.MarketingCampaignGenerateRequest
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
 * 마케팅 캠페인 생성 컨트롤러
 *
 * 여러 마케팅 도구를 한 번에 실행하여 다양한 마케팅 콘텐츠를 생성합니다.
 * DTE를 통해 비동기로 작업을 처리하며, SSE로 진행 상태를 스트리밍합니다.
 *
 * ### 지원 도구:
 * - banner-ad: 배너 광고 (이미지 + 헤드라인/서브헤드라인)
 * - instagram-feed: 인스타그램 피드 (이미지 + 캡션 + 해시태그)
 * - instagram-story: 인스타그램 스토리/릴스 (이미지 + 후킹 문구)
 * - search-ad: 검색 광고 (네이버/구글 텍스트 카피)
 *
 * ### 엔드포인트:
 * - POST /v1.0/product-agents/marketing-campaigns : 마케팅 캠페인 생성 요청
 */
@Tag(name = "AI - Marketing Campaign", description = "마케팅 캠페인 생성 API")
@Validated
@RestController
@RequestMapping("/v1.0")
@EnableConfigurationProperties(DistributedTaskExecutorProperties::class)
class MarketingCampaignController(
  private val properties: DistributedTaskExecutorProperties,
  private val webClientBuilder: WebClient.Builder,
  private val s3Service: S3Service,
  private val objectMapper: ObjectMapper,
  @param:Value("\${app.system-token}") private val systemToken: String,
) : JController() {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** PRODUCT_AGENT 타입으로 통합 (기존 카피라이팅, 이미지 생성과 동일) */
    private const val TASK_TYPE = "PRODUCT_AGENT"

    /** 마케팅 캠페인 서브타입 (핸들러에서 분기 처리용) */
    private const val SUB_TYPE = "MARKETING_CAMPAIGN"

    private const val ENQUEUE_TASK_PATH = "/v1.0/system/distributed-tasks"

    /** 제품 이미지 임시 저장 경로 프리픽스 */
    private const val PRODUCT_IMAGE_PATH_PREFIX = "marketing-campaign/product-images"

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

    kLogger.info { "MarketingCampaignController 초기화 완료 - baseUrl: ${properties.executor.baseUrl}" }
  }

  /**
   * 마케팅 캠페인 생성 요청
   *
   * 선택한 마케팅 도구들을 1개 Job으로 실행합니다.
   * 각 도구별 결과는 TOOL_RESULT SSE 이벤트로 순차 전송됩니다.
   *
   * @param dto 마케팅 캠페인 생성 요청 데이터
   * @return 등록된 분산 작업(Job) 정보
   */
  @Operation(
    summary = "마케팅 캠페인 생성",
    description = "여러 마케팅 도구를 실행하여 배너, SNS 콘텐츠, 검색 광고 등을 한 번에 생성합니다."
  )
  @RateLimit(key = "#{#ip}", limit = 10, window = "1h")
  @RateLimit(key = "#{#ip}", limit = 30, window = "1d")
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping("/product-agents/marketing-campaigns", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun generateMarketingCampaign(@Valid dto: MarketingCampaignGenerateRequest): DistributedJobItem {
    dto.key = generateUuid()

    kLogger.info { "마케팅 캠페인 생성 요청 - key: ${dto.key}" }

    // 1. JSON 데이터 파싱 및 검증
    val campaignData = parseAndValidateCampaignData(dto.data!!)

    kLogger.info {
      "마케팅 캠페인 요청 분석 - key: ${dto.key}, " +
          "tools: ${campaignData.selectedTools?.map { it.code() }}, " +
          "productName: ${campaignData.commonInput?.productName}"
    }

    // 2. 이미지 필요 도구 선택 시 이미지 검증
    validateImageRequirement(campaignData, dto.productImage)

    // 3. 제품 이미지 S3 업로드 (있는 경우)
    val productImageKey = dto.productImage?.let { uploadProductImage(dto.key!!, it) }

    val accessToken = session?.accessToken

    // 4. DTE에 작업 등록
    val jobItem = enqueueTask(
      payload = mapOf(
        "subType" to SUB_TYPE,
        "key" to dto.key!!,
        "data" to dto.data!!,
        "productImageKey" to (productImageKey ?: "")
      ),
      accessToken = accessToken,
      correlationId = dto.key,
    )

    kLogger.info {
      "마케팅 캠페인 작업 등록 완료 - jobId: ${jobItem.id}, key: ${dto.key}, " +
          "tools: ${campaignData.selectedTools?.size ?: 0}개"
    }

    return jobItem
  }

  /**
   * JSON 데이터를 파싱하고 검증합니다.
   *
   * @param dataJson 요청 데이터 JSON 문자열
   * @return 파싱된 MarketingCampaignData
   * @throws IllegalArgumentException 파싱 실패 시
   */
  private fun parseAndValidateCampaignData(dataJson: String): MarketingCampaignData {
    return try {
      objectMapper.readValue(dataJson, MarketingCampaignData::class.java)
    } catch (e: Exception) {
      throw IllegalArgumentException("요청 데이터 JSON 파싱 실패: ${e.message}")
    }
  }

  /**
   * 이미지가 필요한 도구를 선택했는데 이미지가 없는 경우 예외를 발생시킵니다.
   *
   * @param campaignData 마케팅 캠페인 데이터
   * @param productImage 업로드된 제품 이미지
   * @throws IllegalArgumentException 이미지 누락 시
   */
  private fun validateImageRequirement(campaignData: MarketingCampaignData, productImage: MultipartFile?) {
    val selectedTools = campaignData.selectedTools ?: return

    // 이미지가 필요한 도구가 선택되었는지 확인
    val requiresImage = selectedTools.any { it.requiresImage }

    if (requiresImage && productImage == null) {
      val imageRequiredTools = selectedTools.filter { it.requiresImage }.map { it.description }
      throw IllegalArgumentException(
        "선택한 도구(${imageRequiredTools.joinToString(", ")})에는 제품 이미지가 필요합니다."
      )
    }
  }

  /**
   * 제품 이미지를 S3에 업로드합니다.
   *
   * @param requestKey 요청 고유 키
   * @param file 업로드할 이미지 파일
   * @return S3에 저장된 이미지 키
   * @throws IllegalArgumentException 이미지 검증 실패 시
   */
  private fun uploadProductImage(requestKey: String, file: MultipartFile): String {
    // 파일 크기 검증
    if (file.size > MAX_IMAGE_SIZE) {
      throw IllegalArgumentException(
        "이미지 파일 크기(${file.size / 1024 / 1024}MB)가 최대 크기(10MB)를 초과합니다."
      )
    }

    // Content-Type 검증
    val contentType = file.contentType?.lowercase()
    if (contentType == null || contentType !in SUPPORTED_IMAGE_TYPES) {
      throw IllegalArgumentException(
        "지원하지 않는 이미지 형식입니다: $contentType (지원: PNG, JPEG, WEBP)"
      )
    }

    // S3 업로드
    val s3Key = s3Service.uploadImageToTempAndGetKey(
      bytes = file.bytes,
      pathPrefix = "$PRODUCT_IMAGE_PATH_PREFIX/$requestKey",
      contentType = contentType
    )

    kLogger.debug { "제품 이미지 S3 업로드 완료 - s3Key: $s3Key, size: ${file.size}, type: $contentType" }

    return s3Key
  }

  /**
   * 분산 태스크 큐에 작업을 등록합니다.
   *
   * @param payload 작업 데이터
   * @param priority 우선순위 (기본값: null)
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
