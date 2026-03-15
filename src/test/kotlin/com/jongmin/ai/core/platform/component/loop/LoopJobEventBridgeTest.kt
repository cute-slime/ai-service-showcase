package com.jongmin.ai.core.platform.component.loop

import com.jongmin.jspring.dte.component.DistributedJobEventBridge
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import tools.jackson.databind.ObjectMapper

class LoopJobEventBridgeTest {

  @Test
  fun `emitComplete should publish data and complete events`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = ObjectMapper()
    val sut = LoopJobEventBridge(eventBridge, objectMapper)

    sut.emitComplete("job-2", "all done")

    verify(eventBridge, times(1)).emitData(eq("job-2"), eq("LOOP_JOB"), any(), isNull())
    verify(eventBridge, times(1)).emitComplete(eq("job-2"), eq("LOOP_JOB"), isNull())
  }

  @Test
  fun `emitError should publish data and error events`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = ObjectMapper()
    val sut = LoopJobEventBridge(eventBridge, objectMapper)

    sut.emitError("job-3", "oops")

    verify(eventBridge, times(1)).emitData(eq("job-3"), eq("LOOP_JOB"), any(), isNull())
    verify(eventBridge, times(1)).emitError(eq("job-3"), eq("LOOP_JOB"), eq("oops"), isNull())
  }

  @Test
  fun `emitStateChanged should publish data event`() {
    val eventBridge = mock<DistributedJobEventBridge>()
    val objectMapper = ObjectMapper()
    val sut = LoopJobEventBridge(eventBridge, objectMapper)

    sut.emitStateChanged("job-4")

    verify(eventBridge, times(1)).emitData(eq("job-4"), eq("LOOP_JOB"), any(), isNull())
  }
}
