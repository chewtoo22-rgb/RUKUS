package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLifecycleReducerTest {
    @Test
    fun `happy path preserves understand plan execute verify ordering`() {
        var state = AgentLifecycleState.IDLE
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.REQUEST_ACCEPTED)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.UNDERSTANDING_COMPLETE)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.PLAN_READY)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.ACTIONS_COMPLETE)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.VERIFICATION_PASSED)
        assertEquals(AgentLifecycleState.SUCCEEDED, state)
        assertTrue(state.isTerminal)
        assertFalse(state.isActive)
    }

    @Test
    fun `confirmation cannot be skipped once required`() {
        var state = AgentLifecycleState.IDLE
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.REQUEST_ACCEPTED)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.UNDERSTANDING_COMPLETE)
        state = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.CONFIRMATION_REQUIRED)
        assertEquals(AgentLifecycleState.AWAITING_CONFIRMATION, state)
        assertIllegal(state, AgentLifecycleEvent.ACTIONS_COMPLETE)
        assertEquals(
            AgentLifecycleState.EXECUTING,
            AgentLifecycleReducer.transition(state, AgentLifecycleEvent.CONFIRMED),
        )
    }

    @Test
    fun `preflight block is terminal from plan confirmation or execution`() {
        for (state in listOf(
            AgentLifecycleState.PLANNING,
            AgentLifecycleState.AWAITING_CONFIRMATION,
            AgentLifecycleState.EXECUTING,
        )) {
            assertEquals(
                AgentLifecycleState.BLOCKED,
                AgentLifecycleReducer.transition(state, AgentLifecycleEvent.PREFLIGHT_BLOCKED),
            )
        }
    }

    @Test
    fun `verification failure requires explicit recovery outcome`() {
        val recovering = AgentLifecycleReducer.transition(
            AgentLifecycleState.VERIFYING,
            AgentLifecycleEvent.VERIFICATION_FAILED,
        )
        assertEquals(AgentLifecycleState.RECOVERING, recovering)
        assertEquals(
            AgentLifecycleState.EXECUTING,
            AgentLifecycleReducer.transition(recovering, AgentLifecycleEvent.RECOVERY_READY),
        )
        assertEquals(
            AgentLifecycleState.FAILED,
            AgentLifecycleReducer.transition(recovering, AgentLifecycleEvent.RECOVERY_EXHAUSTED),
        )
    }

    @Test
    fun `cancellation before execution terminates immediately`() {
        for (state in listOf(
            AgentLifecycleState.UNDERSTANDING,
            AgentLifecycleState.PLANNING,
            AgentLifecycleState.AWAITING_CONFIRMATION,
        )) {
            assertEquals(
                AgentLifecycleState.CANCELLED,
                AgentLifecycleReducer.transition(state, AgentLifecycleEvent.CANCEL_REQUESTED),
            )
        }
    }

    @Test
    fun `cancellation after side effects begin requires explicit cleanup`() {
        for (state in listOf(
            AgentLifecycleState.EXECUTING,
            AgentLifecycleState.VERIFYING,
            AgentLifecycleState.RECOVERING,
        )) {
            val cancelling = AgentLifecycleReducer.transition(state, AgentLifecycleEvent.CANCEL_REQUESTED)
            assertEquals(AgentLifecycleState.CANCELLING, cancelling)
            assertFalse(cancelling.isTerminal)
            assertTrue(cancelling.isActive)
            assertEquals(
                AgentLifecycleState.CANCELLED,
                AgentLifecycleReducer.transition(cancelling, AgentLifecycleEvent.CANCELLATION_COMPLETE),
            )
            assertEquals(
                AgentLifecycleState.FAILED,
                AgentLifecycleReducer.transition(cancelling, AgentLifecycleEvent.CANCELLATION_FAILED),
            )
        }
    }

    @Test
    fun `cancelling cannot be acknowledged or restarted before cleanup resolves`() {
        assertIllegal(AgentLifecycleState.CANCELLING, AgentLifecycleEvent.TERMINAL_ACKNOWLEDGED)
        assertIllegal(AgentLifecycleState.CANCELLING, AgentLifecycleEvent.REQUEST_ACCEPTED)
        assertIllegal(AgentLifecycleState.CANCELLING, AgentLifecycleEvent.CANCEL_REQUESTED)
    }

    @Test
    fun `cancelled is terminal and requires acknowledgement before reuse`() {
        assertTrue(AgentLifecycleState.CANCELLED.isTerminal)
        assertFalse(AgentLifecycleState.CANCELLED.isActive)
        assertIllegal(AgentLifecycleState.CANCELLED, AgentLifecycleEvent.REQUEST_ACCEPTED)
        assertEquals(
            AgentLifecycleState.IDLE,
            AgentLifecycleReducer.transition(
                AgentLifecycleState.CANCELLED,
                AgentLifecycleEvent.TERMINAL_ACKNOWLEDGED,
            ),
        )
    }

    @Test
    fun `idle rejects cancellation`() {
        assertIllegal(AgentLifecycleState.IDLE, AgentLifecycleEvent.CANCEL_REQUESTED)
        assertIllegal(AgentLifecycleState.IDLE, AgentLifecycleEvent.CANCELLATION_COMPLETE)
        assertIllegal(AgentLifecycleState.IDLE, AgentLifecycleEvent.CANCELLATION_FAILED)
    }

    @Test
    fun `terminal states accept only acknowledgement back to idle`() {
        for (state in listOf(
            AgentLifecycleState.SUCCEEDED,
            AgentLifecycleState.CANCELLED,
            AgentLifecycleState.BLOCKED,
            AgentLifecycleState.FAILED,
        )) {
            assertEquals(
                AgentLifecycleState.IDLE,
                AgentLifecycleReducer.transition(state, AgentLifecycleEvent.TERMINAL_ACKNOWLEDGED),
            )
            assertIllegal(state, AgentLifecycleEvent.REQUEST_ACCEPTED)
        }
    }

    @Test
    fun `idle refuses executor and terminal events`() {
        for (event in AgentLifecycleEvent.entries.filter { it != AgentLifecycleEvent.REQUEST_ACCEPTED }) {
            assertIllegal(AgentLifecycleState.IDLE, event)
        }
    }

    @Test
    fun `active classification excludes idle and terminal states`() {
        assertFalse(AgentLifecycleState.IDLE.isActive)
        assertFalse(AgentLifecycleState.SUCCEEDED.isActive)
        assertFalse(AgentLifecycleState.CANCELLED.isActive)
        assertFalse(AgentLifecycleState.BLOCKED.isActive)
        assertFalse(AgentLifecycleState.FAILED.isActive)
        assertTrue(AgentLifecycleState.UNDERSTANDING.isActive)
        assertTrue(AgentLifecycleState.PLANNING.isActive)
        assertTrue(AgentLifecycleState.EXECUTING.isActive)
        assertTrue(AgentLifecycleState.VERIFYING.isActive)
        assertTrue(AgentLifecycleState.RECOVERING.isActive)
        assertTrue(AgentLifecycleState.CANCELLING.isActive)
    }

    @Test
    fun `illegal transition reports original state and event`() {
        try {
            AgentLifecycleReducer.transition(
                AgentLifecycleState.VERIFYING,
                AgentLifecycleEvent.PLAN_READY,
            )
            throw AssertionError("expected IllegalAgentLifecycleTransition")
        } catch (error: IllegalAgentLifecycleTransition) {
            assertEquals(AgentLifecycleState.VERIFYING, error.state)
            assertEquals(AgentLifecycleEvent.PLAN_READY, error.event)
        }
    }

    private fun assertIllegal(state: AgentLifecycleState, event: AgentLifecycleEvent) {
        try {
            AgentLifecycleReducer.transition(state, event)
            throw AssertionError("expected illegal transition for $state + $event")
        } catch (_: IllegalAgentLifecycleTransition) {
            // Expected fail-closed behavior.
        }
    }
}
