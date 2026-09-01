package com.ruckus.agent.control

/**
 * Converts recoverable Android/API execution exceptions into Result failures without masking
 * fatal VM/linkage Errors that must remain visible to the process and crash reporting.
 */
internal object DeviceExecutionBoundary {
    fun <T> capture(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
