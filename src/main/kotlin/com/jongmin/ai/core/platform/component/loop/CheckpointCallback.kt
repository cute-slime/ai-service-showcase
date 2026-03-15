package com.jongmin.ai.core.platform.component.loop

/**
 * 워크플로우 노드 체크포인트 콜백 인터페이스
 *
 * Loop Job에서 노드 실행 상태를 추적하여 장애 복구에 활용한다.
 * - STARTED: 노드 구동 시작 → 복구 시 해당 노드부터 재실행
 * - COMPLETED: 노드 실행 완료 → 복구 시 다음 노드부터 실행
 *
 * ## 사용 예시
 * ```kotlin
 * val callback = object : CheckpointCallback {
 *     override fun onNodeStarted(nodeId: String, nodeType: String) {
 *         saveCheckpoint(jobId, iteration, nodeId, STARTED)
 *     }
 *     override fun onNodeCompleted(nodeId: String, nodeType: String, output: Any?) {
 *         saveCheckpoint(jobId, iteration, nodeId, COMPLETED)
 *     }
 * }
 * ```
 *
 * @author Claude Code
 * @since 2026.01.04
 */
interface CheckpointCallback {

  /**
   * 노드 구동 시작 시 호출
   *
   * 노드의 `executeInternal()` 호출 직전에 호출된다.
   * 이 시점에 체크포인트를 저장하면, 복구 시 해당 노드부터 재실행된다.
   *
   * @param nodeId 노드 ID
   * @param nodeType 노드 타입 (예: "generate-text", "scenario-concept")
   */
  fun onNodeStarted(nodeId: String, nodeType: String)

  /**
   * 노드 실행 완료 시 호출
   *
   * 노드의 `executeInternal()` 완료 후 호출된다.
   * 이 시점에 체크포인트를 저장하면, 복구 시 다음 노드부터 실행된다.
   *
   * @param nodeId 노드 ID
   * @param nodeType 노드 타입
   * @param output 노드 출력값 (nullable)
   */
  fun onNodeCompleted(nodeId: String, nodeType: String, output: Any?)

  /**
   * 노드 실행 실패 시 호출
   *
   * 노드 실행 중 예외 발생 시 호출된다.
   *
   * @param nodeId 노드 ID
   * @param nodeType 노드 타입
   * @param error 발생한 예외
   */
  fun onNodeFailed(nodeId: String, nodeType: String, error: Throwable)
}

/**
 * 아무 동작도 하지 않는 기본 CheckpointCallback
 *
 * 체크포인트가 필요 없는 일반 워크플로우 실행에 사용.
 */
object NoOpCheckpointCallback : CheckpointCallback {
  override fun onNodeStarted(nodeId: String, nodeType: String) {}
  override fun onNodeCompleted(nodeId: String, nodeType: String, output: Any?) {}
  override fun onNodeFailed(nodeId: String, nodeType: String, error: Throwable) {}
}
