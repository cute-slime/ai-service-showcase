package com.jongmin.ai.core.platform.component.agent.executor.model

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.ProxyProvider
import tools.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * REST API HTTP 요청 실행기
 *
 * WebClient 생성, HTTP 요청 실행, 재시도 로직 등
 * HTTP 통신 관련 로직을 담당한다.
 *
 * @author Claude Code
 * @since 2026.02.19 (RestApiCallToolNode에서 분리)
 */
object RestApiCallHttpExecutor {

  private val kLogger = KotlinLogging.logger {}

  private const val DEFAULT_TIMEOUT_SECONDS = 30
  private const val BACKOFF_MULTIPLIER = 1.5

  /**
   * 설정에 따른 WebClient 생성
   */
  fun createWebClient(config: RestApiCallToolNode.RestApiCallToolNodeConfig): WebClient {
    val timeout = Duration.ofSeconds((config.timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS).toLong())

    var httpClient = HttpClient.create()
      .responseTimeout(timeout)

    // SSL 검증 무시 설정 (개발/테스트 환경용)
    if (config.skipSslVerification == true) {
      kLogger.warn { "[RestApiCallNode] SSL 인증서 검증 무시 - 개발/테스트 환경에서만 사용하세요!" }
      val sslContext = SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()
      httpClient = httpClient.secure { it.sslContext(sslContext) }
    }

    // 프록시 설정
    if (config.proxy != null) {
      kLogger.info { "[RestApiCallNode] 프록시 설정 적용: ${config.proxy.host}:${config.proxy.port}" }
      httpClient = httpClient.proxy { proxy ->
        val spec = proxy.type(ProxyProvider.Proxy.HTTP)
          .host(config.proxy.host)
          .port(config.proxy.port)

        if (config.proxy.username != null && config.proxy.password != null) {
          spec.username(config.proxy.username)
            .password { config.proxy.password }
        }
      }
    }

    return WebClient.builder()
      .clientConnector(ReactorClientHttpConnector(httpClient))
      .build()
  }

  /**
   * HTTP 요청 실행
   */
  fun executeHttpRequest(
    webClient: WebClient,
    config: RestApiCallToolNode.RestApiCallToolNodeConfig,
    url: String,
    payload: Any?,
    objectMapper: ObjectMapper,
  ): Map<String, Any?> {
    val method = HttpMethod.valueOf(config.method.uppercase())

    try {
      val requestSpec = webClient.method(method)
        .uri(url)

      // 헤더 설정
      config.headers?.forEach { (key, value) ->
        requestSpec.header(key, value)
      }

      // Body가 있는 메서드의 경우 payload 전송 (POST, PUT, PATCH만)
      if (method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH) && payload != null) {
        requestSpec.contentType(MediaType.APPLICATION_JSON)
        requestSpec.bodyValue(payload)
      }

      // 요청 실행 및 응답 처리
      val response = requestSpec
        .exchangeToMono { clientResponse ->
          val statusCode = clientResponse.statusCode().value()
          val headers = clientResponse.headers().asHttpHeaders()
            .toSingleValueMap()

          clientResponse.bodyToMono<String>()
            .defaultIfEmpty("")
            .map { body ->
              val parsedBody = tryParseJson(body, objectMapper)
              mapOf(
                "statusCode" to statusCode,
                "headers" to headers,
                "body" to parsedBody,
                "success" to (statusCode in 200..299),
                "error" to null
              )
            }
        }
        .block()

      return response ?: createErrorResponse("응답이 null입니다")

    } catch (e: WebClientResponseException) {
      kLogger.error(e) { "[RestApiCallNode] HTTP 에러 응답: ${e.statusCode} - ${e.statusText}" }
      return mapOf(
        "statusCode" to e.statusCode.value(),
        "headers" to e.headers.toSingleValueMap(),
        "body" to tryParseJson(e.responseBodyAsString, objectMapper),
        "success" to false,
        "error" to "${e.statusCode}: ${e.statusText}"
      )
    } catch (e: Exception) {
      kLogger.error(e) { "[RestApiCallNode] HTTP 요청 실패: ${e.message}" }
      return createErrorResponse(e.message ?: "Unknown error")
    }
  }

  /**
   * 재시도 로직이 적용된 요청 실행 (Exponential Backoff)
   */
  fun <T> executeWithRetry(
    retryCount: Int,
    retryDelayMs: Long,
    request: () -> T
  ): T {
    var lastException: Exception? = null
    var delay = retryDelayMs

    repeat(retryCount + 1) { attempt ->
      try {
        return request()
      } catch (e: Exception) {
        lastException = e

        if (attempt < retryCount) {
          kLogger.warn {
            "[RestApiCallNode] 요청 실패, ${delay}ms 후 재시도 " +
                "(${attempt + 1}/${retryCount}): ${e.message}"
          }
          Thread.sleep(delay)
          delay = (delay * BACKOFF_MULTIPLIER).toLong()
        }
      }
    }

    throw lastException ?: RuntimeException("Unknown error during retry")
  }

  /**
   * JSON 파싱 시도, 실패 시 원본 문자열 반환
   */
  fun tryParseJson(body: String, objectMapper: ObjectMapper): Any? {
    if (body.isBlank()) return null

    return try {
      objectMapper.readValue(body, Any::class.java)
    } catch (_: Exception) {
      body
    }
  }

  /**
   * 에러 응답 생성
   */
  fun createErrorResponse(errorMessage: String): Map<String, Any?> {
    return mapOf(
      "statusCode" to -1,
      "headers" to emptyMap<String, String>(),
      "body" to null,
      "success" to false,
      "error" to errorMessage
    )
  }
}
