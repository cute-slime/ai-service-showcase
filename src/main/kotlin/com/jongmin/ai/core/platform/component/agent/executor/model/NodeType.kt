package com.jongmin.ai.core.platform.component.agent.executor.model

/**
 * 노드 타입 선언 어노테이션
 *
 * NodeExecutor 구현 클래스에 노드 타입 문자열을 선언하는 어노테이션.
 * 복수의 타입명을 지원하여 레거시 호환성을 유지할 수 있다.
 *
 * ### 사용 예시
 * ```kotlin
 * @NodeType(["scenario-concept", "concept-generator"])
 * class ConceptGeneratorNode(...) : BaseScenarioGeneratorNode(...) {
 *   // ...
 * }
 * ```
 *
 * ### 배열 순서 규칙
 * - 첫 번째 타입: 주 타입 (권장 사용 타입명, 예: "scenario-concept")
 * - 나머지 타입: 레거시 호환용 별칭 (예: "concept-generator")
 *
 * ### 단일 타입 선언
 * ```kotlin
 * @NodeType(["text-visualize"])
 * class TextVisualizeNode(...) : NodeExecutor(...) {
 *   // ...
 * }
 * ```
 *
 * ### 동작 원리
 * NodeExecutorRegistry가 런타임에 모든 노드 클래스를 스캔하여
 * 이 어노테이션에 선언된 타입 문자열들을 읽어 타입-클래스 매핑을 자동 구성한다.
 * 이를 통해 NodeExecutorFactory의 하드코딩된 when절을 제거할 수 있다.
 *
 * @property value 노드 타입 문자열 배열 (최소 1개 이상 필수)
 *
 * @author Claude Code
 * @since 2026.01.03
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NodeType(
  /**
   * 노드 타입 문자열 배열
   *
   * 하나의 노드 클래스가 여러 타입명으로 등록될 수 있도록 배열로 선언한다.
   * 첫 번째 요소가 주 타입이며, 나머지는 레거시 호환용 별칭이다.
   *
   * 예:
   * - `["scenario-concept", "concept-generator"]` - ConceptGeneratorNode용
   * - `["text-visualize"]` - TextVisualizeNode용 (단일 타입)
   */
  val value: Array<String>
)
