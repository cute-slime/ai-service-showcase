package com.jongmin.ai.generation.provider

import com.jongmin.ai.core.GenerationMediaType
import com.jongmin.ai.generation.dto.GenerationContext
import com.jongmin.ai.generation.dto.GenerationResult
import com.jongmin.ai.generation.dto.ProgressEvent
import reactor.core.publisher.Flux

/**
 * 에셋 생성 프로바이더 인터페이스
 *
 * Strategy 패턴을 적용하여 각 AI 생성 프로바이더별 로직을 캡슐화한다.
 * 새 프로바이더 추가 시 이 인터페이스를 구현하고 @Component로 등록하면
 * [AssetGenerationProviderRegistry]가 자동으로 수집한다.
 *
 * ### 구현 예시:
 * ```kotlin
 * @Component
 * class ComfyUIProvider : AssetGenerationProvider {
 *   override fun getProviderCode() = "COMFYUI"
 *   override fun getSupportedMediaTypes() = listOf(GenerationMediaType.IMAGE)
 *   override fun generate(context: GenerationContext): GenerationResult { ... }
 *   override fun streamProgress(jobId: String): Flux<ProgressEvent> { ... }
 * }
 * ```
 *
 * ### SOLID 원칙 적용:
 * - **SRP**: 각 프로바이더는 자신의 API 연동만 담당
 * - **OCP**: 새 프로바이더는 인터페이스 구현으로 확장
 * - **LSP**: 모든 프로바이더는 동일한 인터페이스로 대체 가능
 * - **ISP**: 필수 메서드만 정의, 선택적 기능은 별도 인터페이스
 * - **DIP**: 상위 모듈은 인터페이스에만 의존
 *
 * @author Claude Code
 * @since 2026.01.21
 * @see AssetGenerationProviderRegistry
 * @see AbstractAssetGenerationProvider
 */
interface AssetGenerationProvider {

  /**
   * 프로바이더 코드 반환
   *
   * multimedia_provider 테이블의 code 컬럼과 일치해야 한다.
   * 예: "COMFYUI", "NOVELAI", "MIDJOURNEY", "RUNWAY", "KLING", "SUNO", "ELEVENLABS"
   *
   * @return 프로바이더 고유 코드
   */
  fun getProviderCode(): String

  /**
   * 지원하는 미디어 타입 목록 반환
   *
   * 이 프로바이더가 생성할 수 있는 미디어 타입을 반환한다.
   * Registry에서 미디어 타입별 프로바이더 조회에 사용된다.
   *
   * @return 지원 미디어 타입 목록
   */
  fun getSupportedMediaTypes(): List<GenerationMediaType>

  /**
   * 에셋 생성 실행
   *
   * 블로킹 호출로, DTE의 가상 스레드(Virtual Thread)에서 실행된다.
   * 실제 AI API를 호출하고 결과를 반환한다.
   *
   * ### 구현 시 주의사항:
   * - 예외 발생 시 [GenerationResult.failure]를 반환하거나 예외를 throw
   * - 타임아웃 처리는 프로바이더 내부에서 수행
   * - 진행 상황은 [streamProgress]를 통해 별도로 발행
   *
   * @param context 생성 요청 컨텍스트
   * @return 생성 결과
   */
  fun generate(context: GenerationContext): GenerationResult

  /**
   * 생성 진행 상황 스트리밍
   *
   * SSE를 통해 FE에 실시간으로 진행 상황을 전달한다.
   * Job 시작부터 완료까지의 모든 이벤트를 발행해야 한다.
   *
   * ### 이벤트 발행 순서:
   * 1. STARTED (0%)
   * 2. IN_PROGRESS (25%, 50%, 75%)
   * 3. COMPLETED (100%) 또는 FAILED
   *
   * @param jobId DTE Job ID
   * @return 진행 상황 이벤트 스트림
   */
  fun streamProgress(jobId: String): Flux<ProgressEvent>

  /**
   * 프로바이더 상태 확인
   *
   * API 연결 상태, 인증 상태 등을 확인한다.
   * 기본 구현은 항상 true를 반환한다.
   *
   * @return 사용 가능 여부
   */
  fun isAvailable(): Boolean = true

  /**
   * 프로바이더 설명 반환
   *
   * 관리 UI 등에서 표시할 설명 문구.
   * 기본 구현은 프로바이더 코드를 반환한다.
   *
   * @return 프로바이더 설명
   */
  fun getDescription(): String = getProviderCode()

  /**
   * 워크플로우 기반 재생성 실행
   *
   * 기존 워크플로우 JSON을 사용하여 파라미터만 오버라이드해서 재생성한다.
   * 주로 ComfyUI처럼 워크플로우 기반 프로바이더에서 지원된다.
   *
   * @param context 생성 요청 컨텍스트
   * @param workflow 오버라이드된 워크플로우 JSON (Map 형태)
   * @param seed 사용할 시드값
   * @return 생성 결과
   * @throws UnsupportedOperationException 워크플로우 기반 재생성을 지원하지 않는 경우
   */
  fun generateWithWorkflow(
    context: GenerationContext,
    workflow: Map<String, Any>,
    seed: Long
  ): GenerationResult {
    throw UnsupportedOperationException(
      "워크플로우 기반 재생성은 ${getProviderCode()} 프로바이더에서 지원하지 않습니다."
    )
  }
}
