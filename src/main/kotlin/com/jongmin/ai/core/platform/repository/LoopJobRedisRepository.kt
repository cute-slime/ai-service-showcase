package com.jongmin.ai.core.platform.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * Loop Job Redis Repository
 *
 * 분산 락과 실행 상태만 관리한다. (Job 영속 데이터는 DB)
 * Redis SETNX + TTL로 원자적 락 획득을 구현한다.
 *
 * ## Redis 키 구조
 * ```
 * loop:lock:{jobId}        → instanceId (Job 실행 락)
 * loop:lock:recovery       → instanceId (복구 프로세스 락)
 * loop:jobs:running        → Set<jobId> (실행 중 Job Set)
 * ```
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Repository
class LoopJobRedisRepository(
  private val redisTemplate: StringRedisTemplate,
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    /** 락 기본 TTL (초) */
    private const val LOCK_TTL_SECONDS = 60L

    /** 복구 락 TTL (초) - 복구 작업은 시간이 더 걸릴 수 있음 */
    private const val RECOVERY_LOCK_TTL_SECONDS = 300L

    /** 키 접두사 */
    private const val KEY_PREFIX = "loop"

    /**
     * 원자적 Job 실행 락 획득 Lua Script
     *
     * KEYS[1]: lockKey
     * ARGV[1]: instanceId
     * ARGV[2]: ttlSeconds
     *
     * @return "OK" (성공) 또는 nil (이미 락 보유 중)
     */
    private val ATOMIC_ACQUIRE_LOCK_SCRIPT = RedisScript.of<String>(
      """
            local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
            return result
            """.trimIndent(),
      String::class.java
    )

    /**
     * 락 소유자 확인 후 해제 Lua Script (안전한 락 해제)
     *
     * KEYS[1]: lockKey
     * ARGV[1]: expectedInstanceId
     *
     * @return 1 (해제 성공) 또는 0 (소유자 불일치)
     */
    private val ATOMIC_RELEASE_LOCK_SCRIPT = RedisScript.of<Long>(
      """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """.trimIndent(),
      Long::class.java
    )

    /**
     * 락 소유자 확인 후 TTL 갱신 Lua Script (Heartbeat)
     *
     * KEYS[1]: lockKey
     * ARGV[1]: expectedInstanceId
     * ARGV[2]: ttlSeconds
     *
     * @return 1 (갱신 성공) 또는 0 (소유자 불일치)
     */
    private val ATOMIC_RENEW_LOCK_SCRIPT = RedisScript.of<Long>(
      """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return 1
            else
                return 0
            end
            """.trimIndent(),
      Long::class.java
    )
  }

  // ========== 키 생성 ==========

  private fun jobLockKey(jobId: String): String = "$KEY_PREFIX:lock:$jobId"
  private fun recoveryLockKey(): String = "$KEY_PREFIX:lock:recovery"
  private fun runningSetKey(): String = "$KEY_PREFIX:jobs:running"

  // ========== Job 실행 락 ==========

  /**
   * Job 실행 락 획득
   *
   * 동일 Job에 대해 여러 인스턴스가 동시에 실행하는 것을 방지한다.
   * SETNX + TTL을 원자적으로 실행하여 락 획득.
   *
   * @param jobId Job ID
   * @param instanceId 현재 인스턴스 ID
   * @param ttlSeconds 락 TTL (기본: 60초)
   * @return 락 획득 성공 여부
   */
  fun tryAcquireJobLock(jobId: String, instanceId: String, ttlSeconds: Long = LOCK_TTL_SECONDS): Boolean {
    val lockKey = jobLockKey(jobId)

    val result: String? = redisTemplate.execute(
      ATOMIC_ACQUIRE_LOCK_SCRIPT,
      listOf(lockKey),
      instanceId,
      ttlSeconds.toString()
    )

    // Redis SET NX 명령어는 성공 시 "OK" 반환
    val acquired = result == "OK"
    if (acquired) {
      kLogger.debug { "Job 락 획득 성공 - jobId: $jobId, instanceId: $instanceId" }
    } else {
      kLogger.debug { "Job 락 획득 실패 (이미 락 보유 중) - jobId: $jobId" }
    }

    return acquired
  }

  /**
   * Job 실행 락 해제
   *
   * 락 소유자만 해제할 수 있음 (안전한 락 해제).
   *
   * @param jobId Job ID
   * @param instanceId 현재 인스턴스 ID
   * @return 해제 성공 여부
   */
  fun releaseJobLock(jobId: String, instanceId: String): Boolean {
    val lockKey = jobLockKey(jobId)

    val result = redisTemplate.execute(
      ATOMIC_RELEASE_LOCK_SCRIPT,
      listOf(lockKey),
      instanceId
    ) ?: 0L

    val released = result > 0
    if (released) {
      kLogger.debug { "Job 락 해제 성공 - jobId: $jobId, instanceId: $instanceId" }
    } else {
      kLogger.warn { "Job 락 해제 실패 (소유자 불일치) - jobId: $jobId, instanceId: $instanceId" }
    }

    return released
  }

  /**
   * Job 실행 락 강제 해제 (관리자용)
   *
   * 소유자 확인 없이 락 해제. 좀비 락 정리에 사용.
   *
   * @param jobId Job ID
   */
  fun forceReleaseJobLock(jobId: String) {
    val lockKey = jobLockKey(jobId)
    redisTemplate.delete(lockKey)
    kLogger.warn { "Job 락 강제 해제 - jobId: $jobId" }
  }

  /**
   * Job 실행 락 갱신 (Heartbeat)
   *
   * 락 소유자인 경우에만 TTL 갱신.
   *
   * @param jobId Job ID
   * @param instanceId 현재 인스턴스 ID
   * @param ttlSeconds 새 TTL (기본: 60초)
   * @return 갱신 성공 여부
   */
  fun renewJobLock(jobId: String, instanceId: String, ttlSeconds: Long = LOCK_TTL_SECONDS): Boolean {
    val lockKey = jobLockKey(jobId)

    val result = redisTemplate.execute(
      ATOMIC_RENEW_LOCK_SCRIPT,
      listOf(lockKey),
      instanceId,
      ttlSeconds.toString()
    ) ?: 0L

    val renewed = result > 0
    if (!renewed) {
      kLogger.warn { "Job 락 갱신 실패 (소유자 불일치) - jobId: $jobId, instanceId: $instanceId" }
    }

    return renewed
  }

  /**
   * Job 락 소유자 조회
   *
   * @param jobId Job ID
   * @return 락 소유 인스턴스 ID (락 없으면 null)
   */
  fun getJobLockOwner(jobId: String): String? {
    val lockKey = jobLockKey(jobId)
    return redisTemplate.opsForValue().get(lockKey)
  }

  // ========== 복구 프로세스 락 ==========

  /**
   * 복구 프로세스 락 획득
   *
   * 여러 인스턴스가 동시에 복구를 수행하는 것을 방지.
   *
   * @param instanceId 현재 인스턴스 ID
   * @return 락 획득 성공 여부
   */
  fun tryAcquireRecoveryLock(instanceId: String): Boolean {
    val lockKey = recoveryLockKey()

    val result: String? = redisTemplate.execute(
      ATOMIC_ACQUIRE_LOCK_SCRIPT,
      listOf(lockKey),
      instanceId,
      RECOVERY_LOCK_TTL_SECONDS.toString()
    )

    // Redis SET NX 명령어는 성공 시 "OK" 반환
    val acquired = result == "OK"
    if (acquired) {
      kLogger.info { "복구 락 획득 성공 - instanceId: $instanceId" }
    } else {
      kLogger.debug { "복구 락 획득 실패 (다른 인스턴스에서 복구 중) - instanceId: $instanceId" }
    }

    return acquired
  }

  /**
   * 복구 프로세스 락 해제
   *
   * @param instanceId 현재 인스턴스 ID
   * @return 해제 성공 여부
   */
  fun releaseRecoveryLock(instanceId: String): Boolean {
    val lockKey = recoveryLockKey()

    val result = redisTemplate.execute(
      ATOMIC_RELEASE_LOCK_SCRIPT,
      listOf(lockKey),
      instanceId
    ) ?: 0L

    val released = result > 0
    if (released) {
      kLogger.info { "복구 락 해제 성공 - instanceId: $instanceId" }
    }

    return released
  }

  // ========== 실행 중 Job Set ==========

  /**
   * 실행 중 Set에 Job 추가
   *
   * 빠른 조회를 위해 Redis Set 유지 (DB와 동기화).
   *
   * @param jobId Job ID
   */
  fun addToRunningSet(jobId: String) {
    redisTemplate.opsForSet().add(runningSetKey(), jobId)
    kLogger.debug { "실행 중 Set에 Job 추가 - jobId: $jobId" }
  }

  /**
   * 실행 중 Set에서 Job 제거
   *
   * @param jobId Job ID
   */
  fun removeFromRunningSet(jobId: String) {
    redisTemplate.opsForSet().remove(runningSetKey(), jobId)
    kLogger.debug { "실행 중 Set에서 Job 제거 - jobId: $jobId" }
  }

  /**
   * 실행 중 Job ID 목록 조회
   *
   * @return 실행 중인 Job ID Set
   */
  fun getRunningJobIds(): Set<String> {
    return redisTemplate.opsForSet().members(runningSetKey()) ?: emptySet()
  }

  /**
   * 실행 중 Job 수 조회
   *
   * @return 실행 중인 Job 수
   */
  fun getRunningCount(): Long {
    return redisTemplate.opsForSet().size(runningSetKey()) ?: 0
  }

  /**
   * Job이 실행 중인지 확인
   *
   * @param jobId Job ID
   * @return 실행 중 여부
   */
  fun isRunning(jobId: String): Boolean {
    return redisTemplate.opsForSet().isMember(runningSetKey(), jobId) == true
  }

  /**
   * 실행 중 Set 초기화 (테스트/복구용)
   */
  fun clearRunningSet() {
    redisTemplate.delete(runningSetKey())
    kLogger.warn { "실행 중 Set 초기화됨" }
  }

  // ========== Job 제어 플래그 ==========

  private fun cancelFlagKey(jobId: String): String = "$KEY_PREFIX:cancel:$jobId"
  private fun pauseFlagKey(jobId: String): String = "$KEY_PREFIX:pause:$jobId"

  /**
   * 취소 플래그 설정
   */
  fun setCancelFlag(jobId: String) {
    redisTemplate.opsForValue().set(cancelFlagKey(jobId), "1", 1, TimeUnit.HOURS)
    kLogger.debug { "취소 플래그 설정 - jobId: $jobId" }
  }

  /**
   * 취소 플래그 확인
   */
  fun isCancelled(jobId: String): Boolean {
    return redisTemplate.hasKey(cancelFlagKey(jobId))
  }

  /**
   * 취소 플래그 제거
   */
  fun clearCancelFlag(jobId: String) {
    redisTemplate.delete(cancelFlagKey(jobId))
  }

  /**
   * 일시정지 플래그 설정
   */
  fun setPauseFlag(jobId: String) {
    redisTemplate.opsForValue().set(pauseFlagKey(jobId), "1", 1, TimeUnit.HOURS)
    kLogger.debug { "일시정지 플래그 설정 - jobId: $jobId" }
  }

  /**
   * 일시정지 플래그 확인
   */
  fun isPaused(jobId: String): Boolean {
    return redisTemplate.hasKey(pauseFlagKey(jobId))
  }

  /**
   * 일시정지 플래그 제거
   */
  fun clearPauseFlag(jobId: String) {
    redisTemplate.delete(pauseFlagKey(jobId))
  }

  /**
   * 모든 제어 플래그 제거 (Job 종료/재개 시)
   */
  fun clearAllFlags(jobId: String) {
    redisTemplate.delete(cancelFlagKey(jobId))
    redisTemplate.delete(pauseFlagKey(jobId))
  }
}
