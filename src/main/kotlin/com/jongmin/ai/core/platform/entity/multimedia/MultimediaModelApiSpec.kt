package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationResponseType
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer

/**
 * 모델별 API 호출 규격 엔티티
 *
 * 각 모델의 API 엔드포인트, 요청/응답 규격, 파라미터 매핑 등을 관리합니다.
 * 프로바이더마다 API 형식이 다르므로 이 설정을 통해 통일된 인터페이스를 제공합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_model_api_spec",
  indexes = [
    Index(name = "unq_multimediaModelApiSpec_modelMedia", columnList = "modelId, mediaType", unique = true),
    Index(name = "idx_multimediaModelApiSpec_modelId", columnList = "modelId"),
  ]
)
data class MultimediaModelApiSpec(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider_model.id")
  val modelId: Long,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "미디어 타입: IMAGE, VIDEO, BGM 등")
  var mediaType: GenerationMediaType,

  // ========== API 엔드포인트 ==========

  @Column(length = 200, nullable = false, comment = "API 경로 (/v1/generate 등)")
  var endpointPath: String,

  @Column(length = 10, nullable = false, comment = "HTTP 메서드")
  var httpMethod: String = "POST",

  @Column(length = 100, nullable = false, comment = "Content-Type")
  var contentType: String = "application/json",

  // ========== 요청 규격 ==========

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "API 요청 템플릿")
  var requestTemplate: String = "{}",

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "내부필드 → API필드 매핑")
  var paramMapping: String = "{}",

  // ========== 응답 규격 ==========

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "응답 타입: SYNC, ASYNC_POLLING, ASYNC_WEBHOOK, SSE")
  var responseType: GenerationResponseType = GenerationResponseType.SYNC,

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", comment = "API응답 → 내부필드 매핑")
  var responseMapping: String? = null,

  // ========== 비동기 처리 설정 ==========

  @Column(length = 200, comment = "폴링 엔드포인트 (ASYNC_POLLING용)")
  var pollingEndpoint: String? = null,

  @Column(comment = "폴링 간격 (ms)")
  var pollingIntervalMs: Int = 1000,

  @Column(length = 200, comment = "상태 필드 JSON Path")
  var statusFieldPath: String? = null,

  @Column(length = 200, comment = "결과 필드 JSON Path")
  var resultFieldPath: String? = null,

  ) : BaseTimeEntity()
