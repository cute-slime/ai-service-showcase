package com.jongmin.ai.insight.component.tesseract_layout

import com.jongmin.ai.insight.component.vlm.ImageAnalysisResult
import com.jongmin.ai.insight.component.vlm.ImageDimensions
import com.jongmin.ai.insight.component.vlm.ImageSection
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO

/**
 * Tesseract OCR + Layout Analysis 기반 이미지 섹션 분석기
 *
 * VLM 기반 접근 방식(v1)의 한계를 극복하기 위해 개발된 규칙 기반 분석 시스템
 *
 * 주요 특징:
 * - 측정 가능한 시각적 특징 활용 (텍스트 블록 위치, 여백, 폰트 크기)
 * - 결정론적이고 재현 가능한 알고리즘
 * - 빠른 처리 속도 (VLM 대비 5-10배)
 * - 디버깅 및 튜닝 용이
 *
 * @property textExtractor Tesseract 텍스트 추출기
 * @property boundaryDetector 섹션 경계 감지기
 * @property params 레이아웃 분석 파라미터
 */
class LayoutBasedImageAnalyzer(
  private val textExtractor: TesseractTextExtractor = TesseractTextExtractor.create(),
  private val boundaryDetector: SectionBoundaryDetector = SectionBoundaryDetector(),
  private val params: LayoutAnalysisParams = LayoutAnalysisParams.default()
) {
  private val logger = LoggerFactory.getLogger(LayoutBasedImageAnalyzer::class.java)

  /**
   * 이미지를 분석하여 의미 있는 섹션으로 분할
   *
   * 파이프라인:
   * 1. 이미지 메타데이터 로드
   * 2. Tesseract로 텍스트 블록 추출
   * 3. 텍스트 블록 타입 분류
   * 4. 여백 기반 섹션 경계 감지
   * 5. 섹션 생성 및 후처리
   * 6. 유효성 검증
   *
   * @param imagePath 분석할 이미지 파일 경로
   * @return 섹션 분석 결과
   */
  fun analyzeImageSections(imagePath: String): ImageAnalysisResult {
    logger.info("=== Layout 기반 이미지 분석 시작 ===")
    logger.info("이미지: $imagePath")
    logger.info("파라미터: 여백임계값=${params.minGapThreshold}px, 최소섹션높이=${params.minSectionHeight}px")

    // 1. 이미지 메타데이터 로드
    val dimensions = getImageDimensions(imagePath)
    logger.info("이미지 크기: ${dimensions.width}x${dimensions.height}px")

    // 2. Tesseract로 텍스트 블록 추출
    logger.info("--- 1단계: 텍스트 블록 추출 ---")
    val rawTextBlocks = textExtractor.extractTextBlocks(imagePath)

    if (rawTextBlocks.isEmpty()) {
      logger.warn("텍스트 블록을 찾을 수 없습니다. 폴백: 이미지를 3등분")
      return createFallbackResult(dimensions)
    }

    // 3. 텍스트 블록 타입 분류
    logger.info("--- 2단계: 텍스트 블록 분류 ---")
    val classifiedBlocks = textExtractor.classifyAllBlocks(rawTextBlocks, params)

    // 디버깅: 텍스트 블록 정보 출력
    if (logger.isDebugEnabled) {
      textExtractor.printBlocksInfo(classifiedBlocks)
    }

    // 4. 여백 기반 섹션 경계 감지
    logger.info("--- 3단계: 섹션 경계 감지 ---")
    val boundaries = boundaryDetector.findSectionBoundaries(
      textBlocks = classifiedBlocks,
      imageHeight = dimensions.height,
      params = params
    )

    // 5. 섹션 생성 및 후처리
    logger.info("--- 4단계: 섹션 생성 ---")
    val sections = boundaryDetector.createSections(
      boundaries = boundaries,
      textBlocks = classifiedBlocks,
      imageHeight = dimensions.height,
      imageWidth = dimensions.width,
      params = params
    )

    // 디버깅: 섹션 정보 출력
    if (logger.isDebugEnabled) {
      boundaryDetector.printSectionsInfo(sections)
    }

    // 6. 유효성 검증
    logger.info("--- 5단계: 섹션 유효성 검증 ---")
    boundaryDetector.validateSections(sections, dimensions.height)

    logger.info("=== Layout 기반 이미지 분석 완료 ===")
    logger.info("총 섹션 수: ${sections.size}")

    return ImageAnalysisResult(
      sections = sections,
      totalHeight = dimensions.height,
      imageWidth = dimensions.width
    )
  }

  /**
   * 이미지 크기 정보 가져오기
   */
  private fun getImageDimensions(imagePath: String): ImageDimensions {
    val imageFile = File(imagePath)
    val image = ImageIO.read(imageFile)
    return ImageDimensions(
      width = image.width,
      height = image.height,
      needsResize = false  // Layout 분석은 원본 크기 사용
    )
  }

  /**
   * 폴백: 텍스트 블록이 없을 때 이미지를 3등분
   */
  private fun createFallbackResult(dimensions: ImageDimensions): ImageAnalysisResult {
    val sectionHeight = dimensions.height / 3
    val sections = listOf(
      ImageSection(1, 0, sectionHeight, "상단 영역 (텍스트 없음)", "image"),
      ImageSection(2, sectionHeight, sectionHeight * 2, "중간 영역 (텍스트 없음)", "image"),
      ImageSection(3, sectionHeight * 2, dimensions.height, "하단 영역 (텍스트 없음)", "image")
    )

    logger.info("폴백 섹션 생성 완료: ${sections.size}개 (3등분)")

    return ImageAnalysisResult(
      sections = sections,
      totalHeight = dimensions.height,
      imageWidth = dimensions.width
    )
  }

  /**
   * 분석 결과 요약 출력
   */
  fun printAnalysisReport(result: ImageAnalysisResult) {
    logger.info("=== 분석 결과 요약 ===")
    logger.info("이미지 크기: ${result.imageWidth}x${result.totalHeight}px")
    logger.info("총 섹션 수: ${result.sections.size}")
    logger.info("")
    logger.info("섹션 목록:")

    result.sections.forEach { section ->
      val heightPercent = (section.height.toFloat() / result.totalHeight!! * 100).toInt()
      logger.info(
        "  섹션 ${section.index}: ${section.startY}px ~ ${section.endY}px " +
            "(${section.height}px, $heightPercent%) " +
            "[${section.contentType}] ${section.description}"
      )
    }
  }

  companion object {
    /**
     * 기본 설정으로 Analyzer 생성
     */
    fun create(): LayoutBasedImageAnalyzer {
      return LayoutBasedImageAnalyzer()
    }

    /**
     * 커스텀 파라미터로 Analyzer 생성
     */
    fun create(params: LayoutAnalysisParams): LayoutBasedImageAnalyzer {
      return LayoutBasedImageAnalyzer(params = params)
    }

    /**
     * 커스텀 tessdata 경로로 Analyzer 생성
     */
    fun create(tessdataPath: String, language: String = "kor+eng"): LayoutBasedImageAnalyzer {
      val textExtractor = TesseractTextExtractor.create(tessdataPath, language)
      return LayoutBasedImageAnalyzer(textExtractor = textExtractor)
    }

    /**
     * 세밀한 분할을 위한 Analyzer 생성
     */
    fun createFinegrained(): LayoutBasedImageAnalyzer {
      return LayoutBasedImageAnalyzer(params = LayoutAnalysisParams.finegrained())
    }

    /**
     * 큰 섹션 위주 분할을 위한 Analyzer 생성
     */
    fun createCoarse(): LayoutBasedImageAnalyzer {
      return LayoutBasedImageAnalyzer(params = LayoutAnalysisParams.coarse())
    }
  }
}
