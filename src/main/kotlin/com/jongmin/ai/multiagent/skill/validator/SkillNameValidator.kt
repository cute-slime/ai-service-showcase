package com.jongmin.ai.multiagent.skill.validator

import com.jongmin.jspring.core.exception.BadRequestException

/**
 * 스킬 이름 검증기
 * Agent Skills 오픈 스펙 준수
 *
 * 검증 규칙:
 * - 1-64자
 * - 소문자 알파벳, 숫자, 하이픈(-) 만 허용
 * - 하이픈(-)으로 시작/끝 불가
 * - 연속 하이픈(--) 불가
 * - 디렉토리명과 일치해야 함 (파일 로드 시)
 */
object SkillNameValidator {

  // 유효한 스킬 이름 패턴
  // ^[a-z0-9]+ : 소문자 또는 숫자로 시작
  // (-[a-z0-9]+)* : 하이픈 + 소문자/숫자 그룹이 0회 이상 반복
  // 이 패턴으로 시작/끝 하이픈, 연속 하이픈 모두 방지
  private val NAME_PATTERN = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

  private const val MAX_LENGTH = 64

  /**
   * 스킬 이름 검증
   * @throws BadRequestException 검증 실패 시
   */
  fun validate(name: String) {
    if (name.isBlank()) {
      throw BadRequestException("Skill name is required")
    }

    if (name.length > MAX_LENGTH) {
      throw BadRequestException(
        "Skill name must be $MAX_LENGTH characters or less: ${name.take(20)}..."
      )
    }

    if (!NAME_PATTERN.matches(name)) {
      throw BadRequestException(
        "Skill name must contain only lowercase letters, numbers, and hyphens. " +
          "Cannot start/end with hyphen or have consecutive hyphens: $name"
      )
    }
  }

  /**
   * 스킬 이름 유효성 검사 (예외 없이 Boolean 반환)
   */
  fun isValid(name: String): Boolean {
    return name.isNotBlank() &&
      name.length <= MAX_LENGTH &&
      NAME_PATTERN.matches(name)
  }

  /**
   * 유효하지 않은 이름을 유효한 형태로 변환 시도
   * 예: "Web-Search" → "web-search"
   *     "my_skill" → "my-skill"
   */
  fun normalize(name: String): String {
    return name
      .lowercase()
      .replace(Regex("[_\\s]+"), "-")       // 언더스코어, 공백 → 하이픈
      .replace(Regex("[^a-z0-9-]"), "")     // 허용되지 않는 문자 제거
      .replace(Regex("-+"), "-")            // 연속 하이픈 → 단일 하이픈
      .trim('-')                            // 앞뒤 하이픈 제거
      .take(MAX_LENGTH)
  }
}
