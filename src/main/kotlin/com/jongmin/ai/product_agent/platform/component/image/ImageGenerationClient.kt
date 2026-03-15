package com.jongmin.ai.product_agent.platform.component.image

/**
 * 이미지 생성 클라이언트 인터페이스
 *
 * Strategy 패턴을 적용하여 다양한 이미지 생성 모델을 유연하게 교체/추가할 수 있습니다.
 * 각 프로바이더(ComfyUI, DALL-E, Midjourney, Imagen 등)는 이 인터페이스를 구현합니다.
 *
 * ### 사용 예시:
 * ```kotlin
 * val client = imageGenerationClientRouter.getClient(ImageGenerationProvider.COMFYUI)
 * val result = client.generateImage(request)
 * if (result.success) {
 *   // 이미지 처리
 * }
 * ```
 */
interface ImageGenerationClient {

  /**
   * 이미지를 생성합니다.
   *
   * @param request 이미지 생성 요청
   * @return 이미지 생성 결과
   */
  fun generateImage(request: ImageGenerationRequest): ImageGenerationResult

  /**
   * 여러 이미지를 생성합니다.
   *
   * 기본 구현은 generateImage를 반복 호출합니다.
   * 배치 처리가 가능한 프로바이더는 오버라이드할 수 있습니다.
   *
   * @param request 이미지 생성 요청 (imageCount 필드 사용)
   * @return 이미지 생성 결과 목록
   */
  fun generateImages(request: ImageGenerationRequest): List<ImageGenerationResult> {
    return (1..request.imageCount).map { index ->
      // 각 이미지마다 다른 시드 사용
      val seedOffset = if (request.seed != null) request.seed + index else null
      generateImage(request.copy(seed = seedOffset, imageCount = 1))
    }
  }

  /**
   * 이 클라이언트의 프로바이더 타입을 반환합니다.
   *
   * @return 프로바이더 타입
   */
  fun getProviderType(): ImageGenerationProvider

  /**
   * 이 클라이언트가 현재 사용 가능한지 확인합니다.
   *
   * 서버 연결 상태, API 키 유효성 등을 확인할 수 있습니다.
   *
   * @return 사용 가능 여부
   */
  fun isAvailable(): Boolean

  /**
   * 클라이언트 표시 이름을 반환합니다.
   *
   * @return 표시 이름 (예: "ComfyUI (Z Image)")
   */
  fun getDisplayName(): String = getProviderType().displayName
}

/**
 * 이미지 생성 클라이언트 미구현 예외
 *
 * Placeholder 클라이언트에서 사용됩니다.
 */
class ImageGenerationNotImplementedException(
  provider: ImageGenerationProvider,
) : RuntimeException("${provider.displayName} 이미지 생성은 아직 구현되지 않았습니다.")
