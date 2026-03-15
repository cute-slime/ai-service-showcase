package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowFormat
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.jspring.data.entity.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer

/**
 * 멀티미디어 생성 워크플로우 엔티티
 *
 * 공급사(ComfyUI, Runway, Suno 등)별 실행 페이로드(JSON)와 변수 정의(JSON)를 저장한다.
 * providerId를 통해 multimedia_provider와 의존 관계를 가진다.
 */
@Entity
@Table(
  name = "multimedia_workflow",
  indexes = [
    Index(
      name = "unq_multimediaWorkflow_providerNameVersion",
      columnList = "providerId, mediaType, pipeline, name, version",
      unique = true
    ),
    Index(name = "idx_multimediaWorkflow_providerId", columnList = "providerId"),
    Index(name = "idx_multimediaWorkflow_mediaType", columnList = "mediaType"),
    Index(name = "idx_multimediaWorkflow_status", columnList = "status"),
    Index(
      name = "idx_multimediaWorkflow_providerMediaStatus",
      columnList = "providerId, mediaType, pipeline, status"
    ),
    Index(
      name = "idx_multimediaWorkflow_isDefault",
      columnList = "providerId, mediaType, pipeline, isDefault"
    ),
  ]
)
data class MultimediaWorkflow(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, comment = "FK: multimedia_provider.id")
  var providerId: Long,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "미디어 타입: IMAGE, VIDEO, BGM, OST, SFX")
  var mediaType: GenerationMediaType,

  @Column(length = 150, nullable = false, comment = "워크플로우 이름")
  var name: String,

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "동작 파이프라인: PROMPT_TO_MEDIA, MEDIA_TO_MEDIA")
  var pipeline: GenerationWorkflowPipeline = GenerationWorkflowPipeline.PROMPT_TO_MEDIA,

  @Column(columnDefinition = "TEXT", comment = "워크플로우 설명")
  var description: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, comment = "포맷: COMFYUI_API, JSON_TEMPLATE, PROVIDER_SPECIFIC")
  var format: GenerationWorkflowFormat = GenerationWorkflowFormat.JSON_TEMPLATE,

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "실행 페이로드 JSON")
  var payload: String = "{}",

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "치환 변수 메타데이터 JSON 배열")
  var variables: String = "[]",

  @Column(nullable = false, comment = "워크플로우 버전")
  var version: Int = 1,

  @Column(nullable = false, comment = "기본 워크플로우 여부")
  var isDefault: Boolean = false,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "상태: DRAFT, ACTIVE, DEPRECATED, ARCHIVED")
  var status: GenerationWorkflowStatus = GenerationWorkflowStatus.DRAFT,

  ) : BaseTimeEntity()
