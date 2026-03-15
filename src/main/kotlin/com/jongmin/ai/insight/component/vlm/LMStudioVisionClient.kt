package com.jongmin.ai.insight.component.vlm

import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.http.client.jdk.JdkHttpClient
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.readValue
import java.io.File
import java.net.http.HttpClient
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.createTempFile

/**
 * LM Studio를 통해 Qwen3-VL을 사용하는 Vision 클라이언트
 */
class LMStudioVisionClient(
  private val baseUrl: String = "http://192.168.0.105:1234",
  private val modelName: String = "qwen3-vl-8b-instruct",
  private val apiKey: String = "not-needed",  // LM Studio는 API 키 불필요
  private val maxVlmWidth: Int = 1024  // VLM 처리를 위한 최대 이미지 너비
) {
  private val logger = LoggerFactory.getLogger(LMStudioVisionClient::class.java)

  private val objectMapper: ObjectMapper = jsonMapper {
    // 알려지지 않은 속성 무시
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  /**
   * Langchain4j ChatModel 초기화
   */
  private val chatModel: ChatModel = OpenAiChatModel.builder()
    .baseUrl("$baseUrl/v1")  // LM Studio OpenAI 호환 엔드포인트
    .apiKey(apiKey)
    .modelName(modelName)
    .temperature(0.1)  // 낮은 temperature로 일관성 있는 결과 생성
    .maxTokens(3000)
    .logRequests(false)  // 디버깅용 - 프로덕션에서는 false
    .logResponses(true)
    .httpClientBuilder(
      JdkHttpClient
        .builder()
        .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1))
    )
    .build()

  /**
   * 이미지를 Base64 Data URL로 인코딩
   */
  fun encodeImageToDataUrl(imagePath: String): String {
    val imageFile = File(imagePath)
    val imageBytes = imageFile.readBytes()
    val base64Image = Base64.getEncoder().encodeToString(imageBytes)

    // MIME 타입 감지
    val mimeType = when (imageFile.extension.lowercase()) {
      "jpg", "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      "webp" -> "image/webp"
      else -> "image/jpeg"
    }

    return "data:$mimeType;base64,$base64Image"
  }

  /**
   * 이미지의 실제 크기 정보를 가져옴
   */
  fun getImageDimensions(imagePath: String): ImageDimensions {
    val image = ImageIO.read(File(imagePath))
    val needsResize = image.width > maxVlmWidth
    return ImageDimensions(
      width = image.width,
      height = image.height,
      needsResize = needsResize
    )
  }

  /**
   * VLM 처리를 위해 이미지를 적정 크기로 리사이즈
   *
   * @param imagePath 원본 이미지 경로
   * @return 리사이즈된 임시 파일 경로 (리사이즈 불필요 시 원본 경로 반환)
   */
  fun resizeImageForVlm(imagePath: String, originalDimensions: ImageDimensions): String {
    if (!originalDimensions.needsResize) {
      logger.info("이미지 크기 적절 (${originalDimensions.width}x${originalDimensions.height}) - 리사이즈 생략")
      return imagePath
    }

    // 임시 파일 생성
    val tempFile = createTempFile(prefix = "vlm_resized_", suffix = ".jpg").toFile()
    tempFile.deleteOnExit()

    // 비율 유지하며 리사이즈
    val scaleFactor = maxVlmWidth.toDouble() / originalDimensions.width
    val newHeight = (originalDimensions.height * scaleFactor).toInt()

    logger.info("이미지 리사이즈: ${originalDimensions.width}x${originalDimensions.height} → ${maxVlmWidth}x${newHeight}")

    Thumbnails.of(File(imagePath))
      .size(maxVlmWidth, newHeight)
      .outputFormat("jpg")
      .outputQuality(0.9)  // 고품질 유지
      .toFile(tempFile)

    logger.info("VLM 처리용 임시 파일 생성: ${tempFile.name}")
    return tempFile.absolutePath
  }

  /**
   * 리사이즈된 이미지 좌표를 원본 이미지 좌표로 변환
   */
  fun scaleCoordinates(
    sections: List<ImageSection>,
    originalWidth: Int,
    originalHeight: Int,
    vlmWidth: Int,
    vlmHeight: Int
  ): List<ImageSection> {
    if (originalWidth == vlmWidth && originalHeight == vlmHeight) {
      return sections  // 리사이즈 안 한 경우 그대로 반환
    }

    val scaleX = originalWidth.toDouble() / vlmWidth
    val scaleY = originalHeight.toDouble() / vlmHeight

    logger.info("좌표 스케일 변환: scaleX=${String.format("%.2f", scaleX)}, scaleY=${String.format("%.2f", scaleY)}")

    return sections.map { section ->
      section.copy(
        startY = (section.startY * scaleY).toInt(),
        endY = (section.endY * scaleY).toInt()
      )
    }
  }

  /**
   * 이미지를 의미 기반으로 분석하여 섹션 분할 지점 추출
   */
  fun analyzeImageSections(imagePath: String): ImageAnalysisResult {
    logger.info("이미지 분석 시작...")

    // 1. 원본 이미지 크기 확인
    val originalDimensions = getImageDimensions(imagePath)
    logger.info("원본 이미지: ${originalDimensions.width}x${originalDimensions.height}")

    // 2. VLM 처리를 위한 이미지 리사이즈 (필요시)
    val vlmImagePath = resizeImageForVlm(imagePath, originalDimensions)
    val vlmDimensions = if (originalDimensions.needsResize) {
      getImageDimensions(vlmImagePath)
    } else {
      originalDimensions
    }

    // 3. VLM 분석용 이미지 인코딩
    val dataUrl = encodeImageToDataUrl(vlmImagePath)

    // 4. 프롬프트 구성 (VLM 처리 이미지 기준)
    val promptText = """
            Analyze this long vertical image and split it into individual content objects.

            Image height: ${vlmDimensions.height}px

            CRITICAL: Each section must contain ONLY ONE distinct object or content block.

            What is ONE object:
            - ONE product image (front view, back view, detail shot - each separate)
            - ONE text block with heading
            - ONE table or chart
            - ONE diagram or illustration
            - ONE feature description block

            Split strategy:
            1. Find each distinct visual object from top to bottom
            2. Each object = one section (do NOT merge multiple objects)
            3. Separate objects by whitespace, lines, or clear boundaries
            4. If you see 5 product images, create 5 sections

            Respond with JSON only:

            {
              "sections": [
                {
                  "index": 1,
                  "startY": 0,
                  "endY": 500,
                  "description": "product front view",
                  "contentType": "image"
                },
                {
                  "index": 2,
                  "startY": 500,
                  "endY": 1000,
                  "description": "product back view",
                  "contentType": "image"
                }
              ],
              "totalHeight": ${vlmDimensions.height}
            }

            Rules:
            - One object = one section (do NOT combine objects)
            - Minimum section: 200px
            - No gaps or overlaps
            - startY and endY: 0-${vlmDimensions.height}
            - Output JSON only
        """.trimIndent()

    // 5. UserMessage 생성 (텍스트 + 이미지)
    val userMessage = UserMessage.from(
      TextContent.from(promptText),
      ImageContent.from(dataUrl)
    )

    logger.info("Qwen3-VL(${baseUrl}) 모델에 이미지 분석 요청 중...")

    // 6. 모델 호출
    val response = chatModel.chat(userMessage)

    val responseText = response.aiMessage()?.text()
      ?: throw IllegalStateException("[LMStudioVisionClient] VLM 응답이 null입니다. response=$response")

    logger.info("VLM 응답 받음")

    // 7. JSON 파싱 (VLM 이미지 기준 좌표)
    val vlmResult = parseJsonResponse(responseText, vlmDimensions.height)

    // 8. 좌표를 원본 이미지 기준으로 스케일 변환
    val scaledSections = scaleCoordinates(
      sections = vlmResult.sections,
      originalWidth = originalDimensions.width,
      originalHeight = originalDimensions.height,
      vlmWidth = vlmDimensions.width,
      vlmHeight = vlmDimensions.height
    )

    logger.info("이미지 분석 완료!")

    // 9. 최종 결과 반환 (원본 이미지 기준)
    return ImageAnalysisResult(
      sections = scaledSections,
      totalHeight = originalDimensions.height,
      imageWidth = originalDimensions.width
    )
  }

  /**
   * 모델 응답에서 JSON 추출 및 파싱
   */
  fun parseJsonResponse(responseText: String, imageHeight: Int): ImageAnalysisResult {
    return try {
      // Markdown 코드 블록 제거
      val cleanedJson = responseText
        .replace("```json", "")
        .replace("```", "")
        .trim()

      // JSON 파싱
      val result = objectMapper.readValue<ImageAnalysisResult>(cleanedJson)

      // VLM이 반환한 totalHeight는 부정확할 수 있으므로 실제 이미지 높이 사용
      logger.info("VLM 응답 totalHeight: ${result.totalHeight}, 실제 이미지 높이: $imageHeight")

      // 섹션 정규화 및 클램핑
      val normalizedSections = normalizeAndClampSections(result.sections, imageHeight)

      // 유효성 검증 (정규화된 섹션으로)
      validateSections(normalizedSections, imageHeight)

      // 실제 이미지 높이로 결과 반환
      ImageAnalysisResult(
        sections = normalizedSections,
        totalHeight = imageHeight,
        imageWidth = null
      )
    } catch (e: Exception) {
      logger.error("JSON 파싱 실패: ${e.message}")
      logger.debug("원본 응답: $responseText")

      // 폴백: 단순 분할
      ImageAnalysisResult(
        sections = createFallbackSections(imageHeight),
        totalHeight = imageHeight
      )
    }
  }

  /**
   * 섹션 좌표를 정규화하고 이미지 범위 내로 클램핑
   */
  private fun normalizeAndClampSections(
    sections: List<ImageSection>,
    imageHeight: Int
  ): List<ImageSection> {
    return sections.map { section ->
      // startY를 0 이상으로 클램핑
      val clampedStartY = section.startY.coerceIn(0, imageHeight)

      // endY를 이미지 높이 이하로 클램핑
      val clampedEndY = section.endY.coerceIn(clampedStartY + 1, imageHeight)

      if (clampedStartY != section.startY || clampedEndY != section.endY) {
        logger.warn(
          "섹션 ${section.index} 좌표 보정: " +
              "원본(${section.startY}-${section.endY}) → " +
              "보정($clampedStartY-$clampedEndY)"
        )
      }

      section.copy(
        startY = clampedStartY,
        endY = clampedEndY
      )
    }.filter { it.height >= ImageSection.MIN_SECTION_HEIGHT }
      .also { filteredSections ->
        if (filteredSections.size < sections.size) {
          logger.warn(
            "최소 높이(${ImageSection.MIN_SECTION_HEIGHT}px) 미달 섹션 ${sections.size - filteredSections.size}개 제거됨"
          )
        }
      }
  }

  /**
   * 섹션 유효성 검증
   */
  fun validateSections(sections: List<ImageSection>, imageHeight: Int) {
    sections.forEach { section ->
      require(section.startY >= 0) { "startY는 0 이상이어야 합니다" }
      require(section.endY <= imageHeight) { "endY는 이미지 높이 이하여야 합니다" }
      require(section.endY > section.startY) { "endY는 startY보다 커야 합니다" }
      require(section.endY - section.startY >= ImageSection.MIN_SECTION_HEIGHT) {
        "섹션 높이는 최소 ${ImageSection.MIN_SECTION_HEIGHT}px 이상이어야 합니다"
      }
    }
  }

  /**
   * AI 분석 실패 시 폴백 분할 (이미지를 3등분)
   */
  fun createFallbackSections(imageHeight: Int): List<ImageSection> {
    val sectionHeight = imageHeight / 3
    return listOf(
      ImageSection(1, 0, sectionHeight, "상단 영역", "auto"),
      ImageSection(2, sectionHeight, sectionHeight * 2, "중간 영역", "auto"),
      ImageSection(3, sectionHeight * 2, imageHeight, "하단 영역", "auto")
    )
  }
}
