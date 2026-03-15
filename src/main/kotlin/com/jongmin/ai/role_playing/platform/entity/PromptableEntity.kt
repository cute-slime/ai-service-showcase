package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 롤플레잉 관련 프롬프트 가능 엔티티 베이스 클래스
 *
 * BaseTimeAndStatusEntity 상속: createdAt, updatedAt, status 자동 관리
 */
@MappedSuperclass
abstract class PromptableEntity : BaseTimeAndStatusEntity() {
  @Column(nullable = false, updatable = false, comment = "생성 계정 아이디")
  val accountId: Long = -1

  @Column(
    nullable = false,
    updatable = false,
    comment = "오너의 아이디로 -1은 플랫폼의 시스템, 그 외 커스텀하게 생성된 오브젝트의 아이디 (accountId, workspaceUserId, etc..."
  )
  val ownerId: Long = -1

  @Column(comment = "현재 버전으로 실제 코드에서는 prefix인 v를 붙여 사용된다.")
  var currentVersion: Int = 1

  // 버전이 변경될 때 이전 버전이 기록된다.
  // {v1: {tiny: "", small: "", medium: "", large: "", full: ""}, v2: {...}}
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSONB")
  var version: Map<String, Any> = emptyMap()

  // tiny~full은 버전별로 관리될 수 있을 것 같다.
  @Column(length = 200, comment = "참조 수준의 설명 (영문 40~50 tk, 한글 100~160tk)")
  var tiny: String? = null

  @Column(length = 1000, comment = "짧은 설명 (영문 200~250 tk, 한글 500~800tk)")
  var small: String? = null // 1000

  @Column(columnDefinition = "TEXT", comment = "보통 길이의 설명 (영문 1000~1250 tk, 한글 2500~4000tk)")
  var medium: String? = null // 5000

  @Column(columnDefinition = "TEXT", comment = "장황한 설명 (영문 2000~2500 tk, 한글 5000~8000tk)") // 10000
  var large: String? = null

  @Column(columnDefinition = "TEXT", comment = "전체 설명 (영문 10000~12500 tk, 한글 25000~40000tk)") // 50000
  var fullText: String? = null

  // status는 BaseTimeAndStatusEntity에서 상속
}
