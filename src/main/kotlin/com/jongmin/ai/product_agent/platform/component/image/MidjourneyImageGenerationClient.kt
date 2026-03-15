package com.jongmin.ai.product_agent.platform.component.image

import org.springframework.stereotype.Component

/**
 * Midjourney 이미지 생성 클라이언트 (Placeholder)
 *
 * Midjourney API를 사용하여 이미지를 생성합니다.
 * 현재는 미구현 상태이며, 향후 확장 예정입니다.
 */
@Component
class MidjourneyImageGenerationClient : ImageGenerationClient {

  override fun getProviderType(): ImageGenerationProvider = ImageGenerationProvider.MIDJOURNEY

  override fun isAvailable(): Boolean = false

  override fun generateImage(request: ImageGenerationRequest): ImageGenerationResult {
    throw ImageGenerationNotImplementedException(ImageGenerationProvider.MIDJOURNEY)
  }
}
