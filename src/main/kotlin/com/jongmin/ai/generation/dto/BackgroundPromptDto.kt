package com.jongmin.ai.generation.dto

/**
 * 배경 이미지 생성 관련 DTO
 *
 * gen.md 규칙에 따른 프롬프트 생성을 위한 데이터 구조.
 * DraftScenario의 worldBuilding.locations 데이터를 기반으로 3가지 변형(Variant)의
 * NovelAI 프롬프트를 생성한다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */

/**
 * 배경 이미지 변형 타입
 *
 * 각 장소에 대해 3가지 분위기의 변형을 생성한다:
 * - V1: 평화로운 낮/오후 (탐색, 소개 장면)
 * - V2: 긴장감 있는 저녁/황혼 (조사, 전환점 장면)
 * - V3: 공포 클라이맥스 밤/폭풍 (사건, 대결 장면)
 */
enum class BackgroundVariant(
  val timeOfDay: String,
  val mood: String,
  val description: String,
) {
  /** 평화로운 낮/오후 - 따뜻한 분위기, 탐색/소개 장면 */
  V1_PEACEFUL(
    timeOfDay = "afternoon, daytime, golden hour",
    mood = "peaceful, serene, tranquil, warm",
    description = "평화로운 낮/오후 버전"
  ),

  /** 긴장감 있는 저녁/황혼 - 누아르 스타일, 조사/전환점 장면 */
  V2_TENSE(
    timeOfDay = "evening, dusk, twilight",
    mood = "tense, ominous, foreboding, mysterious",
    description = "긴장감 있는 저녁/황혼 버전"
  ),

  /** 공포 클라이맥스 밤/폭풍 - 극적 대비, 사건/대결 장면 */
  V3_HORROR(
    timeOfDay = "night, stormy, lightning, thunder",
    mood = "horror, dread, unsettling, ominous",
    description = "공포 클라이맥스 버전"
  );

  companion object {
    /**
     * 문자열에서 BackgroundVariant 파싱
     * "V1", "v1", "V1_PEACEFUL", "peaceful" 등 다양한 형식 지원
     */
    fun fromString(value: String): BackgroundVariant {
      val normalized = value.uppercase().trim()
      return when {
        normalized == "V1" || normalized.startsWith("V1_") || normalized == "PEACEFUL" -> V1_PEACEFUL
        normalized == "V2" || normalized.startsWith("V2_") || normalized == "TENSE" -> V2_TENSE
        normalized == "V3" || normalized.startsWith("V3_") || normalized == "HORROR" -> V3_HORROR
        else -> throw IllegalArgumentException("Unknown variant: $value")
      }
    }

    /**
     * 전체 변형 목록 반환
     */
    fun all(): List<BackgroundVariant> = entries.toList()

    /**
     * 레거시 variant → DB presetCode 매핑
     *
     * Phase 5에서 DB 기반 프리셋으로 전환 시 하위 호환성 유지용
     */
    val LEGACY_PRESET_MAPPING: Map<BackgroundVariant, String> = mapOf(
      V1_PEACEFUL to "PEACEFUL_DAY",
      V2_TENSE to "TENSE_DUSK",
      V3_HORROR to "HORROR_NIGHT"
    )

    /**
     * presetCode에서 레거시 variant로 역매핑 (호환용)
     */
    fun fromPresetCode(code: String): BackgroundVariant? {
      return LEGACY_PRESET_MAPPING.entries
        .find { it.value == code }
        ?.key
    }

    /**
     * variant를 DB presetCode로 변환
     */
    fun toPresetCode(variant: BackgroundVariant): String {
      return LEGACY_PRESET_MAPPING[variant]
        ?: throw IllegalArgumentException("No preset mapping for variant: $variant")
    }
  }
}

/**
 * 스타일 프리셋 (레거시 - 하드코딩용)
 *
 * gen.md의 stylePresets 정의에 따른 프리셋 구조.
 * ⚠️ 새 시스템에서는 StylePresetParams + DB 기반 프리셋 사용 권장.
 *
 * @deprecated Phase 5에서 StylePresetParams 기반으로 전환
 */
data class StylePreset(
  val name: String,
  val artistTag: String?,
  val styleTag: String,
  val timeOfDay: String,
  val mood: String,
  val additionalNegative: String? = null,
)

/**
 * 배경 이미지 스타일 프리셋 파라미터 (DB 저장용)
 *
 * MultimediaModelPreset.params JSON 필드에 저장되는 배경 스타일 파라미터 구조.
 * presetType=BACKGROUND인 프리셋에서 사용한다.
 *
 * ### 프롬프트 구성 순서:
 * ```
 * [artistTag] + [품질태그] + [배경태그] + [장소] + [timeOfDay] + [mood] + [lighting] + [weather] + [additionalTags]
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class StylePresetParams(
  /** 작가/스타일 태그: "{makoto shinkai}", "{greg rutkowski}" 등 */
  val artistTag: String? = null,

  /** 시간대 태그: "afternoon, daytime, golden hour" */
  val timeOfDay: String? = null,

  /** 분위기 태그: "peaceful, serene, tranquil, warm" */
  val mood: String? = null,

  /** 조명 태그: "warm light, soft shadows, rim lighting" */
  val lighting: String? = null,

  /** 날씨 태그: "rain, storm, fog, snow" */
  val weather: String? = null,

  /** 추가 프롬프트 태그: "atmospheric perspective, wide shot" */
  val additionalTags: String? = null,

  /** 추가 네거티브 프롬프트: "bright, cheerful" */
  val additionalNegative: String? = null,

  /** 스타일 태그 (조명 + 분위기 통합): "warm lighting, soft shadows, atmospheric perspective" */
  val styleTag: String? = null,

  /** 장소 유형별 오버라이드: {"jungle": "{henri rousseau}", "ruins": "{greg rutkowski}"} */
  val locationTypeOverrides: Map<String, String>? = null,
) {
  /**
   * 레거시 StylePreset으로 변환
   */
  fun toStylePreset(name: String): StylePreset {
    return StylePreset(
      name = name,
      artistTag = artistTag,
      styleTag = styleTag ?: buildStyleTag(),
      timeOfDay = timeOfDay ?: "",
      mood = mood ?: "",
      additionalNegative = additionalNegative
    )
  }

  /**
   * styleTag가 없을 경우 lighting + mood로 조합
   */
  private fun buildStyleTag(): String {
    return listOfNotNull(lighting, mood, additionalTags)
      .filter { it.isNotBlank() }
      .joinToString(", ")
  }

  companion object {
    /**
     * StylePreset에서 변환 (하위 호환용)
     */
    fun fromStylePreset(preset: StylePreset): StylePresetParams {
      return StylePresetParams(
        artistTag = preset.artistTag,
        styleTag = preset.styleTag,
        timeOfDay = preset.timeOfDay,
        mood = preset.mood,
        additionalNegative = preset.additionalNegative
      )
    }
  }
}

/**
 * 장소 데이터 (DraftScenario.worldBuilding.locations에서 추출)
 */
data class LocationData(
  val locationId: String,
  val name: String,
  val nameKo: String?,
  val locationType: String,
  val description: String,
  val descriptionKo: String?,
  val detailElements: List<String> = emptyList(),
)

/**
 * 시나리오 메타데이터
 *
 * LLM 프롬프트 생성 시 시나리오 컨텍스트를 전달하기 위한 DTO.
 * 장르/분위기 정보가 포함되면 LLM이 일관된 스타일의 영어 프롬프트를 생성한다.
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class ScenarioMetadata(
  /** 시대 배경 (예: "19세기 말 빅토리아 시대", "modern") */
  val era: String,

  /** 지역/문화권 (예: "영국", "일본", "fantasy world") */
  val region: String,

  /** 전체 분위기 설명 */
  val atmosphere: String?,

  /** 날씨 정보 (예: "rainy", "stormy", "foggy") */
  val weather: String?,

  /** 프로젝트 제목 */
  val projectTitle: String?,

  /**
   * 장르 (예: "고딕 미스터리", "horror", "romance")
   *
   * 한국어/영어 모두 가능하며, LLM이 영어로 변환하여 프롬프트에 반영.
   */
  val genre: String? = null,

  /**
   * 분위기 키워드 목록 (예: ["고딕", "폐쇄공간", "심리적 압박", "dark", "ominous"])
   *
   * 한국어/영어 모두 가능하며, LLM이 영어로 변환하여 프롬프트에 반영.
   */
  val moodKeywords: List<String> = emptyList(),
)

/**
 * 프롬프트 빌더 출력 결과
 *
 * Phase 5에서 두 가지 시스템 지원:
 * - 레거시: variant 필드 사용 (하드코딩된 3가지 변형)
 * - 새 시스템: presetCode 필드 사용 (DB 기반 동적 프리셋)
 */
data class PromptOutput(
  /** NovelAI 태그 기반 프롬프트 */
  val prompt: String,

  /** 네거티브 프롬프트 */
  val negativePrompt: String,

  /** 영어 자연어 설명 (참조용) */
  val naturalPrompt: String? = null,

  /** 한국어 자연어 설명 (참조용) */
  val naturalPromptKo: String? = null,

  /** 적용된 스타일 프리셋 이름 */
  val stylePreset: String,

  /** 적용된 작가 태그 */
  val artistTag: String?,

  /**
   * 변형 타입 (레거시 호환용)
   *
   * @deprecated Phase 5에서 presetCode 사용 권장
   */
  val variant: BackgroundVariant? = null,

  /**
   * DB 프리셋 코드 (Phase 5 새 시스템)
   *
   * MultimediaModelPreset.code 값과 매칭됨.
   * 예: "PEACEFUL_DAY", "TENSE_DUSK", "RAINY_AFTERNOON" 등
   */
  val presetCode: String? = null,
) {
  /**
   * ComfyUI 워크플로우 치환용 Map 반환
   */
  fun toPlaceholderMap(): Map<String, String> = mapOf(
    "PROMPT" to prompt,
    "NEGATIVE" to negativePrompt,
    "NEGATIVE_PROMPT" to negativePrompt
  )

  /**
   * 유효한 프리셋 식별자 반환 (presetCode 우선, 없으면 variant에서 변환)
   */
  fun getEffectivePresetCode(): String {
    return presetCode
      ?: variant?.let { BackgroundVariant.toPresetCode(it) }
      ?: stylePreset
  }
}

/**
 * 배경 이미지 생성 요청
 *
 * Phase 5에서 두 가지 방식 지원:
 * - 레거시: variants 필드 사용 (하드코딩된 3가지 변형)
 * - 새 시스템: presetCodes 필드 사용 (DB 기반 동적 프리셋)
 *
 * presetCodes가 있으면 우선 사용, 없으면 variants 사용
 */
data class BackgroundGenerationRequest(
  val scenarioId: Long,
  val locationIds: List<String>,

  /**
   * 레거시 변형 목록 (하위 호환용)
   *
   * @deprecated Phase 5에서 presetCodes 사용 권장
   */
  val variants: List<BackgroundVariant>? = null,

  /**
   * DB 프리셋 코드 목록 (Phase 5 새 시스템)
   *
   * 예: ["PEACEFUL_DAY", "TENSE_DUSK", "RAINY_AFTERNOON"]
   */
  val presetCodes: List<String>? = null,

  val modelCode: String? = null,
  val priority: String = "NORMAL",
) {
  /**
   * 유효한 프리셋 코드 목록 반환
   *
   * presetCodes가 있으면 사용, 없으면 variants를 presetCode로 변환
   */
  fun getEffectivePresetCodes(): List<String> {
    // presetCodes가 있으면 우선 사용
    if (!presetCodes.isNullOrEmpty()) {
      return presetCodes
    }

    // variants가 있으면 변환해서 사용
    if (!variants.isNullOrEmpty()) {
      return variants.map { BackgroundVariant.toPresetCode(it) }
    }

    // 둘 다 없으면 기본 3가지 프리셋
    return BackgroundVariant.LEGACY_PRESET_MAPPING.values.toList()
  }
}

/**
 * 배경 이미지 생성 작업 결과
 */
data class BackgroundGenerationTask(
  val taskId: String,
  val locationId: String,
  val variant: BackgroundVariant,
  val promptOutput: PromptOutput,
  val status: String = "PENDING",
  val imageUrl: String? = null,
  val errorMessage: String? = null,
)

/**
 * Phase 6 LLM 기반 프롬프트 출력 결과
 *
 * 기존 PromptOutput을 확장하여 LLM 생성 관련 메타데이터를 포함한다.
 *
 * ### 프롬프트 조합 공식 (Phase 6)
 * ```
 * [artistTag] + [QUALITY_TAGS] + [LLM_GENERATED] + [timeOfDay] + [mood] + [lighting] + [weather]
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class Phase6PromptOutput(
  /** 조합된 최종 NovelAI 태그 프롬프트 */
  val prompt: String,

  /** 네거티브 프롬프트 */
  val negativePrompt: String,

  /** 영어 자연어 설명 (참조용) */
  val naturalPrompt: String? = null,

  /** 한국어 자연어 설명 (참조용) */
  val naturalPromptKo: String? = null,

  /** 적용된 스타일 프리셋 이름 */
  val stylePreset: String,

  /** 적용된 작가 태그 */
  val artistTag: String?,

  /** DB 프리셋 코드 */
  val presetCode: String,

  // ========== Phase 6 전용 메타데이터 ==========

  /** LLM이 생성한 원본 프롬프트 (조합 전) */
  val llmGeneratedPrompt: String,

  /** LLM 프롬프트 생성 소요 시간 (ms) */
  val llmDurationMs: Long,

  /** LLM 프롬프트 생성에 사용된 어시스턴트 ID */
  val llmAssistantId: Long,

  /** LLM 프롬프트 생성에 사용된 어시스턴트 이름 */
  val llmAssistantName: String,
) {
  /**
   * ComfyUI 워크플로우 치환용 Map 반환
   */
  fun toPlaceholderMap(): Map<String, String> = mapOf(
    "PROMPT" to prompt,
    "NEGATIVE" to negativePrompt,
    "NEGATIVE_PROMPT" to negativePrompt
  )

  /**
   * 기존 PromptOutput으로 변환 (하위 호환용)
   */
  fun toPromptOutput(): PromptOutput {
    return PromptOutput(
      prompt = prompt,
      negativePrompt = negativePrompt,
      naturalPrompt = naturalPrompt,
      naturalPromptKo = naturalPromptKo,
      stylePreset = stylePreset,
      artistTag = artistTag,
      presetCode = presetCode,
      variant = BackgroundVariant.fromPresetCode(presetCode)
    )
  }

  /**
   * promptConfig JSON 저장용 Map 생성
   *
   * BackgroundAssetItem.promptConfig에 저장될 Phase 6 구조
   */
  fun toPromptConfigMap(): Map<String, Any?> = mapOf(
    "prompt" to prompt,
    "negativePrompt" to negativePrompt,
    "naturalPrompt" to naturalPrompt,
    "naturalPromptKo" to naturalPromptKo,
    "stylePreset" to stylePreset,
    "artistTag" to artistTag,
    "presetCode" to presetCode,
    "phase6" to mapOf(
      "llmGeneratedPrompt" to llmGeneratedPrompt,
      "llmDurationMs" to llmDurationMs,
      "llmAssistantId" to llmAssistantId,
      "llmAssistantName" to llmAssistantName
    )
  )
}

