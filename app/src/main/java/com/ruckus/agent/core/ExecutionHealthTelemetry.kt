package com.ruckus.agent.core

/** Local-only aggregate release-health counters. No request/action payloads or timestamps. */
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
    private var enabled = true
    private var totalEvents = 0L
    private var completedTasks = 0L
    private var verifiedActions = 0L
    private var recoveries = 0L
    private var blockedActions = 0L
    private var confirmationWaits = 0L
    private var verificationFailures = 0L
    private var terminalFailures = 0L

    fun setEnabled(value: Boolean) = synchronized(lock) {
        enabled = value
    }

    fun isEnabled(): Boolean = synchronized(lock) { enabled }

    fun record(outcome: String) = synchronized(lock) {
        if (!enabled) return@synchronized

        totalEvents++
        when {
            outcome.startsWith("TASK_COMPLETE:") -> completedTasks++
            outcome.startsWith("OK+VERIFIED:") -> verifiedActions++
            outcome.startsWith("RECOVERY:") || outcome.startsWith("REPLAN:") ||
                outcome.startsWith("VERIFY_REPLAN:") || outcome.startsWith("COMPLETION_REPAIR:") -> recoveries++
            outcome.startsWith("PLAN_PREFLIGHT_BLOCKED:") || outcome.startsWith("CRASH_AMBIGUOUS_BLOCKED:") ||
                outcome.startsWith("RECOVERY_BUDGET_EXHAUSTED:") -> blockedActions++
            outcome.startsWith("PLAN_AWAITING_CONFIRMATION:") || outcome == "AWAITING_ACTION_BOUND_CONFIRMATION" -> confirmationWaits++
            outcome.startsWith("VERIFY_FAILED:") || outcome.startsWith("COMPLETION_GATE_FAILED:") ||
                outcome.startsWith("COMPLETION_REPAIR_VERIFY_FAILED:") || outcome.startsWith("COMPLETION_REPAIR_GATE_FAILED:") -> verificationFailures++
            outcome.startsWith("FAILED:") || outcome.startsWith("COMPLETION_REPAIR_EXEC_FAILED:") -> terminalFailures++
        }
    }

    fun snapshot(): ExecutionHealthSnapshot = synchronized(lock) {
        ExecutionHealthSnapshot(
            totalEvents, completedTasks, verifiedActions, recoveries,
            blockedActions, confirmationWaits, verificationFailures, terminalFailures
        )
    }

    internal fun resetForTests() = synchronized(lock) {
        enabled = true
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
