package com.jongmin.ai.multiagent.skill.entity

import com.jongmin.jspring.data.entity.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 에이전트-스킬 매핑 Entity
 * 에이전트가 사용할 수 있는 스킬 할당 관리
 *
 * 관계:
 * - AiAgent (1) : AgentSkillMapping (N)
 * - SkillDefinition (1) : AgentSkillMapping (N)
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_agent_skill_mapping_agent_id", columnList = "agentId"),
    Index(name = "idx_agent_skill_mapping_skill_id", columnList = "skillDefinitionId"),
    Index(name = "idx_agent_skill_mapping_account_id", columnList = "accountId"),
    Index(name = "idx_agent_skill_mapping_enabled", columnList = "enabled"),
  ],
  uniqueConstraints = [
    UniqueConstraint(
      name = "unq_agent_skill_mapping",
      columnNames = ["agentId", "skillDefinitionId"]
    )
  ]
)
data class AgentSkillMappingEntity(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,

  @Column(nullable = false, updatable = false, comment = "계정 ID")
  val accountId: Long,

  @Column(nullable = false, updatable = false, comment = "에이전트 ID (AiAgent.id)")
  val agentId: Long,

  @Column(nullable = false, updatable = false, comment = "스킬 정의 ID (SkillDefinitionEntity.id)")
  val skillDefinitionId: Long,

  @Column(nullable = false, comment = "스킬 이름 (조회 편의용, SkillDefinition.name 복사)")
  var skillName: String,

  @Column(nullable = false, comment = "우선순위 (높을수록 먼저 검토)")
  var priority: Int = 0,

  @Column(nullable = false, comment = "활성화 여부")
  var enabled: Boolean = true,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "에이전트별 트리거 설정 오버라이드 (JSON)")
  var overrideConfig: Map<String, Any>? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB", comment = "이 에이전트에서 사용할 별칭 목록")
  var aliases: List<String>? = null,

  @Column(columnDefinition = "TEXT", comment = "매핑에 대한 메모")
  var memo: String? = null,
) : BaseTimeEntity() {

  /**
   * SkillSlot 모델로 변환
   */
  fun toSkillSlot(): com.jongmin.ai.multiagent.model.SkillSlot {
    return com.jongmin.ai.multiagent.model.SkillSlot(
      skillId = skillName,  // skillName을 skillId로 사용 (SkillDefinition.name)
      priority = priority,
      enabled = enabled,
      overrideConfig = overrideConfig?.let { parseOverrideConfig(it) },
      aliases = aliases,
    )
  }

  /**
   * overrideConfig를 SkillTriggerConfig로 파싱
   */
  @Suppress("UNCHECKED_CAST")
  private fun parseOverrideConfig(config: Map<String, Any>): com.jongmin.ai.multiagent.model.SkillTriggerConfig {
    return com.jongmin.ai.multiagent.model.SkillTriggerConfig(
      triggerKeywords = config["triggerKeywords"] as? List<String> ?: emptyList(),
      triggerPatterns = config["triggerPatterns"] as? List<String>,
      triggerMode = (config["triggerMode"] as? Int)?.let {
        com.jongmin.ai.multiagent.model.SkillTriggerMode.getType(it)
      } ?: com.jongmin.ai.multiagent.model.SkillTriggerMode.ON_DEMAND,
      priority = config["priority"] as? Int ?: 0,
    )
  }
}
