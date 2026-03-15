package com.jongmin.ai.product_agent.platform.component

import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIRuntimeConfig
import com.jongmin.ai.generation.provider.image.comfyui.ComfyUIRuntimeConfigResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ComfyUI REST API 클라이언트
 *
 * ComfyUI 서버와 통신하여 이미지 생성 작업을 수행합니다.
 * - 워크플로우 제출 (/prompt)
 * - 작업 상태 조회 (/history/{prompt_id})
 * - 이미지 다운로드 (/view)
 */
@Component
class ComfyUiClient(
  private val runtimeConfigResolver: ComfyUIRuntimeConfigResolver,
  private val webClientBuilder: WebClient.Builder,
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  @Volatile
  private var webClient: WebClient? = null

  @Volatile
  private var cachedRuntimeConfig: ComfyUIRuntimeConfig? = null

  private lateinit var workflowTemplate: String

  private val webClientLock = Any()

  companion object {
    // 워크플로우 템플릿 경로
    private const val WORKFLOW_TEMPLATE_PATH = "comfyui/workflow-template.json"
  }

  @PostConstruct
  fun init() {
    // 워크플로우 템플릿 로드
    workflowTemplate = loadWorkflowTemplate()

    kLogger.info { "ComfyUiClient 초기화 완료 - runtime config source: multimedia_provider_api_config" }
  }

  private fun getRuntimeConfig(): ComfyUIRuntimeConfig {
    return runtimeConfigResolver.resolve()
  }

  private fun getWebClient(): WebClient {
    return getWebClient(getRuntimeConfig())
  }

  private fun getWebClient(runtimeConfig: ComfyUIRuntimeConfig): WebClient {
    val current = webClient
    if (current != null && cachedRuntimeConfig == runtimeConfig) {
      return current
    }

    synchronized(webClientLock) {
      val refreshed = webClient
      if (refreshed != null && cachedRuntimeConfig == runtimeConfig) {
        return refreshed
      }

      val newWebClient = buildWebClient(runtimeConfig)
      webClient = newWebClient
      cachedRuntimeConfig = runtimeConfig
      kLogger.info {
        "ComfyUI WebClient 재구성 - baseUrl: ${runtimeConfig.baseUrl}, " +
            "connectTimeoutMs: ${runtimeConfig.connectTimeoutMs}, readTimeoutMs: ${runtimeConfig.readTimeoutMs}, " +
            "pollingIntervalSeconds: ${runtimeConfig.pollingIntervalSeconds}"
      }
      return newWebClient
    }
  }

  private fun buildWebClient(runtimeConfig: ComfyUIRuntimeConfig): WebClient {
    val httpClient = HttpClient.create()
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, runtimeConfig.connectTimeoutMs)
      .responseTimeout(Duration.ofMillis(runtimeConfig.readTimeoutMs.toLong()))
      .doOnConnected { conn ->
        conn.addHandlerLast(ReadTimeoutHandler(runtimeConfig.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS))
        conn.addHandlerLast(WriteTimeoutHandler(30, TimeUnit.SECONDS))
      }

    val maxBufferSize = 16 * 1024 * 1024 // 16MB
    return webClientBuilder
      .baseUrl(runtimeConfig.baseUrl)
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .codecs { configurer ->
        configurer.defaultCodecs().maxInMemorySize(maxBufferSize)
      }
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
      .build()
  }

  /**
   * 워크플로우 템플릿을 로드합니다.
   */
  private fun loadWorkflowTemplate(): String {
    return try {
      ClassPathResource(WORKFLOW_TEMPLATE_PATH).inputStream.bufferedReader().readText()
    } catch (e: Exception) {
      kLogger.error(e) { "워크플로우 템플릿 로드 실패: $WORKFLOW_TEMPLATE_PATH" }
      throw IllegalStateException("ComfyUI 워크플로우 템플릿을 로드할 수 없습니다", e)
    }
  }

  /**
   * 워크플로우를 빌드합니다.
   *
   * 템플릿의 플레이스홀더를 실제 값으로 치환합니다.
   *
   * @param prompt 이미지 생성 프롬프트 (필수)
   * @param negativePrompt 네거티브 프롬프트 (미지정 시 DB configJson 기본값)
   * @param width 이미지 너비 (미지정 시 DB configJson 기본값)
   * @param height 이미지 높이 (미지정 시 DB configJson 기본값)
   * @param seed 시드값 (기본값: 랜덤)
   * @return 파싱된 워크플로우 맵
   */
  fun buildWorkflow(
    prompt: String,
    negativePrompt: String? = null,
    width: Int? = null,
    height: Int? = null,
    seed: Long = System.currentTimeMillis(),
  ): Map<String, Any> {
    val runtimeConfig = getRuntimeConfig()
    val effectiveNegativePrompt = negativePrompt?.takeIf { it.isNotBlank() }
      ?: runtimeConfig.defaultNegativePrompt
    val effectiveWidth = width ?: runtimeConfig.defaultWidth
    val effectiveHeight = height ?: runtimeConfig.defaultHeight

    val workflowJson = workflowTemplate
      .replace("{{PROMPT}}", escapeJsonString(prompt))
      .replace("{{NEGATIVE_PROMPT}}", escapeJsonString(effectiveNegativePrompt))
      .replace("{{WIDTH}}", effectiveWidth.toString())
      .replace("{{HEIGHT}}", effectiveHeight.toString())
      .replace("{{SEED}}", seed.toString())

    @Suppress("UNCHECKED_CAST")
    return objectMapper.readValue(workflowJson, Map::class.java) as Map<String, Any>
  }

  /**
   * JSON 문자열에 사용할 수 있도록 특수문자를 이스케이프 처리합니다.
   * 줄바꿈, 탭, 백슬래시, 큰따옴표 등을 처리합니다.
   */
  private fun escapeJsonString(input: String): String {
    return input
      .replace("\\", "\\\\")  // 백슬래시 먼저 처리
      .replace("\"", "\\\"")  // 큰따옴표
      .replace("\n", "\\n")   // 줄바꿈
      .replace("\r", "\\r")   // 캐리지 리턴
      .replace("\t", "\\t")   // 탭
  }

  /**
   * ComfyUI에 워크플로우를 제출합니다.
   *
   * @param workflow 워크플로우 맵
   * @param clientId 클라이언트 식별자
   * @return 프롬프트 응답 (prompt_id 포함)
   * @throws ComfyUiException 제출 실패 시
   */
  fun submitPrompt(workflow: Map<String, Any>, clientId: String): ComfyUiPromptResponse {
    val request = mapOf(
      "prompt" to workflow,
      "client_id" to clientId
    )

    kLogger.info { "ComfyUI 워크플로우 제출 - clientId: $clientId" }

    return try {
      val webClient = getWebClient()
      val response = webClient.post()
        .uri("/prompt")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(ComfyUiPromptResponse::class.java)
        .block()
        ?: throw ComfyUiException("ComfyUI 응답이 비어있습니다")

      // node_errors 체크
      if (response.nodeErrors.isNotEmpty()) {
        kLogger.error { "ComfyUI 노드 에러 발생: ${response.nodeErrors}" }
        throw ComfyUiException("워크플로우 노드 에러: ${response.nodeErrors}")
      }

      kLogger.info { "ComfyUI 워크플로우 제출 성공 - promptId: ${response.promptId}" }
      response
    } catch (e: WebClientResponseException) {
      val responseBody = try { e.responseBodyAsString } catch (_: Exception) { "(응답 본문 읽기 실패)" }
      kLogger.error(e) { "ComfyUI 워크플로우 제출 HTTP 에러 - status: ${e.statusCode}, body: $responseBody" }
      throw ComfyUiException("ComfyUI 서버 에러: ${e.statusCode} - $responseBody", e)
    } catch (e: ComfyUiException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 워크플로우 제출 실패" }
      throw ComfyUiException("ComfyUI 워크플로우 제출 실패", e)
    }
  }

  /**
   * 작업 히스토리를 조회합니다.
   *
   * @param promptId 프롬프트 ID
   * @return 히스토리 응답 (완료 여부, outputs 포함)
   */
  fun getHistory(promptId: String): ComfyUiHistoryResponse? {
    return try {
      getHistory(promptId, getWebClient())
    } catch (e: Exception) {
      kLogger.warn(e) { "ComfyUI 히스토리 조회용 WebClient 생성 실패 - promptId: $promptId" }
      null
    }
  }

  private fun getHistory(promptId: String, webClient: WebClient): ComfyUiHistoryResponse? {
    return try {
      val response = webClient.get()
        .uri("/history/{promptId}", promptId)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      if (response.isNullOrBlank() || response == "{}") {
        return null
      }

      // 응답 파싱 (promptId가 키인 맵 구조)
      @Suppress("UNCHECKED_CAST")
      val historyMap = objectMapper.readValue(response, Map::class.java) as Map<String, Any>
      val promptData = historyMap[promptId] as? Map<*, *> ?: return null

      parseHistoryResponse(promptId, promptData)
    } catch (e: Exception) {
      kLogger.warn(e) { "ComfyUI 히스토리 조회 실패 - promptId: $promptId" }
      null
    }
  }

  /**
   * 히스토리 응답을 파싱합니다.
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseHistoryResponse(promptId: String, data: Map<*, *>): ComfyUiHistoryResponse {
    val status = data["status"] as? Map<*, *>
    val statusStr = status?.get("status_str") as? String
    val completed = status?.get("completed") as? Boolean ?: false

    val outputs = data["outputs"] as? Map<*, *> ?: emptyMap<String, Any>()

    // outputs에서 이미지 파일명 추출
    val imageFilenames = mutableListOf<String>()
    outputs.values.forEach { nodeOutput ->
      val nodeOutputMap = nodeOutput as? Map<*, *> ?: return@forEach
      val images = nodeOutputMap["images"] as? List<*> ?: return@forEach
      images.forEach { image ->
        val imageMap = image as? Map<*, *> ?: return@forEach
        val filename = imageMap["filename"] as? String
        if (filename != null) {
          imageFilenames.add(filename)
        }
      }
    }

    return ComfyUiHistoryResponse(
      promptId = promptId,
      status = statusStr ?: "unknown",
      completed = completed,
      imageFilenames = imageFilenames
    )
  }

  /**
   * 작업 완료까지 폴링합니다.
   *
   * @param promptId 프롬프트 ID
   * @return 완료된 히스토리 응답
   * @throws TimeoutException 타임아웃 발생 시
   * @throws ComfyUiException 작업 실패 시
   */
  fun waitForCompletion(promptId: String): ComfyUiHistoryResponse {
    val runtimeConfig = getRuntimeConfig()
    val webClient = getWebClient(runtimeConfig)
    val startTime = System.currentTimeMillis()
    val timeoutMs = runtimeConfig.timeoutMinutes * 60 * 1000
    val pollingMs = runtimeConfig.pollingIntervalSeconds * 1000

    kLogger.info {
      "ComfyUI 작업 완료 대기 시작 - promptId: $promptId, timeout: ${runtimeConfig.timeoutMinutes}분"
    }

    while (true) {
      val elapsed = System.currentTimeMillis() - startTime

      // 타임아웃 체크
      if (elapsed >= timeoutMs) {
        kLogger.error { "ComfyUI 작업 타임아웃 - promptId: $promptId, elapsed: ${elapsed / 1000}초" }
        throw TimeoutException("ComfyUI 작업 타임아웃: ${runtimeConfig.timeoutMinutes}분 초과")
      }

      // 히스토리 조회
      val history = getHistory(promptId, webClient)

      if (history != null && history.completed) {
        kLogger.info { "ComfyUI 작업 완료 - promptId: $promptId, elapsed: ${elapsed / 1000}초, images: ${history.imageFilenames.size}개" }
        return history
      }

      // 상태 로깅
      if (history != null) {
        kLogger.debug { "ComfyUI 작업 진행 중 - promptId: $promptId, status: ${history.status}, elapsed: ${elapsed / 1000}초" }
      }

      // 폴링 대기
      Thread.sleep(pollingMs)
    }
  }

  /**
   * 이미지를 다운로드합니다.
   *
   * 주의: ComfyUI /view 엔드포인트는 파일 삭제 기능을 지원하지 않습니다.
   * output 폴더 정리가 필요한 경우 별도의 정리 스크립트나 파일 시스템 접근이 필요합니다.
   *
   * @param filename 파일명
   * @return 이미지 바이트 배열
   * @throws ComfyUiException 다운로드 실패 시
   */
  fun downloadImage(filename: String): ByteArray {
    kLogger.info { "ComfyUI 이미지 다운로드 - filename: $filename" }

    return try {
      val webClient = getWebClient()
      val response = webClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/view")
            .queryParam("filename", filename)
            .queryParam("subfolder", "")
            .queryParam("type", "output")
            .build()
        }
        .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.APPLICATION_OCTET_STREAM)
        .retrieve()
        .bodyToMono(ByteArray::class.java)
        .block()
        ?: throw ComfyUiException("이미지 다운로드 응답이 비어있습니다")

      kLogger.info { "ComfyUI 이미지 다운로드 완료 - filename: $filename, size: ${response.size} bytes" }
      response
    } catch (e: WebClientResponseException) {
      kLogger.error(e) { "ComfyUI 이미지 다운로드 HTTP 에러 - filename: $filename, status: ${e.statusCode}" }
      throw ComfyUiException("이미지 다운로드 실패: ${e.statusCode}", e)
    } catch (e: ComfyUiException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 이미지 다운로드 실패 - filename: $filename" }
      throw ComfyUiException("이미지 다운로드 실패", e)
    }
  }

  /**
   * ComfyUI input 폴더로 이미지를 업로드합니다.
   *
   * MEDIA_TO_MEDIA 파이프라인의 LoadImage 노드 입력 파일 준비에 사용됩니다.
   *
   * @param bytes 업로드할 파일 바이트
   * @param fileName 저장 파일명
   * @param contentType 파일 MIME 타입 (선택)
   * @return 실제 업로드된 파일명
   * @throws ComfyUiException 업로드 실패 시
   */
  fun uploadInputImage(
    bytes: ByteArray,
    fileName: String,
    contentType: String? = null,
  ): String {
    if (bytes.isEmpty()) {
      throw ComfyUiException("업로드할 이미지 데이터가 비어있습니다")
    }

    val normalizedFileName = fileName.trim().ifBlank { "input_${System.currentTimeMillis()}.png" }

    kLogger.info {
      "ComfyUI 입력 이미지 업로드 - fileName: $normalizedFileName, size: ${bytes.size} bytes, contentType: ${contentType ?: "-"}"
    }

    return try {
      val mediaType = runCatching {
        contentType
          ?.takeIf { it.isNotBlank() }
          ?.let { MediaType.parseMediaType(it) }
      }.getOrNull()

      val multipart = MultipartBodyBuilder().apply {
        val filePart = part(
          "image",
          object : ByteArrayResource(bytes) {
            override fun getFilename(): String = normalizedFileName
          }
        ).filename(normalizedFileName)
        mediaType?.let { filePart.contentType(it) }
        part("type", "input")
        part("overwrite", "true")
      }.build()

      val webClient = getWebClient()
      val responseBody = webClient.post()
        .uri("/upload/image")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(multipart))
        .retrieve()
        .bodyToMono(String::class.java)
        .block()
        ?: throw ComfyUiException("ComfyUI 업로드 응답이 비어있습니다")

      @Suppress("UNCHECKED_CAST")
      val responseMap = objectMapper.readValue(responseBody, Map::class.java) as Map<String, Any>
      val uploadedName = responseMap["name"]?.toString()?.takeIf { it.isNotBlank() } ?: normalizedFileName

      kLogger.info { "ComfyUI 입력 이미지 업로드 완료 - uploadedName: $uploadedName" }
      uploadedName
    } catch (e: WebClientResponseException) {
      val responseBody = runCatching { e.responseBodyAsString }.getOrDefault("(응답 본문 읽기 실패)")
      kLogger.error(e) {
        "ComfyUI 입력 이미지 업로드 HTTP 에러 - status: ${e.statusCode}, fileName: $normalizedFileName, body: $responseBody"
      }
      throw ComfyUiException("ComfyUI 입력 이미지 업로드 실패: ${e.statusCode} - $responseBody", e)
    } catch (e: ComfyUiException) {
      throw e
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 입력 이미지 업로드 실패 - fileName: $normalizedFileName" }
      throw ComfyUiException("ComfyUI 입력 이미지 업로드 실패", e)
    }
  }

  /**
   * ComfyUI 서버 헬스체크
   *
   * @return 서버가 정상인지 여부
   */
  fun isHealthy(): Boolean {
    return try {
      val webClient = getWebClient()
      // /system_stats 엔드포인트로 서버 상태 확인
      val response = webClient.get()
        .uri("/system_stats")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()
      response != null
    } catch (e: Exception) {
      kLogger.warn(e) { "ComfyUI 서버 헬스체크 실패" }
      false
    }
  }

  // ========== 메인터넌스 API ==========

  /**
   * 특정 히스토리를 삭제합니다.
   *
   * 지정된 prompt ID들의 히스토리 기록을 삭제합니다.
   * 디스크 공간 관리나 개인정보 삭제 등에 활용할 수 있습니다.
   *
   * @param promptIds 삭제할 prompt ID 목록
   * @return 삭제 성공 여부
   */
  fun clearHistory(vararg promptIds: String): Boolean {
    if (promptIds.isEmpty()) return true

    kLogger.info { "ComfyUI 히스토리 삭제 요청 - promptIds: ${promptIds.toList()}" }

    return try {
      val webClient = getWebClient()
      val request = mapOf("delete" to promptIds.toList())
      webClient.post()
        .uri("/history")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI 히스토리 삭제 완료 - count: ${promptIds.size}" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 히스토리 삭제 실패" }
      false
    }
  }

  /**
   * 전체 히스토리를 삭제합니다.
   *
   * 모든 히스토리 기록을 삭제합니다.
   * 주의: 복구할 수 없으므로 신중하게 사용해야 합니다.
   *
   * @return 삭제 성공 여부
   */
  fun clearAllHistory(): Boolean {
    kLogger.info { "ComfyUI 전체 히스토리 삭제 요청" }

    return try {
      val webClient = getWebClient()
      val request = mapOf("clear" to true)
      webClient.post()
        .uri("/history")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI 전체 히스토리 삭제 완료" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 전체 히스토리 삭제 실패" }
      false
    }
  }

  /**
   * 큐에서 특정 작업을 삭제합니다.
   *
   * 대기 중인 작업을 큐에서 제거합니다.
   * 이미 실행 중인 작업은 이 메서드로 중단할 수 없습니다.
   * 실행 중인 작업을 중단하려면 interruptCurrentTask()를 사용하세요.
   *
   * @param promptIds 삭제할 prompt ID 목록
   * @return 삭제 성공 여부
   */
  fun deleteFromQueue(vararg promptIds: String): Boolean {
    if (promptIds.isEmpty()) return true

    kLogger.info { "ComfyUI 큐에서 작업 삭제 요청 - promptIds: ${promptIds.toList()}" }

    return try {
      val webClient = getWebClient()
      val request = mapOf("delete" to promptIds.toList())
      webClient.post()
        .uri("/queue")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI 큐에서 작업 삭제 완료 - count: ${promptIds.size}" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 큐에서 작업 삭제 실패" }
      false
    }
  }

  /**
   * 전체 큐를 삭제합니다.
   *
   * 대기 중인 모든 작업을 큐에서 제거합니다.
   * 현재 실행 중인 작업에는 영향을 주지 않습니다.
   *
   * @return 삭제 성공 여부
   */
  fun clearQueue(): Boolean {
    kLogger.info { "ComfyUI 전체 큐 삭제 요청" }

    return try {
      val webClient = getWebClient()
      val request = mapOf("clear" to true)
      webClient.post()
        .uri("/queue")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI 전체 큐 삭제 완료" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 전체 큐 삭제 실패" }
      false
    }
  }

  /**
   * 현재 실행 중인 작업을 중단합니다.
   *
   * GPU에서 실행 중인 이미지 생성 작업을 즉시 중단합니다.
   * 이미 생성된 일부 결과는 보존될 수 있습니다.
   *
   * @return 중단 성공 여부
   */
  fun interruptCurrentTask(): Boolean {
    kLogger.info { "ComfyUI 현재 작업 중단 요청" }

    return try {
      val webClient = getWebClient()
      webClient.post()
        .uri("/interrupt")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI 현재 작업 중단 완료" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 현재 작업 중단 실패" }
      false
    }
  }

  /**
   * 큐 상태를 조회합니다.
   *
   * 현재 실행 중인 작업과 대기 중인 작업 목록을 반환합니다.
   * 시스템 모니터링이나 작업 관리에 활용할 수 있습니다.
   *
   * @return 큐 상태 정보 (running, pending 목록)
   */
  fun getQueueStatus(): ComfyUiQueueStatus? {
    return try {
      val webClient = getWebClient()
      val response = webClient.get()
        .uri("/queue")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      if (response.isNullOrBlank()) return null

      @Suppress("UNCHECKED_CAST")
      val queueData = objectMapper.readValue(response, Map::class.java) as Map<String, Any>

      val runningList = (queueData["queue_running"] as? List<*>) ?: emptyList<Any>()
      val pendingList = (queueData["queue_pending"] as? List<*>) ?: emptyList<Any>()

      ComfyUiQueueStatus(
        runningCount = runningList.size,
        pendingCount = pendingList.size,
        runningPromptIds = extractPromptIdsFromQueue(runningList),
        pendingPromptIds = extractPromptIdsFromQueue(pendingList)
      )
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 큐 상태 조회 실패" }
      null
    }
  }

  /**
   * 큐 데이터에서 prompt ID를 추출합니다.
   */
  private fun extractPromptIdsFromQueue(queueList: List<*>): List<String> {
    return queueList.mapNotNull { item ->
      // 큐 아이템은 [index, promptId, ...] 형태의 배열
      (item as? List<*>)?.getOrNull(1) as? String
    }
  }

  /**
   * 시스템 통계를 조회합니다.
   *
   * GPU 메모리 사용량, 시스템 상태 등의 정보를 반환합니다.
   * 서버 리소스 모니터링에 활용할 수 있습니다.
   *
   * @return 시스템 통계 정보 (JSON 문자열)
   */
  fun getSystemStats(): String? {
    return try {
      val webClient = getWebClient()
      webClient.get()
        .uri("/system_stats")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI 시스템 통계 조회 실패" }
      null
    }
  }

  /**
   * GPU 메모리를 해제합니다.
   *
   * 캐시된 모델 등을 메모리에서 해제하여 VRAM을 확보합니다.
   * 메모리 부족 상황에서 유용합니다.
   *
   * @return 해제 성공 여부
   */
  fun freeMemory(): Boolean {
    kLogger.info { "ComfyUI GPU 메모리 해제 요청" }

    return try {
      val webClient = getWebClient()
      webClient.post()
        .uri("/free")
        .bodyValue(mapOf("unload_models" to true, "free_memory" to true))
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      kLogger.info { "ComfyUI GPU 메모리 해제 완료" }
      true
    } catch (e: Exception) {
      kLogger.error(e) { "ComfyUI GPU 메모리 해제 실패" }
      false
    }
  }
}

/**
 * ComfyUI 프롬프트 응답 DTO
 */
data class ComfyUiPromptResponse(
  /** 프롬프트 ID */
  val promptId: String = "",
  /** 큐 번호 */
  val number: Int = 0,
  /** 노드 에러 */
  val nodeErrors: Map<String, Any> = emptyMap(),
) {
  companion object {
    // Jackson 역직렬화를 위한 팩토리 메서드
    @JvmStatic
    @com.fasterxml.jackson.annotation.JsonCreator
    fun create(
      @com.fasterxml.jackson.annotation.JsonProperty("prompt_id") promptId: String?,
      @com.fasterxml.jackson.annotation.JsonProperty("number") number: Int?,
      @com.fasterxml.jackson.annotation.JsonProperty("node_errors") nodeErrors: Map<String, Any>?,
    ): ComfyUiPromptResponse {
      return ComfyUiPromptResponse(
        promptId = promptId ?: "",
        number = number ?: 0,
        nodeErrors = nodeErrors ?: emptyMap()
      )
    }
  }
}

/**
 * ComfyUI 히스토리 응답 DTO
 */
data class ComfyUiHistoryResponse(
  /** 프롬프트 ID */
  val promptId: String,
  /** 상태 문자열 */
  val status: String,
  /** 완료 여부 */
  val completed: Boolean,
  /** 생성된 이미지 파일명 목록 */
  val imageFilenames: List<String>,
)

/**
 * ComfyUI 큐 상태 DTO
 *
 * 현재 실행 중인 작업과 대기 중인 작업의 상태를 나타냅니다.
 */
data class ComfyUiQueueStatus(
  /** 실행 중인 작업 수 */
  val runningCount: Int,
  /** 대기 중인 작업 수 */
  val pendingCount: Int,
  /** 실행 중인 작업의 prompt ID 목록 */
  val runningPromptIds: List<String>,
  /** 대기 중인 작업의 prompt ID 목록 */
  val pendingPromptIds: List<String>,
) {
  /** 전체 작업 수 (실행 중 + 대기 중) */
  val totalCount: Int get() = runningCount + pendingCount

  /** 큐가 비어있는지 여부 */
  val isEmpty: Boolean get() = totalCount == 0
}

/**
 * ComfyUI 예외
 */
class ComfyUiException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
