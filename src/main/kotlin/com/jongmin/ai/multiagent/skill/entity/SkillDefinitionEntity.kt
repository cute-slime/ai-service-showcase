package com.jongmin.ai.multiagent.skill.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.multiagent.skill.model.LlmTriggerConfig
import com.jongmin.ai.multiagent.skill.model.NetworkPolicy
import com.jongmin.ai.multiagent.skill.model.SkillAsset
import com.jongmin.ai.multiagent.skill.model.SkillCompatibility
import com.jongmin.ai.multiagent.skill.model.SkillDefinition
import com.jongmin.ai.multiagent.skill.model.SkillFrontmatter
import com.jongmin.ai.multiagent.skill.model.SkillReference
import com.jongmin.ai.multiagent.skill.model.SkillScript
import com.jongmin.ai.multiagent.skill.model.TriggerStrategy
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 스킬 정의 Entity
 * Agent Skills 스펙 호환 + DB 영속화
 *
 * 하나의 스킬 정의는 다음을 포함:
 * - Frontmatter 필드들 (name, description, license 등)
 * - Body (마크다운 instructions)
 * - scripts, references, assets (Map 형태로 JSON 저장)
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_skill_definition_accountId", columnList = "accountId"),
    Index(name = "idx_skill_definition_name", columnList = "name"),
    Index(name = "idx_skill_definition_status", columnList = "status"),
  ],
  uniqueConstraints = [
    UniqueConstraint(
      name = "uk_skill_definition_account_name",
      columnNames = ["accountId", "name"]
    )
  ]
)
data class SkillDefinitionEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false)
  val accountId: Long,

  @Column(nullable = false, updatable = false)
  val ownerId: Long,

  // ======== Frontmatter 필수 필드 ========

  // 스킬 식별자 (1-64자, lowercase + hyphen)
  @Column(length = 64, nullable = false)
  var name: String,

  // 스킬 설명 (1-1024자)
  @Column(length = 1024, nullable = false)
  var description: String,

  // ======== Frontmatter 선택 필드 ========

  // 라이선스 (전체 텍스트 내용)
  @Column(columnDefinition = "TEXT")
  var license: String? = null,

  // 호환성 정보 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var compatibility: SkillCompatibility? = null,

  // 추가 메타데이터 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var metadata: Map<String, Any> = emptyMap(),

  // 허용된 도구 목록 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var allowedTools: List<String>? = null,

  // ======== Body (Instructions) ========

  // SKILL.md 본문 (마크다운)
  @Column(columnDefinition = "TEXT")
  var body: String = "",

  // ======== 지원 폴더 구조 ========

  // scripts/ 폴더 내용 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var scripts: Map<String, SkillScript> = emptyMap(),

  // references/ 폴더 내용 (Hibernate 7 JSON 타입 사용)
  // 주의: 'references'는 SQL 예약어이므로 컬럼명 변경 필수
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "skill_references", columnDefinition = "JSONB", nullable = false)
  var references: Map<String, SkillReference> = emptyMap(),

  // assets/ 폴더 내용 (Hibernate 7 JSON 타입 사용)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", nullable = false)
  var assets: Map<String, SkillAsset> = emptyMap(),

  // ======== 샌드박스 실행 설정 ========

  /**
   * 네트워크 접근 정책
   * 스크립트 실행 시 외부 네트워크 접근 수준 설정
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  var networkPolicy: NetworkPolicy = NetworkPolicy.ALLOW_SPECIFIC,

  /**
   * 허용된 도메인 목록
   * networkPolicy가 ALLOW_SPECIFIC일 때 접근 가능한 도메인 리스트
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var allowedDomains: List<String>? = null,

  // ======== 트리거 전략 설정 ========

  /**
   * 트리거 전략
   * 스킬 실행 여부 판단 방식 (RULE_BASED, LLM_BASED, HYBRID)
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  var triggerStrategy: TriggerStrategy = TriggerStrategy.RULE_BASED,

  /**
   * LLM 트리거 설정
   * triggerStrategy가 LLM_BASED 또는 HYBRID일 때 사용
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var llmTriggerConfig: LlmTriggerConfig? = null,

) : BaseTimeAndStatusEntity() {

  /**
   * SkillDefinition 모델로 변환
   */
  fun toSkillDefinition(): SkillDefinition {
    return SkillDefinition(
      frontmatter = SkillFrontmatter(
        name = name,
        description = description,
        license = license,
        compatibility = compatibility,
        metadata = metadata,
        allowedTools = allowedTools,
      ),
      body = body,
      scripts = scripts,
      references = references,
      assets = assets,
    )
  }

  /**
   * SkillFrontmatter만 추출
   */
  fun toFrontmatter(): SkillFrontmatter {
    return SkillFrontmatter(
      name = name,
      description = description,
      license = license,
      compatibility = compatibility,
      metadata = metadata,
      allowedTools = allowedTools,
    )
  }

  companion object {
    /**
     * SkillDefinition에서 Entity 생성
     *
     * @param accountId 계정 ID
     * @param ownerId 소유자 ID
     * @param definition 스킬 정의
     * @param triggerStrategy 트리거 전략 (기본값: RULE_BASED)
     * @param llmTriggerConfig LLM 트리거 설정 (선택)
     */
    fun from(
      accountId: Long,
      ownerId: Long,
      definition: SkillDefinition,
      triggerStrategy: TriggerStrategy = TriggerStrategy.RULE_BASED,
      llmTriggerConfig: LlmTriggerConfig? = null,
    ): SkillDefinitionEntity {
      return SkillDefinitionEntity(
        accountId = accountId,
        ownerId = ownerId,
        name = definition.frontmatter.name,
        description = definition.frontmatter.description,
        license = definition.frontmatter.license,
        compatibility = definition.frontmatter.compatibility,
        metadata = definition.frontmatter.metadata,
        allowedTools = definition.frontmatter.allowedTools,
        body = definition.body,
        scripts = definition.scripts,
        references = definition.references,
        assets = definition.assets,
        triggerStrategy = triggerStrategy,
        llmTriggerConfig = llmTriggerConfig,
      )
    }
  }
}

