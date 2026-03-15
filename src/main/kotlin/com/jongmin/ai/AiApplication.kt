package com.jongmin.ai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.*

/**
 * AI Service Application
 *
 * AI/LLM 통합, 워크플로우 엔진, 멀티에이전트 시스템
 *
 * @author Jongmin
 * @since 2026-01-18
 */
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@SpringBootApplication
@ComponentScan(
  basePackages = [
    "com.jongmin.ai",
    "com.jongmin.jspring"
  ]
)
@EnableJpaRepositories(
  basePackages = ["com.jongmin.ai"],
  excludeFilters = [
    ComponentScan.Filter(
      type = FilterType.REGEX,
      pattern = ["com\\.jongmin\\.legacy\\..*"]
    )
  ]
)
class AiApplication

fun main(args: Array<String>) {
  // UTC 타임존 설정
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  runApplication<AiApplication>(*args)
}
