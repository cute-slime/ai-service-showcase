package com.jongmin.ai.product_agent.platform.entity

import com.jongmin.jspring.core.enums.ObjectType
import com.jongmin.jspring.core.enums.ObjectTypeProvider
import com.jongmin.jspring.data.entity.BaseTimeAndStatusEntity
import com.jongmin.ai.common.entity.JObject
import com.jongmin.ai.core.ProductAgentOutputType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 에이전트 출력물 엔티티
 *
 * AI 에이전트가 생성한 다양한 결과물을 저장하는 범용 엔티티입니다.
 * 상품 카피라이팅, 이미지 분석, 콘텐츠 요약 등 모든 에이전트의 출력물을 통합 관리합니다.
 *
 * @property id 고유 식별자 (Redis에서 생성)
 * @property accountId 결과물을 생성한 사용자의 계정 ID
 * @property type 에이전트 출력물 타입 (PRODUCT_COPY, IMAGE_ANALYSIS 등)
 * @property title 제목 (타입별로 다른 의미: 카피라이팅=메인카피, 이미지=상품명)
 * @property description 설명 (타입별로 다른 의미: 카피라이팅=서브카피, 이미지=프롬프트)
 * @property thumbnailUrl 대표 섬네일 이미지 URL (선택)
 * @property outputDataJson 출력물 생성에 사용된 요청 데이터 (JSON)
 */
@Entity
@Table(
  indexes = [
    Index(name = "idx_agent_output_status", columnList = "status"),
    Index(name = "idx_agent_output_account_id", columnList = "accountId"),
    Index(name = "idx_agent_output_type", columnList = "type"),
  ]
)
data class ProductAgentOutput(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0L,

  @Column(comment = "결과물을 생성한 사용자의 계정 ID, 세션없이 생성가능")
  val accountId: Long?,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50, comment = "에이전트 출력물 타입")
  val type: ProductAgentOutputType,

  @Column(nullable = false, columnDefinition = "TEXT", comment = "제목 (타입별: 카피라이팅=메인카피, 이미지=상품명)")
  var title: String,

  @Column(columnDefinition = "TEXT", comment = "설명 (타입별: 카피라이팅=서브카피, 이미지=프롬프트)")
  var description: String?,

  @Column(length = 2048, comment = "대표 섬네일 이미지 URL")
  var thumbnailUrl: String? = null,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "JSON", comment = "출력물 생성에 사용된 요청 데이터 (JSON)")
  var outputDataJson: String
) : BaseTimeAndStatusEntity(), JObject {
  companion object : ObjectTypeProvider {
    override val getObjectType: ObjectType = ObjectType.PRODUCT_AGENT_OUTPUT
  }

  override fun getObjectType(): ObjectType = getObjectType
}

