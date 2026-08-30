package com.ruckus.agent.core

data class CrashReplayAdmissionDecision(
    val replayAllowed: Boolean,
    val nextRecoveryAttempts: Int,
    val reason: String
)

/**
 * Composes crash replay safety with the task-wide recovery budget.
 *
 * A replay after an ambiguous process death is still autonomous recovery work,
 * so it must consume the same bounded budget as retries/replans. This prevents
 * repeated crashes from silently resetting or bypassing the task-wide cap.
 */
object CrashReplayAdmissionPolicy {
    fun decide(action: AgentAction, recoveryAttempts: Int): CrashReplayAdmissionDecision {
        val replay = CrashRecoveryPolicy.decide(action)
        if (!replay.replayAllowed) {
            return CrashReplayAdmissionDecision(
                replayAllowed = false,
                nextRecoveryAttempts = recoveryAttempts,
                reason = replay.reason
            )
        }

        val budget = RecoveryBudget.decide(recoveryAttempts)
        if (!budget.allowed) {
            return CrashReplayAdmissionDecision(
                replayAllowed = false,
                nextRecoveryAttempts = recoveryAttempts,
                reason = budget.reason
            )
        }

        return CrashReplayAdmissionDecision(
            replayAllowed = true,
            nextRecoveryAttempts = recoveryAttempts + 1,
            reason = "${replay.reason}; ${budget.reason}"
        )
    }
}
