package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedTaskEvidencePolicyTest {
    @Test
    fun currentPlannerTerminalActionAndProofKeepCompletionEvidenceValid() {
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

    @Test
    fun selfConsistentButNoLongerSufficientTerminalObservationFailsClosed() {
        val session = completeSession(
            request = "home then back",
            lastScreenSummaryOverride = "screen=Home | verified=true"
        )

        // The stored digests are valid, but the current completion gate requires package-aware
        // terminal UI evidence for Back/Home-style goals. Durable evidence must be re-proven
        // under the shipping completion semantics rather than trusted forever.
        assertTrue(PersistedSessionIntegrityPolicy.evaluate(session).allowed)
        assertFalse(CompletedTaskEvidencePolicy.isStillValid(session))
    }

    private fun completeSession(
        request: String,
        lastActionOverride: String? = null,
        lastScreenSummaryOverride: String? = null
    ): PersistedTaskSession {
        val plan = CommandPlanner.plan(request)
        check(plan.actions.isNotEmpty() && plan.rejectedParts.isEmpty())

        val base = PersistedTaskSession(
            request = request,
            currentStep = plan.actions.size,
            totalSteps = plan.actions.size,
            lastAction = lastActionOverride ?: plan.actions.last().toString(),
            lastScreenSummary = lastScreenSummaryOverride ?: "pkg=com.example.test | screen=Home | verified=true",
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
