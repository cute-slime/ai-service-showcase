package com.jongmin.ai.product_agent.platform.service

import com.jongmin.jspring.web.entity.JSession
import com.jongmin.jspring.data.entity.StatusType
import com.jongmin.jspring.core.util.JTimeUtils.now
import com.jongmin.jspring.data.util.toOrderSpecifiers
import com.jongmin.ai.core.ProductAgentOutputType
import com.jongmin.ai.product_agent.platform.dto.response.ProductAgentOutputItem
import com.jongmin.ai.product_agent.platform.dto.response.MarketingCampaignOutputData
import com.jongmin.ai.product_agent.platform.dto.response.ProductAgentOutputProjection
import com.jongmin.ai.product_agent.platform.dto.response.ProductAgentOutputProjection.Companion.buildProjection
import com.jongmin.ai.product_agent.platform.dto.response.ProductImageOutputData
import com.jongmin.ai.product_agent.platform.entity.QProductAgentOutput.productAgentOutput
import com.jongmin.ai.storage.S3Service
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.readValue

/**
 * 에이전트 출력물 서비스
 *
 * AgentOutput 엔티티에 대한 CRUD 작업을 제공하는 서비스입니다.
 * 모든 에이전트가 생성한 결과물을 통합 관리합니다.
 * 타입별로 outputDataJson을 파싱하여 상세 정보를 제공합니다.
 *
 * @property queryFactory QueryDSL 쿼리 팩토리
 * @property objectMapper JSON 파싱용 ObjectMapper
 * @property s3Service S3 Presigned URL 생성용 서비스
 */
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Service
class ProductAgentOutputService(
  private val queryFactory: JPAQueryFactory,
  private val objectMapper: ObjectMapper,
  private val s3Service: S3Service
) {
  private val kLogger = KotlinLogging.logger {}

  companion object {
    // Presigned URL 유효 시간 (분)
    private const val PRESIGNED_URL_EXPIRATION_MINUTES = 10
  }

  /**
   * 에이전트 출력물 목록 조회
   *
   * 조건에 맞는 출력물 목록을 페이지네이션하여 조회합니다.
   * 타입에 따라 outputDataJson을 파싱하여 상세 정보를 details 필드에 포함합니다.
   *
   * @param session 사용자 세션 정보
   * @param type 에이전트 출력물 타입 필터 (필수)
   * @param statuses 상태 필터 (선택)
   * @param q 검색어 (제목에서 검색, 선택)
   * @param pageable 페이지 정보
   * @return 출력물 목록 페이지
   */
  fun findAll(
    session: JSession?,
    type: ProductAgentOutputType,
    statuses: Set<StatusType>? = null,
    q: String? = null,
    pageable: Pageable
  ): Page<ProductAgentOutputItem> {
    kLogger.debug { "AgentOutput 목록 조회 - page: $pageable, type: $type, statuses: $statuses, q: $q" }

    // 조건 구성: DELETED 상태 제외, 생성 후 2주 이내 항목만 조회
    val predicate = BooleanBuilder(
      productAgentOutput.status.ne(StatusType.DELETED)
        .and(productAgentOutput.createdAt.after(now().minusWeeks(2)))
    )

    // 본인이 생성한 출력물만 조회
    // session?.let { predicate.and(productAgentOutput.accountId.eq(it.accountId)) }

    // 에이전트 출력물 타입 필터 적용 (필수)
    predicate.and(productAgentOutput.type.eq(type))

    // 상태 필터 적용
    statuses?.let { predicate.and(productAgentOutput.status.`in`(it)) }

    // 검색어 필터 (제목에서 검색)
    q?.let { predicate.and(productAgentOutput.title.contains(it)) }

    // 카운트 쿼리
    val count = queryFactory
      .select(productAgentOutput.count())
      .from(productAgentOutput)
      .where(predicate)
      .fetchOne() ?: 0

    // 데이터 조회
    val projections = queryFactory
      .select(buildProjection())
      .from(productAgentOutput)
      .where(predicate)
      .orderBy(*pageable.sort.toOrderSpecifiers())
      .offset(pageable.offset)
      .limit(pageable.pageSize.toLong())
      .fetch()

    // Projection → Item 변환 (타입별 details 파싱 포함)
    val items = projections.map { projection -> toOutputItem(projection, type) }

    return PageImpl(items, pageable, count)
  }

  /**
   * Projection을 ProductAgentOutputItem으로 변환
   *
   * 타입에 따라 outputDataJson을 파싱하여 details 필드에 포함합니다.
   * S3 key는 Presigned URL로 변환합니다.
   *
   * @param projection 변환할 Projection
   * @param type 에이전트 출력물 타입
   * @return 변환된 ProductAgentOutputItem
   */
  private fun toOutputItem(
    projection: ProductAgentOutputProjection,
    type: ProductAgentOutputType
  ): ProductAgentOutputItem {
    val details = parseDetails(projection.outputDataJson, type)

    // thumbnailUrl이 S3 key인 경우 Presigned URL로 변환
    val thumbnailUrl = projection.thumbnailUrl?.let { key ->
      if (key.startsWith("http")) key else s3Service.generateGetPresignedUrl(
        key = key,
        expirationMinutes = PRESIGNED_URL_EXPIRATION_MINUTES
      )
    }

    return ProductAgentOutputItem(
      id = projection.id!!,
      accountId = projection.accountId,
      type = projection.type!!,
      title = projection.title,
      description = projection.description,
      thumbnailUrl = thumbnailUrl,
      status = projection.status,
      createdAt = projection.createdAt,
      updatedAt = projection.updatedAt,
      details = details
    )
  }

  /**
   * outputDataJson을 타입별로 파싱하여 details Map으로 변환
   *
   * @param outputDataJson JSON 문자열
   * @param type 에이전트 출력물 타입
   * @return 파싱된 details Map, 실패 시 null
   */
  private fun parseDetails(outputDataJson: String?, type: ProductAgentOutputType): Map<String, Any?>? {
    if (outputDataJson.isNullOrBlank()) return null

    return try {
      when (type) {
        ProductAgentOutputType.PRODUCT_IMAGE,
        ProductAgentOutputType.PRODUCT_IMAGE_COMPOSE -> parseImageDetails(outputDataJson)

        ProductAgentOutputType.PRODUCT_COPY -> parseCopyDetails(outputDataJson)
        ProductAgentOutputType.MARKETING_CAMPAIGN -> parseMarketingCampaignDetails(outputDataJson)
        else -> parseGenericDetails(outputDataJson)
      }
    } catch (e: Exception) {
      kLogger.warn(e) { "outputDataJson 파싱 실패 - type: $type, json: ${outputDataJson.take(100)}..." }
      null
    }
  }

  /**
   * PRODUCT_IMAGE 타입의 상세 정보 파싱
   *
   * S3 key를 Presigned URL로 변환하여 반환합니다.
   *
   * **보안 주의**: prompt 필드는 AI 프롬프트 엔지니어링 노하우가 담긴 핵심 자산이므로
   * 외부 응답에서 제외합니다. (영업 비밀 보호)
   */
  private fun parseImageDetails(json: String): Map<String, Any?> {
    val data = objectMapper.readValue<ProductImageOutputData>(json)

    // S3 key → Presigned URL 변환
    val presignedImageUrls = data.generatedImageKeys.map { key ->
      s3Service.generateGetPresignedUrl(key = key, expirationMinutes = PRESIGNED_URL_EXPIRATION_MINUTES)
    }

    // 보안 주의: prompt 필드는 의도적으로 제외 - AI 프롬프트 노출 금지
    return mapOf(
      "productName" to data.productName,
      "imageStyle" to data.imageStyle,
      "aspectRatio" to data.aspectRatio,
      "imageCount" to data.imageCount,
      "generatedImageUrls" to presignedImageUrls
    )
  }

  /**
   * PRODUCT_COPY 타입의 상세 정보 파싱
   */
  private fun parseCopyDetails(json: String): Map<String, Any?> {
    // 카피라이팅의 경우 outputDataJson 그대로 Map으로 반환
    return objectMapper.readValue(json)
  }

  private fun parseMarketingCampaignDetails(json: String): Map<String, Any?> {
    objectMapper.readValue<MarketingCampaignOutputData>(json)
    val rootNode = objectMapper.readTree(json)
    refreshMarketingCampaignImageUrls(rootNode)

    @Suppress("UNCHECKED_CAST")
    return objectMapper.convertValue(rootNode, Map::class.java) as Map<String, Any?>
  }

  private fun refreshMarketingCampaignImageUrls(node: JsonNode) {
    when (node) {
      is ObjectNode -> {
        node.path("generatedImageKey")
          .asString()
          ?.takeIf { it.isNotBlank() }
          ?.let { key ->
            node.put(
              "generatedImageUrl",
              s3Service.generateGetPresignedUrl(
                key = key,
                expirationMinutes = PRESIGNED_URL_EXPIRATION_MINUTES
              )
            )
          }

        node.path("thumbnailKey")
          .asString()
          ?.takeIf { it.isNotBlank() }
          ?.let { key ->
            node.put(
              "thumbnailUrl",
              s3Service.generateGetPresignedUrl(
                key = key,
                expirationMinutes = PRESIGNED_URL_EXPIRATION_MINUTES
              )
            )
          }

        node.forEach { childNode ->
          refreshMarketingCampaignImageUrls(childNode)
        }
      }

      is ArrayNode -> node.forEach { childNode ->
        refreshMarketingCampaignImageUrls(childNode)
      }
    }
  }

  /**
   * 기타 타입의 상세 정보 파싱 (범용)
   */
  private fun parseGenericDetails(json: String): Map<String, Any?> {
    return objectMapper.readValue(json)
  }
}
