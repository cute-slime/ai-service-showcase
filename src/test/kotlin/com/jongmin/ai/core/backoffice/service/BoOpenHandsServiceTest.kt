package com.jongmin.ai.core.backoffice.service

import com.jongmin.ai.core.OpenHandsRunRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class BoOpenHandsServiceTest {

  @Test
  fun `shutdownAgent should interrupt and join running agent thread`() {
    val service = BoOpenHandsService(
      objectMapper = ObjectMapper(),
      redisTemplate = mock<StringRedisTemplate>(),
      openHandsRunRepository = mock<OpenHandsRunRepository>(),
      webClientBuilder = WebClient.builder(),
    )

    val interrupted = CountDownLatch(1)
    val agentThread = Thread.startVirtualThread {
      try {
        Thread.sleep(10_000)
      } catch (_: InterruptedException) {
        interrupted.countDown()
      }
    }

    setPrivateField(service, "agentThread", agentThread)

    service.onApplicationEvent(ContextClosedEvent(GenericApplicationContext()))

    assertTrue(interrupted.await(2, TimeUnit.SECONDS))
    assertTrue(!agentThread.isAlive)
  }

  private fun setPrivateField(target: Any, name: String, value: Any?) {
    val field = target.javaClass.getDeclaredField(name)
    field.isAccessible = true
    field.set(target, value)
  }
}
