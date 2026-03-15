package com.jongmin.ai.generation.dto

import com.jongmin.ai.core.GenerationMediaType

/**
 * 에셋 생성 요청 컨텍스트
 *
 * 프로바이더가 에셋을 생성할 때 필요한 모든 정보를 담는 컨텍스트 객체.
 * DTE Job의 payload에서 추출하여 구성된다.
 *
 * ### 사용 예시:
 * ```kotlin
 * val context = GenerationContext(
 *   jobId = job.id,
 *   groupId = 1L,
 *   itemId = 10L,
 *   assetType = AssetType.BACKGROUND,
 *   mediaType = GenerationMediaType.IMAGE,
 *   providerCode = "COMFYUI",
 *   modelCode = "FLUX_DEV",
 *   promptConfig = mapOf("positive" to "...", "negative" to "..."),
 *   itemIndex = 0,
 *   totalItems = 7
 * )
 * provider.generate(context)
 * ```
 *
 * @property jobId DTE Job ID
 * @property groupId 에셋 그룹 ID (BackgroundAssetGroup 또는 CharacterAssetGroup)
 * @property itemId 에셋 아이템 ID (BackgroundAssetItem 또는 CharacterAssetItem)
 * @property assetType 에셋 타입 (BACKGROUND 또는 CHARACTER)
 * @property mediaType 미디어 타입 (IMAGE, VIDEO, BGM)
 * @property providerCode 프로바이더 코드 (예: "COMFYUI")
 * @property providerId 프로바이더 ID (비용 계산용, 선택)
 * @property modelCode 모델 코드 (예: "FLUX_DEV", "NAI_V3")
 * @property modelId 모델 ID (비용 계산용, 선택)
 * @property promptConfig 프롬프트 설정 (positive, negative 등)
 * @property generationConfig 생성 설정 (width, height, steps 등)
 * @property metadata 추가 메타데이터
 * @property requesterId 요청자 ID (선택)
 * @property correlationId 상관관계 ID (선택, 추적용)
 * @property itemIndex 현재 처리 중인 아이템 인덱스 (0-based)
 * @property totalItems 전체 아이템 수
 *
 * @author Claude Code
 * @since 2026.01.21
 */
data class GenerationContext(
  val jobId: String,
  val groupId: Long,
  val itemId: Long,
  val assetType: AssetType,
  val mediaType: GenerationMediaType,
  val providerCode: String,
  val providerId: Long? = null,
  val modelCode: String?,
  val modelId: Long? = null,
  val promptConfig: Map<String, Any>,
  val generationConfig: Map<String, Any> = emptyMap(),
  val metadata: Map<String, Any> = emptyMap(),
  val requesterId: Long? = null,
  val correlationId: String? = null,
  val itemIndex: Int = 0,
  val totalItems: Int = 1
) {
  /**
   * 프롬프트 설정에서 positive 프롬프트 추출
   */
  fun getPositivePrompt(): String? {
    return extractNestedValue("positive") as? String
  }

  /**
   * 프롬프트 설정에서 negative 프롬프트 추출
   */
  fun getNegativePrompt(): String? {
    return extractNestedValue("negative") as? String
  }

  /**
   * 중첩된 promptConfig에서 값 추출
   * 예: {"image": {"COMFYUI": {"positive": "..."}}} 구조 지원
   */
  @Suppress("UNCHECKED_CAST")
  private fun extractNestedValue(key: String): Any? {
    // 직접 키가 있으면 반환
    if (promptConfig.containsKey(key)) {
      return promptConfig[key]
    }

    // mediaType 기반으로 중첩 구조 탐색
    val mediaTypeKey = mediaType.name.lowercase()
    val mediaConfig = promptConfig[mediaTypeKey] as? Map<String, Any>
      ?: promptConfig["image"] as? Map<String, Any>
      ?: return null

    // providerCode 기반으로 추가 탐색
    val providerConfig = mediaConfig[providerCode] as? Map<String, Any>
      ?: mediaConfig

    return providerConfig[key]
  }

  // ========== 비용 계산용 헬퍼 메서드 ==========

  /**
   * 해상도 코드 추출 (예: "1024x1024")
   */
  fun getResolutionCode(): String? {
    val width = generationConfig["width"] as? Int
    val height = generationConfig["height"] as? Int
    return if (width != null && height != null) {
      "${width}x${height}"
    } else {
      generationConfig["resolutionCode"] as? String
        ?: generationConfig["resolution"] as? String
    }
  }

  /**
   * 품질 코드 추출 (예: "standard", "hd")
   */
  fun getQualityCode(): String? {
    return generationConfig["qualityCode"] as? String
      ?: generationConfig["quality"] as? String
  }

  /**
   * 스타일 코드 추출
   */
  fun getStyleCode(): String? {
    return generationConfig["styleCode"] as? String
      ?: generationConfig["style"] as? String
  }

  /**
   * 길이 추출 (초 단위, 영상/음악용)
   */
  fun getDurationSec(): Int? {
    return (generationConfig["durationSec"] as? Number)?.toInt()
      ?: (generationConfig["duration"] as? Number)?.toInt()
  }
}

/**
 * 에셋 타입
 */
enum class AssetType {
  /** 배경 에셋 */
  BACKGROUND,

  /** 캐릭터 에셋 */
  CHARACTER
}
