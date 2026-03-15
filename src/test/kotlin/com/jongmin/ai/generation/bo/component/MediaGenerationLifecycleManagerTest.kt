package com.jongmin.ai.generation.bo.component

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.Lifecycle
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.StringRedisTemplate

class MediaGenerationLifecycleManagerTest {

  private interface LifecycleRedisConnectionFactory : RedisConnectionFactory, Lifecycle

  @Test
  fun `onApplicationEvent should inspect active jobs before redis shutdown`() {
    val redisTemplate = mock<StringRedisTemplate>()
    val connectionFactory = mock<LifecycleRedisConnectionFactory>()
    val setOperations = mock<SetOperations<String, String>>()
    whenever(redisTemplate.connectionFactory).thenReturn(connectionFactory)
    whenever(connectionFactory.isRunning).thenReturn(true)
    whenever(redisTemplate.keys("media_gen:active_jobs:*")).thenReturn(setOf("media_gen:active_jobs:1"))
    whenever(redisTemplate.opsForSet()).thenReturn(setOperations)
    whenever(setOperations.size("media_gen:active_jobs:1")).thenReturn(1L)
    whenever(setOperations.members("media_gen:active_jobs:1")).thenReturn(setOf("job-1"))

    val lifecycleManager = MediaGenerationLifecycleManager(redisTemplate)

    lifecycleManager.onApplicationEvent(ContextClosedEvent(GenericApplicationContext()))

    verify(redisTemplate).keys("media_gen:active_jobs:*")
    verify(setOperations).size("media_gen:active_jobs:1")
    verify(setOperations).members("media_gen:active_jobs:1")
  }

  @Test
  fun `onApplicationEvent should skip inspection when redis is already stopped`() {
    val redisTemplate = mock<StringRedisTemplate>()
    val connectionFactory = mock<LifecycleRedisConnectionFactory>()
    whenever(redisTemplate.connectionFactory).thenReturn(connectionFactory)
    whenever(connectionFactory.isRunning).thenReturn(false)

    val lifecycleManager = MediaGenerationLifecycleManager(redisTemplate)

    lifecycleManager.onApplicationEvent(ContextClosedEvent(GenericApplicationContext()))

    verify(redisTemplate, never()).keys(any())
  }

  @Test
  fun `onApplicationEvent should run only once`() {
    val redisTemplate = mock<StringRedisTemplate>()
    val connectionFactory = mock<LifecycleRedisConnectionFactory>()
    whenever(redisTemplate.connectionFactory).thenReturn(connectionFactory)
    whenever(connectionFactory.isRunning).thenReturn(true)
    whenever(redisTemplate.keys("media_gen:active_jobs:*")).thenReturn(emptySet())

    val lifecycleManager = MediaGenerationLifecycleManager(redisTemplate)
    val event = ContextClosedEvent(GenericApplicationContext())

    lifecycleManager.onApplicationEvent(event)
    lifecycleManager.onApplicationEvent(event)

    verify(redisTemplate, times(1)).keys("media_gen:active_jobs:*")
  }
}
