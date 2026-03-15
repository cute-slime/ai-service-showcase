package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.jspring.data.converter.StatusConverter
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.role_playing.RpLogType
import com.jongmin.ai.role_playing.RpLogTypeConverter
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.ZonedDateTime

/**
 * @author Jongmin
 * @since  2026. 3. 2
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_rpLog_createdAt", columnList = "createdAt"),
    Index(name = "idx_rpLog_status", columnList = "status"),
    Index(name = "idx_rpLog_type", columnList = "type"),
    Index(name = "idx_rpLog_rolePlayingId", columnList = "rolePlayingId"),
    Index(name = "idx_rpLog_stageId", columnList = "stageId"),
    Index(name = "idx_rpLog_placeId", columnList = "placeId"),
    Index(name = "idx_rpLog_characterId", columnList = "characterId"),
  ]
)
data class RpLog(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false)
  @Convert(converter = RpLogTypeConverter::class)
  var type: RpLogType,

  @Column(nullable = false, updatable = false)
  val rolePlayingId: Long,

  @Column(nullable = false, updatable = false)
  val stageId: Long,

  @Column(nullable = false, updatable = false)
  val placeId: Long,

  @Column(updatable = false)
  val characterId: Long,

  @Column(nullable = false, columnDefinition = "TEXT")
  var content: String,

  @Column(columnDefinition = "INT", nullable = false, comment = "상태값, 소스코드에서는 타입(ENUM)으로 관리된다.")
  @Convert(converter = StatusConverter::class)
  var status: StatusType = StatusType.ACTIVE,

  @Column(comment = "현재 버전으로 실제 코드에서는 prefix인 v를 붙여 사용된다.")
  var currentVersion: Int = 1,

  // 버전이 변경될 때 이전 버전이 기록된다.
  // {v1: {"content": ""}, v2: {...}}
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var version: Map<String, Any> = emptyMap(),

  @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP", comment = "생성일")
  val createdAt: ZonedDateTime = now()
)

