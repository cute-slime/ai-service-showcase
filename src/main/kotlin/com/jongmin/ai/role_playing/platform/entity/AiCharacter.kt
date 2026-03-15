package com.jongmin.ai.role_playing.platform.entity

import com.jongmin.ai.role_playing.AiCharacterTypeConverter
import com.jongmin.ai.role_playing.CharacterType
import jakarta.persistence.*

/**
 * @author Jongmin
 * @since  2026. 2. 26
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_aiCharacter_createdAt", columnList = "createdAt"),
    Index(name = "idx_aiCharacter_status", columnList = "status"),
    Index(name = "idx_aiCharacter_accountId", columnList = "accountId"),
    Index(name = "idx_aiCharacter_ownerId", columnList = "ownerId"),
    Index(name = "idx_aiCharacter_type", columnList = "type"),
  ]
)
data class AiCharacter(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(updatable = false)
  val id: Long = 0L,

  @Column(nullable = false, comment = "타로 리딩, 미팅, 결투 등")
  @Convert(converter = AiCharacterTypeConverter::class)
  var type: CharacterType,

  @Column(length = 80, nullable = false, comment = "AI 캐릭터 이름")
  var name: String,
) : PromptableEntity()

