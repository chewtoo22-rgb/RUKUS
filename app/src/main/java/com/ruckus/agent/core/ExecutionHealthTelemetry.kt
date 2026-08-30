package com.ruckus.agent.core

import java.util.concurrent.atomic.AtomicLong

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
)

object ExecutionHealthTelemetry {
    private val totalEvents = AtomicLong()
    private val completedTasks = AtomicLong()
    private val verifiedActions = AtomicLong()
    private val recoveries = AtomicLong()
    private val blockedActions = AtomicLong()
    private val confirmationWaits = AtomicLong()
    private val verificationFailures = AtomicLong()
    private val terminalFailures = AtomicLong()

    fun record(outcome: String) {
        totalEvents.incrementAndGet()
        when {
            outcome.startsWith("TASK_COMPLETE:") -> completedTasks.incrementAndGet()
            outcome.startsWith("OK+VERIFIED:") -> verifiedActions.incrementAndGet()
            outcome.startsWith("RECOVERY:") || outcome.startsWith("REPLAN:") ||
                outcome.startsWith("VERIFY_REPLAN:") || outcome.startsWith("COMPLETION_REPAIR:") -> recoveries.incrementAndGet()
            outcome.startsWith("PLAN_PREFLIGHT_BLOCKED:") || outcome.startsWith("CRASH_AMBIGUOUS_BLOCKED:") ||
                outcome.startsWith("RECOVERY_BUDGET_EXHAUSTED:") -> blockedActions.incrementAndGet()
            outcome.startsWith("PLAN_AWAITING_CONFIRMATION:") || outcome == "AWAITING_ACTION_BOUND_CONFIRMATION" -> confirmationWaits.incrementAndGet()
            outcome.startsWith("VERIFY_FAILED:") || outcome.startsWith("COMPLETION_GATE_FAILED:") ||
                outcome.startsWith("COMPLETION_REPAIR_VERIFY_FAILED:") || outcome.startsWith("COMPLETION_REPAIR_GATE_FAILED:") -> verificationFailures.incrementAndGet()
            outcome.startsWith("FAILED:") || outcome.startsWith("COMPLETION_REPAIR_EXEC_FAILED:") -> terminalFailures.incrementAndGet()
        }
    }

    fun snapshot() = ExecutionHealthSnapshot(
        totalEvents.get(), completedTasks.get(), verifiedActions.get(), recoveries.get(),
        blockedActions.get(), confirmationWaits.get(), verificationFailures.get(), terminalFailures.get()
    )

    internal fun resetForTests() {
        totalEvents.set(0); completedTasks.set(0); verifiedActions.set(0); recoveries.set(0)
        blockedActions.set(0); confirmationWaits.set(0); verificationFailures.set(0); terminalFailures.set(0)
    }
}
