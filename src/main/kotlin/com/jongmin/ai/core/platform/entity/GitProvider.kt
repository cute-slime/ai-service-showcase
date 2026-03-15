package com.jongmin.ai.core.platform.entity

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.core.enums.ObjectTypeProvider
import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.common.entity.JObject
import com.jongmin.ai.core.backoffice.dto.request.CreateGitProvider
import jakarta.persistence.*
import org.jasypt.util.text.TextEncryptor

/**
 * @author Jongmin
 * @since  2025. 8. 13
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_gitProvider_status", columnList = "status"),
    Index(name = "idx_gitProvider_accountId", columnList = "accountId"),
  ]
)
data class GitProvider(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,
  @Column(nullable = false)
  val accountId: Long,
  @Column(length = 80, nullable = false)
  var name: String,
  @Column(nullable = false, columnDefinition = "TEXT")
  var encryptedToken: String?,
) : BaseTimeAndStatusEntity(), JObject {
  companion object : ObjectTypeProvider {
    fun from(textEncryptor: TextEncryptor, dto: CreateGitProvider): GitProvider {
      val entity = GitProvider(dto.id!!, dto.accountId!!, dto.name!!, textEncryptor.encrypt(dto.token!!))
      entity.status = dto.status!!
      return entity
    }

    override val getObjectType: ObjectType = ObjectType.GIT_PROVIDER
  }

  override fun getObjectType(): ObjectType = getObjectType
}

