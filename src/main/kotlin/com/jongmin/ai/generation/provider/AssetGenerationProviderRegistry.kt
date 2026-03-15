package com.jongmin.ai.generation.provider

import com.jongmin.ai.core.GenerationMediaType
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * 에셋 생성 프로바이더 레지스트리
 *
 * Strategy 패턴의 Context 역할을 수행하며, 모든 [AssetGenerationProvider] 구현체를
 * 자동으로 수집하고 providerCode 기반으로 조회할 수 있도록 한다.
 *
 * ### 사용 예시:
 * ```kotlin
 * val provider = registry.getProvider("COMFYUI")
 * val result = provider.generate(context)
 *
 * // 또는 미디어 타입별 조회
 * val imageProviders = registry.getProvidersByMediaType(GenerationMediaType.IMAGE)
 * ```
 *
 * ### 프로바이더 등록:
 * 1. [AssetGenerationProvider] 인터페이스 구현
 * 2. @Component 어노테이션 추가
 * 3. Spring이 자동으로 주입 → Registry에 등록
 *
 * @author Claude Code
 * @since 2026.01.21
 */
@Component
class AssetGenerationProviderRegistry(
  providers: List<AssetGenerationProvider>
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * 프로바이더 코드 → 프로바이더 맵
   */
  private val providerMap: Map<String, AssetGenerationProvider> =
    providers.associateBy { it.getProviderCode().uppercase() }

  /**
   * 미디어 타입 → 프로바이더 목록 맵
   */
  private val mediaTypeProviderMap: Map<GenerationMediaType, List<AssetGenerationProvider>> =
    providers.flatMap { provider ->
      provider.getSupportedMediaTypes().map { mediaType -> mediaType to provider }
    }.groupBy({ it.first }, { it.second })

  @PostConstruct
  fun init() {
    kLogger.info {
      "[ProviderRegistry] 초기화 완료 - " +
          "providers: ${providerMap.keys}, " +
          "mediaTypes: ${mediaTypeProviderMap.mapValues { it.value.map { p -> p.getProviderCode() } }}"
    }
  }

  /**
   * 프로바이더 코드로 프로바이더 조회
   *
   * @param providerCode 프로바이더 코드 (대소문자 무관)
   * @return 프로바이더 구현체
   * @throws IllegalArgumentException 등록되지 않은 프로바이더 코드인 경우
   */
  fun getProvider(providerCode: String): AssetGenerationProvider {
    val code = providerCode.uppercase()
    return providerMap[code]
      ?: throw IllegalArgumentException("등록되지 않은 프로바이더: $providerCode (등록된 프로바이더: ${providerMap.keys})")
  }

  /**
   * 프로바이더 코드로 프로바이더 조회 (없으면 null)
   *
   * @param providerCode 프로바이더 코드 (대소문자 무관)
   * @return 프로바이더 구현체 또는 null
   */
  fun getProviderOrNull(providerCode: String): AssetGenerationProvider? {
    return providerMap[providerCode.uppercase()]
  }

  /**
   * 미디어 타입으로 프로바이더 목록 조회
   *
   * @param mediaType 미디어 타입
   * @return 해당 미디어 타입을 지원하는 프로바이더 목록
   */
  fun getProvidersByMediaType(mediaType: GenerationMediaType): List<AssetGenerationProvider> {
    return mediaTypeProviderMap[mediaType] ?: emptyList()
  }

  /**
   * 특정 미디어 타입의 사용 가능한 프로바이더 조회
   *
   * isAvailable() == true인 프로바이더만 반환
   *
   * @param mediaType 미디어 타입
   * @return 사용 가능한 프로바이더 목록
   */
  fun getAvailableProvidersByMediaType(mediaType: GenerationMediaType): List<AssetGenerationProvider> {
    return getProvidersByMediaType(mediaType).filter { it.isAvailable() }
  }

  /**
   * 모든 등록된 프로바이더 코드 목록
   */
  fun getAllProviderCodes(): Set<String> = providerMap.keys

  /**
   * 모든 등록된 프로바이더 목록
   */
  fun getAllProviders(): Collection<AssetGenerationProvider> = providerMap.values

  /**
   * 프로바이더 사용 가능 여부 확인
   *
   * @param providerCode 프로바이더 코드
   * @return 사용 가능 여부 (미등록인 경우 false)
   */
  fun isProviderAvailable(providerCode: String): Boolean {
    return getProviderOrNull(providerCode)?.isAvailable() ?: false
  }
}
