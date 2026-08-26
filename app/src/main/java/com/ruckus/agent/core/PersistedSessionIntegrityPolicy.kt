package com.ruckus.agent.core

data class PersistedSessionIntegrityDecision(
    val allowed: Boolean,
    val reason: String
)

/**
 * Validates persisted executor checkpoints before resume logic is allowed to trust them.
 * SharedPreferences is durable storage, not a trusted execution authority: malformed,
 * partially-written, legacy, or corrupted state must fail closed instead of creating
 * impossible step indices or bypassing resume invariants.
 */
object PersistedSessionIntegrityPolicy {
    fun evaluate(session: PersistedTaskSession): PersistedSessionIntegrityDecision {
        if (session.schemaVersion != PERSISTED_SESSION_SCHEMA_VERSION) {
            return reject("Persisted task schema version is unsupported")
        }
        if (session.checkpointDigest.isNullOrBlank()) {
            return reject("Persisted task checkpoint has no integrity digest")
        }
        if (!PersistedSessionDigest.matches(session)) {
            return reject("Persisted task checkpoint integrity digest does not match")
        }
        if (session.request.isBlank()) {
            return reject("Persisted task request is blank")
        }
        if (session.totalSteps < 0) {
            return reject("Persisted total step count is negative")
        }
        if (session.currentStep < 0 || session.currentStep > session.totalSteps) {
            return reject("Persisted current step is outside the task bounds")
        }
        if (session.recoveryAttempts !in 0..RecoveryBudget.MAX_TOTAL_ATTEMPTS) {
            return reject("Persisted recovery count is outside the bounded recovery budget")
        }

        val active = session.status in setOf(
            AgentTaskState.Status.RUNNING,
            AgentTaskState.Status.RECOVERING,
            AgentTaskState.Status.EXECUTING,
            AgentTaskState.Status.WAITING_CONFIRMATION,
            AgentTaskState.Status.COMPLETE
        )
        if (active && session.totalSteps == 0) {
            return reject("Active persisted task has no actions")
        }
        if (active && session.planFingerprint.isNullOrBlank()) {
            return reject("Active persisted task has no plan fingerprint")
        }

        if (session.status == AgentTaskState.Status.RECOVERING ||
            session.status == AgentTaskState.Status.EXECUTING ||
            session.status == AgentTaskState.Status.WAITING_CONFIRMATION
        ) {
            if (session.currentStep >= session.totalSteps) {
                return reject("Pending action checkpoint points past the end of the plan")
            }
            if (session.lastAction.isNullOrBlank()) {
                return reject("Pending action checkpoint has no action identity")
            }
        }

        if (session.status == AgentTaskState.Status.COMPLETE) {
            if (session.currentStep != session.totalSteps) {
                return reject("Complete checkpoint does not account for every action")
            }
            if (session.lastAction.isNullOrBlank()) {
                return reject("Complete checkpoint has no terminal action identity")
            }
            if (session.completionEvidenceDigest.isNullOrBlank()) {
                return reject("Complete checkpoint has no completion evidence digest")
            }
            if (!TaskCompletionEvidence.matches(session)) {
                return reject("Complete checkpoint completion evidence does not match terminal state")
            }
        } else if (!session.completionEvidenceDigest.isNullOrBlank()) {
            return reject("Non-complete checkpoint carries stale completion evidence")
        }

        return PersistedSessionIntegrityDecision(true, "Persisted task checkpoint is structurally valid and corruption-free")
    }

    private fun reject(reason: String) = PersistedSessionIntegrityDecision(false, reason)
}
