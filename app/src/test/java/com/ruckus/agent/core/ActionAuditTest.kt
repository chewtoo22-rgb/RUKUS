package com.ruckus.agent.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionAuditTest {
    @Before
    fun setUp() {
        ActionAudit.resetForTests()
        ExecutionHealthTelemetry.resetForTests()
    }

    @After
    fun tearDown() {
        ActionAudit.resetForTests()
        ExecutionHealthTelemetry.resetForTests()
    }

    @Test
    fun zeroLimitReturnsNoAuditRecords() {
        ActionAudit.record("sensitive request", AgentAction.Home, "TASK_COMPLETE: done")

        assertTrue(ActionAudit.recent(0).isEmpty())
    }

    @Test
    fun negativeLimitReturnsNoAuditRecords() {
        ActionAudit.record("sensitive request", AgentAction.Home, "TASK_COMPLETE: done")

        assertTrue(ActionAudit.recent(-5).isEmpty())
    }

    @Test
    fun positiveLimitStillReturnsNewestRecordsOnly() {
        ActionAudit.record("first", AgentAction.Home, "TASK_COMPLETE: first")
        ActionAudit.record("second", AgentAction.Back, "TASK_COMPLETE: second")

        val recent = ActionAudit.recent(1)
        assertEquals(1, recent.size)
        assertEquals("second", recent.single().request)
    }

    @Test
    fun concurrentRecordingNeverExceedsStorageBound() {
        val workers = 8
        val writesPerWorker = 250
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val pool = Executors.newFixedThreadPool(workers)

        repeat(workers) { worker ->
            pool.execute {
                try {
                    start.await()
                    repeat(writesPerWorker) { index ->
                        ActionAudit.record(
                            "request-$worker-$index",
                            AgentAction.Home,
                            "TASK_COMPLETE: $worker-$index",
                        )
                    }
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertEquals(100, ActionAudit.storedCountForTests())
        assertEquals(100, ActionAudit.recent(100).size)
    }
}
