package com.jongmin.ai.core.platform.component.agent.executor.model

interface PreparedNodeExecutor<T : ExecutionContext> {
  fun prepare(node: Node, context: T)
}
