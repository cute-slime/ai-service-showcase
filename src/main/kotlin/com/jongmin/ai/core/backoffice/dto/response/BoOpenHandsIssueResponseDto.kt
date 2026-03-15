package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.platform.entity.QOpenHandsIssue.openHandsIssue
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import java.time.ZonedDateTime

data class BoOpenHandsIssueItem(
  var id: Long? = null,
  var gitProvider: String? = null,
  var repo: String? = null,
  var title: String? = null,
  var body: String? = null,
  var workflowStatus: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var status: StatusType? = null,
  var bookmarked: Boolean = false,
) {
  companion object {
    fun buildProjection(): ConstructorExpression<BoOpenHandsIssueItem> = Projections.constructor(
      BoOpenHandsIssueItem::class.java,
      openHandsIssue.id,
      openHandsIssue.gitProvider,
      openHandsIssue.repo,
      openHandsIssue.title,
      openHandsIssue.body,
      openHandsIssue.workflowStatus,
      openHandsIssue.createdAt,
      openHandsIssue.status,
      openHandsIssue.bookmarked,
    )
  }
}
