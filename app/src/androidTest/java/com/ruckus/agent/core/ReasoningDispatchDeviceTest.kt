package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator probe for the final reasoning -> controller authorization boundary.
 *
 * CI compiles this into the instrumentation APK. Execution remains a Thursday
 * device/emulator test, so compilation must not be treated as physical validation.
 */
@RunWith(AndroidJUnit4::class)
class ReasoningDispatchDeviceTest {
    private val continueScreen =
        "pkg=com.example.app | node[text=Continue;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]"

    @Test
    fun freshGroundedProposalExposesOnlyTheAdmittedTypedAction() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = continueScreen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 1_000L,
        ).proposal!!

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = continueScreen,
            nowEpochMs = 1_400L,
        )

        assertTrue(result.allowed)
        assertFalse(result.needsConfirmation)
        assertEquals(listOf(AgentAction.TapLabel("Continue")), result.actions)
    }

    @Test
    fun uiDriftExposesNoControllerActionAndRequiresFreshInspection() {
        val proposal = ReasoningPlanGateway.propose(
            goal = "Continue in the current app",
            observation = continueScreen,
            encodedActions = "TAP_LABEL\tContinue",
            nowEpochMs = 2_000L,
        ).proposal!!
        val changedScreen =
            "pkg=com.example.app | node[text=Done;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]"

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = changedScreen,
            nowEpochMs = 2_300L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.error.orEmpty().contains("UI changed", ignoreCase = true))
    }

    @Test
    fun highImpactReasoningActionNeverEscapesExactActionConfirmationBoundary() {
        val deleteScreen =
            "pkg=com.example.app | node[text=Delete photo;clickable=true;enabled=true;editable=false;sensitive=false;focused=false;scrollable=false]"
        val proposal = ReasoningPlanGateway.propose(
            goal = "Delete photo",
            observation = deleteScreen,
            encodedActions = "TAP_LABEL\tDelete photo",
            nowEpochMs = 3_000L,
        ).proposal!!

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = deleteScreen,
            nowEpochMs = 3_300L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertTrue(result.needsConfirmation)
        assertTrue(result.confirmationActionFingerprint.orEmpty().isNotBlank())
        assertTrue(result.error.orEmpty().contains("confirmation", ignoreCase = true))
    }

    @Test
    fun unsupportedControlCharactersCannotCrossFinalReasoningHandoff() {
        val proposal = ObservedPlanProposal.create(
            goal = "Continue\u0001 in the current app",
            actions = listOf(AgentAction.TapLabel("Continue")),
            observation = continueScreen,
            nowEpochMs = 4_000L,
        ).getOrThrow()

        val result = ReasoningDispatchCoordinator.prepare(
            proposal = proposal,
            currentObservation = continueScreen,
            nowEpochMs = 4_300L,
        )

        assertFalse(result.allowed)
        assertTrue(result.actions.isEmpty())
        assertFalse(result.needsConfirmation)
        assertTrue(result.error.orEmpty().contains("control", ignoreCase = true))
    }
}
