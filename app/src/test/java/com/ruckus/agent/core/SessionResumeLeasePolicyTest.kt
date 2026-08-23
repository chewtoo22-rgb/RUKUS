package com.ruckus.agent.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResumeLeasePolicyTest {
    private val now = 1_000_000L

    @Test
    fun fresh_active_checkpoint_is_allowed() {
        val decision = SessionResumeLeasePolicy.evaluate(
            savedAtMs = now - 1_000L,
            status = AgentTaskState.Status.RUNNING,
            nowMs = now
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun checkpoint_at_lease_boundary_is_allowed() {
        val decision = SessionResumeLeasePolicy.evaluate(
            savedAtMs = now - SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS,
            status = AgentTaskState.Status.EXECUTING,
            nowMs = now
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun stale_active_checkpoint_is_rejected() {
        val decision = SessionResumeLeasePolicy.evaluate(
            savedAtMs = now - SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS - 1L,
            status = AgentTaskState.Status.RECOVERING,
            nowMs = now
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun missing_active_timestamp_is_rejected() {
        assertFalse(
            SessionResumeLeasePolicy.evaluate(
                savedAtMs = 0L,
                status = AgentTaskState.Status.RUNNING,
                nowMs = now
            ).allowed
        )
    }

    @Test
    fun implausibly_future_active_checkpoint_is_rejected() {
        assertFalse(
            SessionResumeLeasePolicy.evaluate(
                savedAtMs = now + 5_001L,
                status = AgentTaskState.Status.WAITING_CONFIRMATION,
                nowMs = now
            ).allowed
        )
    }

    @Test
    fun small_clock_skew_is_tolerated() {
        assertTrue(
            SessionResumeLeasePolicy.evaluate(
                savedAtMs = now + 5_000L,
                status = AgentTaskState.Status.RUNNING,
                nowMs = now
            ).allowed
        )
    }

    @Test
    fun terminal_failed_checkpoint_is_retained_even_when_old() {
        assertTrue(
            SessionResumeLeasePolicy.evaluate(
                savedAtMs = 1L,
                status = AgentTaskState.Status.FAILED,
                nowMs = now + SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS * 10
            ).allowed
        )
    }

    @Test
    fun terminal_complete_checkpoint_is_retained_even_when_old() {
        assertTrue(
            SessionResumeLeasePolicy.evaluate(
                savedAtMs = 1L,
                status = AgentTaskState.Status.COMPLETE,
                nowMs = now + SessionResumeLeasePolicy.MAX_ACTIVE_AGE_MS * 10
            ).allowed
        )
    }
}
