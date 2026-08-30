package com.ruckus.agent.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android-runtime probe for durable COMPLETE evidence semantic revalidation. */
@RunWith(AndroidJUnit4::class)
class CompletedTaskEvidencePolicyDeviceTest {
    @Test
    fun durableCompletionRequiresCurrentTerminalProofSemantics() {
        val valid = completeSession(
            request = "home then back",
            finalScreen = "pkg=com.example.launcher | screen=Home | verified=true"
        )
        val weakButSelfConsistent = completeSession(
            request = "home then back",
            finalScreen = "screen=Home | verified=true"
        )

        assertTrue(PersistedSessionIntegrityPolicy.evaluate(valid).allowed)
        assertTrue(CompletedTaskEvidencePolicy.isStillValid(valid))

        // The checkpoint/evidence digests are intentionally valid for this weaker observation.
        // The current TaskCompletionGate must still reject it because terminal UI proof for the
        // final Back action is no longer package-aware.
        assertTrue(PersistedSessionIntegrityPolicy.evaluate(weakButSelfConsistent).allowed)
        assertFalse(CompletedTaskEvidencePolicy.isStillValid(weakButSelfConsistent))
    }

    private fun completeSession(request: String, finalScreen: String): PersistedTaskSession {
        val plan = CommandPlanner.plan(request)
        check(plan.actions.isNotEmpty() && plan.rejectedParts.isEmpty())

        val base = PersistedTaskSession(
            request = request,
            currentStep = plan.actions.size,
            totalSteps = plan.actions.size,
            lastAction = plan.actions.last().toString(),
            lastScreenSummary = finalScreen,
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
