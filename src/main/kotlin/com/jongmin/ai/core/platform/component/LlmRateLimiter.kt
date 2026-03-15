package com.jongmin.ai.core.platform.component

import com.github.benmanes.caffeine.cache.Caffeine
import com.jongmin.jspring.core.enums.JService
import com.jongmin.jspring.core.util.serviceTrace
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.ai.core.platform.entity.AiProvider
import com.jongmin.ai.core.platform.entity.QAiProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*
import kotlin.random.Random

/**
 * 분산 LLM Rate Limiter
 *
 * Redis 기반의 분산 환경에서 LLM API 호출 동시성을 제어합니다.
 * - Provider별 동시 실행 수 제한 (Redis Lua Script로 원자적 처리)
 * - Exponential Backoff 재시도 (Provider별 설정 가능)
 * - 분산 환경 지원 (여러 서버 인스턴스에서 동일한 제한 공유)
 *
 * DTE(DistributedTaskExecutor) 패턴을 참고하여 구현되었습니다.
 *
 * @author Jongmin
 */
@Component
class LlmRateLimiter(
  private val redisTemplate: StringRedisTemplate,
  private val aiProviderRepository: AiProviderRepository,
) {
  private val kLogger = KotlinLogging.logger {}

  // Provider 이름 → Provider 엔티티 캐시 (5분 TTL, 최대 100개)
  private val providerCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(5))
    .maximumSize(100)
    .build<String, AiProvider>()

  companion object {
    // Redis 키 prefix
    private const val KEY_PREFIX = "ai:limiter"

    // 실행 중인 요청의 TTL (안전장치 - 5분)
    private const val RUNNING_REQUEST_TTL_SECONDS = 300L

    /**
     * 원자적 슬롯 확보 Lua Script
     *
     * 동시성 확인 → SADD를 원자적으로 실행하여 Race Condition 방지
     *
     * KEYS[1]: runningKey (Set) - 실행 중인 요청 ID 집합
     * ARGV[1]: maxConcurrency - 최대 동시 실행 수
     * ARGV[2]: requestId - 요청 ID
     * ARGV[3]: ttlSeconds - 요청 TTL (안전장치)
     *
     * @return "OK" (성공) 또는 nil (슬롯 없음)
     */
    private val ACQUIRE_SCRIPT = RedisScript.of<String>(
      """
            -- 현재 실행 중인 요청 수 확인
            local runningCount = redis.call('SCARD', KEYS[1])
            if runningCount >= tonumber(ARGV[1]) then
                return nil
            end

            -- 요청 ID를 running set에 추가
            redis.call('SADD', KEYS[1], ARGV[2])

            -- TTL 설정 (안전장치 - 요청이 정상 종료되지 않아도 자동 정리)
            redis.call('EXPIRE', KEYS[1], ARGV[3])

            return 'OK'
            """.trimIndent(),
      String::class.java
    )

    /**
     * 슬롯 확보 대기 (블로킹) Lua Script
     *
     * 슬롯이 없을 때 남은 슬롯 수를 반환하여 대기 로직에 활용
     *
     * KEYS[1]: runningKey (Set)
     * ARGV[1]: maxConcurrency
     *
     * @return 현재 실행 중인 요청 수 (슬롯 여유 계산용)
     */
    private val GET_RUNNING_COUNT_SCRIPT = RedisScript.of<Long>(
      """
            return redis.call('SCARD', KEYS[1])
            """.trimIndent(),
      Long::class.java
    )
  }

  // ========== Key 생성 ==========

  /**
   * 실행 중 요청 집합 키 생성 (Set)
   */
  private fun runningSetKey(providerId: Long): String =
    "$KEY_PREFIX:$providerId:running"

  // ========== 공개 API ==========

  /**
   * 슬롯 확보 시도 (비차단, Non-blocking)
   *
   * 슬롯이 있으면 즉시 확보하고, 없으면 null 반환.
   * 빠른 실패(fail-fast) 패턴에 적합.
   *
   * @param providerId AI Provider ID
   * @return AcquireResult (requestId 포함) 또는 null (슬롯 없음)
   */
  fun tryAcquire(providerId: Long): AcquireResult? {
    val provider = aiProviderRepository.findByIdOrNull(providerId)
    if (provider == null) {
      kLogger.warn { "Provider not found - providerId: $providerId" }
      return null
    }

    return tryAcquireInternal(provider)
  }

  /**
   * 슬롯 확보 시도 (Provider 객체 직접 전달)
   *
   * DB 조회 없이 Provider 정보를 직접 사용.
   * 이미 Provider를 조회한 경우 사용.
   *
   * @param provider AI Provider 엔티티
   * @return AcquireResult (requestId 포함) 또는 null (슬롯 없음)
   */
  fun tryAcquire(provider: AiProvider): AcquireResult? {
    return tryAcquireInternal(provider)
  }

  /**
   * 슬롯 반환
   *
   * LLM 호출 완료 후 반드시 호출해야 합니다.
   * try-finally 블록에서 호출하는 것을 권장합니다.
   *
   * @param providerId AI Provider ID
   * @param requestId 확보 시 반환받은 요청 ID
   */
  fun release(providerId: Long, requestId: String) {
    val runningKey = runningSetKey(providerId)
    val removed = redisTemplate.opsForSet().remove(runningKey, requestId)
    kLogger.debug { "슬롯 반환 - providerId: $providerId, requestId: $requestId, removed: $removed" }
  }

  /**
   * 슬롯 확보 (대기 + Exponential Backoff)
   *
   * 슬롯이 없으면 Provider 설정에 따라 지수 백오프로 재시도합니다.
   * 최대 재시도 횟수 초과 시 LlmRateLimitExceededException 발생.
   *
   * @param providerId AI Provider ID
   * @return AcquireResult (requestId 포함)
   * @throws LlmRateLimitExceededException 슬롯 확보 실패 시
   */
  fun acquireWithBackoff(providerId: Long): AcquireResult {
    val provider = aiProviderRepository.findByIdOrNull(providerId)
      ?: throw LlmRateLimitExceededException(
        providerId = providerId,
        providerName = "Unknown",
        message = "Provider not found - providerId: $providerId"
      )

    return acquireWithBackoffBlocking(provider)
  }

  /**
   * 슬롯 확보 (Blocking)
   *
   * 코루틴 환경이 아닌 일반 Java/Kotlin 코드에서 사용.
   * Thread.sleep()으로 대기.
   *
   * @param provider AI Provider 엔티티
   * @return AcquireResult (requestId 포함)
   * @throws LlmRateLimitExceededException 슬롯 확보 실패 시
   */
  fun acquireWithBackoffBlocking(provider: AiProvider): AcquireResult {
    var currentDelay = provider.retryDelayMs
    val maxRetries = provider.maxRetryAttempts

    repeat(maxRetries) { attempt ->
      // 슬롯 확보 시도
      tryAcquireInternal(provider)?.let { result ->
        if (attempt > 0) {
          kLogger.info {
            "슬롯 확보 성공 (재시도 후) - provider: ${provider.name}, attempt: ${attempt + 1}/$maxRetries"
          }
        }
        return result
      }

      // 마지막 시도가 아니면 대기
      if (attempt < maxRetries - 1) {
        val jitter = Random.nextLong(0, (currentDelay * 0.2).toLong())
        val totalDelay = currentDelay + jitter

        kLogger.debug {
          "슬롯 없음, 대기 중 - provider: ${provider.name}, attempt: ${attempt + 1}/$maxRetries, delay: ${totalDelay}ms"
        }

        Thread.sleep(totalDelay)

        currentDelay = minOf(
          (currentDelay * provider.retryBackoffMultiplier).toLong(),
          provider.maxRetryDelayMs
        )
      }
    }

    val runningCount = getRunningCount(provider.id)
    throw LlmRateLimitExceededException(
      providerId = provider.id,
      providerName = provider.name,
      message = "Rate limit exceeded - provider: ${provider.name}, " +
          "running: $runningCount/${provider.maxConcurrency}, maxRetries: $maxRetries"
    )
  }

  /**
   * 현재 실행 중인 요청 수 조회
   *
   * @param providerId AI Provider ID
   * @return 실행 중인 요청 수
   */
  fun getRunningCount(providerId: Long): Long {
    val runningKey = runningSetKey(providerId)
    return redisTemplate.opsForSet().size(runningKey) ?: 0
  }

  /**
   * 사용 가능한 슬롯 수 조회
   *
   * @param providerId AI Provider ID
   * @return 사용 가능한 슬롯 수 (0 이상)
   */
  fun getAvailableSlotCount(providerId: Long): Int {
    val provider = aiProviderRepository.findByIdOrNull(providerId) ?: return 0
    val runningCount = getRunningCount(providerId).toInt()
    return (provider.maxConcurrency - runningCount).coerceAtLeast(0)
  }

  // ========== Provider 이름 기반 API (RunnableAiModel 연동용) ==========

  /**
   * Provider 이름으로 슬롯 확보 (동기 버전, Blocking)
   *
   * RunnableAiModel에서 사용하기 위한 메서드.
   * Provider 이름으로 AiProvider를 조회하고 슬롯을 확보합니다.
   * 캐싱을 통해 DB 조회 오버헤드를 최소화합니다.
   *
   * @param providerName Provider 이름 (예: "zai", "openai", "anthropic")
   * @return AcquireResult (requestId 포함)
   * @throws LlmRateLimitExceededException Provider를 찾을 수 없거나 슬롯 확보 실패 시
   */
  fun acquireByProviderName(providerName: String): AcquireResult {
    val provider = getProviderByName(providerName)
      ?: throw LlmRateLimitExceededException(
        providerId = -1,
        providerName = providerName,
        message = "Provider not found - name: $providerName"
      )

    return acquireWithBackoffBlocking(provider)
  }

  /**
   * Provider 이름으로 슬롯 확보 시도 (Non-blocking)
   *
   * 슬롯이 없으면 대기 없이 즉시 null 반환.
   *
   * @param providerName Provider 이름
   * @return AcquireResult 또는 null (슬롯 없음/Provider 없음)
   */
  fun tryAcquireByProviderName(providerName: String): AcquireResult? {
    val provider = getProviderByName(providerName) ?: return null
    return tryAcquireInternal(provider)
  }

  /**
   * Provider 이름으로 AiProvider 엔티티 조회 (캐싱)
   *
   * @param providerName Provider 이름 (대소문자 무시)
   * @return AiProvider 또는 null
   */
  fun getProviderByName(providerName: String): AiProvider? {
    val normalizedName = providerName.lowercase()

    // 캐시 조회
    providerCache.getIfPresent(normalizedName)?.let { return it }

    // DB 조회 (QueryDSL)
    val qProvider = QAiProvider.aiProvider
    val provider = aiProviderRepository.findOne(
      qProvider.name.equalsIgnoreCase(normalizedName)
    ).orElse(null)

    // 캐시에 저장
    if (provider != null) {
      providerCache.put(normalizedName, provider)
    }

    return provider
  }

  /**
   * Provider 캐시 무효화
   *
   * Provider 설정이 변경되었을 때 호출하여 캐시를 갱신합니다.
   *
   * @param providerName Provider 이름 (null이면 전체 캐시 무효화)
   */
  fun invalidateProviderCache(providerName: String? = null) {
    if (providerName != null) {
      providerCache.invalidate(providerName.lowercase())
      kLogger.info { "Provider 캐시 무효화 - name: $providerName" }
    } else {
      providerCache.invalidateAll()
      kLogger.info { "전체 Provider 캐시 무효화" }
    }
  }

  // ========== 내부 메서드 ==========

  /**
   * 내부 슬롯 확보 로직
   */
  private fun tryAcquireInternal(provider: AiProvider): AcquireResult? {
    val requestId = UUID.randomUUID().toString()
    val runningKey = runningSetKey(provider.id)

    // Lua Script로 원자적 슬롯 확보
    val result: String? = redisTemplate.execute(
      ACQUIRE_SCRIPT,
      listOf(runningKey),
      provider.maxConcurrency.toString(),
      requestId,
      RUNNING_REQUEST_TTL_SECONDS.toString()
    )

    return if (result == "OK") {
      kLogger.serviceTrace(JService.AI) {
        "슬롯 확보 성공 - provider: ${provider.name}, requestId: $requestId, maxConcurrency: ${provider.maxConcurrency}"
      }
      AcquireResult(
        requestId = requestId,
        providerId = provider.id,
        providerName = provider.name
      )
    } else {
      kLogger.debug {
        "슬롯 확보 실패 (동시성 제한) - provider: ${provider.name}, maxConcurrency: ${provider.maxConcurrency}"
      }
      null
    }
  }
}

/**
 * 슬롯 확보 결과
 *
 * @property requestId 요청 ID (release 시 필요)
 * @property providerId Provider ID
 * @property providerName Provider 이름 (로깅용)
 */
data class AcquireResult(
  val requestId: String,
  val providerId: Long,
  val providerName: String,
)

/**
 * LLM Rate Limit 초과 예외
 *
 * 슬롯 확보 실패 시 발생합니다.
 * 재시도 가능한 예외로, 상위 레벨에서 적절히 처리해야 합니다.
 */
class LlmRateLimitExceededException(
  val providerId: Long,
  val providerName: String,
  override val message: String,
) : RuntimeException(message)
