package com.jongmin.ai.generation.bo.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 미디어 생성 BO API 설정
 *
 * application.yaml의 `app.media-generation` 프로퍼티를 바인딩한다.
 *
 * ### 설정 예시 (application.yaml):
 * ```yaml
 * app:
 *   media-generation:
 *     max-concurrent-jobs-per-account: 3
 *     default-timeout-minutes: 15
 *     heartbeat-interval-seconds: 30
 * ```
 */
@ConfigurationProperties(prefix = "app.media-generation")
data class MediaGenerationProperties(
  /** 계정당 최대 동시 실행 Job 수 */
  val maxConcurrentJobsPerAccount: Int = 3,

  /** 기본 작업 타임아웃 (분) */
  val defaultTimeoutMinutes: Long = 15,

  /** 하트비트 전송 간격 (초) */
  val heartbeatIntervalSeconds: Long = 30,
)
