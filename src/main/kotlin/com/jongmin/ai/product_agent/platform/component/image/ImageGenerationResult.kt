package com.jongmin.ai.product_agent.platform.component.image

/**
 * 이미지 생성 결과 DTO
 *
 * 이미지 생성 클라이언트가 반환하는 결과 정보를 담습니다.
 * 프로바이더에 독립적인 공통 인터페이스를 제공합니다.
 *
 * @property success 생성 성공 여부
 * @property imageBytes 생성된 이미지 바이트 배열 (null이면 실패)
 * @property s3Key S3에 업로드된 경우 키 (옵션)
 * @property provider 사용된 프로바이더
 * @property prompt 사용된 프롬프트
 * @property negativePrompt 사용된 네거티브 프롬프트
 * @property width 생성된 이미지 너비
 * @property height 생성된 이미지 높이
 * @property seed 사용된 시드값
 * @property generationTimeMs 생성 소요 시간 (밀리초)
 * @property errorMessage 에러 발생 시 메시지
 * @property metadata 추가 메타데이터
 */
data class ImageGenerationResult(
  val success: Boolean,
  val imageBytes: ByteArray? = null,
  val s3Key: String? = null,
  val provider: ImageGenerationProvider,
  val prompt: String,
  val negativePrompt: String,
  val width: Int,
  val height: Int,
  val seed: Long,
  val generationTimeMs: Long = 0,
  val errorMessage: String? = null,
  val metadata: Map<String, Any> = emptyMap(),
) {
  companion object {
    /**
     * 성공 결과 생성
     */
    fun success(
      imageBytes: ByteArray,
      provider: ImageGenerationProvider,
      prompt: String,
      negativePrompt: String,
      width: Int,
      height: Int,
      seed: Long,
      generationTimeMs: Long,
      s3Key: String? = null,
      metadata: Map<String, Any> = emptyMap(),
    ): ImageGenerationResult {
      return ImageGenerationResult(
        success = true,
        imageBytes = imageBytes,
        s3Key = s3Key,
        provider = provider,
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        seed = seed,
        generationTimeMs = generationTimeMs,
        metadata = metadata,
      )
    }

    /**
     * 실패 결과 생성
     */
    fun failure(
      provider: ImageGenerationProvider,
      prompt: String,
      negativePrompt: String,
      width: Int,
      height: Int,
      seed: Long,
      errorMessage: String,
      generationTimeMs: Long = 0,
    ): ImageGenerationResult {
      return ImageGenerationResult(
        success = false,
        provider = provider,
        prompt = prompt,
        negativePrompt = negativePrompt,
        width = width,
        height = height,
        seed = seed,
        generationTimeMs = generationTimeMs,
        errorMessage = errorMessage,
      )
    }
  }

  /**
   * 이미지 크기 (바이트)
   */
  val imageSizeBytes: Int
    get() = imageBytes?.size ?: 0

  /**
   * 이미지 존재 여부
   */
  val hasImage: Boolean
    get() = imageBytes != null && imageBytes.isNotEmpty()

  // ByteArray의 equals/hashCode 재정의
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ImageGenerationResult

    if (success != other.success) return false
    if (imageBytes != null) {
      if (other.imageBytes == null) return false
      if (!imageBytes.contentEquals(other.imageBytes)) return false
    } else if (other.imageBytes != null) return false
    if (s3Key != other.s3Key) return false
    if (provider != other.provider) return false
    if (prompt != other.prompt) return false
    if (width != other.width) return false
    if (height != other.height) return false
    if (seed != other.seed) return false

    return true
  }

  override fun hashCode(): Int {
    var result = success.hashCode()
    result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
    result = 31 * result + (s3Key?.hashCode() ?: 0)
    result = 31 * result + provider.hashCode()
    result = 31 * result + prompt.hashCode()
    result = 31 * result + width
    result = 31 * result + height
    result = 31 * result + seed.hashCode()
    return result
  }
}
