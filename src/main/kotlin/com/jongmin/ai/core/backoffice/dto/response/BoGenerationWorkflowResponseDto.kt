package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.core.GenerationWorkflowFormat
import com.jongmin.ai.core.GenerationWorkflowPipeline
import com.jongmin.ai.core.GenerationWorkflowStatus
import com.jongmin.ai.core.GenerationWorkflowVariableType
import java.time.ZonedDateTime

/**
 * (백오피스) 멀티미디어 워크플로우 관련 Response DTO
 */

data class BoGenerationWorkflowVariable(
  val key: String,
  val type: GenerationWorkflowVariableType,
  val required: Boolean,
  val description: String?,
  val defaultValue: Any?,
)

/**
 * 워크플로우 목록 항목 DTO
 */
data class BoGenerationWorkflowListItem(
  val id: Long,
  val providerId: Long,
  val providerCode: String,
  val providerName: String,
  val mediaType: GenerationMediaType,
  val name: String,
  val pipeline: GenerationWorkflowPipeline,
  val description: String?,
  val format: GenerationWorkflowFormat,
  val version: Int,
  val isDefault: Boolean,
  val status: GenerationWorkflowStatus,
  val variableCount: Int,
  val payloadSize: Int,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)

/**
 * 워크플로우 상세 DTO
 */
data class BoGenerationWorkflow(
  val id: Long,
  val providerId: Long,
  val providerCode: String,
  val providerName: String,
  val mediaType: GenerationMediaType,
  val name: String,
  val pipeline: GenerationWorkflowPipeline,
  val description: String?,
  val format: GenerationWorkflowFormat,
  val payload: Map<String, Any>,
  val variables: List<BoGenerationWorkflowVariable>,
  val version: Int,
  val isDefault: Boolean,
  val status: GenerationWorkflowStatus,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
)
