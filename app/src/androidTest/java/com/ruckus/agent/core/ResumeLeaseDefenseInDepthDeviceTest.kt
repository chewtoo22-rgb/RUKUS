package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android-runtime probes that keep invalid crash checkpoints outside resume authority. */
class ResumeLeaseDefenseInDepthDeviceTest {

    @Test
    fun staleActiveCheckpointCannotResumeThroughPolicyBoundary() {
        val now = 3_000_000L
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home), emptyList())
        val session = PersistedTaskSession(
            request = "go home",
            currentStep = 0,
            totalSteps = plan.actions.size,
            lastAction = null,
            lastScreenSummary = "pkg=com.example",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.RUNNING,
            savedAtMs = now - SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS - 1L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = now)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun implausiblyFutureCheckpointCannotResumeThroughPolicyBoundary() {
        val now = 3_000_000L
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home), emptyList())
        val session = PersistedTaskSession(
            request = "go home",
            currentStep = 0,
            totalSteps = plan.actions.size,
            lastAction = null,
            lastScreenSummary = "pkg=com.example",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.RUNNING,
            savedAtMs = now + 60_000L,
            planFingerprint = PlanFingerprint.of(plan)
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = now)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("future", ignoreCase = true))
    }
}
