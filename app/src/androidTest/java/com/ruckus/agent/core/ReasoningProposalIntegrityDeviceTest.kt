package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator probe for proposal freshness and integrity at the final reasoning dispatch
 * boundary. CI compiles this into the instrumentation APK; execution remains a real
 * device/emulator test and is not implied by compilation success.
 */
@RunWith(AndroidJUnit4::class)
class ReasoningProposalIntegrityDeviceTest {
    private val screen =
        "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]"

    @Test
    fun expiredProposalExposesNoControllerActions() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = screen,
            nowEpochMs = 1_000L + ObservedPlanProposal.MAX_PROPOSAL_AGE_MS + 1L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.error.orEmpty().contains("expired", ignoreCase = true))
    }

    @Test
    fun actionMutationAfterAdmissionExposesNoControllerActions() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 2_000L,
        ).proposal!!
        val tampered = proposal.copy(actions = listOf(AgentAction.InspectScreen))

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = tampered,
            currentObservation = screen,
            nowEpochMs = 2_300L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.error.orEmpty().contains("changed after admission", ignoreCase = true))
    }

    @Test
    fun proposalMetadataMutationExposesNoControllerActions() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = screen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 3_000L,
        ).proposal!!
        val tampered = proposal.copy(goal = "Go home")

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = tampered,
            currentObservation = screen,
            nowEpochMs = 3_300L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.error.orEmpty().contains("metadata changed", ignoreCase = true))
    }
}
