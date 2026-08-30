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

    @Test
    fun `fresh intact proposal receives bounded execution grant`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!

        val result = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        )

        assertTrue(result.allowed)
        val grant = result.grant!!
        assertEquals(proposal.goal, grant.goal)
        assertEquals(proposal.actions, grant.actions)
        assertEquals(proposal.proposalFingerprint, grant.proposalFingerprint)
        assertEquals(proposal.observationFingerprint, grant.observationFingerprint)
        assertEquals(proposal.planFingerprint, grant.planFingerprint)
        assertEquals(1_500L, grant.grantedAtEpochMs)
        assertTrue(grant.grantFingerprint.isNotBlank())
    }

    @Test
    fun `execution authorization rejects changed UI and requires replan`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val changedScreen = "pkg=com.example.app | node[text=Done;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

        val result = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = changedScreen,
            nowEpochMs = 1_500L,
        )

        assertFalse(result.allowed)
        assertTrue(result.grant == null)
        assertTrue(result.error.orEmpty().contains("UI changed", ignoreCase = true))
        assertTrue(result.error.orEmpty().contains("replan", ignoreCase = true))
    }

    @Test
    fun `execution authorization rejects expired proposal lease`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!

        val result = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_000L + ObservedPlanProposal.MAX_PROPOSAL_AGE_MS + 1L,
        )

        assertFalse(result.allowed)
        assertTrue(result.grant == null)
        assertTrue(result.error.orEmpty().contains("expired", ignoreCase = true))
    }

    @Test
    fun `execution authorization rejects action mutation after admission`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val mutated = proposal.copy(actions = listOf(AgentAction.InspectScreen))

        val result = ReasoningPlanGateway.authorizeForExecution(
            proposal = mutated,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        )

        assertFalse(result.allowed)
        assertTrue(result.grant == null)
        assertTrue(result.error.orEmpty().contains("changed after admission", ignoreCase = true))
    }

    @Test
    fun `fresh execution grant receives just in time dispatch grant`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val executionGrant = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = executionGrant,
            currentObservation = screen,
            nowEpochMs = 1_750L,
        )

        assertTrue(dispatch.allowed)
        assertEquals(proposal.actions, dispatch.grant!!.actions)
        assertEquals(proposal.planFingerprint, dispatch.grant!!.planFingerprint)
        assertEquals(1_750L, dispatch.grant!!.authorizedAtEpochMs)
    }

    @Test
    fun `dispatch rejects changed UI after execution authorization`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val executionGrant = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!
        val changedScreen = "pkg=com.example.app | node[text=Done;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = executionGrant,
            currentObservation = changedScreen,
            nowEpochMs = 1_750L,
        )

        assertFalse(dispatch.allowed)
        assertTrue(dispatch.error.orEmpty().contains("UI changed", ignoreCase = true))
        assertTrue(dispatch.error.orEmpty().contains("replan", ignoreCase = true))
    }

    @Test
    fun `dispatch rejects expired execution grant`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val executionGrant = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = executionGrant,
            currentObservation = screen,
            nowEpochMs = 1_500L + ReasoningPlanGateway.MAX_EXECUTION_GRANT_AGE_MS + 1L,
        )

        assertFalse(dispatch.allowed)
        assertTrue(dispatch.error.orEmpty().contains("expired", ignoreCase = true))
    }

    @Test
    fun `dispatch rejects execution grant metadata tampering`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val executionGrant = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!
        val tampered = executionGrant.copy(goal = "Different goal")

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = tampered,
            currentObservation = screen,
            nowEpochMs = 1_750L,
        )

        assertFalse(dispatch.allowed)
        assertTrue(dispatch.error.orEmpty().contains("metadata changed", ignoreCase = true))
    }

    @Test
    fun `dispatch rejects action mutation after execution authorization`() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!
        val executionGrant = ReasoningPlanGateway.authorizeForExecution(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_500L,
        ).grant!!
        val tampered = executionGrant.copy(actions = listOf(AgentAction.InspectScreen))

        val dispatch = ReasoningPlanGateway.authorizeForDispatch(
            executionGrant = tampered,
            currentObservation = screen,
            nowEpochMs = 1_750L,
        )

        assertFalse(dispatch.allowed)
        assertTrue(dispatch.error.orEmpty().contains("actions changed", ignoreCase = true))
    }
}
