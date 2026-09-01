package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DurableCheckpointPublisherTest {
    private val state = AgentTaskState(
        request = "open settings",
        currentStep = 0,
        totalSteps = 1,
        lastAction = null,
        lastScreenSummary = null,
        recoveryAttempts = 0,
        status = AgentTaskState.Status.RUNNING
    )

    @Test
    fun `publishes only after persistence succeeds`() {
        val events = mutableListOf<String>()

        DurableCheckpointPublisher.publish(
            state = state,
            persist = { events += "persist" },
            publishInMemory = { events += "publish" }
        )

        assertEquals(listOf("persist", "publish"), events)
    }

    @Test
    fun `persistence failure is typed and does not publish`() {
        val events = mutableListOf<String>()

        assertThrows(CheckpointPersistenceException::class.java) {
            DurableCheckpointPublisher.publish(
                state = state,
                persist = {
                    events += "persist"
                    throw IllegalStateException("disk rejected checkpoint")
                },
                publishInMemory = { events += "publish" }
            )
        }

        assertEquals(listOf("persist"), events)
    }
}
