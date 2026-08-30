package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningDispatchCoordinatorTest {
    private val screen = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

    @Test
    fun `fresh grounded proposal crosses every dispatch gate as one bounded handoff`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        )

        assertTrue(result.allowed)
        assertEquals(listOf(AgentAction.TapLabel("Continue")), result.actions)
        assertFalse(result.needsConfirmation)
    }

    @Test
    fun `ui drift fails closed before controller safe actions are exposed`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val changedScreen = "pkg=com.example.app | node[text=Done;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = changedScreen,
            nowEpochMs = 1_500L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.error.orEmpty().contains("UI changed", ignoreCase = true))
    }

    @Test
    fun `reasoning goal rejected by normal goal admission exposes no controller actions`() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue\u0001 in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = screen,
            nowEpochMs = 1_000L,
        ).getOrThrow()

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertFalse(result.needsConfirmation)
        assertTrue(result.error.orEmpty().contains("control", ignoreCase = true))
    }
}
