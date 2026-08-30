package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningControllerDispatchLeaseTest {
    private val screen = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

    private fun dispatchGrant(): ReasoningPlanGateway.DispatchGrant {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val execution = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!
        return ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = execution,
            currentObservation = screen,
            nowEpochMs = 1_750L,
        ).grant!!
    }

    @Test
    fun `fresh intact dispatch grant reaches controller`() {
        val grant = dispatchGrant()

        val result = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = grant,
            currentObservation = screen,
            nowEpochMs = 1_900L,
        )

        assertTrue(result.allowed)
        assertEquals(listOf(AgentAction.TapLabel("Continue")), result.actions)
    }

    @Test
    fun `controller handoff rejects changed UI`() {
        val grant = dispatchGrant()
        val changed = "pkg=com.example.app | node[text=Done;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

        val result = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = grant,
            currentObservation = changed,
            nowEpochMs = 1_900L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("UI changed", ignoreCase = true))
        assertTrue(result.error.orEmpty().contains("replan", ignoreCase = true))
    }

    @Test
    fun `controller handoff rejects expired dispatch grant`() {
        val grant = dispatchGrant()

        val result = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = grant,
            currentObservation = screen,
            nowEpochMs = grant.authorizedAtEpochMs + ReasoningPlanGateway.MAX_DISPATCH_GRANT_AGE_MS + 1L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("expired", ignoreCase = true))
    }

    @Test
    fun `controller handoff rejects action mutation`() {
        val grant = dispatchGrant().copy(actions = listOf(AgentAction.InspectScreen))

        val result = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = grant,
            currentObservation = screen,
            nowEpochMs = 1_900L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("actions changed", ignoreCase = true))
    }

    @Test
    fun `controller handoff rejects grant metadata tampering`() {
        val grant = dispatchGrant().copy(goal = "Different goal")

        val result = ReasoningPlanGateway.authorizeControllerDispatch(
            dispatchGrant = grant,
            currentObservation = screen,
            nowEpochMs = 1_900L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("metadata changed", ignoreCase = true))
    }
}
