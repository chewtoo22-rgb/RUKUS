package com.ruckus.agent.core

data class ConfirmationLeaseDecision(
    val allowed: Boolean,
    val reason: String
)

/**
 * Bounds how long a persisted WAITING_CONFIRMATION checkpoint may be used to
 * authorize a high-impact action. Approval is intentionally short-lived so a
 * user response cannot be replayed against a task that has been sitting idle.
 */
object ConfirmationLeasePolicy {
    const val MAX_AGE_MS: Long = 60_000L

    fun evaluate(
        savedAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = MAX_AGE_MS
    ): ConfirmationLeaseDecision {
        if (savedAtMs <= 0L) {
            return ConfirmationLeaseDecision(false, "Confirmation checkpoint has no valid timestamp")
        }
        if (maxAgeMs <= 0L) {
            return ConfirmationLeaseDecision(false, "Confirmation lease duration is invalid")
        }
        if (nowMs < savedAtMs) {
            return ConfirmationLeaseDecision(false, "Confirmation checkpoint timestamp is in the future")
        }

        val ageMs = nowMs - savedAtMs
        if (ageMs > maxAgeMs) {
            return ConfirmationLeaseDecision(false, "Confirmation checkpoint expired after ${ageMs}ms")
        }

        return ConfirmationLeaseDecision(true, "Confirmation checkpoint is fresh (${ageMs}ms old)")
    }
}
