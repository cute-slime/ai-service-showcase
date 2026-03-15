package com.jongmin.ai.insight.component.tesseract_layout

import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.Word
import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Tesseract OCR을 사용하여 이미지에서 텍스트 블록을 추출하는 클래스
 *
 * @property tessdataPath Tesseract 데이터 파일 경로 (기본: null = 시스템 경로 사용)
 * @property language OCR 언어 설정 (기본: "kor+eng" = 한글+영문)
 */
class TesseractTextExtractor(
  private val tessdataPath: String? = null,
  private val language: String = "kor+eng"
) {
  private val logger = LoggerFactory.getLogger(TesseractTextExtractor::class.java)

  private val tesseract: Tesseract by lazy {
    Tesseract().apply {
      // tessdata 경로 설정
      tessdataPath?.let { setDatapath(it) }

      // 언어 설정
      setLanguage(language)

      // 페이지 세그먼트 모드: PSM_AUTO (자동 감지)
      // PSM_SINGLE_BLOCK: 단일 텍스트 블록
      // PSM_AUTO: 자동 감지 (기본값)
      setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO)

      // OEM 설정: LSTM 신경망 기반 (Tesseract 4+)
      setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY)

      logger.info("Tesseract 초기화 완료 - 언어: $language, PSM: AUTO")
    }
  }

  /**
   * 이미지에서 텍스트 블록 추출
   *
   * @param imagePath 이미지 파일 경로
   * @return 추출된 텍스트 블록 리스트
   */
  fun extractTextBlocks(imagePath: String): List<TextBlock> {
    val imageFile = File(imagePath)
    if (!imageFile.exists()) {
      logger.error("이미지 파일을 찾을 수 없습니다: $imagePath")
      return emptyList()
    }

    logger.info("텍스트 블록 추출 시작: $imagePath")

    return try {
      // File을 BufferedImage로 변환
      val bufferedImage = ImageIO.read(imageFile)

      // Tesseract의 getWords() 사용 - 단어 단위 바운딩 박스
      val words: List<Word> = tesseract.getWords(bufferedImage, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)

      // 단어들을 줄(textline) 단위로 그룹화하여 TextBlock 생성
      val textBlocks = groupWordsIntoBlocks(words)

      logger.info("텍스트 블록 추출 완료: ${textBlocks.size}개 블록")

      textBlocks
    } catch (e: Exception) {
      logger.error("텍스트 추출 실패: ${e.message}", e)
      emptyList()
    }
  }

  /**
   * 단어들을 Y 좌표 기준으로 그룹화하여 텍스트 블록 생성
   *
   * 같은 줄(textline)의 단어들을 하나의 블록으로 병합
   */
  private fun groupWordsIntoBlocks(words: List<Word>): List<TextBlock> {
    if (words.isEmpty()) return emptyList()

    // Y 좌표로 정렬
    val sortedWords = words.sortedBy { it.boundingBox.y }

    val blocks = mutableListOf<TextBlock>()
    var currentGroup = mutableListOf<Word>()

    for (word in sortedWords) {
      if (currentGroup.isEmpty()) {
        currentGroup.add(word)
        continue
      }

      val lastWord = currentGroup.last()
      val yDiff = abs(word.boundingBox.y - lastWord.boundingBox.y)

      // Y 좌표 차이가 높이의 50% 이내면 같은 줄로 간주
      if (yDiff < lastWord.boundingBox.height * 0.5) {
        currentGroup.add(word)
      } else {
        // 현재 그룹을 블록으로 변환
        blocks.add(createTextBlock(currentGroup))
        currentGroup = mutableListOf(word)
      }
    }

    // 마지막 그룹 처리
    if (currentGroup.isNotEmpty()) {
      blocks.add(createTextBlock(currentGroup))
    }

    return blocks
  }

  /**
   * 단어 그룹을 하나의 TextBlock으로 변환
   */
  private fun createTextBlock(words: List<Word>): TextBlock {
    // 바운딩 박스 병합
    val minX = words.minOf { it.boundingBox.x }
    val minY = words.minOf { it.boundingBox.y }
    val maxX = words.maxOf { it.boundingBox.x + it.boundingBox.width }
    val maxY = words.maxOf { it.boundingBox.y + it.boundingBox.height }

    // 텍스트 결합 (공백으로 구분)
    val text = words.joinToString(" ") { it.text }

    // 평균 신뢰도 계산
    val avgConfidence = words.map { it.confidence / 100f }.average().toFloat()

    return TextBlock(
      text = text,
      x = minX,
      y = minY,
      width = maxX - minX,
      height = maxY - minY,
      confidence = avgConfidence,
      blockType = BlockType.UNKNOWN
    )
  }

  /**
   * 텍스트 블록 타입 분류
   *
   * @param block 분류할 텍스트 블록
   * @param allBlocks 전체 블록 리스트 (평균 폰트 크기 계산용)
   * @param params 레이아웃 분석 파라미터
   * @return 분류된 BlockType
   */
  fun classifyTextBlock(
    block: TextBlock,
    allBlocks: List<TextBlock>,
    params: LayoutAnalysisParams = LayoutAnalysisParams.default()
  ): BlockType {
    // 신뢰도가 낮으면 UNKNOWN
    if (block.confidence < params.minConfidence) {
      return BlockType.UNKNOWN
    }

    val fontSize = block.estimateFontSize()
    val avgFontSize = allBlocks.map { it.estimateFontSize() }.average()

    return when {
      // 테이블 패턴 감지 (우선순위 높음)
      block.hasTablePattern() -> BlockType.TABLE

      // 리스트 패턴 감지
      block.hasListPattern() -> BlockType.LIST

      // 큰 폰트 → 헤딩
      fontSize > avgFontSize * params.headingSizeMultiplier -> BlockType.HEADING

      // 짧은 텍스트 + 약간 큰 폰트 → 캡션
      block.text.length < 50 && fontSize > avgFontSize * params.captionSizeMultiplier -> BlockType.CAPTION

      // 기본: 단락
      else -> BlockType.PARAGRAPH
    }
  }

  /**
   * 모든 텍스트 블록 타입 일괄 분류
   */
  fun classifyAllBlocks(
    blocks: List<TextBlock>,
    params: LayoutAnalysisParams = LayoutAnalysisParams.default()
  ): List<TextBlock> {
    return blocks.map { block ->
      block.copy(blockType = classifyTextBlock(block, blocks, params))
    }
  }

  /**
   * 디버깅: 추출된 텍스트 블록 정보 출력
   */
  fun printBlocksInfo(blocks: List<TextBlock>) {
    logger.info("=== 추출된 텍스트 블록 정보 ===")
    logger.info("총 블록 수: ${blocks.size}")

    blocks.forEachIndexed { index, block ->
      logger.info(
        "블록 $index: (${block.x}, ${block.y}) ${block.width}x${block.height} " +
            "타입=${block.blockType} 신뢰도=${String.format("%.2f", block.confidence)} " +
            "폰트=${block.estimateFontSize()}px 텍스트=\"${block.text.take(50)}\""
      )
    }
  }

  companion object {
    /**
     * 시스템 Tesseract 사용 (기본)
     */
    fun create(): TesseractTextExtractor {
      return TesseractTextExtractor()
    }

    /**
     * 커스텀 tessdata 경로 사용
     */
    fun create(tessdataPath: String, language: String = "kor+eng"): TesseractTextExtractor {
      return TesseractTextExtractor(tessdataPath, language)
    }
  }
}
