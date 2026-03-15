package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.ai.core.GenerationAuthType
import jakarta.persistence.*

/**
 * 프로바이더 API 연동 설정 엔티티
 *
 * 프로바이더별 API 인증, 엔드포인트, Rate Limit 등의 설정을 관리합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_provider_api_config",
  indexes = [
    Index(name = "unq_multimediaProviderApiConfig_providerId", columnList = "providerId", unique = true),
  ]
)
data class MultimediaProviderApiConfig(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider.id")
  val providerId: Long,

  // ========== 인증 설정 ==========

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "인증 타입: API_KEY, BEARER, OAUTH2, CUSTOM")
  var authType: GenerationAuthType = GenerationAuthType.API_KEY,

  @Column(length = 100, nullable = false, comment = "인증 헤더명 (Authorization, X-Api-Key 등)")
  var authHeaderName: String = "Authorization",

  @Column(length = 50, comment = "인증 값 접두사 (Bearer, Key 등)")
  var authValuePrefix: String? = null,

  // ========== 엔드포인트 설정 ==========

  @Column(length = 500, nullable = false, comment = "API 기본 URL")
  var baseUrl: String,

  // ========== Rate Limit 설정 ==========

  @Column(comment = "분당 요청 제한")
  var rateLimitPerMinute: Int? = null,

  @Column(comment = "일일 요청 제한")
  var rateLimitPerDay: Int? = null,

  @Column(comment = "동시 요청 제한")
  var concurrentLimit: Int? = null,

  // ========== 타임아웃 설정 ==========

  @Column(nullable = false, comment = "연결 타임아웃 (ms)")
  var connectTimeoutMs: Int = 5000,

  @Column(nullable = false, comment = "읽기 타임아웃 (ms)")
  var readTimeoutMs: Int = 60000,

  @Column(columnDefinition = "TEXT", comment = "프로바이더별 확장 설정 JSON")
  var configJson: String? = null,

  ) : BaseTimeEntity()
