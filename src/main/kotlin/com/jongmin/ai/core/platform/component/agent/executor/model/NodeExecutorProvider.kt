package com.jongmin.ai.core.platform.component.agent.executor.model

import com.jongmin.jspring.messaging.event.EventSender
import com.jongmin.jspring.web.entity.JSession
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * 노드 실행자 프로바이더 인터페이스
 *
 * 노드 인스턴스를 생성하는 팩토리 메서드를 정의하는 인터페이스.
 * 각 노드 클래스의 companion object에서 구현하여
 * NodeExecutorFactory의 자동 등록 시스템과 연동한다.
 *
 * ### 구현 예시
 * ```kotlin
 * @NodeType(["scenario-concept", "concept-generator"])
 * class ConceptGeneratorNode(...) : BaseScenarioGeneratorNode(...) {
 *   companion object : NodeExecutorProvider {
 *     override fun createExecutor(
 *       objectMapper: ObjectMapper,
 *       factory: NodeExecutorFactory,
 *       session: JSession,
 *       topic: String,
 *       eventSender: EventSender,
 *       emitter: FluxSink<String>?,
 *       canvasId: String?
 *     ): NodeExecutor<*> {
 *       return ConceptGeneratorNode(
 *         objectMapper, factory, session, topic, eventSender, emitter, canvasId, true
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * ### 동작 원리
 * 1. NodeExecutorRegistry가 Spring Bean 자동 수집 패턴으로 모든 NodeExecutorProvider를 수집
 * 2. 각 Provider가 속한 클래스의 @NodeType 어노테이션에서 지원 타입 추출
 * 3. 타입-Provider 맵 구성 (하나의 Provider가 복수 타입 지원 가능)
 * 4. NodeExecutorFactory가 타입 기반 조회로 Provider.createExecutor() 호출
 *
 * ### 레거시 호환성
 * @NodeType의 배열 속성으로 복수 타입명을 지원하므로,
 * 기존 타입명("concept-generator")과 신규 타입명("scenario-concept") 모두 동일한 Provider로 연결된다.
 *
 * ### 새 노드 추가 절차
 * 1. NodeExecutor를 상속한 노드 클래스 작성
 * 2. 클래스에 @NodeType(["타입명"]) 어노테이션 추가
 * 3. companion object에서 NodeExecutorProvider 구현
 * 4. NodeExecutorFactory 수정 불필요 (자동 등록)
 *
 * @author Claude Code
 * @since 2026.01.03
 */
interface NodeExecutorProvider {
  /**
   * 노드 실행자 인스턴스 생성
   *
   * 주어진 파라미터로 NodeExecutor 인스턴스를 생성한다.
   * 이 메서드는 NodeExecutorFactory가 노드 타입 문자열을 받아
   * 실제 노드 인스턴스를 생성할 때 호출된다.
   *
   * ### 파라미터 설명
   * - objectMapper: JSON 직렬화/역직렬화 (tools.jackson 3.x 사용)
   * - factory: NodeExecutorFactory 인스턴스 (다음 노드 실행 시 필요)
   * - session: 현재 사용자 세션 정보
   * - topic: 이벤트 전송 토픽 (RabbitMQ/Kafka)
   * - eventSender: 이벤트 전송 서비스
   * - emitter: SSE 스트리밍용 FluxSink (WebFlux, null 가능)
   * - canvasId: 워크플로우 캔버스 ID (null 가능)
   *
   * ### 구현 시 주의사항
   * - debugging 파라미터는 일반적으로 true 고정값 사용
   * - 생성자 파라미터 순서는 NodeExecutor의 생성자와 동일하게 유지
   * - companion object로 구현하므로 인스턴스 상태를 가지지 않음
   *
   * @param objectMapper JSON 직렬화/역직렬화용 ObjectMapper (tools.jackson 3.x)
   * @param factory NodeExecutorFactory (다음 노드 실행을 위해 필요)
   * @param session 현재 사용자 세션
   * @param topic 이벤트 전송 토픽
   * @param eventSender 이벤트 전송 서비스
   * @param emitter SSE 스트리밍용 FluxSink (옵션)
   * @param canvasId 워크플로우 캔버스 ID (옵션)
   * @return 생성된 NodeExecutor 인스턴스
   */
  fun createExecutor(
    objectMapper: ObjectMapper,
    factory: NodeExecutorFactory,
    session: JSession,
    topic: String,
    eventSender: EventSender,
    emitter: FluxSink<String>?,
    canvasId: String?
  ): NodeExecutor<*>
}
