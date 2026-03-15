package com.jongmin.ai.core.platform.component.monitor

import com.jongmin.ai.core.AiCachingDetectionLogRepository
import com.jongmin.ai.core.CachingType
import com.jongmin.ai.core.DetectionStatus
import com.jongmin.ai.core.platform.dto.CacheUsageInfo
import com.jongmin.ai.core.platform.entity.AiCachingDetectionLog
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * 예상치 못한 캐싱 감지기
 *
 * CachingType이 NONE으로 설정된 프로바이더에서
 * 캐싱이 발생했을 때 이를 감지하고 기록합니다.
 *
 * 주요 기능:
 * - 예상치 못한 캐싱 활동 감지
 * - AiCachingDetectionLog 엔티티에 기록
 * - (향후) Slack 알림 발송
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
@Component
class UnexpectedCachingDetector(
  private val cachingDetectionLogRepository: AiCachingDetectionLogRepository
) {

  private val kLogger = KotlinLogging.logger {}

  private val jsonMapper: JsonMapper = JsonMapper.builder().build()

  /**
   * 예상치 못한 캐싱 감지 및 기록
   *
   * CachingType.NONE인 프로바이더에서 캐싱 활동이 발생하면
   * DB에 기록하고 경고 로그를 출력합니다.
   *
   * @param usage 캐시 사용량 정보
   * @param expectedCachingType 예상된 캐싱 타입
   * @param rawUsage 원본 API 응답
   * @return 감지 여부 (true: 예상치 못한 캐싱 발생)
   */
  fun detectAndRecord(
    usage: CacheUsageInfo,
    expectedCachingType: CachingType,
    rawUsage: Map<String, Any>?
  ): Boolean {
    // 캐싱 활동이 없거나, 캐싱이 예상되는 경우 패스
    if (!usage.hasCacheActivity()) {
      return false
    }

    // NONE 또는 UNKNOWN에서 캐싱 발생 시 감지
    if (expectedCachingType != CachingType.NONE && expectedCachingType != CachingType.UNKNOWN) {
      // 정상적인 캐싱 (예상된 캐싱)
      return false
    }

    // 예상치 못한 캐싱 발생!
    kLogger.warn {
      "[캐싱 감지] 예상치 못한 캐싱 활동 감지! " +
          "provider: ${usage.provider}, model: ${usage.model}, " +
          "expectedCachingType: $expectedCachingType, " +
          "cachedTokens: ${usage.cachedTokens}, " +
          "cacheCreationTokens: ${usage.cacheCreationTokens}"
    }

    // 비동기로 DB 저장
    recordDetectionAsync(usage, rawUsage)

    return true
  }

  /**
   * 감지 로그 비동기 저장
   */
  @Async
  fun recordDetectionAsync(usage: CacheUsageInfo, rawUsage: Map<String, Any>?) {
    try {
      val rawResponseJson = rawUsage?.let {
        try {
          jsonMapper.writeValueAsString(it)
        } catch (e: Exception) {
          kLogger.warn(e) { "[캐싱 감지] rawUsage JSON 변환 실패" }
          "{\"error\": \"JSON 변환 실패\"}"
        }
      } ?: "{}"

      val log = AiCachingDetectionLog(
        provider = usage.provider,
        model = usage.model,
        cachedTokens = usage.cachedTokens,
        cacheCreationTokens = usage.cacheCreationTokens,
        rawResponse = rawResponseJson,
        status = DetectionStatus.PENDING
      )

      cachingDetectionLogRepository.save(log)

      kLogger.info {
        "[캐싱 감지] 로그 저장 완료 - logId: ${log.id}, " +
            "provider: ${usage.provider}, model: ${usage.model}"
      }

      // TODO: 향후 Slack 알림 연동
      // slackAlertService?.sendUnexpectedCachingAlert(log)

    } catch (e: Exception) {
      kLogger.error(e) {
        "[캐싱 감지] 로그 저장 실패 - provider: ${usage.provider}, model: ${usage.model}"
      }
    }
  }

  /**
   * 직접 감지 기록 (수동 호출용)
   *
   * AiUsageCollector 외부에서 직접 감지를 기록할 때 사용합니다.
   *
   * @param provider 프로바이더명
   * @param model 모델명
   * @param cachedTokens 캐시된 토큰 수
   * @param cacheCreationTokens 캐시 생성 토큰 수
   * @param rawResponse 원본 응답 (JSON 문자열)
   * @return 저장된 로그
   */
  fun recordDirectly(
    provider: String,
    model: String,
    cachedTokens: Long,
    cacheCreationTokens: Long = 0,
    rawResponse: String = "{}"
  ): AiCachingDetectionLog {
    val log = AiCachingDetectionLog(
      provider = provider,
      model = model,
      cachedTokens = cachedTokens,
      cacheCreationTokens = cacheCreationTokens,
      rawResponse = rawResponse,
      status = DetectionStatus.PENDING
    )

    return cachingDetectionLogRepository.save(log).also {
      kLogger.info {
        "[캐싱 감지] 직접 기록 완료 - logId: ${it.id}, provider: $provider, model: $model"
      }
    }
  }
}
