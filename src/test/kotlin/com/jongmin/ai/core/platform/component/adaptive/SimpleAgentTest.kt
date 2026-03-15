package com.jongmin.ai.core.platform.component.adaptive

import com.jongmin.ai.auth.AccountService
import com.jongmin.ai.core.platform.component.AIInferenceCancellationManager
import com.jongmin.ai.core.platform.component.LlmRateLimiter
import com.jongmin.ai.core.platform.component.gateway.LlmGateway
import com.jongmin.ai.core.platform.service.AiAssistantService
import com.jongmin.ai.core.platform.service.AiModelService
import com.jongmin.jspring.messaging.event.EventSender
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.support.GenericApplicationContext
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.assertTrue

class SimpleAgentTest {

  @Test
  fun `shutdown should close executor once`() {
    val agent = SimpleAgent(
      objectMapper = ObjectMapper(),
      chatTopic = "chat-topic",
      cancellationManager = mock<AIInferenceCancellationManager>(),
      eventSender = mock<EventSender>(),
      aiAssistantService = mock<AiAssistantService>(),
      accountService = mock<AccountService>(),
      aiModelService = mock<AiModelService>(),
      rateLimiter = mock<LlmRateLimiter>(),
      llmGateway = mock<LlmGateway>(),
    )

    val event = ContextClosedEvent(GenericApplicationContext())
    agent.onApplicationEvent(event)
    agent.onApplicationEvent(event)

    assertTrue(executors(agent).isShutdown)
  }

  private fun executors(target: SimpleAgent): ScheduledExecutorService {
    val field = target.javaClass.getDeclaredField("executors")
    field.isAccessible = true
    return field.get(target) as ScheduledExecutorService
  }
}
