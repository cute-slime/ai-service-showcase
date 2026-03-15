package com.jongmin.ai.core.platform.parser

import com.jongmin.ai.core.platform.dto.CacheUsageInfo

/**
 * 프로바이더 독립적 캐시 사용량 파서 인터페이스
 *
 * 각 AI 프로바이더의 응답 형식에 맞는 캐시 정보 추출 로직을 구현합니다.
 * 새로운 프로바이더 지원 시 이 인터페이스를 구현하면 됩니다.
 *
 * @author Jongmin
 * @since 2025. 12. 25
 */
interface CacheUsageParser {

  /**
   * 이 파서가 해당 프로바이더를 처리할 수 있는지 확인
   *
   * @param provider 프로바이더명 (대소문자 무관)
   * @return 처리 가능 여부
   */
  fun canParse(provider: String): Boolean

  /**
   * 원시 응답에서 캐시 사용량 정보 추출
   *
   * @param rawUsage API 응답의 usage 객체 (Map 형태)
   * @param provider 프로바이더명
   * @param model 모델명
   * @return 통합 캐시 사용량 정보
   */
  fun parse(rawUsage: Map<String, Any>, provider: String, model: String): CacheUsageInfo

  /**
   * 파서 우선순위 (낮은 값이 높은 우선순위)
   * 여러 파서가 동일 프로바이더를 지원할 때 우선순위 결정에 사용
   */
  fun priority(): Int = 100
}
