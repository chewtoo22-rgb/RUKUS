package com.ruckus.agent.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExecutionHealthTelemetryTest {
    @Before
    fun setUp() {
        ExecutionHealthTelemetry.resetForTests()
    }

    @After
    fun tearDown() {
        ExecutionHealthTelemetry.resetForTests()
    }

    @Test
    fun `classifies release health outcomes without payload storage`() {
        val outcomes = listOf(
            "OK+VERIFIED: semantic effect observed",
            "RECOVERY: retry after stale window",
            "PLAN_PREFLIGHT_BLOCKED: unsupported capability",
            "PLAN_AWAITING_CONFIRMATION: destructive action",
            "VERIFY_FAILED: expected state not observed",
            "FAILED: controller unavailable",
            "TASK_COMPLETE: completion evidence accepted"
        )

        outcomes.forEach(ExecutionHealthTelemetry::record)

        assertEquals(
            ExecutionHealthSnapshot(
                totalEvents = 7,
                completedTasks = 1,
                verifiedActions = 1,
                recoveries = 1,
                blockedActions = 1,
                confirmationWaits = 1,
                verificationFailures = 1,
                terminalFailures = 1
            ),
            ExecutionHealthTelemetry.snapshot()
        )
    }

    @Test
    fun `unknown audit outcomes only affect total event count`() {
        ExecutionHealthTelemetry.record("PLAN_PREFLIGHT_OK: safe")
        ExecutionHealthTelemetry.record("RESUME: allowed")

        assertEquals(
            ExecutionHealthSnapshot(
                totalEvents = 2,
                completedTasks = 0,
                verifiedActions = 0,
                recoveries = 0,
                blockedActions = 0,
                confirmationWaits = 0,
                verificationFailures = 0,
                terminalFailures = 0
            ),
            ExecutionHealthTelemetry.snapshot()
        )
    }

    @Test
    fun `action audit automatically feeds aggregate telemetry`() {
        ActionAudit.record("sensitive request text", null, "TASK_COMPLETE: proof accepted")

        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(1, snapshot.totalEvents)
        assertEquals(1, snapshot.completedTasks)
    }
}
