package com.jongmin.ai.product_agent.platform.dto.request

import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.ProductAgentOutputType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null

/**
 * AgentOutput 생성 요청 DTO
 *
 * 에이전트 출력물을 저장하기 위한 생성 요청 DTO입니다.
 *
 * @property id 고유 식별자 (서버에서 자동 생성)
 * @property accountId 생성한 사용자의 계정 ID (세션에서 자동 설정)
 * @property type 에이전트 출력물 타입 (필수)
 * @property status 상태값 (기본값: ACTIVE)
 * @property title 제목 (타입별: 카피라이팅=메인카피, 이미지=상품명)
 * @property description 설명 (타입별: 카피라이팅=서브카피, 이미지=프롬프트)
 * @property thumbnailUrl 대표 섬네일 이미지 URL (선택)
 */
data class CreateAgentOutputDto(
  @field:Null
  var id: Long? = null,

  @field:Null
  var accountId: Long? = null,

  @field:NotNull(message = "에이전트 출력물 타입은 필수입니다")
  var type: ProductAgentOutputType? = null,

  var status: StatusType? = StatusType.ACTIVE,

  @field:NotBlank(message = "제목은 필수입니다")
  var title: String? = null,

  @field:NotBlank(message = "설명은 필수입니다")
  var description: String? = null,

  var thumbnailUrl: String? = null
)

/**
 * AgentOutput 수정 요청 DTO
 *
 * 에이전트 출력물을 부분 수정하기 위한 요청 DTO입니다.
 * id는 필수이며, 나머지 필드는 선택적으로 수정할 수 있습니다.
 *
 * @property id 수정 대상 ID (필수)
 * @property status 상태값 (선택)
 * @property title 제목 (선택)
 * @property description 설명 (선택)
 * @property thumbnailUrl 대표 섬네일 이미지 URL (선택)
 */
data class PatchAgentOutputDto(
  @field:NotNull(message = "ID는 필수입니다")
  var id: Long? = null,

  var status: StatusType? = null,

  var title: String? = null,

  var description: String? = null,

  var thumbnailUrl: String? = null
)
