package com.ruckus.agent.core

/**
 * Local-only execution health counters for release diagnostics.
 *
 * Deliberately records no request text, action arguments, screen content, package names,
 * or timestamps. This is plumbing for aggregate health signals, not user analytics.
 *
 * Record and snapshot operations share one lock so callers never observe an impossible
 * partially-updated snapshot while executor/audit events are arriving concurrently.
 */
data class ExecutionHealthSnapshot(
    val totalEvents: Long,
    val completedTasks: Long,
    val verifiedActions: Long,
    val recoveries: Long,
    val blockedActions: Long,
    val confirmationWaits: Long,
    val verificationFailures: Long,
    val terminalFailures: Long
) {
    init {
        require(totalEvents >= 0)
        require(completedTasks >= 0)
        require(verifiedActions >= 0)
        require(recoveries >= 0)
        require(blockedActions >= 0)
        require(confirmationWaits >= 0)
        require(verificationFailures >= 0)
        require(terminalFailures >= 0)
        require(classifiedEvents <= totalEvents) {
            "classified execution-health events cannot exceed total events"
        }
    }

    val classifiedEvents: Long
        get() = completedTasks + verifiedActions + recoveries + blockedActions +
            confirmationWaits + verificationFailures + terminalFailures

    val unclassifiedEvents: Long
        get() = totalEvents - classifiedEvents
}

object ExecutionHealthTelemetry {
    private val lock = Any()
    private var totalEvents = 0L
    private var completedTasks = 0L
    private var verifiedActions = 0L
    private var recoveries = 0L
    private var blockedActions = 0L
    private var confirmationWaits = 0L
    private var verificationFailures = 0L
    private var terminalFailures = 0L

    fun record(outcome: String) = synchronized(lock) {
        totalEvents++
        when {
            outcome.startsWith("TASK_COMPLETE:") -> completedTasks++
            outcome.startsWith("OK+VERIFIED:") -> verifiedActions++
            outcome.startsWith("RECOVERY:") ||
                outcome.startsWith("REPLAN:") ||
                outcome.startsWith("VERIFY_REPLAN:") ||
                outcome.startsWith("COMPLETION_REPAIR:") -> recoveries++
            outcome.startsWith("PLAN_PREFLIGHT_BLOCKED:") ||
                outcome.startsWith("CRASH_AMBIGUOUS_BLOCKED:") ||
                outcome.startsWith("RECOVERY_BUDGET_EXHAUSTED:") -> blockedActions++
            outcome.startsWith("PLAN_AWAITING_CONFIRMATION:") ||
                outcome == "AWAITING_ACTION_BOUND_CONFIRMATION" -> confirmationWaits++
            outcome.startsWith("VERIFY_FAILED:") ||
                outcome.startsWith("COMPLETION_GATE_FAILED:") ||
                outcome.startsWith("COMPLETION_REPAIR_VERIFY_FAILED:") ||
                outcome.startsWith("COMPLETION_REPAIR_GATE_FAILED:") -> verificationFailures++
            outcome.startsWith("FAILED:") ||
                outcome.startsWith("COMPLETION_REPAIR_EXEC_FAILED:") -> terminalFailures++
        }
    }

    fun snapshot(): ExecutionHealthSnapshot = synchronized(lock) {
        ExecutionHealthSnapshot(
            totalEvents = totalEvents,
            completedTasks = completedTasks,
            verifiedActions = verifiedActions,
            recoveries = recoveries,
            blockedActions = blockedActions,
            confirmationWaits = confirmationWaits,
            verificationFailures = verificationFailures,
            terminalFailures = terminalFailures
        )
    }

    internal fun resetForTests() = synchronized(lock) {
        totalEvents = 0
        completedTasks = 0
        verifiedActions = 0
        recoveries = 0
        blockedActions = 0
        confirmationWaits = 0
        verificationFailures = 0
        terminalFailures = 0
    }
}
