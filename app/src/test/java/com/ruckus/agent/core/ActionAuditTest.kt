package com.ruckus.agent.core

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
}
