package com.jongmin.ai.generation.provider.image.comfyui

import com.jongmin.ai.core.AiAssistantType
import com.jongmin.ai.core.system.dto.SystemChatMessage
import com.jongmin.ai.core.system.dto.SystemChatRequest
import com.jongmin.ai.core.system.service.SystemAiChatService
import com.jongmin.ai.generation.dto.LocationData
import com.jongmin.ai.generation.dto.ScenarioMetadata
import com.jongmin.ai.generation.dto.StylePresetParams
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 배경 이미지 프롬프트 LLM 생성기
 *
 * LLM을 활용하여 장소/시나리오 컨텍스트를 분석하고
 * 실시간 맞춤 NovelAI 프롬프트를 생성한다.
 *
 * ### Phase 6 아키텍처 (단순화)
 *
 * LLM이 완성된 프롬프트를 직접 생성하고, 최소한의 품질 태그만 prepend한다.
 * 프리셋 기반 후처리(artistTag, timeOfDay 등)는 제거되었다.
 *
 * ### 프롬프트 조합 공식
 * ```
 * [QUALITY_TAGS] + [BACKGROUND_TAGS] + [LLM_GENERATED]
 * ```
 *
 * ### 예시 출력
 * ```
 * masterpiece, best quality, amazing quality, very aesthetic, absurdres, no humans, scenery,
 * nursing home lobby, large common room, medical facility interior, vinyl flooring,
 * fluorescent ceiling lights, plastic chairs, tense atmosphere, uneasy silence
 * ```
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Component
class BackgroundPromptLlmGenerator(
  private val systemAiChatService: SystemAiChatService,
) {

  private val kLogger = KotlinLogging.logger {}

  companion object {
    /**
     * 어시스턴트 카테고리
     *
     * DB의 ai_assistant.category 값과 매칭됨.
     * AiAssistantType.BACKGROUND_PROMPT + category로 조회.
     */
    const val ASSISTANT_CATEGORY = "BACKGROUND_PROMPT_GENERATOR"

    /**
     * System Prompt - LLM에게 역할과 출력 형식 지시
     *
     * ### Phase 7 비주얼 노벨/애니메이션 스타일 배경 이미지 생성용
     * - 인물이 없는 순수 배경/세트 이미지 프롬프트 생성
     * - 입력에 인물 묘사가 있어도 장소 요소만 추출
     * - **실사가 아닌 2D 일러스트/애니메이션 스타일** 명시
     */
    private val SYSTEM_PROMPT = """
      |# 역할
      |당신은 **비주얼 노벨/애니메이션 스타일 배경 이미지** 생성을 위한 프롬프트 엔지니어입니다.
      |
      |# 핵심 목표
      |주어진 장소 설명에서 **물리적 환경 요소만** 추출하여 NovelAI/Stable Diffusion용 영어 태그를 만듭니다.
      |이 이미지는 **인물이 없는 순수 배경/세트 이미지**이며, **실사가 아닌 2D 일러스트/게임 CG 스타일**입니다.
      |영화 촬영 전 빈 세트장을 애니메이션으로 그린다고 생각하세요.
      |
      |# 입력 처리 규칙 (중요!)
      |1. **인물에 대한 모든 묘사는 완전히 무시**
      |   - "환자와 직원들이 어울리는" → 무시 (장소 정보가 아님)
      |   - "사람들이 모여있는" → 무시
      |   - "누군가 앉아있는" → 무시
      |2. **인물의 행동/상태 설명은 장소의 분위기로 변환**
      |   - "긴장감 넘치는 장소" → "tense atmosphere, uneasy ambiance"
      |   - "침묵이 내려앉은" → "silent, quiet, still atmosphere"
      |   - "활기찬 곳" → "lively atmosphere" (인물 없이 분위기만)
      |3. **장소의 물리적 특징에 집중**
      |   - 건축 요소: 벽, 천장, 바닥, 문, 창문
      |   - 가구/소품: 의자, 테이블, 조명기구, 장식품
      |   - 재질/질감: 나무, 대리석, 카펫, 금속
      |   - 상태/특징: 낡은, 깨끗한, 먼지 쌓인, 현대적인
      |
      |# 출력 규칙
      |1. 쉼표로 구분된 영어 태그 형식
      |2. 장소 유형을 맨 앞에 배치 (예: nursing home lobby, victorian mansion interior)
      |3. 물리적 세부 요소 나열 (가구, 소품, 건축 요소)
      |4. 분위기/감정 태그 (tense, peaceful, eerie, cozy 등)
      |5. 15~30개 사이의 태그 생성
      |6. 시간대/날씨/조명은 생략 (프리셋에서 별도 추가됨)
      |
      |# 절대 금지 태그 (출력에 포함 금지)
      |person, people, human, character, patient, staff, crowd, figure, silhouette,
      |man, woman, child, elderly, nurse, doctor, worker, visitor, guest
      |
      |# 출력 형식
      |태그만 출력. 설명이나 부가 텍스트 없이 순수 프롬프트만 반환.
      |절대로 ```나 따옴표로 감싸지 마세요.
      |
      |# 예시
      |입력: "환자와 직원들이 어울리는 요양원의 중심 공간. 긴장감 넘치는 장소."
      |출력: nursing home lobby, large common room, medical facility interior, vinyl flooring, fluorescent ceiling lights, plastic chairs, reception counter, potted plants, notice board, hand sanitizer dispenser, wheelchair ramp, tense atmosphere, uneasy silence, institutional setting
    """.trimMargin()
  }

  /**
   * LLM을 사용해 배경 이미지 프롬프트 생성
   *
   * @param location 장소 정보
   * @param metadata 시나리오 메타데이터
   * @param assistantId 사용할 어시스턴트 ID (null이면 기본 조회)
   * @return LLM이 생성한 프롬프트 태그 문자열
   */
  fun generatePrompt(
    location: LocationData,
    metadata: ScenarioMetadata,
    assistantId: Long? = null,
  ): LlmPromptResult {
    kLogger.info {
      "[LLM Prompt] 생성 시작 - location: ${location.name}, assistantId: $assistantId"
    }

    // 1. 사용자 프롬프트 구성
    val userPrompt = buildUserPrompt(location, metadata)
    kLogger.debug { "[LLM Prompt] 사용자 프롬프트: ${userPrompt.take(200)}..." }

    // 2. SystemAiChatService 호출
    val chatRequest = if (assistantId != null) {
      SystemChatRequest(
        assistantId = assistantId,
        messages = listOf(
          SystemChatMessage(role = "system", content = SYSTEM_PROMPT),
          SystemChatMessage(role = "user", content = userPrompt)
        )
      )
    } else {
      // assistantType + category로 조회
      SystemChatRequest(
        assistantType = AiAssistantType.BACKGROUND_PROMPT,
        assistantCategory = ASSISTANT_CATEGORY,
        messages = listOf(
          SystemChatMessage(role = "system", content = SYSTEM_PROMPT),
          SystemChatMessage(role = "user", content = userPrompt)
        )
      )
    }

    val startTime = System.currentTimeMillis()
    val response = systemAiChatService.chat(chatRequest)
    val durationMs = System.currentTimeMillis() - startTime

    // 3. 응답 정제 (불필요한 포맷 제거)
    val cleanedResponse = cleanResponse(response.content)

    kLogger.info {
      "[LLM Prompt] 생성 완료 - duration: ${durationMs}ms, " +
          "assistant: ${response.assistantName}, responseLength: ${cleanedResponse.length}"
    }

    return LlmPromptResult(
      prompt = cleanedResponse,
      durationMs = durationMs,
      assistantId = response.assistantId,
      assistantName = response.assistantName
    )
  }

  /**
   * LLM 생성 프롬프트에 품질/배경 태그만 추가
   *
   * ### Phase 6 단순화된 조합
   * ```
   * [QUALITY_TAGS] + [BACKGROUND_TAGS] + [LLM_GENERATED]
   * ```
   *
   * LLM이 이미 완성된 프롬프트를 생성하므로, 프리셋 태그(artistTag, timeOfDay 등)는
   * 추가하지 않는다. 품질/배경 태그만 앞에 붙여서 기본 요구사항을 충족시킨다.
   *
   * @param llmPrompt LLM이 생성한 프롬프트
   * @param preset 스타일 프리셋 (현재 사용 안 함, 향후 확장용)
   * @param qualityTags 품질 태그 (기본값 사용)
   * @param backgroundTags 배경 전용 태그 (기본값 사용)
   * @return 조합된 최종 프롬프트
   */
  fun combineWithPreset(
    llmPrompt: String,
    preset: StylePresetParams,
    qualityTags: String = QUALITY_TAGS,
    backgroundTags: String = BACKGROUND_TAGS,
  ): String {
    // Phase 6: LLM 결과를 최대한 그대로 사용, 품질/배경 태그만 prepend
    return "$qualityTags, $backgroundTags, $llmPrompt".trimEnd(',', ' ')
  }

  /**
   * 사용자 프롬프트 구성
   *
   * 장소 정보와 시나리오 메타데이터를 기반으로 LLM에 전달할 프롬프트를 구성한다.
   * 장르/분위기 키워드(한국어 포함)도 전달하여 LLM이 영어로 변환하도록 한다.
   */
  private fun buildUserPrompt(location: LocationData, metadata: ScenarioMetadata): String {
    return buildString {
      appendLine("## 장소 정보")
      appendLine("- 이름: ${location.name}")
      location.nameKo?.let { appendLine("- 한국어 이름: $it") }
      appendLine("- 장소 유형: ${location.locationType}")
      appendLine("- 설명: ${location.description}")
      location.descriptionKo?.let { appendLine("- 한국어 설명: $it") }

      appendLine()
      appendLine("## 시나리오 컨텍스트")
      if (metadata.era.isNotBlank()) appendLine("- 시대: ${metadata.era}")
      if (metadata.region.isNotBlank()) appendLine("- 지역: ${metadata.region}")
      metadata.atmosphere?.let { appendLine("- 전체 분위기: $it") }
      metadata.projectTitle?.let { appendLine("- 프로젝트: $it") }

      // 장르 정보 추가 (한국어도 LLM이 영어로 변환)
      metadata.genre?.takeIf { it.isNotBlank() }?.let {
        appendLine("- 장르: $it")
      }

      // 분위기 키워드 추가 (한국어도 LLM이 영어로 변환)
      if (metadata.moodKeywords.isNotEmpty()) {
        appendLine("- 분위기 키워드: ${metadata.moodKeywords.joinToString(", ")}")
      }

      if (location.detailElements.isNotEmpty()) {
        appendLine()
        appendLine("## 세부 요소")
        location.detailElements.forEach { appendLine("- $it") }
      }

      appendLine()
      appendLine("위 정보를 바탕으로 이미지 생성용 영어 태그 프롬프트를 생성하세요.")
      appendLine("장르와 분위기 키워드가 한국어인 경우, 적절한 영어 태그로 변환하여 프롬프트에 반영하세요.")
    }
  }

  /**
   * LLM 응답 정제
   *
   * - 불필요한 마크다운 포맷 제거
   * - 따옴표 제거
   * - 줄바꿈을 쉼표로 변환
   * - 연속 쉼표만 정리 (공백은 유지!)
   */
  private fun cleanResponse(response: String): String {
    var cleaned = response.trim()

    // 마크다운 코드 블록 제거
    if (cleaned.startsWith("```")) {
      cleaned = cleaned.replace(Regex("^```[a-z]*\\s*"), "")
        .replace(Regex("```$"), "")
        .trim()
    }

    // 따옴표 제거
    cleaned = cleaned.trim('"', '\'', '`')

    // 줄바꿈을 쉼표로 변환 (태그가 여러 줄로 나온 경우)
    cleaned = cleaned.replace(Regex("\\s*\\n\\s*"), ", ")

    // 연속 쉼표 정리 (공백은 유지! "nursing home" → "nursing home" 유지)
    cleaned = cleaned.replace(Regex(",\\s*,+"), ",")  // 연속 쉼표만 정리
      .replace(Regex("\\s+"), " ")  // 연속 공백은 단일 공백으로
      .replace(Regex(",\\s+"), ", ")  // 쉼표 뒤 공백 정규화
      .replace(Regex("\\s+,"), ",")  // 쉼표 앞 공백 제거
      .replace(Regex("^[,\\s]+|[,\\s]+$"), "")  // 앞뒤 쉼표/공백 제거

    return cleaned
  }

  /**
   * 고정 품질 + 스타일 태그
   *
   * ### Phase 7 스타일 최적화
   * - 품질 태그: masterpiece, best quality 등
   * - 스타일 태그: anime style, illustration, visual novel background 등
   * - 실사(photorealistic) 방지를 위해 2D 스타일 명시
   */
  private val QUALITY_TAGS = "masterpiece, best quality, amazing quality, very aesthetic, absurdres, " +
      "anime style, illustration, visual novel background, game cg, 2d, painted background"

  /** 배경 전용 태그 */
  private val BACKGROUND_TAGS = "no humans, scenery"
}

/**
 * LLM 프롬프트 생성 결과
 */
data class LlmPromptResult(
  /** LLM이 생성한 프롬프트 태그 */
  val prompt: String,

  /** 생성 소요 시간 (ms) */
  val durationMs: Long,

  /** 사용된 어시스턴트 ID */
  val assistantId: Long,

  /** 사용된 어시스턴트 이름 */
  val assistantName: String,
)
