package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

abstract class RolePlayingNodeExecutor<RolePlayingExecutionContext : ExecutionContext>(
  objectMapper: ObjectMapper,
  factory: NodeExecutorFactory,
  session: JSession,
  topic: String,
  eventSender: EventSender,
  sink: FluxSink<String>? = null,
  canvasId: String?,
  debugging: Boolean = false
) : NodeExecutor<RolePlayingExecutionContext>(objectMapper, factory, session, topic, eventSender, sink, canvasId, debugging)
