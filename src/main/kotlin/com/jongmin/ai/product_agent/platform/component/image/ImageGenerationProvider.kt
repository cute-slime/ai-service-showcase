package com.jongmin.ai.product_agent.platform.component.image

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 이미지 생성 프로바이더
 *
 * 지원하는 이미지 생성 모델/서비스를 정의합니다.
 * Strategy 패턴으로 다양한 이미지 생성 모델을 유연하게 교체/추가할 수 있습니다.
 *
 * @property code 프로바이더 코드
 * @property displayName 표시 이름
 * @property available 현재 사용 가능 여부
 */
enum class ImageGenerationProvider(
  private val code: String,
  val displayName: String,
  val available: Boolean,
) {
  /**
   * ComfyUI - 현재 기본 프로바이더
   * Z Image 모델을 사용하여 이미지 생성
   */
  COMFYUI("comfyui", "ComfyUI (Z Image)", true),

  /**
   * OpenAI DALL-E 3
   * Placeholder - 향후 구현 예정
   */
  DALLE("dalle", "DALL-E 3", false),

  /**
   * Midjourney API
   * Placeholder - 향후 구현 예정
   */
  MIDJOURNEY("midjourney", "Midjourney", false),

  /**
   * Google Imagen / Nano Banana
   * Placeholder - 향후 구현 예정
   */
  IMAGEN("imagen", "Google Imagen", false),
  ;

  companion object {
    private val codeMap = entries.associateBy { it.code.lowercase() }

    /**
     * 코드로 프로바이더 조회
     * @param code 프로바이더 코드
     * @return 해당 프로바이더, 없으면 기본값(COMFYUI) 반환
     */
    @JsonCreator
    @JvmStatic
    fun fromCode(code: String?): ImageGenerationProvider {
      if (code.isNullOrBlank()) return getDefault()
      return codeMap[code.lowercase()] ?: getDefault()
    }

    /**
     * 기본 프로바이더 반환
     */
    fun getDefault(): ImageGenerationProvider = COMFYUI

    /**
     * 사용 가능한 프로바이더 목록 반환
     */
    fun getAvailableProviders(): List<ImageGenerationProvider> {
      return entries.filter { it.available }
    }
  }

  @JsonValue
  fun code(): String = code
}
