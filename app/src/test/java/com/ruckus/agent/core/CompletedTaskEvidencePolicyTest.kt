package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedTaskEvidencePolicyTest {
    @Test
    fun currentPlannerTerminalActionKeepsCompletionEvidenceValid() {
        val session = completeSession("home then back")

        assertTrue(CompletedTaskEvidencePolicy.isStillValid(session))
    }

    @Test
    fun selfConsistentButWrongTerminalActionFailsClosed() {
        val session = completeSession(
            request = "home then back",
            lastActionOverride = AgentAction.Home.toString()
        )

        // The completion/checkpoint digests are deliberately recomputed for the wrong terminal
        // action. Structural integrity alone is therefore insufficient: current-plan semantic
        // revalidation must still reject the evidence.
        assertTrue(PersistedSessionIntegrityPolicy.evaluate(session).allowed)
        assertFalse(CompletedTaskEvidencePolicy.isStillValid(session))
    }

    private fun completeSession(
        request: String,
        lastActionOverride: String? = null
    ): PersistedTaskSession {
        val plan = CommandPlanner.plan(request)
        check(plan.actions.isNotEmpty() && plan.rejectedParts.isEmpty())

        val base = PersistedTaskSession(
            request = request,
            currentStep = plan.actions.size,
            totalSteps = plan.actions.size,
            lastAction = lastActionOverride ?: plan.actions.last().toString(),
            lastScreenSummary = "pkg=com.example.test",
            recoveryAttempts = 0,
            status = AgentTaskState.Status.COMPLETE,
            savedAtMs = System.currentTimeMillis(),
            planFingerprint = PlanFingerprint.of(plan),
            schemaVersion = PERSISTED_SESSION_SCHEMA_VERSION,
            completionEvidenceDigest = null,
            checkpointDigest = null
        )
        val withEvidence = base.copy(
            completionEvidenceDigest = TaskCompletionEvidence.compute(base)
        )
        return withEvidence.copy(
            checkpointDigest = PersistedSessionDigest.compute(withEvidence)
        )
    }
}
