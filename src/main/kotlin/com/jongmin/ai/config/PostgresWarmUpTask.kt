package com.jongmin.ai.config

import com.jongmin.ai.core.AiAgentRepository
import com.jongmin.ai.core.AiModelRepository
import com.jongmin.ai.core.AiProviderRepository
import com.jongmin.jspring.web.health.WarmUpTask
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

private val kLogger = KotlinLogging.logger {}

/**
 * PostgreSQL 연결 웜업 작업.
 *
 * ai-service에서 사용하는 PostgreSQL 연결을 초기화하고
 * JPA/Hibernate 메타데이터 캐시를 웜업한다.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.health.warmup.tasks.postgresql",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class PostgresWarmUpTask(
    private val aiProviderRepository: AiProviderRepository,
    private val aiModelRepository: AiModelRepository,
    private val aiAgentRepository: AiAgentRepository
) : WarmUpTask {

    override fun name(): String = "PostgreSQL"

    override fun order(): Int = 20  // ObjectMapper 다음에 실행

    override fun isRequired(): Boolean = true  // DB 연결은 필수

    override fun execute(): Boolean {
        return try {
            // AiProvider 테이블 조회 (JPA 메타데이터 초기화)
            val providers = aiProviderRepository.findAll(PageRequest.of(0, 1))
            kLogger.debug { "AiProvider 테이블 웜업 완료 (${providers.totalElements}개 레코드)" }

            // AiModel 테이블 조회
            val models = aiModelRepository.findAll(PageRequest.of(0, 1))
            kLogger.debug { "AiModel 테이블 웜업 완료 (${models.totalElements}개 레코드)" }

            // AiAgent 테이블 조회
            val agents = aiAgentRepository.findAll(PageRequest.of(0, 1))
            kLogger.debug { "AiAgent 테이블 웜업 완료 (${agents.totalElements}개 레코드)" }

            true
        } catch (e: Exception) {
            kLogger.error(e) { "PostgreSQL 웜업 실패" }
            false
        }
    }
}
