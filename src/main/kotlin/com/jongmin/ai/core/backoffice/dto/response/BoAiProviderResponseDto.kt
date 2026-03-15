package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime

data class BoAiProviderItem(
  var id: Long? = null,
  var name: String? = null,
  var description: String? = null,
  var baseUrl: String? = null,
  var modelCount: Int? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
  var apiKeys: Set<BoAiApiKeyItem>? = null
)

data class BoAiApiKeyItem(
  var id: Long? = null,
  private var encryptedKey: String? = null,
) {
  fun getKey(): String {
    return if (encryptedKey != null && encryptedKey!!.length >= 13) {
      val firstFive = encryptedKey!!.substring(0, 5)
      val lastFive = encryptedKey!!.substring(encryptedKey!!.length - 5)
      "$firstFive ... $lastFive"
    } else {
      encryptedKey!!
    }
  }
}
