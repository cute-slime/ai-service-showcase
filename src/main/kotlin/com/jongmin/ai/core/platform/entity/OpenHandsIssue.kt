package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.core.enums.ObjectTypeProvider
import com.jongmin.jspring.data.converter.StatusConverter
import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.common.entity.JObject
import com.jongmin.jspring.data.entity.StatusType
import jakarta.persistence.*

/**
 * @author Jongmin
 * @since  2025. 7. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_openHandsIssue_createdAt", columnList = "createdAt"),
    Index(name = "idx_openHandsIssue_status", columnList = "status"),
    Index(name = "idx_openHandsIssue_gitProvider", columnList = "gitProvider"),
    Index(name = "idx_openHandsIssue_repo", columnList = "repo"),
    Index(name = "idx_openHandsIssue_workflowStatus", columnList = "workflowStatus"),
  ]
)
data class OpenHandsIssue(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,
  @Column(length = 140, nullable = false)
  val gitProvider: String,
  @Column(length = 140, nullable = false)
  val repo: String,
  @Column(length = 200, nullable = false)
  var title: String?,
  @Column(columnDefinition = "TEXT", comment = "TRD 가 포함된 이슈 본문")
  var body: String?,
  @Column(nullable = false, comment = "작업 진행상황")
  @Convert(converter = StatusConverter::class)
  var workflowStatus: StatusType,
  @Column(nullable = false, comment = "북마크 설정되었는지 여부")
  var bookmarked: Boolean = false,
) : BaseTimeAndStatusEntity(), JObject {
  companion object : ObjectTypeProvider {
    override val getObjectType: ObjectType = ObjectType.OPEN_HANDS_ISSUE
  }

  override fun getObjectType(): ObjectType = getObjectType
}

