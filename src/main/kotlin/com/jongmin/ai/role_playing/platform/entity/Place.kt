package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.ai.role_playing.PlaceType
import com.jongmin.ai.role_playing.PlaceTypeConverter
import jakarta.persistence.*

/**
 * 장소를 뜻함.
 * 추가로 아래 정보도 알아두면 좋을 것 같다.
 *
 * "Placement"는 연극에서 배우나 소품의 배치, 위치 지정과 관련된 기술적인 용어로 더 많이 사용됩니다.
 * 예: "The director worked on the placement of actors on stage" (감독은 무대 위 배우들의 배치를 작업했습니다).
 *
 * @author Jongmin
 * @since  2026. 2. 26
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_place_createdAt", columnList = "createdAt"),
    Index(name = "idx_place_status", columnList = "status"),
    Index(name = "idx_place_accountId", columnList = "accountId"),
    Index(name = "idx_place_ownerId", columnList = "ownerId"),
    Index(name = "idx_place_type", columnList = "type"),
  ]
)
data class Place(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, comment = "가정집, 상점, 점술집 등")
  @Convert(converter = PlaceTypeConverter::class)
  var type: PlaceType,

  @Column(length = 80, nullable = false, comment = "AI 캐릭터 이름")
  var name: String,
) : PromptableEntity()

