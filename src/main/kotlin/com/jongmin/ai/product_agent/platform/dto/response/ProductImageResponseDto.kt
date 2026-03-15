package com.jongmin.ai.product_agent.platform.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.product_agent.platform.entity.QProductAgentOutput.productAgentOutput
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import java.time.ZonedDateTime

/**
 * 상품 이미지 생성 결과 상세 DTO
 *
 * outputDataJson을 파싱하여 이미지 생성 결과의 상세 정보를 제공합니다.
 *
 * @property id 고유 식별자
 * @property productName 상품명
 * @property prompt 사용된 프롬프트
 * @property imageStyle 적용된 이미지 스타일
 * @property aspectRatio 적용된 이미지 비율
 * @property generatedImageUrls 생성된 이미지 URL 목록
 * @property thumbnailUrl 대표 썸네일 이미지 URL
 * @property status 상태
 * @property createdAt 생성일시
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductImageGenerationItem(
  val id: Long,
  val productName: String?,
  val prompt: String?,
  val imageStyle: String?,
  val aspectRatio: String?,
  val generatedImageUrls: List<String>?,
  val thumbnailUrl: String?,
  val status: StatusType?,
  val createdAt: ZonedDateTime?
)

/**
 * 상품 이미지 생성 이력 목록 아이템 DTO
 *
 * 목록 조회 시 사용되는 간략한 정보를 제공합니다.
 * QueryDSL Projection을 지원합니다.
 *
 * @property id 고유 식별자
 * @property accountId 생성한 사용자의 계정 ID
 * @property title 제목 (상품명)
 * @property description 설명 (프롬프트)
 * @property thumbnailUrl 대표 섬네일 이미지 URL
 * @property status 상태값
 * @property createdAt 생성일시
 * @property updatedAt 수정일시
 */
data class ProductImageHistoryItem(
  var id: Long? = null,
  var accountId: Long? = null,
  var title: String? = null,
  var description: String? = null,
  var thumbnailUrl: String? = null,
  var outputDataJson: String? = null,
  var status: StatusType? = null,
  var createdAt: ZonedDateTime? = null,
  var updatedAt: ZonedDateTime? = null
) {
  companion object {
    /**
     * QueryDSL Projection 빌더
     *
     * 목록 조회 시 사용하는 Projection입니다.
     * ProductAgentOutput 엔티티의 주요 필드를 매핑합니다.
     */
    fun buildProjection(): ConstructorExpression<ProductImageHistoryItem> = Projections.constructor(
      ProductImageHistoryItem::class.java,
      productAgentOutput.id,
      productAgentOutput.accountId,
      productAgentOutput.title,
      productAgentOutput.description,
      productAgentOutput.thumbnailUrl,
      productAgentOutput.outputDataJson,
      productAgentOutput.status,
      productAgentOutput.createdAt,
      productAgentOutput.updatedAt
    )
  }
}

/**
 * outputDataJson에 저장되는 이미지 생성 결과 데이터
 *
 * 이미지 생성 완료 후 JSON으로 직렬화되어 저장됩니다.
 * S3 key를 저장하여 조회 시 Presigned URL을 생성할 수 있도록 합니다.
 *
 * @property productName 상품명
 * @property prompt 사용된 프롬프트
 * @property imageStyle 적용된 이미지 스타일
 * @property aspectRatio 적용된 이미지 비율
 * @property imageCount 생성된 이미지 개수
 * @property generatedImageKeys 생성된 이미지의 S3 key 목록 (조회 시 Presigned URL 생성)
 */
data class ProductImageOutputData(
  val productName: String,
  val prompt: String,
  val imageStyle: String?,
  val aspectRatio: String,
  val imageCount: Int,
  val generatedImageKeys: List<String>
)
