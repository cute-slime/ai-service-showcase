package com.jongmin.ai.product_agent.platform.component.image

/**
 * 이미지 생성 요청 DTO
 *
 * 이미지 생성 클라이언트에 전달되는 요청 정보를 담습니다.
 * 프로바이더에 독립적인 공통 인터페이스를 제공합니다.
 *
 * @property prompt 이미지 생성 프롬프트 (positive prompt)
 * @property negativePrompt 네거티브 프롬프트 (생성하지 않을 요소)
 * @property width 이미지 너비 (픽셀)
 * @property height 이미지 높이 (픽셀)
 * @property seed 시드값 (재현성을 위해 사용, null이면 랜덤)
 * @property imageCount 생성할 이미지 수
 * @property metadata 추가 메타데이터 (프로바이더별 커스텀 옵션)
 */
data class ImageGenerationRequest(
  val prompt: String,
  val negativePrompt: String = DEFAULT_NEGATIVE_PROMPT,
  val width: Int = DEFAULT_WIDTH,
  val height: Int = DEFAULT_HEIGHT,
  val seed: Long? = null,
  val imageCount: Int = 1,
  val metadata: Map<String, Any> = emptyMap(),
) {
  companion object {
    const val DEFAULT_WIDTH = 600
    const val DEFAULT_HEIGHT = 600
    const val DEFAULT_NEGATIVE_PROMPT = "blurry, ugly, bad quality, distorted, artifacts"

    /**
     * 종횡비에 따른 해상도 반환
     *
     * 600 기준 해상도를 사용하며, 총 픽셀 수(약 360,000)를 유지합니다.
     *
     * @param aspectRatio 종횡비 코드 (예: "1:1", "16:9")
     * @return Pair(width, height)
     */
    fun getResolutionFromAspectRatio(aspectRatio: String): Pair<Int, Int> {
      return when (aspectRatio) {
        "1:1" -> Pair(600, 600)     // 정사각형
        "16:9" -> Pair(768, 432)    // 와이드 (유튜브 썸네일)
        "9:16" -> Pair(432, 768)    // 세로형 (스토리, 릴스)
        "4:3" -> Pair(672, 504)     // 가로형 (블로그)
        "3:4" -> Pair(504, 672)     // 세로형 (쇼핑몰)
        else -> Pair(600, 600)      // 기본값
      }
    }
  }

  /**
   * 시드값 반환 (없으면 현재 시간 기반 생성)
   */
  fun getEffectiveSeed(): Long = seed ?: System.currentTimeMillis()

  /**
   * 메타데이터에서 특정 키의 값 조회
   */
  @Suppress("UNCHECKED_CAST")
  fun <T> getMetadata(key: String, defaultValue: T): T {
    return (metadata[key] as? T) ?: defaultValue
  }
}
