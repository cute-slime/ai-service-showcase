package com.jongmin.ai.insight.component.vlm

import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import java.awt.image.BufferedImage
import java.io.File

/**
 * Apache Commons Imaging을 활용한 이미지 유틸리티
 */
object CommonsImagingUtils {

  /**
   * 이미지 읽기 (PNG, BMP, TIFF 등)
   */
  fun readImage(imagePath: String): BufferedImage {
    val file = File(imagePath)
    return Imaging.getBufferedImage(file)
  }

  /**
   * PNG 포맷으로 이미지 저장
   * 참고: JPEG 쓰기는 지원하지 않으므로 ImageIO 사용 필요
   */
  fun writeImageAsPng(image: BufferedImage, outputPath: String) {
    val outputFile = File(outputPath)
    Imaging.writeImage(image, outputFile, ImageFormats.JPEG)
  }

  /**
   * JPEG EXIF 메타데이터 읽기
   */
  fun readExifMetadata(jpegPath: String): Map<String, String> {
    val file = File(jpegPath)
    val metadata = Imaging.getMetadata(file) as? JpegImageMetadata
      ?: return emptyMap()

    val result = mutableMapOf<String, String>()

    // GPS 정보
    metadata.exif?.let { exif ->
      exif.gpsInfo?.let { gps ->
        // GPS 좌표를 도(degrees) 형식으로 가져오기
        result["위도"] = gps.getLatitudeAsDegreesNorth().toString()
        result["경도"] = gps.getLongitudeAsDegreesEast().toString()
      }

      // 촬영 날짜
      exif.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)?.let {
        result["촬영일시"] = it.stringValue
      }

      // 카메라 모델 (TIFF 태그 사용)
      exif.findField(TiffTagConstants.TIFF_TAG_MODEL)?.let {
        result["카메라"] = it.stringValue
      }
    }

    return result
  }

  /**
   * 이미지 포맷 정보 확인
   */
  fun getImageInfo(imagePath: String): ImageInfo {
    val file = File(imagePath)
    val imageInfo = Imaging.getImageInfo(file)

    return ImageInfo(
      width = imageInfo.width,
      height = imageInfo.height,
      format = imageInfo.format.name,
      colorType = imageInfo.colorType.name,
      bitsPerPixel = imageInfo.bitsPerPixel
    )
  }

  /**
   * 이미지가 JPEG 포맷인지 확인
   */
  fun isJpeg(imagePath: String): Boolean {
    val file = File(imagePath)
    val imageInfo = Imaging.getImageInfo(file)
    return imageInfo.format.name.contains("JPEG", ignoreCase = true)
  }

  /**
   * 이미지가 PNG 포맷인지 확인
   */
  fun isPng(imagePath: String): Boolean {
    val file = File(imagePath)
    val imageInfo = Imaging.getImageInfo(file)
    return imageInfo.format.name.contains("PNG", ignoreCase = true)
  }
}
