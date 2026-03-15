//package com.jongmin.ai.product_agent.platform.task
//
//import com.jongmin.jspring.web.entity.JSession
//import com.jongmin.ai.product_agent.platform.dto.request.CopywritingRequest
//import com.jongmin.ai.product_agent.platform.service.ProductAgentService
//import com.jongmin.service.task_executor.model.*
//import reactor.core.publisher.FluxSink
//
///**
// * 카피라이팅 생성 태스크
// *
// * 분산 태스크 실행기를 통해 실행되는 카피라이팅 생성 작업입니다.
// * LLM 호출 비용 제어를 위해 동시 실행이 제한됩니다.
// *
// * @property session 요청 사용자 세션
// * @property request 카피라이팅 요청 DTO
// * @property productAgentService 실제 카피라이팅 생성 서비스
// */
//class CopywritingTask(
//  private val session: JSession?,
//  private val request: CopywritingRequest,
//  private val productAgentService: ProductAgentService
//) : StreamingTask<Unit, String> {
//
//  override val taskType: TaskType = TaskType.PRODUCT_AGENT
//
//  override val priority: Int = TaskPriority.DEFAULT_PRIORITY
//
//  // 카피라이팅은 최대 10분 타임아웃
//  override val timeoutMillis: Long = 600000L
//
//  override fun execute(context: TaskExecutionContext) {
//    throw UnsupportedOperationException(
//      "CopywritingTask는 스트리밍 태스크입니다. executeWithStreaming을 사용하세요."
//    )
//  }
//
//  /**
//   * 스트리밍 카피라이팅 생성 실행
//   *
//   * 기존 ProductAgentService.generateCopywriting을 호출하여
//   * SSE 스트리밍 방식으로 카피라이팅을 생성합니다.
//   */
//  override fun executeWithStreaming(
//    context: TaskExecutionContext,
//    emitter: FluxSink<String>
//  ) {
//    // 진행 상황 업데이트 (시작)
//    context.updateProgress(0, "카피라이팅 생성 시작")
//
//    // 기존 서비스 호출
//    productAgentService.generateCopywriting(session, emitter, request)
//
//    // 참고: generateCopywriting은 내부에서 emitter.complete()를 호출함
//    // 따라서 여기서는 별도 처리 불필요
//  }
//
//  override fun onCancel() {
//    // 취소 처리는 ProductAgentService 내부의 cancellationManager가 담당
//  }
//
//  override fun getDescription(): String {
//    return "카피라이팅 생성 (계정: ${session?.accountId})"
//  }
//}
//
///**
// * CopywritingTask 팩토리
// *
// * Spring DI를 통해 ProductAgentService를 주입받아 태스크를 생성합니다.
// */
//@org.springframework.stereotype.Component
//class CopywritingTaskFactory(
//  private val productAgentService: ProductAgentService
//) {
//
//  /**
//   * 카피라이팅 태스크 생성
//   *
//   * @param session 사용자 세션
//   * @param request 카피라이팅 요청
//   * @return CopywritingTask 인스턴스
//   */
//  fun create(session: JSession?, request: CopywritingRequest): CopywritingTask {
//    return CopywritingTask(session, request, productAgentService)
//  }
//}
