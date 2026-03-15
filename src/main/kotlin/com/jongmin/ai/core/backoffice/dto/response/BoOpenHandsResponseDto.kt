package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.ai.core.platform.entity.QOpenHandsRun.openHandsRun
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.LocalDateTime
import java.time.ZonedDateTime

data class BoOpenHandsRunItem(
  var gitProvider: String,
  var repo: String,
  var issueNumber: Int,
  var title: String?,
  var conversationId: String,
  var createdAt: ZonedDateTime = now(),
  var endedAt: ZonedDateTime? = null,
  var model: String? = null, // default
  var promptTokens: Int? = null, // 425574
  var completionTokens: Int? = null, // 2342
  var cacheReadTokens: Int? = null, // 0
  var cacheWriteTokens: Int? = null, // 0
  var contextWindow: Int? = null, // 0
  var perTurnToken: Int? = null, // 29627
  var responseId: String? = null, // ""
) {
  companion object {
    fun buildProjection(): ConstructorExpression<BoOpenHandsRunItem> = Projections.constructor(
      BoOpenHandsRunItem::class.java,
      openHandsRun.gitProvider,
      openHandsRun.repo,
      openHandsRun.issueNumber,
      openHandsRun.title,
      openHandsRun.conversationId,
      openHandsRun.createdAt,
      openHandsRun.endedAt,
      openHandsRun.model,
      openHandsRun.promptTokens,
      openHandsRun.completionTokens,
      openHandsRun.cacheReadTokens,
      openHandsRun.cacheWriteTokens,
      openHandsRun.contextWindow,
      openHandsRun.perTurnToken,
      openHandsRun.responseId,
    )
  }
}


// Open Hands Dto:--------------------------------------------------------------------------

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsConversationsResponse(
  var results: List<BoOpenHandsConversation>,
  var nextPageId: Any?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class OpenHandsRepositoryItem(
  var id: String? = null, // 989368539
  var fullName: String? = null, // cute-slime/dotjary
  var gitProvider: String? = null, // github
  var isPublic: Boolean? = null, // is_public
  var stargazersCount: Int? = null, // stargazers_count
  var linkHeader: String? = null, // null
  var pushedAt: ZonedDateTime? = null // null
)

data class CamelCaseOpenHandsRepositoryItem(
  var id: String? = null, // 989368539
  var fullName: String? = null, // cute-slime/dotjary
  var gitProvider: String? = null, // github
  var isPublic: Boolean? = null, // is_public
  var stargazersCount: Int? = null, // stargazers_count
  var linkHeader: String? = null, // null
  var pushedAt: ZonedDateTime? = null // null
) {
  companion object {
    fun of(item: OpenHandsRepositoryItem): CamelCaseOpenHandsRepositoryItem {
      return CamelCaseOpenHandsRepositoryItem(
        id = item.id,
        fullName = item.fullName,
        gitProvider = item.gitProvider,
        isPublic = item.isPublic,
        stargazersCount = item.stargazersCount,
        linkHeader = item.linkHeader,
        pushedAt = item.pushedAt
      )
    }
  }
}

data class CamelOpenHandsRepositoryItem(
  var id: String? = null, // 989368539
  var fullName: String? = null, // cute-slime/dotjary
  var gitProvider: String? = null, // github
  var isPublic: Boolean? = null, // is_public
  var stargazersCount: Int? = null, // stargazers_count
  var linkHeader: String? = null, // null
  var pushedAt: ZonedDateTime? = null // null
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsConversation(
  var conversationId: String? = null, // 8e7a4f21144b48e4952f27961f31b359
  var title: String? = null, // Conversation 8e7a4
  var lastUpdatedAt: ZonedDateTime? = null,
  var status: String? = null, // STARTING
  var runtimeStatus: String? = null, // STATUS$STARTING_RUNTIME,
  var selectedRepository: String? = null, // cute-slime/dotjary,
  var selectedBranch: String? = null, // dev
  var gitProvider: String? = null, // github
  var trigger: String? = null, // gui
  var numConnections: Int? = null, // 0
  var url: String? = null, // /api/conversations/8e7a4f21144b48e4952f27961f31b359,
  var sessionApiKey: String? = null, // null
  var createdAt: ZonedDateTime? = null // 2025-01-24T09:04:02.134785Z
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsTaskItem(
  var gitProvider: String? = null,
  var taskType: String? = null,
  var repo: String? = null,
  var issueNumber: Int? = null,
  var title: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsConversationCreated(
  var status: String? = null,
  var conversationId: String? = null,
  var message: String? = null,
  var conversationStatus: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsTrajectoryResponse(
  var trajectory: List<BoOpenHandsTrajectoryItem>? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsTrajectoryItem(
  var id: Long? = null,
  var timestamp: LocalDateTime? = null, // 2025-01-25T13:48:46.183193
  var action: String? = null, // finish
  var source: String? = null, // environment
  var observation: String? = null, // agent_state_changed
  var extras: BoOpenHandsTrajectoryItemExtras? = null,
  var llmMetrics: BoOpenHandsLlmMetrics? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsTrajectoryItemExtras(
  var agentState: String? = null, // 실행 중 케이스 [loading, running], 미 실행 중 케이스 [finished, stopped, awaiting_user_input]
  var reason: String? = null, // ""
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoOpenHandsLlmMetrics(
// "accumulated_cost": 0,
// "max_budget_per_task": null,
  var accumulatedTokenUsage: BoAccumulatedTokenUsage,
// "costs": [],
// "response_latencies": [],
// "token_usages": [],
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BoAccumulatedTokenUsage(
  var model: String? = null, // default
  var promptTokens: Int? = null, // 425574
  var completionTokens: Int? = null, // 2342
  var cacheReadTokens: Int? = null, // 0
  var cacheWriteTokens: Int? = null, // 0
  var contextWindow: Int? = null, // 0
  var perTurnToken: Int? = null, // 29627
  var responseId: String? = null, // ""
)
