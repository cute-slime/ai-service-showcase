package com.jongmin.ai.core.backoffice.service

import com.jongmin.jspring.data.util.fetchPage
import com.jongmin.ai.core.backoffice.dto.response.BoOpenHandsRunItem
import com.jongmin.ai.core.platform.entity.QOpenHandsRun.openHandsRun
import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class BoOpenHandsRunService(
  private val queryFactory: JPAQueryFactory
) {
  private val kLogger = KotlinLogging.logger {}

  fun findAll(q: String?, pageable: Pageable): Page<BoOpenHandsRunItem> {
    kLogger.debug { "(BO) OpenHandsRun 목록 조회, q: $q, page: $pageable" }

    val predicate = BooleanBuilder()
    q?.let { predicate.and(openHandsRun.title.contains(q)) }

    val countQuery = queryFactory
      .select(openHandsRun.count())
      .from(openHandsRun)
      .where(predicate)

    return queryFactory
      .select(BoOpenHandsRunItem.buildProjection())
      .from(openHandsRun)
      .where(predicate)
      .fetchPage(countQuery, pageable)
  }
}
