package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.role_playing.RolePlayingType
import com.jongmin.ai.role_playing.RolePlayingTypeConverter
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.ZonedDateTime

/**
 * @author Jongmin
 * @since  2026. 2. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "unq_rolePlaying_subject", columnList = "subject", unique = true),
    Index(name = "idx_rolePlaying_createdAt", columnList = "createdAt"),
    Index(name = "idx_rolePlaying_status", columnList = "status"),
    Index(name = "idx_rolePlaying_accountId", columnList = "accountId"),
    Index(name = "idx_rolePlaying_ownerId", columnList = "ownerId"),
    Index(name = "idx_rolePlaying_type", columnList = "type"),
    Index(name = "idx_rolePlaying_worldviewId", columnList = "worldviewId"),
  ]
)
data class RolePlaying(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, updatable = false, comment = "RP를 생성한 계정의 아이디")
  val accountId: Long,

  @Column(
    nullable = false,
    updatable = false,
    comment = "오너의 아이디로 -1은 플랫폼의 시스템, 그 외 커스텀하게 생성된 오브젝트의 아이디 (accountId, workspaceUserId, etc..."
  )
  val ownerId: Long,

  @Column(nullable = false, comment = "RP의 플로우를 정의하는 타입으로 변경될 수 없음")
  @Convert(converter = RolePlayingTypeConverter::class)
  val type: RolePlayingType,

  @Column(nullable = false, comment = "세계관 아이디")
  var worldviewId: Long,

  @Column(length = 40, nullable = false, comment = "직관적으로 이해할 수 있는 RP의 이름")
  var subject: String,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var workflow: Map<String, Any>,

  @Column(length = 2000, comment = "RP의 설명")
  var description: String,

  var lastUsedAt: ZonedDateTime?,
) : BaseTimeAndStatusEntity()

