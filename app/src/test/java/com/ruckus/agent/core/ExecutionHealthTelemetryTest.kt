package com.ruckus.agent.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(ExecutionHealthSnapshot(2, 0, 0, 0, 0, 0, 0, 0), snapshot)
        assertEquals(0, snapshot.classifiedEvents)
        assertEquals(2, snapshot.unclassifiedEvents)
    }

    @Test fun `action audit feeds aggregate telemetry without copying request payload`() {
        ActionAudit.record("sensitive request text", null, "TASK_COMPLETE: proof accepted")
        val snapshot = ExecutionHealthTelemetry.snapshot()
        assertEquals(1, snapshot.totalEvents)
        assertEquals(1, snapshot.completedTasks)
    }

    @Test fun `disabled telemetry drops events until explicitly reenabled`() {
        ExecutionHealthTelemetry.setEnabled(false)
        assertFalse(ExecutionHealthTelemetry.isEnabled())

        ExecutionHealthTelemetry.record("TASK_COMPLETE: should not count")
        ActionAudit.record("request still stays in local audit", null, "FAILED: should not count")
        assertEquals(ExecutionHealthSnapshot(0, 0, 0, 0, 0, 0, 0, 0), ExecutionHealthTelemetry.snapshot())

        ExecutionHealthTelemetry.setEnabled(true)
        assertTrue(ExecutionHealthTelemetry.isEnabled())
        ExecutionHealthTelemetry.record("TASK_COMPLETE: counted")
        assertEquals(1, ExecutionHealthTelemetry.snapshot().totalEvents)
        assertEquals(1, ExecutionHealthTelemetry.snapshot().completedTasks)
    }

    @Test fun `concurrent recording preserves exact totals and snapshot invariants`() {
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
                        assertEquals(snapshot.totalEvents, snapshot.classifiedEvents + snapshot.unclassifiedEvents)
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
        ExecutionHealthSnapshot(0, 1, 0, 0, 0, 0, 0, 0)
    }
}
