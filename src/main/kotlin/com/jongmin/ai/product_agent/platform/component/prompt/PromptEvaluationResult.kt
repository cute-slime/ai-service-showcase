package com.jongmin.ai.product_agent.platform.component.prompt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 프롬프트 평가 결과 DTO
 *
 * LLM 기반 프롬프트 평가 어시스턴트의 응답을 파싱한 결과입니다.
 *
 * @property approved 승인 여부 (true: 통과, false: 거부)
 * @property rejectionReason 거부 사유 (approved=false일 때만 존재)
 * @property rejectionDetail 거부 사유 상세 설명 (내부 관리자용, 사용자에게 노출하지 않음)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromptEvaluationResult(
  @param:JsonProperty("approved")
  val approved: Boolean,

  @param:JsonProperty("rejectionReason")
  val rejectionReason: PromptRejectionReason? = null,

  @param:JsonProperty("rejectionDetail")
  val rejectionDetail: String? = null,
) {
  companion object {
    /**
     * 승인 결과 생성
     */
    fun approved(): PromptEvaluationResult {
      return PromptEvaluationResult(approved = true)
    }

    /**
     * 거부 결과 생성
     */
    fun rejected(
      reason: PromptRejectionReason,
      detail: String,
    ): PromptEvaluationResult {
      return PromptEvaluationResult(
        approved = false,
        rejectionReason = reason,
        rejectionDetail = detail,
      )
    }

    /**
     * 파싱 실패 시 기본 승인 결과 (폴백)
     */
    fun fallbackApproved(): PromptEvaluationResult {
      return PromptEvaluationResult(approved = true)
    }
  }

  /**
   * 거부되었는지 확인
   */
  val isRejected: Boolean
    get() = !approved

  /**
   * 거부 사유 코드 반환
   */
  val rejectionReasonCode: String?
    get() = rejectionReason?.code()
}

/**
 * 프롬프트 생성 결과 DTO
 *
 * LLM 기반 프롬프트 생성 어시스턴트의 응답을 파싱한 결과입니다.
 * 최신 이미지 생성 모델에 최적화된 자연어 서술 형식을 사용합니다.
 *
 * @property positivePrompt 생성된 positive 프롬프트 (자연어 서술)
 * @property negativePrompt 생성된 negative 프롬프트 (최소화 권장)
 * @property styleModifiers 적용된 스타일 수식어 목록 (조명, 구도, 색감 등)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PromptGenerationResult(
  @param:JsonProperty("positivePrompt")
  val positivePrompt: String,

  @param:JsonProperty("negativePrompt")
  val negativePrompt: String,

  @param:JsonProperty("styleModifiers")
  val styleModifiers: List<String>? = null,
) {
  companion object {
    /**
     * 기본 프롬프트 생성 (폴백)
     *
     * LLM 호출 실패 시 사용되는 자연어 스타일 기본 프롬프트입니다.
     * Z-Image 스타일의 flowing narrative 형식을 따릅니다.
     */
    fun fallback(
      productName: String,
      userPrompt: String,
    ): PromptGenerationResult {
      val naturalPrompt = buildString {
        append("A professional product photograph featuring $productName, ")
        append("$userPrompt. ")
        append("The product is placed on a clean white seamless backdrop with soft key light from the upper left, ")
        append("creating gentle shadows that emphasize the product's form and texture. ")
        append("The composition uses shallow depth of field to draw attention to the main subject. ")
        append("Shot with neutral color temperature and balanced exposure. ")
        append("Correct anatomy, no text overlays, no watermarks, no logos, commercially appropriate.")
      }

      return PromptGenerationResult(
        positivePrompt = naturalPrompt,
        negativePrompt = "",
        styleModifiers = listOf("fallback", "studio_lighting", "product_photography"),
      )
    }
  }

  /**
   * 스타일 수식어를 콤마로 연결한 문자열 반환
   */
  val styleModifiersText: String
    get() = styleModifiers?.joinToString(", ") ?: ""
}
