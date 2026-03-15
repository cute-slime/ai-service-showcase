package com.jongmin.ai.core.platform.component.agent.executor.model

/**
 * 단일 노드 실행을 위한 특수 ExecutionContext
 *
 * 전체 워크플로우 없이 개별 노드만 실행할 때 사용.
 * 이전 노드의 출력을 직접 주입하고, 실행 결과를 수집한다.
 *
 * 주요 특징:
 * - Edge 기반 입력 조회 대신 직접 주입된 inputs 사용
 * - 단일 노드만 포함하는 최소 워크플로우 구성
 * - 출력 결과를 Map 형태로 수집
 *
 * @author Claude Code
 * @since 2025.12.26
 */
class SingleNodeExecutionContext(
  factory: NodeExecutorFactory,
  workflow: Workflow,
  /** 주입된 입력 데이터 (handleId → value) */
  private val injectedInputs: Map<String, String>,
  onFinish: ((output: Any?) -> Unit)? = null
) : BasicExecutionContext(factory, workflow, onFinish) {

  /** 노드 출력 결과 저장소 */
  private val outputResults = mutableMapOf<String, Any>()

  /**
   * 노드 입력 조회 오버라이드
   *
   * Edge 기반 조회 대신, 직접 주입된 inputs에서 값을 가져온다.
   * 이를 통해 단일 노드 실행 시 이전 노드 없이도 입력을 제공할 수 있다.
   *
   * @param nodeId 현재 노드 ID
   * @param inputHandle 입력 핸들 ID (예: "concept", "truth", "input")
   * @param valueMapper 값 매핑 키 (선택)
   * @return 주입된 입력값, 없으면 null
   */
  override fun findAndGetInputForNode(nodeId: String, inputHandle: String, valueMapper: String?): Any? {
    // 1. 주입된 inputs에서 먼저 조회
    val injectedValue = injectedInputs[inputHandle]
    if (injectedValue != null) {
      return injectedValue
    }

    // 2. 부모 클래스의 기본 로직 사용 (Edge 기반 조회)
    // 단일 노드 실행에서는 보통 사용되지 않지만, 호환성을 위해 유지
    return super.findAndGetInputForNode(nodeId, inputHandle, valueMapper)
  }

  /**
   * 노드 출력 저장 오버라이드
   *
   * 출력을 저장하면서 동시에 결과 맵에도 기록한다.
   *
   * @param nodeId 노드 ID
   * @param output 출력 데이터 (문자열 또는 Map)
   */
  override fun storeOutput(nodeId: String, output: Any) {
    super.storeOutput(nodeId, output)
    outputResults[nodeId] = output
  }

  /**
   * 실행 결과 조회
   *
   * 노드 실행 후 저장된 모든 출력을 핸들별 Map 형태로 반환.
   *
   * @return 출력 핸들 ID → 출력값 Map
   */
  fun getOutputResults(): Map<String, String> {
    val results = mutableMapOf<String, String>()

    for ((nodeId, output) in outputResults) {
      when (output) {
        // 다중 출력 핸들 (Map<handleId, value>)
        is Map<*, *> -> {
          @Suppress("UNCHECKED_CAST")
          val outputMap = output as Map<String, Any>
          for ((handleId, value) in outputMap) {
            results[handleId] = value.toString()
          }
        }
        // 단일 출력 (문자열 그대로)
        is String -> {
          // 단일 출력인 경우 기본 핸들 이름 사용
          results["output"] = output
        }
        // 기타 객체 → 문자열 변환
        else -> {
          results["output"] = output.toString()
        }
      }
    }

    return results
  }

  companion object {
    /**
     * 단일 노드 실행용 최소 워크플로우 생성
     *
     * @param nodeId 노드 ID
     * @param nodeType 노드 타입 (예: "scenario-concept", "rest-api-call-tool")
     * @param aiAssistantId AI 어시스턴트 ID (LLM 노드에서만 필수, Tool 노드는 null 허용)
     * @param streaming 스트리밍 모드 여부
     * @param nodeConfig 노드 자체 설정 (url, method, headers 등 - Tool 노드에서 필수)
     * @param overrides LLM 오버라이드 설정 (Map 형태)
     * @return 최소 Workflow 객체
     */
    fun createMinimalWorkflow(
      nodeId: String,
      nodeType: String,
      aiAssistantId: Long?,
      streaming: Boolean = false,
      nodeConfig: Map<String, Any>? = null,
      overrides: Map<String, Any>? = null
    ): Workflow {
      // 노드 설정 구성 - nodeConfig가 있으면 먼저 적용
      val config = (nodeConfig?.toMutableMap() ?: mutableMapOf()).apply {
        // streaming 설정 추가
        this["streaming"] = streaming

        // aiAssistantId가 있을 때만 설정 (LLM 노드용)
        aiAssistantId?.let { this["aiAssistantId"] = it }

        // LLM 오버라이드 설정 추가
        overrides?.let { overrideMap ->
          // aiProviderId, aiModelId, aiApiKeyId
          overrideMap["aiProviderId"]?.let { this["aiProviderId"] = it }
          overrideMap["aiModelId"]?.let { this["aiModelId"] = it }
          overrideMap["aiApiKeyId"]?.let { this["aiApiKeyId"] = it }

          // 생성 파라미터
          overrideMap["temperature"]?.let { this["temperature"] = it }
          overrideMap["topP"]?.let { this["topP"] = it }
          overrideMap["topK"]?.let { this["topK"] = it }
          overrideMap["maxTokens"]?.let { this["maxTokens"] = it }
          overrideMap["frequencyPenalty"]?.let { this["frequencyPenalty"] = it }
          overrideMap["presencePenalty"]?.let { this["presencePenalty"] = it }
          overrideMap["responseFormat"]?.let { this["responseFormat"] = it }

          // 리즈닝 설정
          overrideMap["reasoningEffort"]?.let { this["reasoningEffort"] = it }
          overrideMap["noThinkTrigger"]?.let { this["noThinkTrigger"] = it }
        }
      }

      val node = Node(
        id = nodeId,
        type = nodeType,
        data = NodeData(
          config = config,
          startNode = true,
          executionState = ExecutionState(NodeExecutionState.IDLE)
        )
      )

      return Workflow(
        canvasId = "single-node-execution",
        id = "single-node-workflow-$nodeId",
        nodes = listOf(node),
        edges = emptyList(),
        executionOrder = listOf(nodeId)
      )
    }
  }
}
