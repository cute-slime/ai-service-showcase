package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeEntity
import com.jongmin.ai.core.GenerationProviderStatus
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer

/**
 * 미디어 생성 프로바이더 엔티티
 *
 * 이미지, 영상, 음악 등 AI 미디어 생성에 사용되는 프로바이더(ComfyUI, NovelAI, Midjourney 등)를 관리합니다.
 *
 * @author Claude Code
 * @since 2026.01.10
 */
@Entity
@Table(
  name = "multimedia_provider",
  indexes = [
    Index(name = "idx_multimediaProvider_code", columnList = "code"),
    Index(name = "idx_multimediaProvider_status", columnList = "status"),
    Index(name = "idx_multimediaProvider_sortOrder", columnList = "sortOrder"),
  ]
)
data class MultimediaProvider(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(length = 50, nullable = false, comment = "프로바이더 코드 (COMFYUI, NOVELAI 등)")
  var code: String,

  @Column(length = 100, nullable = false, comment = "프로바이더 표시명")
  var name: String,

  @Column(columnDefinition = "TEXT", comment = "프로바이더 설명")
  var description: String? = null,

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false, comment = "상태: ACTIVE, INACTIVE, MAINTENANCE")
  var status: GenerationProviderStatus = GenerationProviderStatus.ACTIVE,

  @Column(length = 500, comment = "로고 이미지 URL")
  var logoUrl: String? = null,

  @Column(length = 500, comment = "공식 웹사이트 URL")
  var websiteUrl: String? = null,

  @Column(nullable = false, comment = "정렬 순서")
  var sortOrder: Int = 0,

  // ========== 지원 미디어 타입 ==========

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "지원 미디어 타입 [\"IMAGE\", \"VIDEO\", \"BGM\", \"OST\", \"SFX\"]")
  var supportedMediaTypes: String = "[\"IMAGE\"]",

  ) : BaseTimeEntity()
