package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.core.AiAgentType
import com.jongmin.ai.core.AiAgentTypeConverter
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.ZonedDateTime

/**
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "unq_aiAgent_name", columnList = "name", unique = true),
    Index(name = "idx_aiAgent_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiAgent_status", columnList = "status"),
    Index(name = "idx_aiAgent_accountId", columnList = "accountId"),
    Index(name = "idx_aiAgent_ownerId", columnList = "ownerId"),
    Index(name = "idx_aiAgent_type", columnList = "type"),
    Index(name = "idx_aiAgent_category", columnList = "category"),
  ]
)
data class AiAgent(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "AI 어시스턴트를 생성한 계정의 아이디")
  val accountId: Long,

  @Column(
    nullable = false,
    updatable = false,
    comment = "오너의 아이디로 -1은 플랫폼의 시스템, 그 외 커스텀하게 생성된 오브젝트의 아이디 (accountId, workspaceUserId, etc..."
  )
  val ownerId: Long,

  @Column(nullable = false, comment = "어시스턴트의 핵심 기능을 정의하는 타입으로 변경될 수 없음")
  @Convert(converter = AiAgentTypeConverter::class)
  val type: AiAgentType,

  @Column(length = 40, nullable = false, comment = "직관적으로 이해할 수 있는 어시스턴트의 이름")
  var name: String,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var workflow: Map<String, Any>,

  @Column(length = 500, comment = "AI 어시스턴트의 설명")
  var description: String,

  @Column(length = 40, comment = "검색 및 분류를 위한 카테고리 (예: expand, summarize, formal, friendly 등)")
  var category: String? = null,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "입력 토큰 비용")
  var totalInputTokenSpend: Double = 0.0,

  @Column(nullable = false, columnDefinition = "DECIMAL(14, 6)", comment = "출력 토큰 비용")
  var totalOutputTokenSpend: Double = 0.0,

  var lastUsedAt: ZonedDateTime?,
) : BaseTimeAndStatusEntity()

