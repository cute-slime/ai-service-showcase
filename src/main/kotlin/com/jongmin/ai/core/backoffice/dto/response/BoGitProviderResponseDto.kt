package com.jongmin.ai.core.backoffice.dto.response

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.platform.entity.QGitProvider.gitProvider
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import org.jasypt.util.text.TextEncryptor
import java.time.ZonedDateTime

data class BoGitProviderItem(
  var id: Long? = null,
  var accountId: Long? = null,
  var name: String? = null,
  @JsonIgnore
  var encryptedToken: String? = null,
  var createdAt: ZonedDateTime? = null,
  var status: StatusType? = null,
) {
  companion object {
    fun buildProjection(): ConstructorExpression<BoGitProviderItem> = Projections.constructor(
      BoGitProviderItem::class.java,
      gitProvider.id,
      gitProvider.accountId,
      gitProvider.name,
      gitProvider.encryptedToken,
      gitProvider.createdAt,
      gitProvider.status,
    )
  }

  @JsonGetter("token")
  fun getToken(): String? {
    return encryptedToken
  }

  fun decrypt(textEncryptor: TextEncryptor) {
    encryptedToken = textEncryptor.decrypt(encryptedToken)
  }
}
