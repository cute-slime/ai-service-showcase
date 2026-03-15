package com.jongmin.ai.insight.platform.service

import com.jongmin.jspring.core.dto.MessageType
import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.CopyWriteEventType
import com.jongmin.ai.insight.component.AiAnalysisResponseBuilder
import com.jongmin.ai.insight.component.AnalysisParameterParser
import com.jongmin.ai.insight.component.DataQualityEvaluator
import com.jongmin.ai.insight.component.ProductDataMergerService
import com.jongmin.ai.insight.platform.dto.request.ProductAnalysisOptions
import com.jongmin.ai.insight.platform.dto.request.ProductAnalyze
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.FluxSink
import tools.jackson.databind.ObjectMapper

/**
 * PDP 카피라이팅 메인 서비스
 *
 * PDP(Product Detail Page) 카피라이팅 생성의 전체 프로세스를 조율하고 관리합니다.
 * SSE 스트리밍 방식으로 실시간 진행 상황을 클라이언트에 전송합니다.
 *
 * ### 주요 책임:
 * - 전체 카피라이팅 생성 프로세스 조율
 * - SSE 이벤트 메시지 생성 및 전송
 * - 각 단계별 서비스 호출 및 결과 취합
 * - 단계별 진행 상황 로깅
 *
 * ### 프로세스 단계:
 * 1. INPUT_ANALYSIS: 입력 데이터 분석 (약 5초)
 * 2. IMAGE_PROCESSING: 이미지 처리 (약 25초, 이미지 개수에 비례)
 * 3. COPYWRITING: 카피라이팅 생성 (약 15초)
 * 4. COMPLETED: 전체 프로세스 완료
 *
 * ### 오류 처리:
 * - 각 단계에서 예외 발생 시 ERROR 이벤트 전송
 * - 스트림 에러 처리 및 로깅
 *
 * @property objectMapper JSON 직렬화/역직렬화
 * @property analysisParameterParser 메타데이터 파싱 서비스
 * @property dataQualityEvaluator 데이터 품질 평가 서비스
 * @property aiAnalysisResponseBuilder AI 분석 응답 구성 서비스
 * @property productDataMergerService 제품 데이터 병합 서비스
 */
@Service
class ProductAnalysisService(
  private val objectMapper: ObjectMapper,
  private val analysisParameterParser: AnalysisParameterParser,
  private val dataQualityEvaluator: DataQualityEvaluator,
  private val aiAnalysisResponseBuilder: AiAnalysisResponseBuilder,
  private val productDataMergerService: ProductDataMergerService
) {
  private val kLogger = KotlinLogging.logger {}

  /**
   * PDP 카피라이팅 생성 프로세스 실행
   *
   * ### 실행 흐름:
   * 1. 메타데이터 파싱
   * 2. 입력 분석 단계 (INPUT_ANALYSIS)
   * 3. 이미지 처리 단계 (IMAGE_PROCESSING)
   * 4. 카피라이팅 생성 단계 (COPYWRITING)
   * 5. 완료 이벤트 전송 (COMPLETED)
   *
   * ### SSE 이벤트:
   * - 각 단계마다 ACTIVE, IN_PROGRESS, DONE 상태 전송
   * - 오류 발생 시 ERROR 이벤트 전송
   *
   * @param dto PDP 카피라이팅 요청 DTO
   * @param session 요청 사용자 계정 ID
   * @param emitter SSE Flux Emitter
   */
  fun executeProductAnalysis(
    session: JSession,
    emitter: FluxSink<String>,
    dto: ProductAnalyze
  ) {
    try {
      // === 메타데이터 파싱 ===
      val analysisOptions = analysisParameterParser.parseProductAnalysisOptions(dto.analysisOptions)
      val parsedMetadata = analysisParameterParser.parseMetadata(dto.metadata)
      val imageCount = dto.images?.size ?: 0

      // === 1단계: 입력 분석 (약 5초) ===
      // 이 단계에서는 요청받은 사용자의 선호설정과 metadata 의 정보를 통해 분석에 사용할 유의미한 액션을 만들어야한다.
      val refinedOptions = processInputAnalysis(dto, analysisOptions, parsedMetadata, session, emitter)

      // === 2단계: 이미지 처리 (약 25초) ===
      processImageProcessing(dto, imageCount, emitter)

      // === 3단계: 카피라이팅 생성 (약 15초) ===
      // 정제된 옵션을 사용하여 카피라이팅 생성
      processSummary(dto, refinedOptions, parsedMetadata, imageCount, emitter)

      // === 전체 프로세스 완료 ===
      emitter.next(
        createEventMessage(
          CopyWriteEventType.COMPLETED,
          StatusType.DONE,
          data = mapOf(
            "totalDuration" to "약 30초",
            "completedAt" to System.currentTimeMillis()
          )
        )
      )

      kLogger.info { "PDP 카피라이팅 스트리밍 완료 - 3단계 처리 완료 (입력분석 -> 이미지처리 -> 카피라이팅)" }

      // 스트림 종료
      emitter.complete()
    } catch (e: Exception) {
      handleError(e, emitter)
    }
  }

  /**
   * 1단계: 입력 분석 처리
   *
   * ### 처리 내용:
   * - 메타데이터 분석
   * - 분석 옵션과 메타데이터 병합
   * - LLM을 통한 데이터 정제
   * - 데이터 품질 평가
   *
   * ### 이벤트:
   * - ACTIVE: 입력 분석 시작
   * - IN_PROGRESS: 메타데이터 분석 및 병합 중
   * - DONE: 입력 분석 완료 (정제된 분석 옵션 포함)
   *
   * @param dto PDP 요청 DTO
   * @param analysisOptions 사용자 입력 분석 옵션
   * @param parsedMetadata 파싱된 메타데이터
   * @param session 계정
   * @param emitter SSE Emitter
   * @return 병합 및 정제된 ProductAnalysisOptions
   */
  private fun processInputAnalysis(
    dto: ProductAnalyze,
    analysisOptions: ProductAnalysisOptions,
    parsedMetadata: Map<String, Any>,
    session: JSession,
    emitter: FluxSink<String>
  ): ProductAnalysisOptions {
    kLogger.info { "입력 분석 시작 - 계정: ${session.accountId}" }

    // 분석 시작 이벤트
    emitter.next(createEventMessage(CopyWriteEventType.INPUT_ANALYSIS, StatusType.ACTIVE, data = session.accountId))
    Thread.sleep(500)

    // 메타데이터 분석 중 이벤트
    emitter.next(
      createEventMessage(
        CopyWriteEventType.INPUT_ANALYSIS,
        StatusType.IN_PROGRESS,
        data = mapOf(
          "analyzing" to "metadata",
          "step" to "데이터 병합 및 정제 중"
        )
      )
    )

    // ProductDataMergerService를 사용하여 데이터 병합 및 정제
    val refinedOptions = try {
      kLogger.info { "데이터 병합 시작 - 메타데이터 키: ${parsedMetadata.keys}" }

      // LLM을 통한 데이터 병합 및 정제 (ultrathink 모델 사용)
      val refined = productDataMergerService.mergeAndRefineData(analysisOptions, parsedMetadata)

      kLogger.info { "데이터 병합 완료 - 제품명: ${refined.productBasicInfo?.productName}" }
      refined
    } catch (e: Exception) {
      kLogger.error(e) { "데이터 병합 실패, 기본 옵션 사용" }
      // 병합 실패 시 기본 옵션 반환
      analysisOptions
    }

    // 데이터 품질 평가
    val qualityScore = dataQualityEvaluator.evaluateDataQuality(refinedOptions)

    kLogger.info { "입력 분석 완료 - 데이터 품질 점수: $qualityScore" }

    // 분석 완료 이벤트 (정제된 옵션 정보 포함)
    emitter.next(
      createEventMessage(
        CopyWriteEventType.INPUT_ANALYSIS,
        StatusType.DONE,
        data = mapOf(
          "productName" to (refinedOptions.productBasicInfo?.productName ?: "미정"),
          "category" to (refinedOptions.productBasicInfo?.category ?: "미분류"),
          "dataQualityScore" to qualityScore,
          "refinedFields" to listOfNotNull(
            refinedOptions.productBasicInfo?.let { "제품 기본 정보" },
            refinedOptions.salesInfo?.let { "판매 정보" },
            refinedOptions.customerFeedbackInfo?.let { "고객 피드백" },
            refinedOptions.analysisFocus?.let { "분석 초점" }
          )
        )
      )
    )

    return refinedOptions
  }

  /**
   * 2단계: 이미지 처리
   *
   * ### 처리 내용:
   * - 이미지 업로드 확인
   * - 이미지 개수에 비례한 처리 시간
   * - 각 이미지별 진행률 전송
   *
   * ### 이벤트:
   * - ACTIVE: 이미지 처리 시작
   * - IN_PROGRESS: 각 이미지 처리 진행 (진행률 포함)
   * - DONE: 이미지 처리 완료 (처리된 이미지 정보 포함)
   *
   * @param dto PDP 요청 DTO
   * @param imageCount 이미지 개수
   * @param emitter SSE Emitter
   */
  private fun processImageProcessing(
    dto: ProductAnalyze,
    imageCount: Int,
    emitter: FluxSink<String>
  ) {
    // 이미지 처리 시작 이벤트
    emitter.next(
      createEventMessage(
        CopyWriteEventType.IMAGE_PROCESSING,
        StatusType.ACTIVE,
        data = mapOf("totalImages" to imageCount)
      )
    )
    Thread.sleep(1000)
    // TODO VLM CALL

    // 각 이미지별 처리 진행 (총 25초 정도 소요되도록 조정)
    val totalImageProcessingTime = 25000L // 25초
    val delayPerImage = if (imageCount > 0) totalImageProcessingTime / imageCount else 0L

    repeat(imageCount) { index ->
      val progress = ((index + 1).toDouble() / imageCount * 100).toInt()

      // 이미지 처리 진행 이벤트
      emitter.next(
        createEventMessage(
          CopyWriteEventType.IMAGE_PROCESSING,
          StatusType.IN_PROGRESS,
          data = mapOf(
            "currentImage" to (index + 1),
            "totalImages" to imageCount,
            "progress" to progress,
            "imageName" to (dto.images?.get(index)?.originalFilename ?: "image_${index + 1}.jpg"),
            "imageSize" to (dto.images?.get(index)?.size ?: 0L)
          )
        )
      )

      // 마지막 이미지가 아닌 경우에만 대기
      if (index < imageCount - 1) {
        Thread.sleep(delayPerImage)
      }
    }

    // 이미지 처리 완료 (처리된 이미지 정보 포함)
    val processedImages = dto.images?.mapIndexed { index, image ->
      mapOf(
        "index" to index,
        "name" to (image.originalFilename ?: "image_${index + 1}.jpg"),
        "size" to image.size,
        "processedAt" to System.currentTimeMillis()
      )
    } ?: emptyList()

    emitter.next(
      createEventMessage(
        CopyWriteEventType.IMAGE_PROCESSING,
        StatusType.DONE,
        data = mapOf(
          "processedImages" to processedImages,
          "totalProcessed" to imageCount
        )
      )
    )
    Thread.sleep(2000)
  }

  /**
   * 3단계: 카피라이팅 생성
   *
   * ### 처리 내용:
   * - AI 분석 응답 생성 (데이터 품질 평가, 카피라이팅, 리뷰 요약, 마케팅 인사이트 등)
   * - 최종 응답 객체 구성
   *
   * ### 이벤트:
   * - ACTIVE: 카피라이팅 생성 시작
   * - IN_PROGRESS: 카피라이팅 생성 중
   * - DONE: 카피라이팅 생성 완료 (AI 분석 결과 포함)
   *
   * @param dto PDP 요청 DTO
   * @param refinedOptions 정제된 분석 옵션
   * @param parsedMetadata 파싱된 메타데이터
   * @param imageCount 이미지 개수
   * @param emitter SSE Emitter
   * @return 생성된 카피라이팅 응답
   */
  private fun processSummary(
    dto: ProductAnalyze,
    refinedOptions: ProductAnalysisOptions,
    parsedMetadata: Map<String, Any>,
    imageCount: Int,
    emitter: FluxSink<String>
  ) {
    // 카피라이팅 시작 이벤트
    emitter.next(createEventMessage(CopyWriteEventType.COPYWRITING, StatusType.ACTIVE, data = null))
    Thread.sleep(2000)

    // 카피라이팅 진행 중
    emitter.next(
      createEventMessage(
        CopyWriteEventType.COPYWRITING,
        StatusType.IN_PROGRESS,
        data = mapOf("generating" to "product_description")
      )
    )
    Thread.sleep(13000)

    // 카피라이팅 완료 (AI 분석 결과 기반 실제 데이터)
    val generatedInsight = aiAnalysisResponseBuilder.buildResponse(dto, refinedOptions, parsedMetadata, imageCount)

    emitter.next(createEventMessage(CopyWriteEventType.COPYWRITING, StatusType.DONE, data = generatedInsight))
  }

  /**
   * 오류 처리
   *
   * ### 오류 처리 흐름:
   * 1. 오류 로깅 (ERROR 레벨)
   * 2. ERROR 이벤트 전송 (오류 타입 포함)
   * 3. 스트림 에러 전파
   *
   * @param e 발생한 예외
   * @param emitter SSE Emitter
   */
  private fun handleError(e: Exception, emitter: FluxSink<String>) {
    kLogger.error(e) { "PDP 카피라이팅 스트리밍 중 오류 발생" }

    // 에러 이벤트 전송
    emitter.next(
      createEventMessage(
        CopyWriteEventType.ERROR, StatusType.FAILED, data = mapOf("errorType" to (e::class.simpleName ?: "Unknown"))
      )
    )

    // emitter.error(e)
    emitter.complete()
  }

  /**
   * SSE 이벤트 메시지 생성 헬퍼 함수
   *
   * ### 메시지 구조:
   * ```json
   * {
   *   "type": "AI_COPYWRITING_STATUS_CHANGED",
   *   "eventType": "INPUT_ANALYSIS|IMAGE_PROCESSING|COPYWRITING|COMPLETED|ERROR",
   *   "status": "ACTIVE|IN_PROGRESS|DONE|FAILED",
   *   "data": {...}
   * }
   * ```
   *
   * @param eventType 카피라이팅 이벤트 타입
   * @param status 현재 처리 상태
   * @param data 추가 데이터 (단계별 처리 결과 등)
   * @return JSON 형식의 이벤트 메시지
   */
  private fun createEventMessage(
    eventType: CopyWriteEventType,
    status: StatusType,
    data: Any? = null
  ): String {
    return objectMapper.writeValueAsString(
      mapOf(
        "type" to MessageType.AI_COPYWRITING_STATUS_CHANGED,
        "eventType" to eventType,
        "status" to status,
        "data" to data,
      )
    )
  }
}
