package com.jongmin.ai.insight.component

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * 세로로 긴 상품 이미지를 개별 상품 이미지로 분할하는 클래스
 * - 픽셀 기반 휴리스틱 방식으로 흰색 구분선을 감지하여 이미지 분할
 * - VLM 방식과 비교하여 더 단순하고 빠르지만, 복잡한 레이아웃에는 제한적
 */
class PureImageSplitter {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 이미지 내 상품 영역을 나타내는 데이터 클래스
   * @param start 시작 Y 좌표 (픽셀)
   * @param end 종료 Y 좌표 (픽셀)
   */
  data class Region(val start: Int, val end: Int) {
    val height: Int get() = end - start
  }

  /**
   * 이미지를 분할하여 저장
   *
   * @param inputPath 입력 이미지 경로
   * @param outputDir 출력 디렉토리 경로
   * @param whiteThreshold 흰색으로 간주할 RGB 임계값 (기본: 250)
   * @param contentThreshold 상품이 있다고 판단할 최소 픽셀 수 (기본: 10)
   * @param minHeight 저장할 최소 이미지 높이 (기본: 50)
   * @return 저장된 파일 경로 리스트
   */
  fun splitAndSave(
    inputPath: String,
    outputDir: String,
    whiteThreshold: Int = 250,
    contentThreshold: Int = 10,
    minHeight: Int = 50
  ): List<String> {
    kLogger.info { "이미지 분할 시작 - 입력: $inputPath, 출력: $outputDir" }

    // 이미지 로드
    val image = ImageIO.read(File(inputPath))
    kLogger.debug { "이미지 크기: ${image.width}x${image.height}px" }

    // 각 행의 비-흰색 픽셀 수 계산
    val nonWhitePerRow = countNonWhitePixelsPerRow(image, whiteThreshold)
    kLogger.debug { "행별 비-흰색 픽셀 수 범위: ${nonWhitePerRow.minOrNull()} ~ ${nonWhitePerRow.maxOrNull()}" }

    // 상품 영역 찾기
    val regions = findContentRegions(nonWhitePerRow, contentThreshold)
    kLogger.info { "찾은 상품 이미지 영역 수: ${regions.size}" }

    // 최소 높이 필터링
    val validRegions = regions.filter { it.height >= minHeight }
    kLogger.info { "유효한 상품 이미지 수: ${validRegions.size} (최소 높이: ${minHeight}px)" }

    // 출력 디렉토리 생성 (스레드 안전)
    val outputDirectory = File(outputDir)
    Files.createDirectories(outputDirectory.toPath())

    // 각 영역을 별도 이미지로 저장
    val savedFiles = mutableListOf<String>()
    validRegions.forEachIndexed { index, region ->
      val subImage = image.getSubimage(0, region.start, image.width, region.height)
      val filename = "section_${String.format("%02d", index + 1)}.jpg"
      val outputPath = File(outputDir, filename)

      ImageIO.write(subImage, "jpg", outputPath)
      savedFiles.add(outputPath.absolutePath)

      kLogger.debug { "✓ $filename 저장 완료 (크기: ${subImage.width}x${subImage.height}px)" }
    }

    kLogger.info { "이미지 분할 완료 - 총 ${savedFiles.size}개의 이미지 생성" }
    return savedFiles
  }

  /**
   * 각 행의 비-흰색 픽셀 개수를 계산
   * - 흰색 구분선을 감지하기 위해 각 행에서 흰색이 아닌 픽셀의 개수를 계산
   * - threshold 값보다 낮은 RGB 값을 가진 픽셀을 비-흰색으로 간주
   */
  internal fun countNonWhitePixelsPerRow(image: BufferedImage, threshold: Int): IntArray {
    val height = image.height
    val width = image.width
    val nonWhitePerRow = IntArray(height)

    for (y in 0 until height) {
      var count = 0
      for (x in 0 until width) {
        val rgb = image.getRGB(x, y)
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF

        // 모든 RGB 값이 threshold보다 크면 흰색으로 간주
        if (red <= threshold || green <= threshold || blue <= threshold) {
          count++
        }
      }
      nonWhitePerRow[y] = count
    }

    return nonWhitePerRow
  }

  /**
   * 상품이 있는 연속된 영역 찾기
   * - 비-흰색 픽셀 수가 contentThreshold를 초과하는 연속된 행들을 하나의 영역으로 그룹화
   * - 상품과 상품 사이의 흰색 구분선을 기준으로 영역을 분리
   */
  internal fun findContentRegions(nonWhitePerRow: IntArray, contentThreshold: Int): List<Region> {
    val regions = mutableListOf<Region>()
    var inContent = false
    var startRow = 0

    nonWhitePerRow.forEachIndexed { index, count ->
      val hasContent = count > contentThreshold

      when {
        hasContent && !inContent -> {
          // 상품 영역 시작
          startRow = index
          inContent = true
        }

        !hasContent && inContent -> {
          // 상품 영역 끝
          regions.add(Region(startRow, index))
          inContent = false
        }
      }
    }

    // 마지막 영역 처리
    if (inContent) {
      regions.add(Region(startRow, nonWhitePerRow.size))
    }

    return regions
  }
}
