package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeLeaseDefenseInDepthTest {

    @Test
    fun staleActiveCheckpointIsRejectedEvenWhenResumePolicyCalledDirectly() {
        val now = 2_000_000L
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home), emptyList())
        val session = checkpoint(
            plan = plan,
            savedAtMs = now - SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS - 1L
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = now)

        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun freshActiveCheckpointRemainsEligibleForResume() {
        val now = 2_000_000L
        val plan = CommandPlanner.Plan(listOf(AgentAction.Home), emptyList())
        val session = checkpoint(
            plan = plan,
            savedAtMs = now - SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS
        )

        val decision = ResumePolicy.decide(session, plan, nowMs = now)

        assertTrue(decision.allowed)
    }

    private fun checkpoint(
        plan: CommandPlanner.Plan,
        savedAtMs: Long
    ) = PersistedTaskSession(
        request = "go home",
        currentStep = 0,
        totalSteps = plan.actions.size,
        lastAction = null,
        lastScreenSummary = "pkg=com.example",
        recoveryAttempts = 0,
        status = AgentTaskState.Status.RUNNING,
        savedAtMs = savedAtMs,
        planFingerprint = PlanFingerprint.of(plan)
    )
}
