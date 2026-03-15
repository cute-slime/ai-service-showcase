package com.jongmin.ai.multiagent.skill.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 스킬 스크립트 실행 시 네트워크 접근 정책
 *
 * 샌드박스 환경에서 스크립트가 외부 네트워크에 접근할 수 있는 수준을 정의한다.
 * 기본값은 ALLOW_SPECIFIC (특정 도메인만 허용)이다.
 */
enum class NetworkPolicy(private val typeCode: Int) {
  /**
   * 모든 외부 네트워크 차단
   * 순수 계산 스킬에 적합
   */
  DENY_ALL(1),

  /**
   * 특정 도메인만 허용 (기본값)
   * allowedDomains 필드에 지정된 도메인만 접근 가능
   */
  ALLOW_SPECIFIC(2),

  /**
   * 모든 외부 접근 허용
   * 신뢰된 스킬 전용 - 주의하여 사용
   */
  ALLOW_ALL(3),
  ;

  companion object {
    private val map = entries.associateBy(NetworkPolicy::typeCode)

    @JsonCreator
    fun getType(value: Int): NetworkPolicy = map[value] ?: ALLOW_SPECIFIC

    fun fromString(value: String): NetworkPolicy {
      return entries.find { it.name.equals(value, ignoreCase = true) } ?: ALLOW_SPECIFIC
    }
  }

  fun value(): Int = typeCode

  @JsonValue
  override fun toString(): String = super.toString()
}
