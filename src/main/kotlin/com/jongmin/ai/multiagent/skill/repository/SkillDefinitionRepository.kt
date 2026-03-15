package com.jongmin.ai.multiagent.skill.repository

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.multiagent.skill.entity.SkillDefinitionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 스킬 정의 Repository
 */
@Repository
interface SkillDefinitionRepository : JpaRepository<SkillDefinitionEntity, Long> {

  /**
   * 계정별 스킬 목록 조회 (삭제 제외)
   */
  fun findByAccountIdAndStatusNot(
    accountId: Long,
    status: StatusType,
  ): List<SkillDefinitionEntity>

  /**
   * 계정별 스킬 목록 조회 - 페이징 (삭제 제외)
   */
  fun findByAccountIdAndStatusNot(
    accountId: Long,
    status: StatusType,
    pageable: Pageable,
  ): Page<SkillDefinitionEntity>

  /**
   * 계정 + 이름으로 스킬 조회 (삭제 제외)
   */
  fun findByAccountIdAndNameAndStatusNot(
    accountId: Long,
    name: String,
    status: StatusType,
  ): SkillDefinitionEntity?

  /**
   * 계정 + 이름으로 스킬 조회 (상태 무관)
   */
  fun findByAccountIdAndName(
    accountId: Long,
    name: String,
  ): SkillDefinitionEntity?

  /**
   * 계정 + 이름으로 스킬 존재 여부 확인 (삭제 제외)
   */
  fun existsByAccountIdAndNameAndStatusNot(
    accountId: Long,
    name: String,
    status: StatusType,
  ): Boolean

  /**
   * 계정별 스킬 수 조회 (삭제 제외)
   */
  fun countByAccountIdAndStatusNot(
    accountId: Long,
    status: StatusType,
  ): Long

  /**
   * 이름 목록으로 스킬 조회 (삭제 제외)
   */
  fun findByAccountIdAndNameInAndStatusNot(
    accountId: Long,
    names: List<String>,
    status: StatusType,
  ): List<SkillDefinitionEntity>
}
