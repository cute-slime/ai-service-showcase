package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size

data class CreateOpenHandsIssue(
  @field:Null
  var id: Long? = null,

  @field:NotBlank
  @field:Size(max = 140)
  val gitProvider: String? = null,
  @field:NotBlank
  @field:Size(max = 140)
  val repo: String? = null,

  @field:NotBlank
  @field:Size(max = 200)
  var title: String? = null,
  @field:Size(max = 10000)
  var body: String? = null,
  @field:NotNull
  var workflowStatus: StatusType? = null,

  @field:NotNull
  var status: StatusType? = null,

  var bookmarked: Boolean = false,
)

data class PatchOpenHandsIssue(
  @field:NotNull
  var id: Long? = null,

  @field:Size(max = 140)
  val gitProvider: String? = null,
  @field:Size(max = 140)
  val repo: String? = null,

  @field:Size(max = 200)
  var title: String? = null,
  @field:Size(max = 10000)
  var body: String? = null,
  var workflowStatus: StatusType? = null,

  var status: StatusType? = null,

  var bookmarked: Boolean? = null,
)
