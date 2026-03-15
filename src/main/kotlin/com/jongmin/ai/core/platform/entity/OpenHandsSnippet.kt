package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.core.enums.ObjectTypeProvider
import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.common.entity.JObject
import jakarta.persistence.*

/**
 * @author Jongmin
 * @since  2025. 7. 25
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_openHandsSnippet_createdAt", columnList = "createdAt"),
    Index(name = "idx_openHandsSnippet_status", columnList = "status"),
    Index(name = "idx_openHandsSnippet_gitProvider", columnList = "gitProvider"),
    Index(name = "idx_openHandsSnippet_repo", columnList = "repo"),
  ]
)
data class OpenHandsSnippet(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,
  @Column(length = 140, nullable = false)
  val gitProvider: String,
  @Column(length = 140, nullable = false)
  val repo: String,
  @Column(length = 200, nullable = false)
  var title: String,
  @Column(columnDefinition = "TEXT")
  var description: String?,
  @Column(columnDefinition = "TEXT", comment = "TRD 가 포함된 이슈 본문")
  var body: String?,
) : BaseTimeAndStatusEntity(), JObject {
  companion object : ObjectTypeProvider {
    override val getObjectType: ObjectType = ObjectType.OPEN_HANDS_ISSUE
  }

  override fun getObjectType(): ObjectType = getObjectType
}

