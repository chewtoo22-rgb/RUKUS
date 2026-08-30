package com.ruckus.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRecoveryCheckpointTest {
    @Test
    fun recoveringAlternateActionCannotBeForgottenAcrossRestart() {
        val original = AgentAction.TapLabel("Continue")
        val alternate = AgentAction.Tap(400f, 900f)
        val plan = CommandPlanner.Plan(
            actions = listOf(original),
            rejectedParts = emptyList()
        )
        val session = PersistedTaskSession(
            request = "tap continue",
            currentStep = 0,
            totalSteps = plan.actions.size,
            lastAction = alternate.toString(),
            lastScreenSummary = "pkg=com.example.app",
            recoveryAttempts = 1,
            status = AgentTaskState.Status.RECOVERING,
            savedAtMs = 1L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = session.savedAtMs)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("alternate action", ignoreCase = true))
    }

    @Test
    fun recoveringOriginalActionCanResumeAtSameUnverifiedStep() {
        val original = AgentAction.TapLabel("Continue")
        val plan = CommandPlanner.Plan(
            actions = listOf(original),
            rejectedParts = emptyList()
        )
        val session = PersistedTaskSession(
            request = "tap continue",
            currentStep = 0,
            totalSteps = plan.actions.size,
            lastAction = original.toString(),
            lastScreenSummary = "pkg=com.example.app",
            recoveryAttempts = 1,
            status = AgentTaskState.Status.RECOVERING,
            savedAtMs = 1L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = session.savedAtMs)

        assertTrue(decision.allowed)
        assertEquals(0, decision.startStep)
        assertTrue(decision.reason.contains("first unverified checkpoint", ignoreCase = true))
    }
}
