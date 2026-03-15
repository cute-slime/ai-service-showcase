package com.jongmin.ai.insight.component.vlm

import org.slf4j.LoggerFactory
import java.io.File
import javax.imageio.ImageIO

/**
 * 이미지 크롭 유틸리티
 */
class ImageCropper {
  private val logger = LoggerFactory.getLogger(ImageCropper::class.java)

  /**
   * 섹션 정보에 따라 이미지를 분할
   *
   * @param originalImagePath 원본 이미지 경로
   * @param sections 섹션 리스트
   * @param outputDir 출력 디렉토리
   * @return 생성된 파일 경로 리스트
   */
  fun splitImageBySections(
    originalImagePath: String,
    sections: List<ImageSection>,
    outputDir: String
  ): List<String> {
    val originalImage = ImageIO.read(File(originalImagePath))
    val outputDirectory = File(outputDir)

    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs()
    }

    logger.info("이미지 분할 시작...")

    val createdFiles = mutableListOf<String>()

    sections.forEach { section ->
      val height = section.endY - section.startY

      if (height > 0 && section.startY >= 0 && section.endY <= originalImage.height) {
        try {
          // 이미지 자르기
          val croppedImage = originalImage.getSubimage(
            0,
            section.startY,
            originalImage.width,
            height
          )

          // 파일 저장
          val fileName = "section_${section.index}_" +
              "${section.startY}-${section.endY}.jpg"
          val outputFile = File(outputDirectory, fileName)

          ImageIO.write(croppedImage, "jpg", outputFile)

          logger.info("섹션 생성: ${outputFile.name}")
          logger.debug("위치: ${section.startY}px ~ ${section.endY}px")
          logger.debug("설명: ${section.description}")

          createdFiles.add(outputFile.absolutePath)

        } catch (e: Exception) {
          logger.error("섹션 ${section.index} 처리 실패: ${e.message}", e)
        }
      } else {
        logger.warn("섹션 ${section.index} 스킵 (잘못된 좌표)")
      }
    }

    logger.info("이미지 분할 완료! 생성된 파일: ${createdFiles.size}개")
    return createdFiles
  }

  /**
   * 섹션 정보에 따라 이미지를 분할 (포맷 지정 가능)
   *
   * @param originalImagePath 원본 이미지 경로
   * @param sections 섹션 리스트
   * @param outputDir 출력 디렉토리
   * @param outputFormat 출력 포맷 ("jpg" 또는 "png")
   * @return 생성된 파일 경로 리스트
   */
  fun splitImageBySections(
    originalImagePath: String,
    sections: List<ImageSection>,
    outputDir: String,
    outputFormat: String = "jpg"
  ): List<String> {
    // ImageIO 사용 (JPEG 쓰기 지원)
    val originalImage = ImageIO.read(File(originalImagePath))
    val outputDirectory = File(outputDir)

    if (!outputDirectory.exists()) {
      outputDirectory.mkdirs()
    }

    logger.info("이미지 분할 시작...")

    val createdFiles = mutableListOf<String>()

    sections.forEach { section ->
      val height = section.endY - section.startY

      if (height > 0 && section.startY >= 0 && section.endY <= originalImage.height) {
        try {
          // 이미지 자르기
          val croppedImage = originalImage.getSubimage(
            0,
            section.startY,
            originalImage.width,
            height
          )

          val fileName = "section_${section.index}_" +
              "${section.startY}-${section.endY}.$outputFormat"
          val outputFile = File(outputDirectory, fileName)

          // JPEG는 ImageIO, PNG는 Commons Imaging 또는 ImageIO 사용 가능
          when (outputFormat.lowercase()) {
            "jpg", "jpeg" -> {
              // ImageIO 사용 (JPEG 쓰기 지원)
              ImageIO.write(croppedImage, "jpg", outputFile)
            }

            "png" -> {
              // ImageIO 또는 Commons Imaging 사용 가능
              ImageIO.write(croppedImage, "png", outputFile)
            }

            else -> {
              logger.warn("지원하지 않는 포맷: $outputFormat")
              return@forEach
            }
          }

          logger.info("섹션 생성: ${outputFile.name}")
          logger.debug("위치: ${section.startY}px ~ ${section.endY}px")
          logger.debug("설명: ${section.description}")

          createdFiles.add(outputFile.absolutePath)

        } catch (e: Exception) {
          logger.error("섹션 ${section.index} 처리 실패: ${e.message}", e)
        }
      }
    }

    logger.info("이미지 분할 완료!")
    return createdFiles
  }
}
