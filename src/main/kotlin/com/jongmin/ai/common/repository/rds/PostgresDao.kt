package com.jongmin.ai.common.repository.rds

import org.springframework.context.MessageSource
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*

/**
 * PostgreSQL용 DAO 추상 클래스
 *
 * JdbcTemplate 기반 쿼리 유틸리티 제공:
 * - 쿼리 빌딩 헬퍼 (WHERE, ORDER BY, LIMIT/OFFSET)
 * - 파라미터 바인딩 유틸리티
 *
 * TODO: 추후 jspring 모듈로 이관 예정
 *
 * @author Jongmin
 */
abstract class PostgresDao {
  protected abstract val jdbcTemplate: JdbcTemplate

  protected abstract val messageSource: MessageSource

  protected fun getQuery(code: String, vararg args: Any): String {
    return messageSource.getMessage(code, args, Locale.KOREAN)
  }

  protected fun getWhere(builder: StringBuilder): String {
    return if (builder.isNotBlank()) {
      "WHERE $builder"
    } else ""
  }

  protected fun addParam(builder: StringBuilder, params: MutableList<Any>, query: String, param: Any): StringBuilder {
    params.add(param)
    return if (params.size == 0) {
      builder.append(query)
    } else {
      builder.append(" AND ").append(query)
    }
  }

  protected fun addParam(params: MutableList<Any>, query: String, param: Any): StringBuilder {
    val builder = StringBuilder("")
    return if (params.size == 0) {
      params.add(param)
      builder.append(query)
    } else {
      params.add(param)
      builder.append(" AND ").append(query)
    }
  }

  protected fun getOrderBy(sort: Sort): String {
    if (sort.isUnsorted) return ""

    var builder = StringBuilder()
    sort.forEach { builder = builder.append(", ").append(it.property).append(" ").append(it.direction) }
    return "ORDER BY ${builder.substring(1)}"
  }

  /**
   * PostgreSQL LIMIT/OFFSET 쿼리 생성
   * PostgreSQL 문법: LIMIT count OFFSET offset
   */
  protected fun getLimit(params: MutableList<Any>, pageable: Pageable): String {
    params.add(pageable.pageSize)
    params.add(pageable.pageNumber * pageable.pageSize)
    return "LIMIT ? OFFSET ?"
  }

  protected fun getLimit(params: MutableList<Any>, size: Int): String {
    params.add(size)
    return "LIMIT ?"
  }
}
