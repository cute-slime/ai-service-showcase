package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.ai.core.GenerationProviderApiConfigRepository
import com.jongmin.ai.core.GenerationProviderStatus
import com.jongmin.ai.core.GenerationProviderRepository
import com.jongmin.ai.generation.provider.image.ComfyUIConstants
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import kotlin.math.ceil

/**
 * ComfyUI 런타임 설정 리졸버
 *
 * multimedia_provider + multimedia_provider_api_config 테이블에서
 * ComfyUI 연동에 필요한 런타임 설정을 조회한다.
 */
@Component
class ComfyUIRuntimeConfigResolver(
  private val providerRepository: GenerationProviderRepository,
  private val providerApiConfigRepository: GenerationProviderApiConfigRepository,
  private val objectMapper: ObjectMapper,
) {
  private val kLogger = KotlinLogging.logger {}

  fun resolve(preferredProviderId: Long? = null): ComfyUIRuntimeConfig {
    val providers = providerRepository.findByCodeAndStatusOrderBySortOrderAscIdAsc(
      ComfyUIConstants.PROVIDER_CODE,
      GenerationProviderStatus.ACTIVE
    )
    if (providers.isEmpty()) {
      throw IllegalStateException("활성 ComfyUI provider가 DB에 없습니다: ${ComfyUIConstants.PROVIDER_CODE}")
    }

    // providerId가 명시된 요청은 해당 provider만 사용한다.
    // 다른 provider로 fallback하면 workflow(provider_id)와 runtime(provider_id)가 어긋날 수 있다.
    if (preferredProviderId != null) {
      val provider = providers.firstOrNull { it.id == preferredProviderId }
        ?: throw IllegalStateException("요청된 ComfyUI providerId=$preferredProviderId 가 ACTIVE 상태가 아닙니다")

      val apiConfig = providerApiConfigRepository.findByProviderId(provider.id)
        ?: throw IllegalStateException("ComfyUI providerId=${provider.id}: API 설정 없음")
      val configJson = apiConfig.configJson
      if (configJson.isNullOrBlank()) {
        throw IllegalStateException("ComfyUI providerId=${provider.id}: configJson 비어있음")
      }

      return try {
        parseRuntimeConfig(provider.id, configJson)
      } catch (e: Exception) {
        throw IllegalStateException("ComfyUI providerId=${provider.id} runtime config 해석 실패: ${e.message}", e)
      }
    }

    val failures = mutableListOf<String>()

    providers.forEach { provider ->
      val apiConfig = providerApiConfigRepository.findByProviderId(provider.id)
      if (apiConfig == null) {
        failures.add("providerId=${provider.id}: API 설정 없음")
        return@forEach
      }

      val configJson = apiConfig.configJson
      if (configJson.isNullOrBlank()) {
        failures.add("providerId=${provider.id}: configJson 비어있음")
        return@forEach
      }

      try {
        return parseRuntimeConfig(provider.id, configJson)
      } catch (e: Exception) {
        kLogger.warn(e) { "ComfyUI runtime config 해석 실패, 다음 서버로 fallback - providerId=${provider.id}" }
        failures.add("providerId=${provider.id}: ${e.message}")
      }
    }

    throw IllegalStateException(
      "사용 가능한 ComfyUI 서버 설정을 찾을 수 없습니다: ${failures.joinToString(" | ")}"
    )
  }

  private fun parseRuntimeConfig(providerId: Long, configJson: String): ComfyUIRuntimeConfig {
    val node = try {
      objectMapper.readTree(configJson)
    } catch (e: Exception) {
      throw IllegalStateException("ComfyUI API configJson 파싱 실패: providerId=$providerId", e)
    }

    val normalizedBaseUrl = node.path("baseUrl").asString()?.trim()?.trimEnd('/')
      .orEmpty()
      .ifBlank { throw IllegalStateException("ComfyUI configJson.baseUrl이 비어있습니다: providerId=$providerId") }

    val connectTimeoutMs = node.path("connectTimeoutMs").asInt(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException("ComfyUI configJson.connectTimeoutMs가 올바르지 않습니다: providerId=$providerId")

    val readTimeoutMs = node.path("readTimeoutMs").asInt(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException("ComfyUI configJson.readTimeoutMs가 올바르지 않습니다: providerId=$providerId")

    val pollingIntervalSeconds = node.path("pollingIntervalSeconds").asLong(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException(
        "ComfyUI configJson.pollingIntervalSeconds가 올바르지 않습니다: providerId=$providerId"
      )

    val timeoutMinutes = ceil(readTimeoutMs.toDouble() / 60_000.0).toLong().coerceAtLeast(1)

    val wsUrl = node.path("webSocketUrl").asString()?.trim()?.takeIf { it.isNotBlank() } ?: run {
      normalizedBaseUrl
        .replace("http://", "ws://")
        .replace("https://", "wss://") + "/ws"
    }

    val defaultWidth = node.path("defaultWidth").asInt(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException("ComfyUI configJson.defaultWidth가 올바르지 않습니다: providerId=$providerId")

    val defaultHeight = node.path("defaultHeight").asInt(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException("ComfyUI configJson.defaultHeight가 올바르지 않습니다: providerId=$providerId")

    val defaultNegativePrompt = node.path("defaultNegativePrompt").asString()?.trim()?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException(
        "ComfyUI configJson.defaultNegativePrompt가 비어있습니다: providerId=$providerId"
      )

    val defaultModelCode = node.path("defaultModelCode").asString()?.trim()?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("ComfyUI configJson.defaultModelCode가 비어있습니다: providerId=$providerId")

    val defaultModelSteps = node.path("defaultModelSteps").asInt(-1)
      .takeIf { it > 0 }
      ?: throw IllegalStateException(
        "ComfyUI configJson.defaultModelSteps가 올바르지 않습니다: providerId=$providerId"
      )

    val storagePathPrefix = node.path("storagePathPrefix").asString()?.trim()?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException(
        "ComfyUI configJson.storagePathPrefix가 비어있습니다: providerId=$providerId"
      )

    val modelSteps = parseModelSteps(node.path("modelSteps"), providerId)

    return ComfyUIRuntimeConfig(
      providerId = providerId,
      baseUrl = normalizedBaseUrl,
      connectTimeoutMs = connectTimeoutMs,
      readTimeoutMs = readTimeoutMs,
      timeoutMinutes = timeoutMinutes,
      pollingIntervalSeconds = pollingIntervalSeconds,
      webSocketUrl = wsUrl,
      defaultWidth = defaultWidth,
      defaultHeight = defaultHeight,
      defaultNegativePrompt = defaultNegativePrompt,
      defaultModelCode = defaultModelCode,
      defaultModelSteps = defaultModelSteps,
      modelSteps = modelSteps,
      storagePathPrefix = storagePathPrefix,
    )
  }

  private fun parseModelSteps(node: JsonNode, providerId: Long): Map<String, Int> {
    if (!node.isObject) {
      throw IllegalStateException("ComfyUI configJson.modelSteps가 object가 아닙니다: providerId=$providerId")
    }

    @Suppress("UNCHECKED_CAST")
    val rawMap = objectMapper.convertValue(node, Map::class.java) as? Map<String, Any?>
      ?: throw IllegalStateException("ComfyUI configJson.modelSteps 파싱 실패: providerId=$providerId")

    val result = linkedMapOf<String, Int>()
    rawMap.forEach { (modelCode, rawSteps) ->
      val steps = when (rawSteps) {
        is Number -> rawSteps.toInt()
        is String -> rawSteps.toIntOrNull()
        else -> null
      } ?: throw IllegalStateException(
        "ComfyUI configJson.modelSteps.$modelCode 값이 올바르지 않습니다: providerId=$providerId"
      )

      if (steps <= 0) {
        throw IllegalStateException(
          "ComfyUI configJson.modelSteps.$modelCode 값이 올바르지 않습니다: providerId=$providerId"
        )
      }

      result[modelCode] = steps
    }

    return result
  }
}

data class ComfyUIRuntimeConfig(
  val providerId: Long,
  val baseUrl: String,
  val connectTimeoutMs: Int,
  val readTimeoutMs: Int,
  val timeoutMinutes: Long,
  val pollingIntervalSeconds: Long,
  val webSocketUrl: String,
  val defaultWidth: Int,
  val defaultHeight: Int,
  val defaultNegativePrompt: String,
  val defaultModelCode: String,
  val defaultModelSteps: Int,
  val modelSteps: Map<String, Int>,
  val storagePathPrefix: String,
)
