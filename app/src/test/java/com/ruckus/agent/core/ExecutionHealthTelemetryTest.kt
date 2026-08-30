package com.ruckus.agent.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(2, snapshot.totalEvents)
        assertEquals(0, snapshot.classifiedEvents)
        assertEquals(2, snapshot.unclassifiedEvents)
    }

    @Test
    fun `action audit automatically feeds aggregate telemetry`() {
        ActionAudit.record("sensitive request text", null, "TASK_COMPLETE: proof accepted")

        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(1, snapshot.totalEvents)
        assertEquals(1, snapshot.completedTasks)
    }

    @Test
    fun `concurrent recording preserves exact totals and snapshot invariants`() {
        val workers = 8
        val recordsPerWorker = 500
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val pool = Executors.newFixedThreadPool(workers)

        try {
            repeat(workers) { worker ->
                pool.execute {
                    start.await()
                    repeat(recordsPerWorker) { index ->
                        val outcome = when ((worker + index) % 4) {
                            0 -> "TASK_COMPLETE: proof"
                            1 -> "VERIFY_FAILED: mismatch"
                            2 -> "PLAN_PREFLIGHT_OK: safe"
                            else -> "RECOVERY: retry"
                        }
                        ExecutionHealthTelemetry.record(outcome)
                        val snapshot = ExecutionHealthTelemetry.snapshot()
                        assertTrue(snapshot.classifiedEvents <= snapshot.totalEvents)
                        assertEquals(
                            snapshot.totalEvents,
                            snapshot.classifiedEvents + snapshot.unclassifiedEvents
                        )
                    }
                    done.countDown()
                }
            }

            start.countDown()
            assertTrue("workers did not finish", done.await(10, TimeUnit.SECONDS))

            val snapshot = ExecutionHealthTelemetry.snapshot()
            assertEquals((workers * recordsPerWorker).toLong(), snapshot.totalEvents)
            assertEquals(snapshot.totalEvents, snapshot.classifiedEvents + snapshot.unclassifiedEvents)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `snapshot refuses impossible classified totals`() {
        ExecutionHealthSnapshot(
            totalEvents = 0,
            completedTasks = 1,
            verifiedActions = 0,
            recoveries = 0,
            blockedActions = 0,
            confirmationWaits = 0,
            verificationFailures = 0,
            terminalFailures = 0
        )
    }
}
