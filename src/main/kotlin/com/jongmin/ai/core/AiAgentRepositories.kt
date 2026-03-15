package com.jongmin.ai.core

import com.jongmin.ai.core.platform.entity.*
import com.jongmin.ai.product_agent.platform.entity.ProductAgentOutput
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import org.springframework.stereotype.Repository

// JpaRepository, CrudRepository 거의 유사한 두 상위 인터페이스 중 무엇을 사용할것인지 결정이 필요하다.
// 결정은 JpaRepository 왜냐 findAll 이 반환하는 인터페이스가 List vs Iterable 이다.
@Repository
interface AiProviderRepository : JpaRepository<AiProvider, Long>, QuerydslPredicateExecutor<AiProvider>

@Repository
interface AiApiKeyRepository : JpaRepository<AiApiKey, Long>, QuerydslPredicateExecutor<AiApiKey>

@Repository
interface AiModelRepository : JpaRepository<AiModel, Long>, QuerydslPredicateExecutor<AiModel>

@Repository
interface AiAgentRepository : JpaRepository<AiAgent, Long>, QuerydslPredicateExecutor<AiAgent>

@Repository
interface AiAssistantRepository : JpaRepository<AiAssistant, Long>, QuerydslPredicateExecutor<AiAssistant>

// :--------------------------------------------------------------------------
@Repository
interface AiThreadRepository : JpaRepository<AiThread, Long>, QuerydslPredicateExecutor<AiThread>

@Repository
interface FavoriteAiThreadRepository : JpaRepository<AiThreadStarredJoinTable, AiThreadStarredPk>,
  QuerydslPredicateExecutor<AiThreadStarredJoinTable>

// :--------------------------------------------------------------------------

@Repository
interface AiMessageRepository : JpaRepository<AiMessage, Long>, QuerydslPredicateExecutor<AiMessage> {
  fun findFirstByAiThreadIdOrderByIdDesc(id: Long): AiMessage?
}

@Repository
interface AiRunRepository : JpaRepository<AiRun, Long>, QuerydslPredicateExecutor<AiRun>

@Repository
interface AiRunStepRepository : JpaRepository<AiRunStep, Long>, QuerydslPredicateExecutor<AiRunStep>

// :--------------------------------------------------------------------------
@Repository
interface OpenHandsRunRepository : JpaRepository<OpenHandsRun, OpenHandsRunPk>, QuerydslPredicateExecutor<OpenHandsRun>

@Repository
interface OpenHandsIssueRepository : JpaRepository<OpenHandsIssue, Long>, QuerydslPredicateExecutor<OpenHandsIssue>

@Repository
interface OpenHandsSnippetRepository : JpaRepository<OpenHandsSnippet, Long>, QuerydslPredicateExecutor<OpenHandsSnippet>

@Repository
interface GitProviderRepository : JpaRepository<GitProvider, Long>, QuerydslPredicateExecutor<GitProvider>

// :--------------------------------------------------------------------------
// Agent Output (모든 에이전트 출력물 통합 관리)
@Repository
interface AgentOutputRepository : JpaRepository<ProductAgentOutput, Long>, QuerydslPredicateExecutor<ProductAgentOutput>

// :--------------------------------------------------------------------------
// AI Caching Detection (캐싱 감지 로그)
@Repository
interface AiCachingDetectionLogRepository : JpaRepository<AiCachingDetectionLog, Long>,
  QuerydslPredicateExecutor<AiCachingDetectionLog>
