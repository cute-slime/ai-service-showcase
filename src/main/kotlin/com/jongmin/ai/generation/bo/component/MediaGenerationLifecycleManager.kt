package com.jongmin.ai.generation.bo.component

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationListener
import org.springframework.context.Lifecycle
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.Ordered
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 미디어 생성 라이프사이클 관리자
 *
 * 애플리케이션 종료 시 활성 Job 상태를 점검하여
 * RateLimiter 카운터 누수 징후를 기록한다.
 *
 * ### 점검 대상:
 * - `media_gen:active_jobs:*` Redis Set 키의 잔존 Job 상태
 *
 * ### 안전장치 중첩:
 * 1. Graceful Shutdown 시 이 컴포넌트가 활성 Job 잔존 상태 점검
 * 2. Redis Key TTL 1시간 (비정상 종료 시 자동 정리)
 * 3. DTE 타임아웃 → TaskHandler.execute() 예외 → finally에서 release()
 */
@Component
class MediaGenerationLifecycleManager(
  private val redisTemplate: StringRedisTemplate,
) : ApplicationListener<ContextClosedEvent>, Ordered {
  private val kLogger = KotlinLogging.logger {}
  private val shutdownHandled = AtomicBoolean(false)

  companion object {
    private const val KEY_PATTERN = "media_gen:active_jobs:*"
  }

  override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

  /**
   * 컨텍스트 종료 직전 활성 Job 상태 점검
   *
   * `@PreDestroy` 시점에는 RedisConnectionFactory가 이미 stop된 뒤일 수 있으므로
   * ContextClosedEvent 단계에서 먼저 상태를 확인한다.
   */
  override fun onApplicationEvent(event: ContextClosedEvent) {
    if (!shutdownHandled.compareAndSet(false, true)) {
      return
    }

    if (!isRedisAvailable()) {
      kLogger.info { "[MediaGeneration] Shutdown - Redis 연결이 이미 종료되어 활성 Job 점검을 건너뜀" }
      return
    }

    try {
      val keys = redisTemplate.keys(KEY_PATTERN)
      if (keys.isNullOrEmpty()) {
        kLogger.info { "[MediaGeneration] Shutdown - 활성 Job 없음, 추가 점검 불필요" }
        return
      }

      var totalActiveJobs = 0L
      keys.forEach { key ->
        val jobCount = redisTemplate.opsForSet().size(key) ?: 0
        if (jobCount > 0) {
          val jobIds = redisTemplate.opsForSet().members(key)
          totalActiveJobs += jobCount
          kLogger.warn {
            "[MediaGeneration] Shutdown - 활성 Job 잔존: key=$key, count=$jobCount, jobs=$jobIds"
          }
        }
      }

      if (totalActiveJobs > 0) {
        kLogger.warn {
          "[MediaGeneration] Shutdown - 총 ${totalActiveJobs}개 활성 Job 잔존. " +
              "Redis TTL(1h)에 의해 자동 정리됩니다."
        }
      } else {
        kLogger.info { "[MediaGeneration] Shutdown - 모든 활성 Job 정상 정리 완료" }
      }
    } catch (e: Exception) {
      kLogger.error(e) { "[MediaGeneration] Shutdown 활성 Job 점검 중 오류 발생" }
    }
  }

  private fun isRedisAvailable(): Boolean {
    val lifecycle = redisTemplate.connectionFactory as? Lifecycle ?: return true
    return lifecycle.isRunning
  }
}
