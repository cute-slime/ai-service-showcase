package com.jongmin.ai.multiagent.service

import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.ObjectMapper

class MultiAgentProgressManagerTest {

  @Test
  fun `initializeProgress should cache state and emit data event`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = mock<ObjectMapper>()
    whenever(objectMapper.writeValueAsString(any())).thenReturn("""{"ok":true}""")

    val manager = MultiAgentProgressManager(eventBridge, objectMapper)
    manager.initializeProgress(jobId = "job-1", workflowId = 100L, totalAgents = 3)

    val progress = manager.getProgress("job-1")
    assertNotNull(progress)
    assertEquals("job-1", progress?.jobId)
    assertEquals(ProgressStatus.INITIALIZED, progress?.status)
    assertEquals(3, progress?.totalAgents)

    verify(eventBridge, times(1)).emitData(eq("job-1"), eq("MULTI_AGENT_WORKFLOW"), any(), isNull())
  }

  @Test
  fun `onWorkflowCompleted should emit complete and close local stream`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = mock<ObjectMapper>()
    whenever(objectMapper.writeValueAsString(any())).thenReturn("""{"ok":true}""")

    val manager = MultiAgentProgressManager(eventBridge, objectMapper)
    manager.initializeProgress(jobId = "job-2", workflowId = 200L, totalAgents = 2)
    manager.onWorkflowCompleted(jobId = "job-2", output = mapOf("result" to "done"))

    val progress = manager.getProgress("job-2")
    assertNotNull(progress)
    assertEquals(ProgressStatus.COMPLETED, progress?.status)

    verify(eventBridge, times(2)).emitData(eq("job-2"), eq("MULTI_AGENT_WORKFLOW"), any(), isNull())
    verify(eventBridge, times(1)).emitComplete(eq("job-2"), eq("MULTI_AGENT_WORKFLOW"), isNull())

    assertThrows(IllegalArgumentException::class.java) {
      manager.subscribe("job-2").blockFirst()
    }
  }

  @Test
  fun `onWorkflowFailed should emit error and close local stream`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = mock<ObjectMapper>()
    whenever(objectMapper.writeValueAsString(any())).thenReturn("""{"ok":true}""")

    val manager = MultiAgentProgressManager(eventBridge, objectMapper)
    manager.initializeProgress(jobId = "job-3", workflowId = 300L, totalAgents = 1)
    manager.onWorkflowFailed(jobId = "job-3", error = "boom")

    val progress = manager.getProgress("job-3")
    assertNotNull(progress)
    assertEquals(ProgressStatus.FAILED, progress?.status)

    verify(eventBridge, times(2)).emitData(eq("job-3"), eq("MULTI_AGENT_WORKFLOW"), any(), isNull())
    verify(eventBridge, times(1)).emitError(eq("job-3"), eq("MULTI_AGENT_WORKFLOW"), eq("boom"), isNull())

    assertThrows(IllegalArgumentException::class.java) {
      manager.subscribe("job-3").blockFirst()
    }
  }
}
