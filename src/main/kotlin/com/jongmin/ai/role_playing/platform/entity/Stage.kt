package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.ai.role_playing.StageType
import com.jongmin.ai.role_playing.StageTypeConverter
import jakarta.persistence.*

/**
 * 스테이지는 배우가 연기할 무대이다.
 *
 * 무대 설정은 연기에 큰 영향을 미치며, 배우의 연기를 통해 무대가 더욱 생생하게 느껴진다.
 * 동일한 상황이라도 무대를 바꾸면 연기의 느낌이 달라질 수 있다.
 *
 * 이러한 스테이지를 셋팅하는 것은 번거로운 일이기 때문에 AI를 통해 자동으로 최적의 무대를 생성할 수 있도록 지원해야한다.
 *
 * @author Jongmin
 * @since  2026. 2. 26
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_stage_createdAt", columnList = "createdAt"),
    Index(name = "idx_stage_status", columnList = "status"),
    Index(name = "idx_stage_accountId", columnList = "accountId"),
    Index(name = "idx_stage_ownerId", columnList = "ownerId"),
    Index(name = "idx_stage_type", columnList = "type"),
  ]
)
data class Stage(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  // TODO 접근 권한자 관계 테이블 생성해야함.
  // 이 스테이지에 접근할 수 있는 accountId 목록이 관리됨.

  // TODO 연결 오프젝트 관계 테이블 생성해야함.
  // 이 스테이지에 관련된 모든 오브젝트(mainCharId, npcId, buildingId 등)의 목록이 관리됨.

  @Column(nullable = false, comment = "타로 리딩, 미팅, 결투 등")
  @Convert(converter = StageTypeConverter::class)
  var type: StageType,

  @Column(length = 80, nullable = false, comment = "무대 이름")
  var name: String,

  ) : PromptableEntity() {
  fun build(): String {
    return "situation"
  }
}

