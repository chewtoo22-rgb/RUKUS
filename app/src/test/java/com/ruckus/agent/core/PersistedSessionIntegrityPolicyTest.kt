package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class PersistedSessionIntegrityPolicyTest {
    private fun session(
        request:String="tap Continue",
        currentStep:Int=0,
        totalSteps:Int=1,
        lastAction:String?="TapLabel(label=Continue)",
        recoveryAttempts:Int=0,
        status:AgentTaskState.Status=AgentTaskState.Status.WAITING_CONFIRMATION,
        planFingerprint:String?="plan-fingerprint",
        schemaVersion:Int=PERSISTED_SESSION_SCHEMA_VERSION,
        sign:Boolean=true
    ): PersistedTaskSession {
        val base = PersistedTaskSession(
            request=request,
            currentStep=currentStep,
            totalSteps=totalSteps,
            lastAction=lastAction,
            lastScreenSummary="pkg=test\ntext=Continue",
            recoveryAttempts=recoveryAttempts,
            status=status,
            savedAtMs=1_000_000L,
            planFingerprint=planFingerprint,
            schemaVersion=schemaVersion,
            completionEvidenceDigest=null,
            checkpointDigest=null
        )
        val unsigned = if (status == AgentTaskState.Status.COMPLETE) {
            base.copy(completionEvidenceDigest = TaskCompletionEvidence.compute(base))
        } else {
            base
        }
        return if (sign) {
            unsigned.copy(checkpointDigest=PersistedSessionDigest.compute(unsigned))
        } else {
            unsigned
        }
    }

    @Test fun validPendingCheckpointIsAccepted() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session())
        assertTrue(decision.allowed)
    }

    @Test fun checkpointWithoutDigestFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(sign=false))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("integrity digest"))
    }

    @Test fun semanticMutationAfterPersistenceFailsClosed() {
        val persisted=session()
        val corrupted=persisted.copy(request="tap Delete")
        val decision=PersistedSessionIntegrityPolicy.evaluate(corrupted)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("does not match"))
    }

    @Test fun stepMutationAfterPersistenceFailsClosed() {
        val persisted=session(currentStep=0,totalSteps=2)
        val corrupted=persisted.copy(currentStep=1)
        val decision=PersistedSessionIntegrityPolicy.evaluate(corrupted)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("does not match"))
    }

    @Test fun legacyCheckpointWithoutCurrentSchemaFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(schemaVersion=0))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("schema version"))
    }

    @Test fun futureCheckpointSchemaFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(
            session(schemaVersion=PERSISTED_SESSION_SCHEMA_VERSION+1)
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("schema version"))
    }

    @Test fun stepPastPlanEndFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(currentStep=2,totalSteps=1))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("bounds"))
    }

    @Test fun negativeRecoveryCountFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(recoveryAttempts=-1))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("recovery"))
    }

    @Test fun recoveryCountBeyondGlobalBudgetFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(
            session(recoveryAttempts=RecoveryBudget.MAX_TOTAL_ATTEMPTS+1)
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("recovery"))
    }

    @Test fun activeCheckpointWithoutPlanFingerprintFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(planFingerprint=null))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("fingerprint"))
    }

    @Test fun pendingCheckpointWithoutActionIdentityFailsClosed() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(lastAction=null))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("action identity"))
    }

    @Test fun waitingCheckpointCannotPointAtCompletedPlan() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(session(currentStep=1,totalSteps=1))
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("past the end"))
    }

    @Test fun completeCheckpointMustAccountForEveryStep() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(
            session(currentStep=0,totalSteps=1,status=AgentTaskState.Status.COMPLETE)
        )
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("every action"))
    }

    @Test fun completeCheckpointAtPlanEndIsAccepted() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(
            session(currentStep=1,totalSteps=1,status=AgentTaskState.Status.COMPLETE)
        )
        assertTrue(decision.allowed)
    }

    @Test fun completeCheckpointWithoutEvidenceFailsClosedEvenWhenCheckpointIsResigned() {
        val completed=session(currentStep=1,totalSteps=1,status=AgentTaskState.Status.COMPLETE)
        val withoutEvidence=completed.copy(completionEvidenceDigest=null,checkpointDigest=null)
        val resigned=withoutEvidence.copy(checkpointDigest=PersistedSessionDigest.compute(withoutEvidence))
        val decision=PersistedSessionIntegrityPolicy.evaluate(resigned)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("completion evidence"))
    }

    @Test fun completionEvidenceMutationFailsClosedEvenWhenCheckpointIsResigned() {
        val completed=session(currentStep=1,totalSteps=1,status=AgentTaskState.Status.COMPLETE)
        val mutated=completed.copy(lastScreenSummary="pkg=other\ntext=Unexpected",checkpointDigest=null)
        val resigned=mutated.copy(checkpointDigest=PersistedSessionDigest.compute(mutated))
        val decision=PersistedSessionIntegrityPolicy.evaluate(resigned)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("completion evidence"))
    }

    @Test fun nonCompleteCheckpointCannotCarryStaleCompletionEvidence() {
        val pending=session().copy(completionEvidenceDigest="deadbeef",checkpointDigest=null)
        val resigned=pending.copy(checkpointDigest=PersistedSessionDigest.compute(pending))
        val decision=PersistedSessionIntegrityPolicy.evaluate(resigned)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("stale completion evidence"))
    }

    @Test fun failedZeroActionCheckpointRemainsValidForDiagnostics() {
        val decision=PersistedSessionIntegrityPolicy.evaluate(
            session(
                request="nonsense",
                currentStep=0,
                totalSteps=0,
                lastAction=null,
                status=AgentTaskState.Status.FAILED,
                planFingerprint=null
            )
        )
        assertTrue(decision.allowed)
    }
}
