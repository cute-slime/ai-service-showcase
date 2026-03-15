package com.jongmin.ai.insight.component.tesseract_layout

import com.jongmin.ai.insight.component.vlm.ImageSection
import org.slf4j.LoggerFactory

/**
 * 텍스트 블록 간 여백을 분석하여 섹션 경계를 감지하는 클래스
 */
class SectionBoundaryDetector {
  private val logger = LoggerFactory.getLogger(SectionBoundaryDetector::class.java)

  /**
   * 텍스트 블록 간 여백을 분석하여 섹션 경계 Y 좌표 리스트 반환
   *
   * @param textBlocks 텍스트 블록 리스트
   * @param imageHeight 이미지 전체 높이
   * @param params 레이아웃 분석 파라미터
   * @return 섹션 경계 Y 좌표 리스트
   */
  fun findSectionBoundaries(
    textBlocks: List<TextBlock>,
    imageHeight: Int,
    params: LayoutAnalysisParams = LayoutAnalysisParams.default()
  ): List<Int> {
    if (textBlocks.isEmpty()) {
      logger.warn("텍스트 블록이 없습니다. 빈 경계 리스트 반환")
      return emptyList()
    }

    // Y 좌표로 정렬
    val sortedBlocks = textBlocks.sortedBy { it.y }
    val boundaries = mutableListOf<Int>()

    logger.info("섹션 경계 감지 시작 - 블록 수: ${sortedBlocks.size}, 임계값: ${params.minGapThreshold}px")

    for (i in 0 until sortedBlocks.size - 1) {
      val currentBlock = sortedBlocks[i]
      val nextBlock = sortedBlocks[i + 1]

      // 현재 블록의 끝과 다음 블록의 시작 사이 간격
      val gap = nextBlock.y - (currentBlock.y + currentBlock.height)

      // 다음 블록이 헤딩인지 확인
      val isNextHeading = nextBlock.blockType == BlockType.HEADING

      // 경계 생성 조건:
      // 1. 여백이 임계값보다 큰 경우
      // 2. 다음 블록이 HEADING인 경우 (콘텐츠 변경 지점)
      // 3. 현재 블록이 TABLE이고 다음이 TABLE이 아닌 경우 (테이블 종료)
      val isTableEnd = currentBlock.blockType == BlockType.TABLE && nextBlock.blockType != BlockType.TABLE

      if (gap > params.minGapThreshold || isNextHeading || isTableEnd) {
        // 경계는 다음 블록 바로 직전 (컨텐츠 잘림 방지)
        val boundaryY = nextBlock.y

        logger.debug(
          "경계 발견: Y=$boundaryY (블록 $i → ${i + 1}, 여백=${gap}px, " +
              "다음헤딩=$isNextHeading, 테이블종료=$isTableEnd)"
        )

        boundaries.add(boundaryY)
      }
    }

    logger.info("섹션 경계 ${boundaries.size}개 감지 완료")
    return boundaries
  }

  /**
   * 경계를 기반으로 섹션 생성
   *
   * @param boundaries 섹션 경계 Y 좌표 리스트
   * @param textBlocks 텍스트 블록 리스트
   * @param imageHeight 이미지 전체 높이
   * @param imageWidth 이미지 너비 (선택)
   * @param params 레이아웃 분석 파라미터
   * @return 생성된 섹션 리스트
   */
  fun createSections(
    boundaries: List<Int>,
    textBlocks: List<TextBlock>,
    imageHeight: Int,
    imageWidth: Int? = null,
    params: LayoutAnalysisParams = LayoutAnalysisParams.default()
  ): List<ImageSection> {
    // 경계를 기준으로 섹션 범위 결정
    val allBoundaries = listOf(0) + boundaries.sorted() + listOf(imageHeight)
    val sections = mutableListOf<ImageSection>()

    logger.info("섹션 생성 시작 - 경계 수: ${boundaries.size}, 예상 섹션 수: ${allBoundaries.size - 1}")

    for (i in 0 until allBoundaries.size - 1) {
      val startY = allBoundaries[i]
      val endY = allBoundaries[i + 1]
      val sectionHeight = endY - startY

      // 최소 높이 미달 섹션은 이전 섹션과 병합
      if (sectionHeight < params.minSectionHeight && sections.isNotEmpty()) {
        val lastSection = sections.removeLast()
        val mergedSection = lastSection.copy(endY = endY)

        logger.debug(
          "섹션 병합: ${lastSection.startY}-${lastSection.endY} + $startY-$endY " +
              "→ ${mergedSection.startY}-${mergedSection.endY} (높이: ${mergedSection.height}px)"
        )

        sections.add(mergedSection)
        continue
      }

      // 해당 범위의 텍스트 블록 찾기
      val blocksInSection = textBlocks.filter { block ->
        block.y >= startY && block.y < endY
      }

      // 섹션 설명 생성
      val description = generateSectionDescription(blocksInSection, startY, endY)
      val contentType = determineContentType(blocksInSection)

      sections.add(
        ImageSection(
          index = sections.size + 1,
          startY = startY,
          endY = endY,
          description = description,
          contentType = contentType
        )
      )

      logger.debug(
        "섹션 ${sections.size} 생성: $startY-$endY (${sectionHeight}px), " +
            "블록 수: ${blocksInSection.size}, 타입: $contentType"
      )
    }

    logger.info("섹션 생성 완료: ${sections.size}개")
    return sections
  }

  /**
   * 섹션 설명 생성
   *
   * 텍스트 블록 내용을 기반으로 섹션의 간략한 설명 생성
   */
  private fun generateSectionDescription(
    blocks: List<TextBlock>,
    startY: Int,
    endY: Int
  ): String {
    val headings = blocks.filter { it.blockType == BlockType.HEADING }
    val hasTable = blocks.any { it.blockType == BlockType.TABLE }
    val hasList = blocks.any { it.blockType == BlockType.LIST }

    return when {
      // 헤딩이 있는 경우: 첫 헤딩 텍스트 사용
      headings.isNotEmpty() -> {
        val mainHeading = headings.first().text.take(50).trim()
        when {
          hasTable -> "$mainHeading (with table)"
          hasList -> "$mainHeading (with list)"
          else -> mainHeading
        }
      }

      // 헤딩은 없지만 테이블이 있는 경우
      hasTable -> "Data table or specification chart"

      // 헤딩은 없지만 리스트가 있는 경우
      hasList -> "List content"

      // 텍스트 블록이 없는 경우 (이미지 영역)
      blocks.isEmpty() -> "Image content (no text detected)"

      // 일반 본문
      else -> {
        val firstText = blocks.firstOrNull()?.text?.take(30)?.trim() ?: "Content section"
        "Content: $firstText..."
      }
    }
  }

  /**
   * 섹션의 콘텐츠 타입 결정
   */
  private fun determineContentType(blocks: List<TextBlock>): String {
    return when {
      blocks.any { it.blockType == BlockType.TABLE } -> "table"
      blocks.any { it.blockType == BlockType.HEADING } -> "feature"
      blocks.any { it.blockType == BlockType.LIST } -> "list"
      blocks.isEmpty() -> "image"
      blocks.all { it.blockType == BlockType.CAPTION } -> "caption"
      else -> "content"
    }
  }

  /**
   * 섹션 유효성 검증
   *
   * @throws IllegalStateException 유효하지 않은 섹션이 있는 경우
   */
  fun validateSections(sections: List<ImageSection>, imageHeight: Int) {
    sections.forEach { section ->
      require(section.startY >= 0) {
        "섹션 ${section.index}의 startY(${section.startY})는 0 이상이어야 합니다"
      }
      require(section.endY <= imageHeight) {
        "섹션 ${section.index}의 endY(${section.endY})는 이미지 높이($imageHeight) 이하여야 합니다"
      }
      require(section.endY > section.startY) {
        "섹션 ${section.index}의 endY(${section.endY})는 startY(${section.startY})보다 커야 합니다"
      }
    }

    // 섹션 간 겹침 검사
    for (i in 0 until sections.size - 1) {
      val current = sections[i]
      val next = sections[i + 1]
      require(current.endY <= next.startY) {
        "섹션 ${current.index}와 ${next.index}가 겹칩니다: ${current.endY} > ${next.startY}"
      }
    }

    logger.info("섹션 유효성 검증 완료: ${sections.size}개 섹션")
  }

  /**
   * 디버깅: 섹션 정보 출력
   */
  fun printSectionsInfo(sections: List<ImageSection>) {
    logger.info("=== 생성된 섹션 정보 ===")
    logger.info("총 섹션 수: ${sections.size}")

    sections.forEach { section ->
      logger.info(
        "섹션 ${section.index}: ${section.startY}-${section.endY}px (${section.height}px) " +
            "타입=${section.contentType} 설명=\"${section.description}\""
      )
    }
  }
}
