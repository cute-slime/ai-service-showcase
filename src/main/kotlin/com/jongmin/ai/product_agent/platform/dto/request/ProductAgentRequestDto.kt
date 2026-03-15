package com.jongmin.ai.product_agent.platform.dto.request

import com.jongmin.ai.product_agent.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length
import org.springframework.web.multipart.MultipartFile

/**
 * 카피라이팅 생성 요청 DTO (멀티파트 지원)
 *
 * 상품 정보와 고객 피드백, 이미지를 기반으로 AI가 PDP(Product Detail Page) 카피라이팅을 생성합니다.
 * SSE(Server-Sent Events) 방식으로 실시간 스트리밍 응답을 제공합니다.
 *
 * 멀티파트 폼 데이터로 전송되며, 상품 정보는 data 필드에 JSON String으로 전달됩니다.
 */
data class CopywritingRequest(
  /**
   * 요청 고유 키 (서버에서 자동 설정)
   * 플래그라운드에서 활용되는 값
   */
  @field:Null
  var key: String? = null,

  /**
   * 캔버스 ID (플래그라운드에서 활용)
   */
  var canvasId: String? = null,

  /**
   * 카피라이팅 요청 데이터 (JSON String)
   *
   * CopywritingData 형식의 JSON 문자열로, 다음 정보를 포함합니다:
   * - type: 요청 타입 식별자
   * - copyStyle: 카피라이팅 스타일
   * - productBasicInfo: 상품 기본 정보
   * - customerFeedbackInfo: 고객 피드백 정보 (선택)
   * - additionalPrompt: 추가 프롬프트 (선택)
   */
  @field:NotBlank(message = "요청 데이터는 필수입니다")
  var data: String? = null,

  /**
   * 상품 이미지 목록 (선택)
   *
   * AI가 이미지를 분석하여 카피라이팅에 활용합니다.
   * 최대 10개의 이미지를 업로드할 수 있으며, 각 이미지는 5MB를 초과할 수 없습니다.
   */
  @field:Size(max = 10, message = "이미지는 최대 10개까지 업로드할 수 있습니다")
  @field:MaxFileSize(maxSizeInBytes = 5 * 1024 * 1024, message = "각 이미지 파일은 5MB를 초과할 수 없습니다")
  @field:ValidImageFile(message = "유효하지 않은 이미지 파일 형식입니다. 허용된 형식: JPEG, PNG, GIF, WebP, BMP")
  var images: MutableList<MultipartFile>? = null
)

/**
 * 카피라이팅 요청 데이터 (JSON 파싱용)
 *
 * CopywritingRequest.data 필드의 JSON을 파싱할 때 사용하는 DTO입니다.
 * 가이드 문서(commerce_pdp_ai_copywriting_guide.md)의 마스터 프롬프트 구조를 따릅니다.
 */
data class CopywritingData(
  /**
   * 카피라이팅 스타일 (필수)
   * 생성할 카피의 톤과 스타일을 지정합니다.
   */
  @field:NotNull(message = "카피라이팅 스타일은 필수입니다")
  var copyStyle: CopywritingStyle? = null,

  /**
   * 상품 기본 정보 (필수)
   * 상품명, 카테고리, 브랜드, 타겟 고객층, 제품 스펙, 가격대, 경쟁 우위 등을 포함합니다.
   */
  @field:NotNull(message = "상품 기본 정보는 필수입니다")
  @field:Valid
  var productBasicInfo: ProductBasicInfo? = null,

  /**
   * 고객 피드백 정보 (선택)
   * 평점, 리뷰, 재구매율 등 실제 고객 반응 데이터를 포함합니다.
   */
  @field:Valid
  val customerFeedbackInfo: CustomerFeedbackInfo? = null,

  /**
   * 이벤트 정보 (선택)
   * 프로모션/이벤트 관련 정보를 포함합니다.
   * 이벤트 상품인 경우에만 제공하며, 이벤트명, 유형, 할인율, 기간, 혜택 등을 설정합니다.
   */
  @field:Valid
  val eventInfo: EventInfo? = null,

  /**
   * 스타일 설정 (선택)
   * 설명 스타일, 계절 컨텍스트, 트렌드 강조, 가격 포지셔닝, 이벤트 강조 레벨 등을 설정합니다.
   * 미설정 시 기본값이 적용됩니다.
   */
  @field:Valid
  val stylePreferences: StylePreferences? = null,

  /**
   * 추가 프롬프트 (선택)
   * 특별히 강조하고 싶은 내용이나 추가 지시사항을 자유롭게 입력합니다.
   */
  @field:Length(max = 2000, message = "추가 프롬프트는 2000자를 초과할 수 없습니다")
  val additionalPrompt: String? = null
)
