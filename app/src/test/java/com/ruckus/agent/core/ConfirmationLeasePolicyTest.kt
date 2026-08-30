package com.ruckus.agent.core

import org.junit.Assert.*
import org.junit.Test

class ConfirmationLeasePolicyTest {
    @Test fun freshCheckpointIsAccepted() {
        val now=1_000_000L
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=now-30_000L,nowMs=now)
        assertTrue(decision.allowed)
    }

    @Test fun expiredCheckpointIsRejected() {
        val now=1_000_000L
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=now-60_001L,nowMs=now)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("expired"))
    }

    @Test fun boundaryAgeIsStillAccepted() {
        val now=1_000_000L
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=now-ConfirmationLeasePolicy.MAX_AGE_MS,nowMs=now)
        assertTrue(decision.allowed)
    }

    @Test fun missingTimestampFailsClosed() {
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=0L,nowMs=1_000_000L)
        assertFalse(decision.allowed)
    }

    @Test fun futureTimestampFailsClosed() {
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=1_001_000L,nowMs=1_000_000L)
        assertFalse(decision.allowed)
        assertTrue(decision.reason.contains("future"))
    }

    @Test fun invalidLeaseDurationFailsClosed() {
        val decision=ConfirmationLeasePolicy.evaluate(savedAtMs=999_000L,nowMs=1_000_000L,maxAgeMs=0L)
        assertFalse(decision.allowed)
    }
}
