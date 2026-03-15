package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.ai.role_playing.WorldviewType
import com.jongmin.ai.role_playing.WorldviewTypeConverter
import jakarta.persistence.*

/**
 * 문학, 영화, 게임 등의 창작물에서는 작품 내의 가상 세계에 대한 설정과 규칙을 '세계관'이라 부릅니다.
 *
 * 일반적으로 세계관은 종교, 철학, 문화, 교육, 개인적 경험 등 다양한 요소에 의해 형성됩니다.
 * 또한 역사적, 사회적 맥락에 따라 변화할 수 있으며, 개인의 행동과 의사결정에 큰 영향을 미칩니다.
 *
 * @author Jongmin
 * @since  2026. 2. 26
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_worldview_createdAt", columnList = "createdAt"),
    Index(name = "idx_worldview_status", columnList = "status"),
    Index(name = "idx_worldview_accountId", columnList = "accountId"),
    Index(name = "idx_worldview_ownerId", columnList = "ownerId"),
    Index(name = "idx_worldview_type", columnList = "type"),
  ]
)
data class Worldview(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, comment = "판타지, SF, 현실 등")
  @Convert(converter = WorldviewTypeConverter::class)
  var type: WorldviewType,

  @Column(length = 80, nullable = false, comment = "직관적으로 이해할 수 있는 RP의 이름")
  var subject: String,
) : PromptableEntity() {
  fun build(): String {
    return "stage"
  }
}

