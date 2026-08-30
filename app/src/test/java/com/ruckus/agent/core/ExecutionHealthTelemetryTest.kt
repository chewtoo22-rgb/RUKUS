package com.ruckus.agent.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExecutionHealthTelemetryTest {
    @Before fun setUp() = ExecutionHealthTelemetry.resetForTests()
    @After fun tearDown() = ExecutionHealthTelemetry.resetForTests()

    @Test fun `classifies release health outcomes`() {
        listOf(
            "OK+VERIFIED: observed", "RECOVERY: retry", "PLAN_PREFLIGHT_BLOCKED: unsupported",
            "PLAN_AWAITING_CONFIRMATION: destructive", "VERIFY_FAILED: missing state",
            "FAILED: controller unavailable", "TASK_COMPLETE: proof accepted"
        ).forEach(ExecutionHealthTelemetry::record)
        assertEquals(ExecutionHealthSnapshot(7, 1, 1, 1, 1, 1, 1, 1), ExecutionHealthTelemetry.snapshot())
    }

    @Test fun `unknown outcomes only affect total count`() {
        ExecutionHealthTelemetry.record("PLAN_PREFLIGHT_OK: safe")
        ExecutionHealthTelemetry.record("RESUME: allowed")
        assertEquals(ExecutionHealthSnapshot(2, 0, 0, 0, 0, 0, 0, 0), ExecutionHealthTelemetry.snapshot())
    }

    @Test fun `action audit feeds aggregate telemetry without copying request payload`() {
        ActionAudit.record("sensitive request text", null, "TASK_COMPLETE: proof accepted")
        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(1, snapshot.totalEvents)
        assertEquals(1, snapshot.completedTasks)
    }
}
