package com.jongmin.ai.product_agent.platform.controller

import com.jongmin.jspring.web.controller.JController
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.product_agent.platform.dto.response.ProductAgentOutputItem
import com.jongmin.ai.product_agent.platform.service.ProductAgentOutputService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 에이전트 출력물 컨트롤러
 *
 * AI 에이전트가 생성한 출력물에 대한 CRUD 엔드포인트를 제공합니다.
 * 상품 카피라이팅, 이미지 생성 등 다양한 타입의 출력물을 통합 관리합니다.
 * 타입별로 상세 정보(details)가 포함되어 반환됩니다.
 *
 * ### 엔드포인트:
 * - GET    /v1.0/product-agent-outputs       : 목록 조회 (type 필수)
 */
@Tag(name = "AI - Product Agent Output", description = "AI 에이전트 출력물 통합 API")
@Validated
@RestController
@RequestMapping("/v1.0")
class ProductAgentOutputController(
  private val productAgentOutputService: ProductAgentOutputService
) : JController() {

  /**
   * 에이전트 출력물 목록 조회
   *
   * 본인이 생성한 출력물 목록을 페이지네이션하여 조회합니다.
   * 타입별로 outputDataJson을 파싱하여 details 필드에 상세 정보가 포함됩니다.
   *
   * @param type 에이전트 출력물 타입 (필수)
   * @param statuses 상태 필터 (선택)
   * @param q 검색어 (제목에서 검색, 선택)
   * @param pageable 페이지 정보
   * @return 출력물 목록 페이지
   */
  @GetMapping("/product-agent-outputs")
  fun findAll(
    @Parameter(name = "type", description = "에이전트 출력물 타입 (필수)", required = true)
    @RequestParam type: ProductAgentOutputType,

    @Parameter(name = "statuses", description = "상태 필터")
    @RequestParam(required = false) statuses: Set<StatusType>? = null,

    @Parameter(name = "q", description = "검색어 (제목에서 검색)")
    @RequestParam(required = false) q: String? = null,

    @PageableDefault(sort = ["productAgentOutput.id"], direction = Sort.Direction.DESC, size = 20)
    pageable: Pageable
  ): Page<ProductAgentOutputItem> = productAgentOutputService.findAll(session, type, statuses, q, jPageable(pageable))
}
