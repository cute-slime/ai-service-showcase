package com.jongmin.ai.product_agent.platform.component.image

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 이미지 생성 클라이언트 라우터
 *
 * 프로바이더별로 적절한 이미지 생성 클라이언트를 반환합니다.
 * Strategy 패턴의 Context 역할을 수행합니다.
 *
 * ### 사용 예시:
 * ```kotlin
 * val client = router.getClient(ImageGenerationProvider.COMFYUI)
 * val result = client.generateImage(request)
 * ```
 *
 * @property clients 등록된 모든 이미지 생성 클라이언트
 */
@Component
class ImageGenerationClientRouter(
  private val clients: List<ImageGenerationClient>,
) {
  private val kLogger = KotlinLogging.logger {}

  // 프로바이더별 클라이언트 맵
  private val clientMap: Map<ImageGenerationProvider, ImageGenerationClient> by lazy {
    clients.associateBy { it.getProviderType() }.also {
      kLogger.info { "이미지 생성 클라이언트 등록 완료: ${it.keys.map { p -> p.displayName }}" }
    }
  }

  /**
   * 프로바이더에 해당하는 클라이언트를 반환합니다.
   *
   * @param provider 이미지 생성 프로바이더
   * @return 해당 프로바이더의 클라이언트
   * @throws IllegalArgumentException 해당 프로바이더가 등록되지 않은 경우
   */
  fun getClient(provider: ImageGenerationProvider): ImageGenerationClient {
    return clientMap[provider]
      ?: throw IllegalArgumentException("등록되지 않은 이미지 생성 프로바이더: ${provider.displayName}")
  }

  /**
   * 기본 클라이언트(ComfyUI)를 반환합니다.
   *
   * @return 기본 이미지 생성 클라이언트
   */
  fun getDefaultClient(): ImageGenerationClient {
    return getClient(ImageGenerationProvider.getDefault())
  }

  /**
   * 사용 가능한 클라이언트 목록을 반환합니다.
   *
   * @return 현재 사용 가능한 클라이언트 목록
   */
  fun getAvailableClients(): List<ImageGenerationClient> {
    return clients.filter { it.isAvailable() }
  }

  /**
   * 모든 등록된 프로바이더 목록을 반환합니다.
   *
   * @return 등록된 프로바이더 목록
   */
  fun getRegisteredProviders(): List<ImageGenerationProvider> {
    return clientMap.keys.toList()
  }

  /**
   * 특정 프로바이더가 사용 가능한지 확인합니다.
   *
   * @param provider 확인할 프로바이더
   * @return 사용 가능 여부
   */
  fun isProviderAvailable(provider: ImageGenerationProvider): Boolean {
    return clientMap[provider]?.isAvailable() ?: false
  }
}
