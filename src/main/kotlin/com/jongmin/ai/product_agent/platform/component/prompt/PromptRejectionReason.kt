package com.jongmin.ai.product_agent.platform.component.prompt

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 프롬프트 거부 사유
 *
 * 이미지 생성 프롬프트가 평가에서 거부된 경우의 사유를 정의합니다.
 *
 * @property code 거부 사유 코드
 * @property displayName 표시 이름
 * @property description 상세 설명
 */
enum class PromptRejectionReason(
  private val code: String,
  val displayName: String,
  val description: String,
) {
  /**
   * 성인 컨텐츠
   *
   * - 과도하게 선정적인 속옷 (끈 팬티, 시스루, 본디지 등)
   * - 누드 또는 누드에 가까운 노출
   * - 성적 암시가 포함된 포즈나 표현
   * - 과도하게 노출된 수영복/비키니
   *
   * 참고: 일반적인 속옷/수영복 광고는 허용됩니다.
   */
  ADULT_CONTENT(
    code = "ADULT_CONTENT",
    displayName = "성인 컨텐츠",
    description = "선정적인 노출이나 성적 암시가 포함된 요청입니다."
  ),

  /**
   * 특정 인물 지칭
   *
   * - 연예인, 아이돌, 배우 등 유명인 이름 언급
   * - "~처럼", "~스타일" 등으로 특정 인물 지칭
   * - 정치인, 역사적 인물 포함
   */
  CELEBRITY_REFERENCE(
    code = "CELEBRITY_REFERENCE",
    displayName = "특정 인물 지칭",
    description = "특정 유명인이나 인물을 지칭하는 요청입니다."
  ),

  /**
   * 품질 미달
   *
   * - 상품과 무관한 요청
   * - 지나치게 모호하거나 구체성이 없는 요청
   * - 상품 이미지로 적합하지 않은 요청
   */
  QUALITY_INSUFFICIENT(
    code = "QUALITY_INSUFFICIENT",
    displayName = "품질 미달",
    description = "상품 이미지 생성에 적합하지 않은 요청입니다."
  ),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.code.uppercase() }

    /**
     * 코드로 거부 사유 조회
     * @param code 거부 사유 코드
     * @return 해당 거부 사유, 없으면 null
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): PromptRejectionReason? {
      if (code.isNullOrBlank()) return null
      return codeMap[code.uppercase()]
    }
  }

  @JsonValue
  fun code(): String = code
}
