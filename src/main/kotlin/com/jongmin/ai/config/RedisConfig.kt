package com.jongmin.ai.config

import com.jongmin.jspring.data.repository.redis.RedisNodeRepository as DataRedisNodeRepository
import com.jongmin.jspring.web.repository.redis.RedisNodeRepository as WebRedisNodeRepository
import com.jongmin.jspring.web.repository.redis.RedisAuthRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.Executors

/**
 * Redis 설정 (AI 서비스)
 *
 * Redis 연결 및 Pub/Sub 리스너 컨테이너 설정을 담당한다.
 * 플랫폼 설정 동기화(PlatformConfigListener)에서 사용된다.
 *
 * @author Jongmin
 * @since 2026. 01. 20
 */
@Configuration
class RedisConfig {
  private val kLogger = KotlinLogging.logger {}

  @Value($$"${spring.application.name:ai-service}")
  private lateinit var env: String

  @Value($$"${spring.data.redis.host}")
  private lateinit var host: String

  @Value($$"${spring.data.redis.port}")
  private val port: Int = 0

  @Value($$"${spring.data.redis.database:0}")
  private val database: Int = 0

  @Value($$"${spring.data.redis.password:}")
  private lateinit var password: String

  @Bean
  fun redisConnectionFactory(): RedisConnectionFactory {
    val config = RedisStandaloneConfiguration(host, port)
    if (password.isNotBlank()) {
      config.password = RedisPassword.of(password)
    }
    config.database = database
    kLogger.info { "Redis 연결 설정: host=$host, port=$port, database=$database" }
    return LettuceConnectionFactory(config)
  }

  @Bean
  @Primary
  fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate {
    return StringRedisTemplate(connectionFactory)
  }

  /**
   * Redis Pub/Sub 메시지 리스너 컨테이너
   *
   * PlatformConfigListener에서 플랫폼 설정 변경 이벤트를 수신하기 위해 사용된다.
   * Virtual Thread를 사용하여 메시지 처리 성능을 최적화한다.
   */
  @Bean
  fun redisMessageListenerContainer(redisConnectionFactory: RedisConnectionFactory): RedisMessageListenerContainer {
    val container = RedisMessageListenerContainer()
    container.setConnectionFactory(redisConnectionFactory)
    container.setTaskExecutor(Executors.newVirtualThreadPerTaskExecutor())
    kLogger.info { "Redis 메시지 리스너 컨테이너 생성 완료 (AI 서비스)" }
    return container
  }

  @Bean
  fun authRepository(
    @Qualifier("jacksonJsonMapper") objectMapper: ObjectMapper,
    @Qualifier("stringRedisTemplate") redisTemplate: StringRedisTemplate,
  ): RedisAuthRepository {
    return RedisAuthRepository(env, objectMapper, redisTemplate)
  }

  @Bean("webRedisNodeRepository")
  fun webRedisNodeRepository(
    @Qualifier("stringRedisTemplate") redisTemplate: StringRedisTemplate,
  ): WebRedisNodeRepository {
    return WebRedisNodeRepository(redisTemplate)
  }

  @Bean("dataRedisNodeRepository")
  fun dataRedisNodeRepository(
    @Qualifier("stringRedisTemplate") redisTemplate: StringRedisTemplate,
  ): DataRedisNodeRepository {
    return DataRedisNodeRepository(redisTemplate)
  }
}
