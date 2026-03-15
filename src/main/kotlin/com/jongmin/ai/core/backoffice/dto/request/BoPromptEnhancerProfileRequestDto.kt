package com.jongmin.ai.core.backoffice.dto.request

import com.jongmin.jspring.data.entity.StatusType
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode

/**
 * (백오피스) 프롬프트 인첸터 프로필 요청 DTO
 */
data class PromptEnhancerLockedTemplatePayload(
  @field:Size(max = 2000, message = "styleBlock은 2000자를 초과할 수 없습니다")
  val styleBlock: String? = null,

  @field:Size(max = 2000, message = "characterBlock은 2000자를 초과할 수 없습니다")
  val characterBlock: String? = null,

  @field:Size(max = 2000, message = "backgroundBlock은 2000자를 초과할 수 없습니다")
  val backgroundBlock: String? = null,

  @field:Size(max = 40, message = "sampler는 40자를 초과할 수 없습니다")
  val sampler: String? = null,

  @field:Min(value = 1, message = "steps는 1 이상이어야 합니다")
  @field:Max(value = 200, message = "steps는 200 이하여야 합니다")
  val steps: Int? = null,

  @field:DecimalMin(value = "0.1", message = "cfgScale은 0.1 이상이어야 합니다")
  @field:DecimalMax(value = "30.0", message = "cfgScale은 30.0 이하여야 합니다")
  val cfgScale: Double? = null,

  @field:Min(value = 64, message = "width는 64 이상이어야 합니다")
  @field:Max(value = 4096, message = "width는 4096 이하여야 합니다")
  val width: Int? = null,

  @field:Min(value = 64, message = "height는 64 이상이어야 합니다")
  @field:Max(value = 4096, message = "height는 4096 이하여야 합니다")
  val height: Int? = null,

  val seed: Long? = null,
)

data class CreatePromptEnhancerProfile(
  @field:NotBlank(message = "providerCode는 필수입니다")
  @field:Size(max = 50, message = "providerCode는 50자를 초과할 수 없습니다")
  val providerCode: String,

  @field:NotBlank(message = "name은 필수입니다")
  @field:Size(max = 150, message = "name은 150자를 초과할 수 없습니다")
  val name: String,

  @field:Size(max = 1000, message = "description은 1000자를 초과할 수 없습니다")
  val description: String? = null,

  val targetRule: JsonNode? = null,

  @field:Min(value = 0, message = "priority는 0 이상이어야 합니다")
  @field:Max(value = 100000, message = "priority는 100000 이하여야 합니다")
  val priority: Int = 100,

  @field:Size(max = 30, message = "preferredArtistTags는 30개를 초과할 수 없습니다")
  val preferredArtistTags: List<String> = emptyList(),

  @field:Size(max = 30, message = "styleKeywords는 30개를 초과할 수 없습니다")
  val styleKeywords: List<String> = emptyList(),

  @field:Size(max = 30, message = "vibeKeywords는 30개를 초과할 수 없습니다")
  val vibeKeywords: List<String> = emptyList(),

  val lockedTemplate: PromptEnhancerLockedTemplatePayload? = null,

  val isDefault: Boolean = false,

  val status: StatusType = StatusType.ACTIVE,
)

data class PatchPromptEnhancerProfile(
  val providerCode: String? = null,
  val name: String? = null,
  val description: String? = null,
  val targetRule: JsonNode? = null,
  @field:Min(value = 0, message = "priority는 0 이상이어야 합니다")
  @field:Max(value = 100000, message = "priority는 100000 이하여야 합니다")
  val priority: Int? = null,
  @field:Size(max = 30, message = "preferredArtistTags는 30개를 초과할 수 없습니다")
  val preferredArtistTags: List<String>? = null,
  val styleKeywords: List<String>? = null,
  val vibeKeywords: List<String>? = null,
  val lockedTemplate: PromptEnhancerLockedTemplatePayload? = null,
  val isDefault: Boolean? = null,
  val status: StatusType? = null,
)
