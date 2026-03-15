package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.platform.entity.QOpenHandsSnippet.openHandsSnippet
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import java.time.ZonedDateTime

data class BoOpenHandsSnippetItem(
  var id: Long? = null,
  var gitProvider: String? = null,
  var repo: String? = null,
  var title: String? = null,
  var description: String? = null,
  var body: String? = null,
  var createdAt: ZonedDateTime? = null,
  var status: StatusType? = null,
) {
  companion object {
    fun buildProjection(): ConstructorExpression<BoOpenHandsSnippetItem> = Projections.constructor(
      BoOpenHandsSnippetItem::class.java,
      openHandsSnippet.id,
      openHandsSnippet.gitProvider,
      openHandsSnippet.repo,
      openHandsSnippet.title,
      openHandsSnippet.description,
      openHandsSnippet.body,
      openHandsSnippet.createdAt,
      openHandsSnippet.status,
    )
  }
}
