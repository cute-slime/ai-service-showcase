package com.jongmin.ai.core.platform.component.loop

import com.jongmin.ai.core.platform.component.agent.executor.model.BasicExecutionContext
import com.jongmin.ai.core.platform.component.agent.executor.model.NodeExecutionState
import com.jongmin.ai.core.platform.component.agent.executor.model.NodeExecutorFactory
import com.jongmin.ai.core.platform.component.agent.executor.model.Workflow

/**
 * Loop Job 전용 ExecutionContext
 *
 * BasicExecutionContext를 확장하여 노드 실행 시 체크포인트 콜백을 호출한다.
 * Loop Job에서 워크플로우 실행 시 이 컨텍스트를 사용하면,
 * 각 노드의 시작/완료/실패 시점에 체크포인트가 저장된다.
 *
 * ## 체크포인트 저장 시점
 * - IN_PROGRESS: 노드 구동 시작 → onNodeStarted() 호출
 * - SUCCESS: 노드 실행 완료 → onNodeCompleted() 호출
 * - ERROR: 노드 실행 실패 → onNodeFailed() 호출
 *
 * ## 복구 시 활용
 * - STARTED 체크포인트: 해당 노드부터 재실행
 * - COMPLETED 체크포인트: 다음 노드부터 실행
 *
 * @param factory 노드 실행기 팩토리
 * @param workflow 워크플로우 정의
 * @param checkpointCallback 체크포인트 콜백 (노드 상태 변경 시 호출)
 * @param onFinish 워크플로우 완료 콜백
 *
 * @author Claude Code
 * @since 2026.01.04
 */
class LoopExecutionContext(
  factory: NodeExecutorFactory,
  workflow: Workflow,
  private val checkpointCallback: CheckpointCallback,
  onFinish: ((output: Any?) -> Unit)? = null
) : BasicExecutionContext(factory, workflow, onFinish) {

  /**
   * 마지막 에러 정보 (노드 실패 시 저장)
   */
  private var lastError: Throwable? = null

  /**
   * 노드 상태 업데이트 (체크포인트 콜백 호출 포함)
   *
   * 상위 클래스의 updateNodeStatus()를 오버라이드하여
   * 노드 상태 변경 시 체크포인트 콜백을 호출한다.
   *
   * @param id 노드 ID
   * @param status 새로운 상태
   */
  override fun updateNodeStatus(id: String, status: NodeExecutionState) {
    // 상위 클래스의 상태 업데이트 먼저 수행
    super.updateNodeStatus(id, status)

    // 노드 타입 조회
    val node = workflow.nodes.firstOrNull { it.id == id }
    val nodeType = node?.type ?: "unknown"

    // 상태에 따라 적절한 콜백 호출
    when (status) {
      NodeExecutionState.IN_PROGRESS -> {
        // 노드 구동 시작
        checkpointCallback.onNodeStarted(id, nodeType)
      }

      NodeExecutionState.SUCCESS -> {
        // 노드 실행 완료 - output 포함
        val output = getOutputForNode(id)
        checkpointCallback.onNodeCompleted(id, nodeType, output)
      }

      NodeExecutionState.ERROR -> {
        // 노드 실행 실패
        val error = lastError ?: RuntimeException("Unknown error at node: $id")
        checkpointCallback.onNodeFailed(id, nodeType, error)
      }

      else -> {
        // WAIT, READY, PREPARED 등은 체크포인트 저장하지 않음
      }
    }
  }

  /**
   * 에러 정보 저장
   *
   * NodeExecutor에서 예외 발생 시 이 메서드를 호출하여
   * 에러 정보를 저장한 후 updateNodeStatus(ERROR)를 호출한다.
   *
   * @param error 발생한 예외
   */
  fun setLastError(error: Throwable) {
    this.lastError = error
  }

  /**
   * 마지막 에러 정보 조회
   */
  fun getLastError(): Throwable? = lastError
}
