package com.jongmin.ai.product_agent.platform.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.product_agent.platform.entity.QProductAgentOutput.productAgentOutput
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import java.time.ZonedDateTime

/**
 * AgentOutput 목록 조회 응답 DTO
 *
 * 에이전트 출력물 목록 조회 시 반환되는 아이템입니다.
 * 타입별로 details 필드에 상세 정보가 포함됩니다.
 *
 * @property id 고유 식별자
 * @property accountId 생성한 사용자의 계정 ID
 * @property type 에이전트 출력물 타입
 * @property title 제목 (타입별: 카피라이팅=메인카피, 이미지=상품명)
 * @property description 설명 (타입별: 카피라이팅=서브카피, 이미지=프롬프트)
 * @property thumbnailUrl 대표 섬네일 이미지 URL
 * @property status 상태값
 * @property createdAt 생성일시
 * @property updatedAt 수정일시
 * @property details 타입별 상세 정보 (outputDataJson 파싱 결과)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProductAgentOutputItem(
  val id: Long,
  val accountId: Long?,
  val type: ProductAgentOutputType,
  val title: String?,
  val description: String?,
  val thumbnailUrl: String?,
  val status: StatusType?,
  val createdAt: ZonedDateTime?,
  val updatedAt: ZonedDateTime?,
  val details: Map<String, Any?>? = null
)

/**
 * QueryDSL Projection용 내부 DTO
 *
 * 데이터베이스에서 조회 후 ProductAgentOutputItem으로 변환됩니다.
 * outputDataJson을 포함하여 타입별 상세 정보 파싱에 사용됩니다.
 */
data class ProductAgentOutputProjection(
  var id: Long? = null,
  var accountId: Long? = null,
  var type: ProductAgentOutputType? = null,
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
     * outputDataJson을 포함하여 타입별 상세 정보 파싱에 사용됩니다.
     */
    fun buildProjection(): ConstructorExpression<ProductAgentOutputProjection> = Projections.constructor(
      ProductAgentOutputProjection::class.java,
      productAgentOutput.id,
      productAgentOutput.accountId,
      productAgentOutput.type,
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
