package com.jongmin.ai.core.platform.component.agent.executor.model

/**
 * Flow 모드 실행을 위한 ExecutionContext
 *
 * 워크플로우 에디터에서 특정 노드부터 끝까지 순차 실행할 때 사용.
 * 이전 노드의 출력을 다음 노드의 입력으로 자동 전달한다.
 *
 * 주요 특징:
 * - 첫 번째 노드에는 initialInputs 주입
 * - 이후 노드들은 이전 노드의 출력을 입력으로 사용
 * - 출력 결과를 핸들별로 누적 저장
 *
 * @author Claude Code
 * @since 2025.12.26
 */
class FlowExecutionContext(
  factory: NodeExecutorFactory,
  workflow: Workflow,
  /** 첫 번째 노드에 주입할 초기 입력 (handleId → value) */
  private val initialInputs: Map<String, String>,
  onFinish: ((output: Any?) -> Unit)? = null
) : BasicExecutionContext(factory, workflow, onFinish) {

  /**
   * 노드별 출력 결과 저장소
   * key: nodeId
   * value: 해당 노드의 출력 (Map<handleId, value> 또는 String)
   */
  private val nodeOutputs = mutableMapOf<String, Map<String, String>>()

  /** 첫 번째 노드 실행 여부 (initialInputs 사용 판단용) */
  private var isFirstNodeExecuted = false

  /** 로거 */
  private val kLogger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

  /**
   * 노드 입력 조회 오버라이드
   *
   * Flow 모드에서는 다음 순서로 입력을 조회한다:
   * 1. 이전 노드 출력에서 조회 (Edge 기반)
   * 2. 핸들 ID로 직접 조회 (이전 노드들의 모든 출력에서)
   * 3. initialInputs에서 조회 (시작 시 주입된 값 - 모든 노드에서 fallback으로 사용)
   * 4. 부모 클래스 기본 로직
   *
   * @param nodeId 현재 노드 ID
   * @param inputHandle 입력 핸들 ID
   * @param valueMapper 값 매핑 키 (선택)
   * @return 입력값, 없으면 null
   */
  override fun findAndGetInputForNode(nodeId: String, inputHandle: String, valueMapper: String?): Any? {
    kLogger.debug { "[FlowContext] findAndGetInputForNode - nodeId: $nodeId, inputHandle: $inputHandle" }

    // 1. 이전 노드 출력에서 조회 (Edge 기반으로 source 노드 찾기)
    val sourceEdge = workflow.edges.find { edge ->
      edge.target == nodeId && edge.targetHandle == inputHandle
    }

    if (sourceEdge != null) {
      val sourceNodeOutput = nodeOutputs[sourceEdge.source]
      if (sourceNodeOutput != null) {
        // sourceHandle이 있으면 해당 핸들의 값, 없으면 "output" 키로 조회
        val handleId = sourceEdge.sourceHandle ?: "output"
        val edgeValue = sourceNodeOutput[handleId]
        if (edgeValue != null) {
          kLogger.debug { "[FlowContext] Edge 기반 조회 성공: $inputHandle from ${sourceEdge.source}.$handleId" }
          return edgeValue
        }
      }
    }

    // 2. 핸들 ID로 직접 조회 (이전 노드들의 모든 출력에서)
    for ((nodeId, outputs) in nodeOutputs) {
      outputs[inputHandle]?.let {
        kLogger.debug { "[FlowContext] 노드 출력에서 조회 성공: $inputHandle from $nodeId" }
        return it
      }
    }

    // 3. initialInputs에서 조회 (시작 시 주입된 값 - 모든 노드에서 fallback으로 사용)
    val initialValue = initialInputs[inputHandle]
    if (initialValue != null) {
      kLogger.debug { "[FlowContext] initialInputs에서 조회 성공: $inputHandle" }
      return initialValue
    }

    // 4. 부모 클래스 기본 로직 (Edge 기반 조회)
    kLogger.debug { "[FlowContext] 입력 찾지 못함: $inputHandle, 부모 클래스로 위임" }
    return super.findAndGetInputForNode(nodeId, inputHandle, valueMapper)
  }

  /**
   * 노드 출력 저장 오버라이드
   *
   * Flow 컨텍스트에 노드 출력을 저장하고, 부모에도 저장.
   *
   * @param nodeId 노드 ID
   * @param output 출력 데이터
   */
  override fun storeOutput(nodeId: String, output: Any) {
    super.storeOutput(nodeId, output)

    // 첫 번째 노드 실행 완료 표시
    if (!isFirstNodeExecuted) {
      isFirstNodeExecuted = true
    }

    // 출력을 Map<handleId, String> 형태로 정규화하여 저장
    val normalizedOutput = normalizeOutput(output)
    nodeOutputs[nodeId] = normalizedOutput
  }

  /**
   * 노드 출력 저장 (외부 호출용)
   *
   * Flow 모드에서 순차 실행 시 각 노드의 결과를 저장.
   *
   * @param nodeId 노드 ID
   * @param results 출력 결과 맵
   */
  fun storeNodeOutput(nodeId: String, results: Map<String, String>) {
    // 첫 번째 노드 실행 완료 표시
    if (!isFirstNodeExecuted) {
      isFirstNodeExecuted = true
    }
    nodeOutputs[nodeId] = results
  }

  /**
   * 출력을 Map<String, String> 형태로 정규화
   */
  private fun normalizeOutput(output: Any): Map<String, String> {
    return when (output) {
      is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        (output as Map<String, Any>).mapValues { it.value.toString() }
      }

      is String -> mapOf("output" to output)
      else -> mapOf("output" to output.toString())
    }
  }

  /**
   * 모든 노드 출력 조회
   */
  fun getAllNodeOutputs(): Map<String, Map<String, String>> = nodeOutputs.toMap()

  /**
   * 이전 노드 실행 상태 체크 스킵 여부
   *
   * Flow 모드에서 첫 번째 노드(시작 노드)는 이전 노드가 실행되지 않았더라도
   * initialInputs로 입력값이 직접 주입되므로 체크를 스킵한다.
   *
   * @param nodeId 현재 노드 ID
   * @return 첫 번째 노드면 true (스킵), 이후 노드면 false
   */
  override fun shouldSkipPreviousNodeCheck(nodeId: String): Boolean {
    // 첫 번째 노드가 아직 실행되지 않았으면 현재 노드가 시작 노드
    return !isFirstNodeExecuted
  }
}
