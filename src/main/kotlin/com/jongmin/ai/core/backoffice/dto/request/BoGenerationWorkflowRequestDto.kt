package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowFormat
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.GenerationWorkflowVariableType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * (백오피스) 멀티미디어 워크플로우 관련 Request DTO
 */

data class GenerationWorkflowVariablePayload(
  @field:NotBlank(message = "variables.key는 필수입니다")
  @field:Size(max = 100, message = "variables.key는 100자를 초과할 수 없습니다")
  val key: String,

  @field:NotNull(message = "variables.type은 필수입니다")
  val type: GenerationWorkflowVariableType,

  val required: Boolean = true,

  @field:Size(max = 500, message = "variables.description은 500자를 초과할 수 없습니다")
  val description: String? = null,

  val defaultValue: Any? = null,
)

/**
 * 워크플로우 생성 요청 DTO
 */
data class CreateGenerationWorkflow(
  @field:NotNull(message = "providerId는 필수입니다")
  @field:Min(value = 1, message = "providerId는 1 이상이어야 합니다")
  val providerId: Long,

  @field:NotNull(message = "mediaType은 필수입니다")
  val mediaType: GenerationMediaType,

  @field:NotBlank(message = "name은 필수입니다")
  @field:Size(max = 150, message = "name은 150자를 초과할 수 없습니다")
  val name: String,

  @field:NotNull(message = "pipeline은 필수입니다")
  val pipeline: GenerationWorkflowPipeline = GenerationWorkflowPipeline.PROMPT_TO_MEDIA,

  val description: String? = null,

  @field:NotNull(message = "format은 필수입니다")
  val format: GenerationWorkflowFormat,

  @field:NotNull(message = "payload는 필수입니다")
  val payload: Map<String, Any> = emptyMap(),

  val variables: List<GenerationWorkflowVariablePayload> = emptyList(),

  @field:Min(value = 1, message = "version은 1 이상이어야 합니다")
  val version: Int = 1,

  val isDefault: Boolean = false,

  val status: GenerationWorkflowStatus = GenerationWorkflowStatus.DRAFT,
)

/**
 * 워크플로우 수정 요청 DTO
 */
data class PatchGenerationWorkflow(
  var id: Long? = null,

  var providerId: Long? = null,

  var mediaType: GenerationMediaType? = null,
  var name: String? = null,
  var pipeline: GenerationWorkflowPipeline? = null,
  var description: String? = null,
  var format: GenerationWorkflowFormat? = null,
  var payload: Map<String, Any>? = null,
  var variables: List<GenerationWorkflowVariablePayload>? = null,
  var version: Int? = null,
  var isDefault: Boolean? = null,
  var status: GenerationWorkflowStatus? = null,
)
