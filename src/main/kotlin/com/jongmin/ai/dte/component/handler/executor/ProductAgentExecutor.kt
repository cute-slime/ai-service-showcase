package com.jongmin.ai.dte.component.handler.executor

import com.jongmin.jspring.dte.entity.DistributedJob

/**
 * 상품 에이전트 작업 실행기 인터페이스
 *
 * Strategy 패턴을 적용하여 각 작업 타입별로 독립적인 실행기를 구현합니다.
 * ProductAgentTaskHandler는 이 인터페이스에 의존하여 작업을 위임합니다.
 *
 * ### SOLID 원칙 적용:
 * - **SRP**: 각 구현체는 하나의 작업 타입만 담당
 * - **OCP**: 새 작업 타입 추가 시 기존 코드 수정 없이 새 구현체만 추가
 * - **LSP**: 모든 구현체는 동일한 계약을 준수
 * - **DIP**: 상위 모듈(TaskHandler)은 추상화에 의존
 */
interface ProductAgentExecutor {

  /**
   * 이 실행기가 처리하는 서브타입을 반환합니다.
   *
   * @return 서브타입 문자열, null이면 기본 실행기로 동작
   */
  fun getSubType(): String?

  /**
   * 작업을 실행합니다.
   *
   * @param job 실행할 분산 작업
   * @param payload 작업 페이로드 (JSON 파싱된 Map)
   */
  fun execute(job: DistributedJob, payload: Map<*, *>)
}
