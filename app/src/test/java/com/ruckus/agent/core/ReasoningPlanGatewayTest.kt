package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningPlanGatewayTest {
    private val screen = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

    @Test
    fun `decodes model output directly into observation-bound proposal`() {
        val result = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        )

        assertTrue(result.allowed)
        val proposal = assertNotNull(result.proposal).let { result.proposal!! }
        assertEquals(listOf(AgentAction.TapLabel("Continue")), proposal.actions)
        assertTrue(ObservedPlanFreshnessGate.evaluate(proposal, screen, 1_500L).allowed)
    }

    @Test
    fun `malformed model output fails before proposal admission`() {
        val result = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "RUN_SHELL\tid",
            nowEpochMs = 1_000L,
        )

        assertFalse(result.allowed)
        assertTrue(result.proposal == null)
        assertTrue(result.error.orEmpty().contains("decode rejected", ignoreCase = true))
    }

    @Test
    fun `decoded action still requires grounding in exact observation`() {
        val result = ReasoningPlanGateway.propose(
            goal = "Tap Search",
            observation = screen,
            encodedActions = "TAP_LABEL\tSearch",
            nowEpochMs = 1_000L,
        )

        assertFalse(result.allowed)
        assertTrue(result.proposal == null)
        assertTrue(result.error.orEmpty().contains("proposal rejected", ignoreCase = true))
    }

    @Test
    fun `decoded setting still requires exact goal intent`() {
        val result = ReasoningPlanGateway.propose(
            goal = "Make the screen brighter",
            observation = screen,
            encodedActions = "SET_BRIGHTNESS\t80",
            nowEpochMs = 1_000L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("exact target value", ignoreCase = true))
    }

    @Test
    fun `one-mutation horizon cannot be bypassed through gateway`() {
        val result = ReasoningPlanGateway.propose(
            goal = "Continue and then go home",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue\nHOME",
            nowEpochMs = 1_000L,
        )

        assertFalse(result.allowed)
        assertTrue(result.error.orEmpty().contains("state-changing", ignoreCase = true))
        assertTrue(result.error.orEmpty().contains("replan", ignoreCase = true))
    }
}
