package com.jongmin.ai.insight.component.vlm

/**
 * 이미지 분석 결과를 담는 데이터 클래스
 */
data class ImageAnalysisResult(
  val sections: List<ImageSection>,
  val totalHeight: Int? = null,
  val imageWidth: Int? = null
)

/**
 * 이미지 섹션 정보
 */
data class ImageSection(
  val index: Int,
  val startY: Int,
  val endY: Int,
  val description: String,
  val contentType: String? = null  // "header", "feature", "specification" 등
) {
  /**
   * 섹션의 높이를 반환
   */
  val height: Int
    get() = endY - startY

  /**
   * 섹션이 유효한지 검증
   */
  fun isValid(maxHeight: Int): Boolean {
    return startY >= 0 &&
        endY > startY &&
        endY <= maxHeight &&
        height >= MIN_SECTION_HEIGHT
  }

  companion object {
    const val MIN_SECTION_HEIGHT = 200
  }
}

/**
 * 이미지 크기 정보를 담는 데이터 클래스
 */
data class ImageDimensions(
  val width: Int,
  val height: Int,
  val needsResize: Boolean
)

/**
 * 이미지 포맷 정보
 */
data class ImageInfo(
  val width: Int,
  val height: Int,
  val format: String,
  val colorType: String,
  val bitsPerPixel: Int
)
