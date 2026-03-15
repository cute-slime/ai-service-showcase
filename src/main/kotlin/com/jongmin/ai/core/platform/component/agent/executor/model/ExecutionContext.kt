package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.ai.core.IAiChatMessage

interface ExecutionContext {
  fun setQuestion(input: Any): Any
  fun setMessages(messages: List<IAiChatMessage>): List<IAiChatMessage>
  fun getMessages(): List<IAiChatMessage>?
  fun isPlaygroundRequest(): Boolean
  fun storeOutput(nodeId: String, output: Any)
  fun get(key: String): Any?
  fun set(key: String, value: Any)
  fun getNextNode(nodeId: String): Node?
  fun getNextNodeByType(nodeId: String, nodeType: String): Node?
  fun getPreviousNode(nodeId: String): Node?
  fun getPreviousNodeByType(nodeId: String, nodeType: String): Node?
  fun findAndGetInputForNode(nodeId: String, inputHandle: String, valueMapper: String? = null): Any?
  fun findDestHandle(nodeId: String, sourceHandle: String): String
  fun getOutputForNode(nodeId: String): Any?
  fun updateNodeStatus(id: String, status: NodeExecutionState)
  fun getOrDefault(s: String, default: Any): Any

  /**
   * 이전 노드 실행 상태 체크를 스킵할지 여부
   *
   * Flow 모드에서 중간 노드부터 실행할 때, 시작 노드는 이전 노드가
   * 실행되지 않았더라도 입력값이 직접 주입되므로 체크를 스킵해야 한다.
   *
   * @param nodeId 현재 노드 ID
   * @return true면 이전 노드 체크 스킵
   */
  fun shouldSkipPreviousNodeCheck(nodeId: String): Boolean = false

  val workflow: Workflow
  val factory: NodeExecutorFactory
  val onFinish: ((output: Any?) -> Unit)?
}
