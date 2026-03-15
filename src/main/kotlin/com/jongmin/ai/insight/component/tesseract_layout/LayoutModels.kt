package com.jongmin.ai.insight.component.tesseract_layout

/**
 * 텍스트 블록 타입
 */
enum class BlockType {
  HEADING,      // 큰 폰트, 볼드 - 제목
  PARAGRAPH,    // 일반 본문 텍스트
  LIST,         // 목록 형식
  TABLE,        // 표 형식 (숫자/정렬 패턴)
  CAPTION,      // 작은 폰트 설명/캡션
  UNKNOWN       // 분류 불가
}

/**
 * Tesseract로 추출한 텍스트 블록 정보
 *
 * @property text 추출된 텍스트 내용
 * @property x 블록 좌측 상단 X 좌표
 * @property y 블록 좌측 상단 Y 좌표
 * @property width 블록 너비
 * @property height 블록 높이
 * @property confidence OCR 신뢰도 (0.0 ~ 1.0)
 * @property blockType 블록 타입 (HEADING, PARAGRAPH 등)
 */
data class TextBlock(
  val text: String,
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
  val confidence: Float,
  var blockType: BlockType = BlockType.UNKNOWN
) {
  /**
   * 블록의 좌측 상단 Y 좌표
   */
  val startY: Int get() = y

  /**
   * 블록의 우측 하단 Y 좌표
   */
  val endY: Int get() = y + height

  /**
   * 예상 폰트 크기 계산 (높이 / 줄 수)
   */
  fun estimateFontSize(): Int {
    val lineCount = text.split('\n').size.coerceAtLeast(1)
    return height / lineCount
  }

  /**
   * 텍스트가 테이블 패턴인지 확인
   * - 숫자가 많은 경우
   * - 구분자(|, tab) 포함
   * - 짧은 단어들의 나열
   */
  fun hasTablePattern(): Boolean {
    val digitRatio = text.count { it.isDigit() }.toFloat() / text.length.coerceAtLeast(1)
    val hasSeparators = text.contains('|') || text.contains('\t')
    val shortWords = text.split(Regex("\\s+")).count { it.length <= 3 }
    val wordCount = text.split(Regex("\\s+")).size

    return digitRatio > 0.3f || hasSeparators || (shortWords.toFloat() / wordCount > 0.5f)
  }

  /**
   * 텍스트가 리스트 패턴인지 확인
   * - 번호 시작 (1., 2., ...)
   * - 불릿 포인트 (•, -, *)
   */
  fun hasListPattern(): Boolean {
    val trimmed = text.trim()
    return trimmed.matches(Regex("^\\d+\\..*")) ||  // "1. "
        trimmed.startsWith("• ") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ")
  }
}

/**
 * 이미지 영역 정보 (OpenCV 등으로 감지된 비텍스트 영역)
 *
 * @property x 영역 좌측 상단 X 좌표
 * @property y 영역 좌측 상단 Y 좌표
 * @property width 영역 너비
 * @property height 영역 높이
 */
data class ImageRegion(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int
) {
  val startY: Int get() = y
  val endY: Int get() = y + height
}

/**
 * 레이아웃 분석 파라미터
 *
 * @property minGapThreshold 섹션 분할을 위한 최소 여백 임계값 (픽셀)
 * @property minSectionHeight 섹션의 최소 높이 (픽셀)
 * @property headingSizeMultiplier 헤딩으로 판단하는 폰트 크기 배수
 * @property captionSizeMultiplier 캡션으로 판단하는 폰트 크기 배수
 * @property minConfidence 텍스트 블록을 사용할 최소 신뢰도
 */
data class LayoutAnalysisParams(
  val minGapThreshold: Int = 100,
  val minSectionHeight: Int = 200,
  val headingSizeMultiplier: Float = 1.5f,
  val captionSizeMultiplier: Float = 1.2f,
  val minConfidence: Float = 0.5f
) {
  companion object {
    /**
     * 기본 파라미터
     */
    fun default() = LayoutAnalysisParams()

    /**
     * 세밀한 분할을 위한 파라미터 (여백 임계값 낮춤)
     */
    fun finegrained() = LayoutAnalysisParams(
      minGapThreshold = 50,
      minSectionHeight = 150
    )

    /**
     * 큰 섹션 위주의 분할 (여백 임계값 높임)
     */
    fun coarse() = LayoutAnalysisParams(
      minGapThreshold = 200,
      minSectionHeight = 300
    )
  }
}
