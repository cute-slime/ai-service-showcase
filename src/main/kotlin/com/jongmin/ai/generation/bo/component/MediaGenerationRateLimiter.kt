package com.jongmin.ai.generation.bo.component

import com.jongmin.ai.generation.bo.config.MediaGenerationProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

/**
 * 미디어 생성 동시성 제한기
 *
 * Redis 기반으로 계정당 동시 실행 Job 수를 제한한다.
 * LlmRateLimiter 패턴을 참조하여 Lua Script로 원자적 슬롯 확보를 구현.
 *
 * ### Redis 키 구조:
 * - `media_gen:active_jobs:{accountId}` (Set) - 활성 Job ID 집합
 * - TTL 1시간 (안전장치)
 */
@Component
@EnableConfigurationProperties(MediaGenerationProperties::class)
class MediaGenerationRateLimiter(
  private val redisTemplate: StringRedisTemplate,
  private val properties: MediaGenerationProperties,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    private const val KEY_PREFIX = "media_gen:active_jobs"

    // 안전장치 TTL (1시간) - Job이 비정상 종료 시 자동 정리
    private const val TTL_SECONDS = 3600L

    /**
     * 원자적 슬롯 확보 Lua Script
     *
     * KEYS[1]: activeJobsKey (Set) - 활성 Job ID 집합
     * ARGV[1]: maxConcurrency - 최대 동시 실행 수
     * ARGV[2]: jobId - Job ID
     * ARGV[3]: ttlSeconds - TTL (안전장치)
     *
     * @return "OK" (성공) 또는 nil (슬롯 없음)
     */
    private val ACQUIRE_SCRIPT = RedisScript.of<String>(
      """
      local runningCount = redis.call('SCARD', KEYS[1])
      if runningCount >= tonumber(ARGV[1]) then
        return nil
      end
      redis.call('SADD', KEYS[1], ARGV[2])
      redis.call('EXPIRE', KEYS[1], ARGV[3])
      return 'OK'
      """.trimIndent(),
      String::class.java
    )
  }

  /**
   * 활성 Job 집합 Redis 키 생성
   */
  private fun activeJobsKey(accountId: Long): String =
    "$KEY_PREFIX:$accountId"

  /**
   * 슬롯 확보 시도 (비차단)
   *
   * 슬롯이 있으면 Job ID를 활성 집합에 추가하고 true 반환.
   * 슬롯이 없으면 false 반환.
   *
   * @param accountId 계정 ID
   * @param jobId 등록할 Job ID
   * @return 슬롯 확보 성공 여부
   */
  fun tryAcquire(accountId: Long, jobId: String): Boolean {
    val key = activeJobsKey(accountId)
    val maxConcurrency = properties.maxConcurrentJobsPerAccount

    val result: String? = redisTemplate.execute(
      ACQUIRE_SCRIPT,
      listOf(key),
      maxConcurrency.toString(),
      jobId,
      TTL_SECONDS.toString()
    )

    val acquired = result == "OK"
    if (acquired) {
      kLogger.debug {
        "슬롯 확보 성공 - accountId: $accountId, jobId: $jobId, max: $maxConcurrency"
      }
    } else {
      kLogger.info {
        "슬롯 확보 실패 (동시성 제한) - accountId: $accountId, jobId: $jobId, max: $maxConcurrency"
      }
    }

    return acquired
  }

  /**
   * 슬롯 반환
   *
   * Job 완료/실패 시 반드시 호출하여 슬롯을 반환해야 한다.
   * try-finally 블록에서 호출하는 것을 권장.
   *
   * @param accountId 계정 ID
   * @param jobId 반환할 Job ID
   */
  fun release(accountId: Long, jobId: String) {
    val key = activeJobsKey(accountId)
    val removed = redisTemplate.opsForSet().remove(key, jobId)
    kLogger.debug { "슬롯 반환 - accountId: $accountId, jobId: $jobId, removed: $removed" }
  }

  /**
   * 현재 활성 Job 수 조회
   *
   * @param accountId 계정 ID
   * @return 활성 Job 수
   */
  fun getActiveCount(accountId: Long): Long {
    val key = activeJobsKey(accountId)
    return redisTemplate.opsForSet().size(key) ?: 0
  }

  /**
   * 활성 Job ID 목록 조회
   *
   * @param accountId 계정 ID
   * @return 활성 Job ID 집합
   */
  fun getActiveJobIds(accountId: Long): Set<String> {
    val key = activeJobsKey(accountId)
    return redisTemplate.opsForSet().members(key) ?: emptySet()
  }
}
