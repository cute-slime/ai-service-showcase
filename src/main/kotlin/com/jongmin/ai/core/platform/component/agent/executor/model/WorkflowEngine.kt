package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.ai.core.IAiChatMessage

/**
 * AI 워크플로우 실행을 담당하는 엔진 인터페이스
 *
 * 워크플로우는 여러 노드(단계)로 구성되어 있으며, 각 노드는 특정 작업을 수행합니다.
 * 이 엔진은 시작 노드부터 종료 노드까지의 전체 플로우를 순차적으로 실행하고,
 * 노드 간 데이터를 전달하며 최종 결과를 생성합니다.
 */
interface WorkflowEngine {
  /**
   * 채팅 메시지 기반으로 Workflow를 실행합니다.
   *
   * 이 메서드는 대화형 AI 워크플로우 실행에 사용되며, 사용자의 채팅 메시지를
   * 입력으로 받아 워크플로우를 처리합니다.
   *
   * @param messages 워크플로우 실행에 사용될 채팅 메시지 목록
   *                 - null인 경우: 시작 노드의 config에 설정된 기본값을 사용
   *                 - 비어있지 않은 경우: 마지막 메시지를 워크플로우의 질문(question)으로 사용
   *                 - multiturn 옵션이 true인 경우: 전체 메시지 히스토리를 컨텍스트로 전달
   *
   * ### 동작 방식:
   * 1. messages가 null이면 시작 노드의 기본 설정을 사용하여 워크플로우 시작
   * 2. messages가 있으면 마지막 메시지를 현재 입력으로 사용
   * 3. 실행 노드의 multiturn 설정이 활성화되어 있으면 전체 대화 컨텍스트 유지
   * 4. 각 노드를 순차적으로 실행하며 결과를 다음 노드로 전달
   */
  fun executeWorkflow(messages: List<IAiChatMessage>? = null)

  /**
   * 키-값 맵 기반으로 Workflow를 실행합니다.
   *
   * 이 메서드는 구조화된 데이터 입력을 받아 워크플로우를 실행하며,
   * 채팅 메시지가 아닌 일반적인 데이터 처리 워크플로우에 사용됩니다.
   *
   * @param input 워크플로우 실행에 필요한 입력 데이터 맵
   *              - null인 경우: 시작 노드의 config에 설정된 기본값을 사용
   *              - 각 키는 워크플로우 내 변수명에 대응
   *              - 값은 해당 변수에 할당될 데이터 (Any 타입으로 다양한 데이터 구조 지원)
   *
   * ### 사용 예시:
   * ```kotlin
   * val input = mapOf(
   *     "userId" to "user123",
   *     "query" to "상품 추천",
   *     "context" to mapOf("category" to "전자제품")
   * )
   * workflowEngine.executeWorkflow(input)
   * ```
   */
  fun executeWorkflow(input: Map<String, Any>)
}
