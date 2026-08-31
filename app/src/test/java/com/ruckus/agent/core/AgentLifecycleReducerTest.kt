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
    fun `terminal states accept only acknowledgement back to idle`() {
        for (state in listOf(
            AgentLifecycleState.SUCCEEDED,
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
        assertFalse(AgentLifecycleState.BLOCKED.isActive)
        assertFalse(AgentLifecycleState.FAILED.isActive)
        assertTrue(AgentLifecycleState.UNDERSTANDING.isActive)
        assertTrue(AgentLifecycleState.PLANNING.isActive)
        assertTrue(AgentLifecycleState.EXECUTING.isActive)
        assertTrue(AgentLifecycleState.VERIFYING.isActive)
        assertTrue(AgentLifecycleState.RECOVERING.isActive)
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
