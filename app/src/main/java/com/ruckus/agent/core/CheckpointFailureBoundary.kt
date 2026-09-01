package com.ruckus.agent.core

internal object CheckpointFailureBoundary {
    const val MESSAGE = "Execution stopped because durable task checkpoint storage is unavailable"

    fun execute(block: () -> ExecutionReport): ExecutionReport = try {
        block()
    } catch (_: CheckpointPersistenceException) {
        ExecutionReport(ok = false, message = MESSAGE)
    }
}
