package com.ruckus.agent.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Non-blocking single-execution lease used to prevent overlapping command and
 * resume flows from mutating the same device/session state concurrently.
 */
internal class ExecutionAdmissionGate {
    private val held = AtomicBoolean(false)

    fun tryAcquire(): Boolean = held.compareAndSet(false, true)

    fun release() {
        check(held.compareAndSet(true, false)) { "Execution admission lease was not held" }
    }

    fun isHeld(): Boolean = held.get()
}
