package com.ruckus.agent.core

/**
 * Bounds how long an interrupted active task may remain eligible for automatic resume.
 *
 * Crash recovery is intended to bridge a short process/device interruption, not to resurrect an
 * old plan against a phone whose external state may have changed substantially. Terminal sessions
 * remain readable for diagnostics; only active execution states are lease-bound.
 */
object SessionResumeLeasePolicy {
    const val MAX_ACTIVE_AGE_MS = 15 * 60 * 1000L
    private const val MAX_FUTURE_SKEW_MS = 5_000L

    data class Decision(val allowed: Boolean, val reason: String)

    fun evaluate(
        savedAtMs: Long,
        status: AgentTaskState.Status,
        nowMs: Long = System.currentTimeMillis()
    ): Decision {
        if (status == AgentTaskState.Status.COMPLETE || status == AgentTaskState.Status.FAILED) {
            return Decision(true, "Terminal checkpoint retained for diagnostics")
        }

        if (savedAtMs <= 0L) {
            return Decision(false, "Active checkpoint is missing a valid save timestamp")
        }

        if (savedAtMs > nowMs + MAX_FUTURE_SKEW_MS) {
            return Decision(false, "Active checkpoint timestamp is implausibly in the future")
        }

        val ageMs = (nowMs - savedAtMs).coerceAtLeast(0L)
        if (ageMs > MAX_ACTIVE_AGE_MS) {
            return Decision(false, "Active checkpoint resume lease expired")
        }

        return Decision(true, "Active checkpoint is within the resume lease")
    }
}
