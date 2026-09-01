package com.ruckus.agent

import com.ruckus.agent.core.ExecutionReport
import java.util.concurrent.CancellationException

/**
 * UI-facing execution boundary for command work launched from Compose.
 *
 * Recoverable Exceptions become a user-visible failed report. Coroutine cancellation and fatal
 * Error conditions must keep propagating so lifecycle cancellation and process-fatal failures are
 * never mislabeled as ordinary command failures.
 */
object ExecutionUiBoundary {
    suspend fun run(block: suspend () -> ExecutionReport): ExecutionReport =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ExecutionReport(false, failure.message ?: "Command execution failed")
        }
}
