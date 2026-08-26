package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeConfirmationCheckpointTest {
    @Test
    fun waitingConfirmationRequiresCheckpointActionToMatchExactPlanStep() {
        val privileged = AgentAction.RunApprovedShell("demo")
        val plan = CommandPlanner.Plan(
            actions = listOf(AgentAction.Home, privileged),
            rejectedParts = emptyList()
        )
        val session = PersistedTaskSession(
            request = "home then approved shell",
            currentStep = 1,
            totalSteps = plan.actions.size,
            lastAction = AgentAction.Home.toString(),
            lastScreenSummary = "pkg=com.android.launcher",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.WAITING_CONFIRMATION,
            savedAtMs = 1L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("confirmation checkpoint action differs", ignoreCase = true))
    }

    @Test
    fun waitingConfirmationWithExactActionRemainsGatedAtSameStep() {
        val privileged = AgentAction.RunApprovedShell("demo")
        val plan = CommandPlanner.Plan(
            actions = listOf(AgentAction.Home, privileged),
            rejectedParts = emptyList()
        )
        val session = PersistedTaskSession(
            request = "home then approved shell",
            currentStep = 1,
            totalSteps = plan.actions.size,
            lastAction = privileged.toString(),
            lastScreenSummary = "pkg=com.android.launcher",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.WAITING_CONFIRMATION,
            savedAtMs = 1L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan)

        assertTrue(decision.allowed)
        assertEquals(1, decision.startStep)
        assertTrue(decision.reason.contains("confirmation-gated", ignoreCase = true))
    }
}
