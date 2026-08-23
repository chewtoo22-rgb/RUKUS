package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningDispatchSafetyPreflightTest {
    @Test
    fun `safe grounded reasoning action can receive dispatch grant`() {
        val screen = "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"
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
        assertNotNull(dispatch.grant)
        assertFalse(dispatch.needsConfirmation)
        assertEquals(null, dispatch.confirmationActionFingerprint)
    }

    @Test
    fun `high impact reasoning action cannot bypass executor confirmation path`() {
        val screen = "pkg=com.example.app | node[text=Send;clickable=true;enabled=true;editable=false;sensitive=false;focused=false]"
        val proposalResult = ReasoningPlanGateway.propose(
            goal = "Tap Send",
            observation = screen,
            encodedActions = "TAP_LABEL\tSend",
            nowEpochMs = 1_000L,
        )
        assertTrue(proposalResult.allowed)
        val proposal = proposalResult.proposal!!
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

        assertFalse(dispatch.allowed)
        assertTrue(dispatch.grant == null)
        assertTrue(dispatch.needsConfirmation)
        assertTrue(dispatch.error.orEmpty().contains("executor confirmation", ignoreCase = true))
        assertEquals(
            PlanSafetyPreflight.approvalFingerprint(AgentAction.TapLabel("Send")),
            dispatch.confirmationActionFingerprint,
        )
    }
}
