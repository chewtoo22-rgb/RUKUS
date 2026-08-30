package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentTaskStateInvariantTest {
    @Test
    fun validLifecycleStatesAreAccepted() {
        val action = AgentAction.InspectScreen
        val states = listOf(
            AgentTaskState("", 0, 0, null, null, 0, AgentTaskState.Status.IDLE),
            AgentTaskState("check screen", 0, 1, null, null, 0, AgentTaskState.Status.RUNNING),
            AgentTaskState("check screen", 0, 1, action, null, 0, AgentTaskState.Status.EXECUTING),
            AgentTaskState("check screen", 0, 1, action, null, 1, AgentTaskState.Status.RECOVERING),
            AgentTaskState("check screen", 0, 1, action, null, 0, AgentTaskState.Status.WAITING_CONFIRMATION),
            AgentTaskState("check screen", 1, 1, action, "screen", 0, AgentTaskState.Status.RUNNING),
            AgentTaskState("check screen", 1, 1, action, "screen", 0, AgentTaskState.Status.COMPLETE),
            AgentTaskState("check screen", 1, 1, action, "screen", 1, AgentTaskState.Status.FAILED)
        )

        states.forEach { state ->
            assertTrue("Expected valid state: $state; error=${state.validationError()}", state.isValid())
        }
    }

    @Test
    fun impossibleProgressAndRecoveryCountersAreRejected() {
        assertFalse(
            AgentTaskState("task", 2, 1, null, null, 0, AgentTaskState.Status.RUNNING).isValid()
        )
        assertFalse(
            AgentTaskState("task", 0, 1, null, null, -1, AgentTaskState.Status.RUNNING).isValid()
        )
    }

    @Test
    fun activeExecutionStatesRequirePendingStepAndAction() {
        val statuses = listOf(
            AgentTaskState.Status.EXECUTING,
            AgentTaskState.Status.RECOVERING,
            AgentTaskState.Status.WAITING_CONFIRMATION
        )

        statuses.forEach { status ->
            assertFalse(AgentTaskState("task", 0, 1, null, null, 0, status).isValid())
            assertFalse(AgentTaskState("task", 1, 1, AgentAction.InspectScreen, null, 0, status).isValid())
        }
    }

    @Test
    fun completeRequiresRealCompletedPlan() {
        assertFalse(
            AgentTaskState("task", 0, 0, null, null, 0, AgentTaskState.Status.COMPLETE).isValid()
        )
        assertFalse(
            AgentTaskState("task", 0, 1, AgentAction.InspectScreen, null, 0, AgentTaskState.Status.COMPLETE).isValid()
        )
        assertFalse(
            AgentTaskState("task", 1, 1, null, null, 0, AgentTaskState.Status.COMPLETE).isValid()
        )
    }

    @Test
    fun storeFailsClosedAndPreservesLastValidState() {
        AgentTaskStateStore.clear()
        val valid = AgentTaskState("task", 0, 1, null, null, 0, AgentTaskState.Status.RUNNING)
        AgentTaskStateStore.set(valid)

        try {
            AgentTaskStateStore.set(
                AgentTaskState("task", 2, 1, null, null, 0, AgentTaskState.Status.RUNNING)
            )
            fail("Expected invalid state to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertTrue(AgentTaskStateStore.get() == valid)
        AgentTaskStateStore.clear()
        assertTrue(AgentTaskStateStore.get().status == AgentTaskState.Status.IDLE)
    }
}
