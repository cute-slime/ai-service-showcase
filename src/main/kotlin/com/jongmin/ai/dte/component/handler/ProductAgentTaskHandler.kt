package com.jongmin.ai.dte.component.handler

import com.jongmin.ai.dte.component.handler.executor.ProductAgentExecutor
import com.jongmin.jspring.dte.component.handler.TaskHandler
import com.jongmin.jspring.dte.entity.DistributedJob
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * PRODUCT_AGENT 타입 태스크 핸들러
 *
 * 상품 관련 AI 작업을 통합 라우팅합니다.
 * 실제 작업 실행은 각 [ProductAgentExecutor] 구현체에 위임합니다.
 *
 * ### 지원 작업 타입:
 * - **COPYWRITING** (기본): 카피라이팅 생성
 * - **IMAGE_GENERATE**: 상품 이미지 생성 (Text-to-Image)
 * - **IMAGE_COMPOSE**: 상품 이미지 합성 (Image Mixing)
 * - **MARKETING_CAMPAIGN**: 마케팅 캠페인 생성
 * - **WRITING**: 글쓰기 도구
 *
 * ### SOLID 원칙 적용:
 * - **SRP**: 라우팅 역할만 담당, 실제 작업은 Executor에 위임
 * - **OCP**: 새 작업 타입 추가 시 새 Executor만 구현하면 됨
 * - **DIP**: 추상화(ProductAgentExecutor)에 의존
 *
 * @property executors 등록된 모든 작업 실행기 목록 (Spring이 자동 주입)
 */
@Component
class ProductAgentTaskHandler(
  private val executors: List<ProductAgentExecutor>
) : TaskHandler {

  private val kLogger = KotlinLogging.logger {}

  /**
   * subType → Executor 매핑
   * Spring이 주입한 Executor 목록을 subType 기준으로 인덱싱
   */
  private val executorMap: Map<String?, ProductAgentExecutor> = executors.associateBy { it.getSubType() }

  /**
   * 기본 Executor (subType이 null인 Executor, 즉 CopywritingExecutor)
   */
  private val defaultExecutor: ProductAgentExecutor? = executorMap[null]

  companion object {
    const val TASK_TYPE = "PRODUCT_AGENT"
  }

  init {
    kLogger.info {
      """
            |========== ProductAgentTaskHandler 초기화 ==========
            |등록된 Executor 목록:
            |${executors.map { "  - ${it.getSubType() ?: "DEFAULT"}: ${it::class.simpleName}" }.joinToString("\n")}
            |=====================================================
            """.trimMargin()
    }
  }

  override val type: String = TASK_TYPE

  /**
   * PRODUCT_AGENT 작업을 실행합니다.
   *
   * payload의 subType에 따라 적절한 Executor를 찾아 작업을 위임합니다.
   * subType이 없거나 매칭되는 Executor가 없으면 기본 Executor를 사용합니다.
   *
   * @param job 실행할 작업
   * @throws IllegalStateException 적절한 Executor를 찾을 수 없는 경우
   */
  override fun execute(job: DistributedJob) {
    val payload = job.payload as Map<*, *>
    val subType = payload["subType"] as String?

    kLogger.info { "PRODUCT_AGENT 작업 실행 - jobId: ${job.id}, subType: ${subType ?: "DEFAULT"}" }

    // subType에 맞는 Executor 찾기
    val executor = findExecutor(subType)

    // 작업 위임
    executor.execute(job, payload)
  }

  /**
   * subType에 맞는 Executor를 찾습니다.
   *
   * @param subType 작업 서브타입
   * @return 매칭되는 Executor
   * @throws IllegalStateException Executor를 찾을 수 없는 경우
   */
  private fun findExecutor(subType: String?): ProductAgentExecutor {
    // 1. subType으로 직접 매칭 시도
    val executor = executorMap[subType]
    if (executor != null) {
      kLogger.debug { "Executor 매칭 성공 - subType: $subType, executor: ${executor::class.simpleName}" }
      return executor
    }

    // 2. 기본 Executor 사용
    if (defaultExecutor != null) {
      kLogger.debug { "기본 Executor 사용 - subType: $subType, executor: ${defaultExecutor::class.simpleName}" }
      return defaultExecutor
    }

    // 3. Executor를 찾을 수 없음
    throw IllegalStateException("적절한 Executor를 찾을 수 없습니다. subType: $subType")
  }
}
