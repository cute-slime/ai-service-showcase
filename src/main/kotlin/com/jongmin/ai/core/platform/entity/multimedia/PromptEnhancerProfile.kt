package com.jongmin.ai.core.platform.entity.multimedia

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import jakarta.persistence.*
import org.hibernate.annotations.ColumnTransformer

/**
 * 프롬프트 인첸터 프로필 엔티티
 *
 * 작품/세계관 단위로 프롬프트 인첸트 시 사용할 고정 스타일과 분위기 키워드를 저장한다.
 * providerCode를 기준으로 분리하여 향후 타 프로바이더 확장을 수용한다.
 */
@Entity
@Table(
  name = "prompt_enhancer_profile",
  indexes = [
    Index(name = "idx_promptEnhancerProfile_providerCode", columnList = "providerCode"),
    Index(name = "idx_promptEnhancerProfile_status", columnList = "status"),
    Index(name = "idx_promptEnhancerProfile_isDefault", columnList = "providerCode, isDefault"),
    Index(
      name = "idx_promptEnhancerProfile_priority",
      columnList = "providerCode, priority, status"
    ),
  ]
)
data class PromptEnhancerProfile(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(length = 50, nullable = false, comment = "프로바이더 코드 (예: NOVELAI)")
  var providerCode: String,

  @Column(length = 150, nullable = false, comment = "프로필명")
  var name: String,

  @Column(columnDefinition = "TEXT", comment = "프로필 설명")
  var description: String? = null,

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "프로필 타겟 매칭 규칙 JSON")
  var targetRule: String = "{}",

  @Column(nullable = false, comment = "매칭 우선순위(낮을수록 우선)")
  var priority: Int = 100,

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "작가 태그 JSON 배열")
  var preferredArtistTags: String = "[]",

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "스타일 키워드 JSON 배열")
  var styleKeywords: String = "[]",

  @ColumnTransformer(write = "?::json")
  @Column(columnDefinition = "JSON", nullable = false, comment = "분위기 키워드 JSON 배열")
  var vibeKeywords: String = "[]",

  @Column(columnDefinition = "TEXT", comment = "고정 스타일 블록")
  var styleBlock: String? = null,

  @Column(columnDefinition = "TEXT", comment = "캐릭터 고정 블록")
  var characterBlock: String? = null,

  @Column(columnDefinition = "TEXT", comment = "배경 고정 블록")
  var backgroundBlock: String? = null,

  @Column(length = 40, comment = "고정 샘플러")
  var sampler: String? = null,

  @Column(comment = "고정 스텝")
  var steps: Int? = null,

  @Column(comment = "고정 CFG")
  var cfgScale: Double? = null,

  @Column(comment = "고정 해상도 width")
  var width: Int? = null,

  @Column(comment = "고정 해상도 height")
  var height: Int? = null,

  @Column(comment = "고정 시드")
  var seed: Long? = null,

  @Column(nullable = false, comment = "기본 프로필 여부")
  var isDefault: Boolean = false,
) : BaseTimeAndStatusEntity()
