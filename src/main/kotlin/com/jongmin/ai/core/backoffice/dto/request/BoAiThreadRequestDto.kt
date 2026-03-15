package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import org.hibernate.validator.constraints.Length

data class BoCreateThread(
  @field:Schema(description = "ID", type = "Long", requiredMode = RequiredMode.NOT_REQUIRED, hidden = true)
  @field:Null
  var id: Long? = null,

  @field:Schema(description = "계정 ID", type = "Long", requiredMode = RequiredMode.NOT_REQUIRED, hidden = true)
  @field:Null
  var accountId: Long? = null,

  @field:Schema(description = "제목", type = "String", requiredMode = RequiredMode.NOT_REQUIRED)
  @field:Length(max = 80)
  var title: String? = null,

  @field:Schema(description = "상태", type = "StatusType", requiredMode = RequiredMode.NOT_REQUIRED, hidden = true)
  @field:Null
  var status: StatusType? = null
)

data class BoPatchThread(
  @field:Schema(description = "ID", type = "Long", requiredMode = RequiredMode.REQUIRED)
  @field:NotNull
  var id: Long? = null,

  @field:Schema(description = "제목", type = "String", requiredMode = RequiredMode.NOT_REQUIRED)
  @field:Length(max = 80)
  var title: String? = null,

  @field:Schema(description = "상태", type = "StatusType", requiredMode = RequiredMode.NOT_REQUIRED)
  var status: StatusType? = null
)

