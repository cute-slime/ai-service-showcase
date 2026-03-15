package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import java.time.ZonedDateTime
import tools.jackson.databind.JsonNode

/**
 * (백오피스) 프롬프트 인첸터 프로필 응답 DTO
 */
data class BoPromptEnhancerLockedTemplate(
  val styleBlock: String?,
  val characterBlock: String?,
  val backgroundBlock: String?,
  val sampler: String?,
  val steps: Int?,
  val cfgScale: Double?,
  val width: Int?,
  val height: Int?,
  val seed: Long?,
)

data class BoPromptEnhancerProfileListItem(
  val id: Long,
  val providerCode: String,
  val name: String,
  val description: String?,
  val targetRule: JsonNode?,
  val priority: Int,
  val preferredArtistTagCount: Int,
  val styleKeywordCount: Int,
  val vibeKeywordCount: Int,
  val isDefault: Boolean,
  val status: StatusType,
  val createdAt: ZonedDateTime,
  val updatedAt: ZonedDateTime?,
)

data class BoPromptEnhancerProfile(
  val id: Long,
  val providerCode: String,
  val name: String,
  val description: String?,
  val targetRule: JsonNode?,
  val priority: Int,
  val preferredArtistTags: List<String>,
  val styleKeywords: List<String>,
  val vibeKeywords: List<String>,
  val lockedTemplate: BoPromptEnhancerLockedTemplate,
  val isDefault: Boolean,
  val status: StatusType,
  val createdAt: ZonedDateTime,
  val updatedAt: ZonedDateTime?,
)
