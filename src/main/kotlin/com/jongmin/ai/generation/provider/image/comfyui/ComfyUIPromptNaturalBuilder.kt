package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.ai.generation.dto.BackgroundVariant
import com.jongmin.ai.generation.dto.LocationData
import com.jongmin.ai.generation.dto.ScenarioMetadata
import com.jongmin.ai.generation.dto.StylePresetParams

/**
 * ComfyUI 자연어 프롬프트 빌더
 *
 * 영어/한국어 자연어 프롬프트 생성 및 프롬프트 유틸리티 메서드를 담당한다.
 * Variant 기반, StylePresetParams 기반, LLM 기반 총 3가지 모드의
 * 자연어 프롬프트를 생성할 수 있다.
 *
 * @author Claude Code
 * @since 2026.02.19 (ComfyUIPromptBuilder에서 분리)
 */
object ComfyUIPromptNaturalBuilder {

  // ========== Variant 기반 자연어 프롬프트 (레거시) ==========

  /**
   * 영어 자연어 프롬프트 빌드 (Variant 기반)
   */
  fun buildNaturalPrompt(
    location: LocationData,
    variant: BackgroundVariant,
    metadata: ScenarioMetadata,
  ): String {
    val timeDescription = when (variant) {
      BackgroundVariant.V1_PEACEFUL -> "during a peaceful afternoon with warm golden light"
      BackgroundVariant.V2_TENSE -> "at dusk with dramatic shadows and an ominous atmosphere"
      BackgroundVariant.V3_HORROR -> "during a violent storm at night with lightning flashes"
    }

    val moodDescription = when (variant) {
      BackgroundVariant.V1_PEACEFUL -> "The atmosphere is serene and inviting."
      BackgroundVariant.V2_TENSE -> "The atmosphere is tense and foreboding."
      BackgroundVariant.V3_HORROR -> "The atmosphere is terrifying and chaotic."
    }

    return buildString {
      append("A cinematic wide shot of ${location.name}")
      if (metadata.era.isNotBlank()) {
        append(" from the ${metadata.era}")
      }
      if (metadata.region.isNotBlank()) {
        append(", set in ${metadata.region}")
      }
      append(", $timeDescription. ")
      append(location.description)
      append(". $moodDescription ")
      append("No people present. Highly detailed, cinematic composition.")
    }
  }

  /**
   * 한국어 자연어 프롬프트 빌드 (Variant 기반)
   */
  fun buildNaturalPromptKo(
    location: LocationData,
    variant: BackgroundVariant,
    metadata: ScenarioMetadata,
  ): String {
    val timeDescription = when (variant) {
      BackgroundVariant.V1_PEACEFUL -> "따뜻한 황금빛이 비치는 평화로운 오후"
      BackgroundVariant.V2_TENSE -> "불길한 분위기의 극적인 그림자가 드리운 황혼녘"
      BackgroundVariant.V3_HORROR -> "번개가 치는 폭풍우 밤"
    }

    val moodDescription = when (variant) {
      BackgroundVariant.V1_PEACEFUL -> "분위기는 평화롭고 따뜻하다."
      BackgroundVariant.V2_TENSE -> "분위기는 긴장되고 불길하다."
      BackgroundVariant.V3_HORROR -> "분위기는 공포스럽고 혼란스럽다."
    }

    val locationName = location.nameKo ?: location.name
    val locationDesc = location.descriptionKo ?: location.description

    return buildString {
      append("${locationName}의 시네마틱 와이드 샷")
      if (metadata.era.isNotBlank()) {
        append(", ${metadata.era} 배경")
      }
      if (metadata.region.isNotBlank()) {
        append(", ${metadata.region} 지역")
      }
      append(". $timeDescription. ")
      append(locationDesc)
      append(". $moodDescription ")
      append("인물 없음. 고도로 상세하고 영화적인 구도.")
    }
  }

  // ========== StylePresetParams 기반 자연어 프롬프트 ==========

  /**
   * 영어 자연어 프롬프트 (StylePresetParams 기반)
   */
  fun buildNaturalPromptFromParams(
    location: LocationData,
    params: StylePresetParams,
    metadata: ScenarioMetadata,
  ): String {
    return buildString {
      append("A cinematic wide shot of ${location.name}")
      if (metadata.era.isNotBlank()) {
        append(" from the ${metadata.era}")
      }
      if (metadata.region.isNotBlank()) {
        append(", set in ${metadata.region}")
      }
      params.timeOfDay?.let { append(", during $it") }
      params.weather?.let { append(", with $it") }
      append(". ")
      append(location.description)
      params.mood?.let { append(". The atmosphere is $it.") }
      append(" No people present. Highly detailed, cinematic composition.")
    }
  }

  /**
   * 한국어 자연어 프롬프트 (StylePresetParams 기반)
   */
  fun buildNaturalPromptKoFromParams(
    location: LocationData,
    params: StylePresetParams,
    metadata: ScenarioMetadata,
  ): String {
    val locationName = location.nameKo ?: location.name
    val locationDesc = location.descriptionKo ?: location.description

    return buildString {
      append("${locationName}의 시네마틱 와이드 샷")
      if (metadata.era.isNotBlank()) {
        append(", ${metadata.era} 배경")
      }
      if (metadata.region.isNotBlank()) {
        append(", ${metadata.region} 지역")
      }
      params.timeOfDay?.let { append(". $it 시간대") }
      params.weather?.let { append(", $it 날씨") }
      append(". ")
      append(locationDesc)
      params.mood?.let { append(". 분위기: $it") }
      append(". 인물 없음. 고도로 상세하고 영화적인 구도.")
    }
  }

  // ========== LLM 기반 자연어 프롬프트 ==========

  /**
   * LLM 결과 기반 영어 자연어 프롬프트
   */
  fun buildNaturalPromptWithLlm(
    location: LocationData,
    llmPrompt: String,
    params: StylePresetParams,
    metadata: ScenarioMetadata,
  ): String {
    return buildString {
      append("A cinematic wide shot of ${location.name}")
      if (metadata.era.isNotBlank()) {
        append(" from the ${metadata.era}")
      }
      if (metadata.region.isNotBlank()) {
        append(", set in ${metadata.region}")
      }
      params.timeOfDay?.let { append(", during $it") }
      params.weather?.let { append(", with $it") }
      append(". ")
      val llmDescription = llmPrompt.split(",")
        .take(5)
        .joinToString(", ") { it.trim() }
      append("Featuring $llmDescription. ")
      params.mood?.let { append("The atmosphere is $it. ") }
      append("No people present. Highly detailed, cinematic composition.")
    }
  }

  /**
   * LLM 결과 기반 한국어 자연어 프롬프트
   */
  fun buildNaturalPromptKoWithLlm(
    location: LocationData,
    llmPrompt: String,
    params: StylePresetParams,
    metadata: ScenarioMetadata,
  ): String {
    val locationName = location.nameKo ?: location.name

    return buildString {
      append("${locationName}의 시네마틱 와이드 샷")
      if (metadata.era.isNotBlank()) {
        append(", ${metadata.era} 배경")
      }
      if (metadata.region.isNotBlank()) {
        append(", ${metadata.region} 지역")
      }
      params.timeOfDay?.let { append(". $it 시간대") }
      params.weather?.let { append(", $it 날씨") }
      append(". ")
      val llmTags = llmPrompt.split(",")
        .take(5)
        .joinToString(", ") { it.trim() }
      append("주요 요소: $llmTags. ")
      params.mood?.let { append("분위기: $it. ") }
      append("인물 없음. 고도로 상세하고 영화적인 구도.")
    }
  }

  // ========== 유틸리티 메서드 ==========

  /**
   * 시대 정보 정규화
   *
   * "1925년 프랑스령 인도차이나" → "1920s, colonial era"
   */
  fun normalizeEra(era: String): String {
    val yearMatch = Regex("(\\d{4})").find(era)
    val decade = yearMatch?.groupValues?.get(1)?.let {
      "${it.substring(0, 3)}0s"
    }

    return buildString {
      decade?.let { append("$it, ") }

      when {
        era.contains("식민") || era.contains("colonial") || era.contains("령") -> {
          append("colonial era")
        }
        era.contains("현대") || era.contains("modern") -> {
          append("modern day")
        }
        era.contains("중세") || era.contains("medieval") -> {
          append("medieval era")
        }
        era.contains("빅토리아") || era.contains("victorian") -> {
          append("victorian era")
        }
        else -> {
          append(toNovelAITags(era))
        }
      }
    }
  }

  /**
   * 자연어 설명을 NovelAI 태그로 변환
   *
   * 문장을 쉼표로 구분된 태그 형식으로 변환한다.
   * 주요 요소에는 중괄호로 강조를 추가한다.
   *
   * 예: "large canvas tent with kerosene lamp" → "{large canvas tent}, {kerosene lamp}"
   */
  fun toNovelAITags(description: String): String {
    if (description.isBlank()) return ""

    // 이미 태그 형식이면 그대로 반환
    if (description.contains(",") && !description.contains(". ")) {
      return description
    }

    return description
      .replace(". ", ", ")
      .replace(".", ",")
      .replace(Regex("\\b(a|an|the|with|and|of|in|on|at|to|for)\\b", RegexOption.IGNORE_CASE), "")
      .replace(Regex("[,\\s]+"), ", ")
      .replace(Regex("^[,\\s]+|[,\\s]+$"), "")
      .split(", ")
      .filter { it.isNotBlank() && it.length > 2 }
      .mapIndexed { index, tag ->
        if (index < 3 && !tag.startsWith("{") && !tag.startsWith("[")) {
          "{${tag.trim()}}"
        } else {
          tag.trim()
        }
      }
      .joinToString(", ")
  }

  /**
   * 장소 유형에 따른 스타일 찾기
   */
  fun findLocationTypeStyle(locationType: String): Pair<String?, String>? {
    val normalizedType = locationType.lowercase()

    ComfyUIPromptConstants.LOCATION_TYPE_STYLES[normalizedType]?.let { return it }

    return ComfyUIPromptConstants.LOCATION_TYPE_STYLES.entries.find { (key, _) ->
      normalizedType.contains(key) || key.contains(normalizedType)
    }?.value
  }
}
