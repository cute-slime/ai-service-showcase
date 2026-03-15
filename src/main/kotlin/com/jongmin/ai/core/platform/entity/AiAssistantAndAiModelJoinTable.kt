package com.jongmin.ai.core.platform.entity

import jakarta.persistence.*

/**
 * AIAssistant와 AIModel의 다대다 관계를 나타내는 조인 테이블
 *
 * @author Jongmin
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_AMAAA_aiAssistant", columnList = "aiAssistant"),
    Index(name = "idx_AMAAA_aiModel", columnList = "aiModel"),
  ]
)
data class AiModelAndAiAssistant(
  @Id
  @Column(updatable = false, nullable = false)
  val aiAssistant: Long,

  @Id
  @Column(updatable = false, nullable = false)
  val aiModel: Long,
)
