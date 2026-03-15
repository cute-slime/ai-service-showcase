package com.jongmin.ai.product_agent.platform.dto.request

import com.jongmin.ai.product_agent.AspectRatio
import com.jongmin.ai.product_agent.ImageStyle
import com.jongmin.ai.product_agent.ReferenceImageRolePreset
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.web.multipart.MultipartFile

/**
 * 상품 이미지 생성 요청 DTO (폼 데이터)
 *
 * AI를 활용하여 마케팅용 상품 이미지를 생성합니다.
 * DTE(분산 태스크 실행기)를 통해 비동기로 처리되며, 생성 완료 후 SSE로 결과가 스트리밍됩니다.
 *
 * 폼 데이터로 전송되며, 상품 정보는 data 필드에 JSON String으로 전달됩니다.
 *
 * @property key 요청 고유 키 (서버에서 자동 설정)
 * @property data 이미지 생성 요청 데이터 (JSON String)
 */
data class ProductImageGenerateRequest(
  /**
   * 요청 고유 키 (서버에서 자동 설정)
   * DTE 작업 추적에 사용됩니다.
   */
  @field:Null(message = "key는 서버에서 자동 생성됩니다")
  var key: String? = null,

  /**
   * 이미지 생성 요청 데이터 (JSON String)
   *
   * ProductImageGenerateData 형식의 JSON 문자열로, 다음 정보를 포함합니다:
   * - productName: 상품명 (필수)
   * - prompt: 이미지 생성 프롬프트 (필수)
   * - imageStyle: 이미지 스타일 (선택)
   * - aspectRatio: 이미지 비율 (선택, 기본값: 1:1)
   * - imageCount: 생성할 이미지 개수 (선택, 기본값: 1)
   */
  @field:NotBlank(message = "요청 데이터는 필수입니다")
  var data: String? = null
)

/**
 * 이미지 생성 요청 데이터 (JSON 파싱용)
 *
 * ProductImageGenerateRequest.data 필드의 JSON을 파싱할 때 사용하는 DTO입니다.
 *
 * @property productName 상품명 (필수, 최대 100자)
 * @property prompt 이미지 생성 프롬프트 (필수, 최대 1000자)
 * @property imageStyle 이미지 스타일 (선택)
 * @property aspectRatio 이미지 비율 (기본값: 1:1)
 * @property imageCount 생성할 이미지 개수 (기본값: 1, 최대 5)
 */
data class ProductImageGenerateData(
  /**
   * 상품명 (필수)
   * 생성할 이미지의 주요 상품명을 입력합니다.
   */
  @field:NotBlank(message = "상품명은 필수입니다")
  @field:Size(max = 100, message = "상품명은 100자를 초과할 수 없습니다")
  val productName: String? = null,

  /**
   * 이미지 생성 프롬프트 (필수)
   * 상품 설명, 원하는 이미지 스타일 등 AI에게 전달할 상세 지시사항을 입력합니다.
   */
  @field:NotBlank(message = "프롬프트는 필수입니다")
  @field:Size(max = 1000, message = "프롬프트는 1000자를 초과할 수 없습니다")
  val prompt: String? = null,

  /**
   * 이미지 스타일 (선택)
   * studio, lifestyle, minimal, vibrant, natural 중 선택
   * null인 경우 기본 스타일 적용
   */
  val imageStyle: ImageStyle? = null,

  /**
   * 이미지 비율 (기본값: 1:1)
   * 1:1, 16:9, 9:16, 4:3, 3:4 중 선택
   */
  val aspectRatio: AspectRatio = AspectRatio.SQUARE,

  /**
   * 생성할 이미지 개수 (기본값: 1)
   * 1~5 사이의 정수값
   */
  @field:Min(value = 1, message = "이미지 개수는 최소 1개입니다")
  @field:Max(value = 5, message = "이미지 개수는 최대 5개입니다")
  val imageCount: Int = 1
)

// ==================== 이미지 합성 (Compose) 관련 DTO ====================

/**
 * 상품 이미지 합성 요청 DTO (multipart/form-data)
 *
 * 여러 참조 이미지를 합성하여 새로운 마케팅 이미지를 생성합니다.
 * DTE(분산 태스크 실행기)를 통해 비동기로 처리되며, 생성 완료 후 SSE로 결과가 스트리밍됩니다.
 *
 * Content-Type: multipart/form-data
 * - data: JSON 문자열 (ProductImageComposeData)
 * - referenceImages: 참조 이미지 파일들 (최대 5장)
 *
 * @property key 요청 고유 키 (서버에서 자동 설정)
 * @property data 이미지 합성 요청 데이터 (JSON String)
 * @property referenceImages 참조 이미지 파일 목록
 */
data class ProductImageComposeRequest(
  /**
   * 요청 고유 키 (서버에서 자동 설정)
   * DTE 작업 추적에 사용됩니다.
   */
  @field:Null(message = "key는 서버에서 자동 생성됩니다")
  var key: String? = null,

  /**
   * 이미지 합성 요청 데이터 (JSON String)
   *
   * ProductImageComposeData 형식의 JSON 문자열로, 다음 정보를 포함합니다:
   * - productName: 상품명 (필수)
   * - prompt: 최종 이미지 생성 프롬프트 (필수)
   * - referenceImageRoles: 참조 이미지 역할 정보 배열 (필수)
   * - imageStyle: 이미지 스타일 (선택)
   * - aspectRatio: 이미지 비율 (선택, 기본값: 1:1)
   * - imageCount: 생성할 이미지 개수 (선택, 기본값: 1)
   */
  @field:NotBlank(message = "요청 데이터는 필수입니다")
  var data: String? = null,

  /**
   * 참조 이미지 파일 목록
   *
   * 최대 5장까지 업로드 가능합니다.
   * 순서가 중요: data.referenceImageRoles[i]와 referenceImages[i]가 1:1 매칭됩니다.
   * 지원 형식: PNG, JPEG, WEBP (각 파일 최대 10MB)
   */
  @field:NotNull(message = "참조 이미지는 필수입니다")
  @field:Size(min = 1, max = 5, message = "참조 이미지는 1~5장이어야 합니다")
  var referenceImages: List<MultipartFile>? = null
)

/**
 * 이미지 합성 요청 데이터 (JSON 파싱용)
 *
 * ProductImageComposeRequest.data 필드의 JSON을 파싱할 때 사용하는 DTO입니다.
 *
 * @property productName 상품명 (필수, 최대 100자)
 * @property prompt 최종 이미지 생성 프롬프트 (필수, 최대 1000자)
 * @property referenceImageRoles 참조 이미지 역할 정보 배열 (필수, 1~5개)
 * @property imageStyle 이미지 스타일 (선택)
 * @property aspectRatio 이미지 비율 (기본값: 1:1)
 * @property imageCount 생성할 이미지 개수 (기본값: 1, 최대 5)
 */
data class ProductImageComposeData(
  /**
   * 상품명 (필수)
   * 합성할 이미지의 주요 상품명을 입력합니다.
   */
  @field:NotBlank(message = "상품명은 필수입니다")
  @field:Size(max = 100, message = "상품명은 100자를 초과할 수 없습니다")
  val productName: String? = null,

  /**
   * 최종 이미지 생성 프롬프트 (필수)
   * 참조 이미지들을 어떻게 합성할지에 대한 상세 지시사항을 입력합니다.
   * 예: "모델이 토트백을 들고 카페에서 커피를 마시는 장면, 자연스러운 라이프스타일 분위기"
   */
  @field:NotBlank(message = "프롬프트는 필수입니다")
  @field:Size(max = 1000, message = "프롬프트는 1000자를 초과할 수 없습니다")
  val prompt: String? = null,

  /**
   * 참조 이미지 역할 정보 배열 (필수)
   * 각 참조 이미지가 어떤 역할을 하는지 설명합니다.
   * 배열 순서는 referenceImages 파일 순서와 1:1 매칭됩니다.
   */
  @field:NotNull(message = "참조 이미지 역할 정보는 필수입니다")
  @field:Size(min = 1, max = 5, message = "참조 이미지 역할 정보는 1~5개이어야 합니다")
  @field:Valid
  val referenceImageRoles: List<ReferenceImageRoleInfo>? = null,

  /**
   * 이미지 스타일 (선택)
   * studio, lifestyle, minimal, vibrant, natural 중 선택
   * null인 경우 기본 스타일 적용
   */
  val imageStyle: ImageStyle? = null,

  /**
   * 이미지 비율 (기본값: 1:1)
   * 1:1, 16:9, 9:16, 4:3, 3:4 중 선택
   */
  val aspectRatio: AspectRatio = AspectRatio.SQUARE,

  /**
   * 생성할 이미지 개수 (기본값: 1)
   * 1~5 사이의 정수값
   */
  @field:Min(value = 1, message = "이미지 개수는 최소 1개입니다")
  @field:Max(value = 5, message = "이미지 개수는 최대 5개입니다")
  val imageCount: Int = 1
)

/**
 * 참조 이미지 역할 정보
 *
 * 각 참조 이미지가 합성 과정에서 어떤 역할을 하는지 정의합니다.
 * preset을 통해 사전 정의된 역할을 선택하거나,
 * description으로 자유롭게 역할을 설명할 수 있습니다.
 *
 * @property preset 역할 프리셋 (선택, product/model/background/props/style-ref)
 * @property description 역할에 대한 상세 설명 (필수)
 */
data class ReferenceImageRoleInfo(
  /**
   * 역할 프리셋 (선택)
   * 사전 정의된 역할 중 하나를 선택합니다.
   * null인 경우 description만으로 역할을 정의합니다.
   */
  val preset: ReferenceImageRolePreset? = null,

  /**
   * 역할에 대한 상세 설명 (필수)
   * 이 이미지가 최종 합성 이미지에서 어떤 역할을 하는지 설명합니다.
   * 예: "합성할 메인 제품 이미지", "토트백을 들고 있는 여성 모델 포즈"
   */
  @field:NotBlank(message = "역할 설명은 필수입니다")
  @field:Size(max = 500, message = "역할 설명은 500자를 초과할 수 없습니다")
  val description: String? = null
)
