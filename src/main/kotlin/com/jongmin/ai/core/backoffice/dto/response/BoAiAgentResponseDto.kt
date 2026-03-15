package com.jongmin.ai.core.backoffice.dto.response

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.AiAgentType
import java.time.ZonedDateTime

data class BoAiAgentItem(
  var id: Long? = null,
  var name: String? = null,
  var type: AiAgentType? = null,
  var description: String? = null,
  var lastUsedAt: ZonedDateTime? = null,
  var workflow: Map<String, Any>? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null,
)

/**
 * 워크플로우 실행 응답 DTO
 *
 * DTE 기반 워크플로우 실행 요청 후 반환되는 응답입니다.
 * 클라이언트는 이 executionId를 사용하여 SSE 엔드포인트에 구독합니다.
 *
 * @property executionId DTE Job ID (SSE 구독 시 사용)
 */
data class ExecuteWorkflowResponse(
  val executionId: String,
)
