package com.jongmin.ai.multiagent.repository

import com.jongmin.ai.multiagent.entity.MultiAgentWorkflow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 멀티 에이전트 워크플로우 Repository
 */
@Repository
interface MultiAgentWorkflowRepository : JpaRepository<MultiAgentWorkflow, Long> {

  /**
   * 특정 계정의 워크플로우 목록 조회 (삭제 상태 제외)
   */
  fun findByAccountIdAndStatusNot(accountId: Long, status: Int): List<MultiAgentWorkflow>

  /**
   * 특정 소유자의 워크플로우 목록 조회 (삭제 상태 제외)
   */
  fun findByOwnerIdAndStatusNot(ownerId: Long, status: Int): List<MultiAgentWorkflow>

  /**
   * 특정 계정의 워크플로우 목록 페이징 조회
   */
  fun findByAccountIdOrderByCreatedAtDesc(accountId: Long, pageable: Pageable): Page<MultiAgentWorkflow>
}
