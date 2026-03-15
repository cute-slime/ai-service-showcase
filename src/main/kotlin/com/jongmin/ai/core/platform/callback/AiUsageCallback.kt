package com.jongmin.ai.core.platform.callback

import com.jongmin.ai.core.platform.dto.CacheUsageInfo

/**
 * AI 사용량 수집 콜백 인터페이스
 *
 * AI 호출 완료 후 토큰 사용량 정보를 수집하기 위한 콜백입니다.
 * 각 AI 호출 위치에서 이 인터페이스를 통해 사용량을 보고할 수 있습니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
interface AiUsageCallback {

  /**
   * 사용량 정보 수집
   *
   * @param usage 수집된 캐시 사용량 정보
   * @param runStepId AiRunStep ID (있는 경우)
   * @param rawUsage 원본 API 응답의 usage 객체 (디버깅/저장용)
   */
  fun onUsageCollected(
    usage: CacheUsageInfo,
    runStepId: Long? = null,
    rawUsage: Map<String, Any>? = null
  )

  /**
   * 사용량 수집 실패 시 호출
   *
   * @param provider 프로바이더명
   * @param model 모델명
   * @param error 발생한 예외
   * @param runStepId AiRunStep ID (있는 경우)
   */
  fun onUsageCollectionFailed(
    provider: String,
    model: String,
    error: Throwable,
    runStepId: Long? = null
  )
}
